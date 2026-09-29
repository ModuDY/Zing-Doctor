package com.zing.doctor.quality.service;

import com.zing.doctor.quality.config.QualityProperties;
import com.zing.doctor.quality.dsl.FactDefinition;
import com.zing.doctor.quality.dsl.MetricDefinition;
import com.zing.doctor.quality.dsl.SourceConfig;
import com.zing.doctor.quality.engine.QualityDslLoader;
import com.zing.doctor.quality.engine.QualityExpressionAnalyzer;
import com.zing.doctor.quality.engine.QualitySqlGuard;
import com.zing.doctor.quality.engine.QualitySqlMapper;
import com.zing.doctor.quality.engine.SqlCompiler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 质控配置四级校验链的单元测试（<b>不连数据库、不启动 Spring</b>）。
 *
 * <p><b>为什么必须给这条链路补测试</b>：可视化配置把「改生产口径」的权力交给了页面使用者，
 * 而 {@link QualityConfigValidator} 是这条路径上<b>唯一的护栏</b> —— 它一旦漏判，
 * 错误的口径会直接落库并参与全院的月度批算，且事后只能靠人工比对发现。
 * 这类代码「看起来没问题」不代表没问题，必须用测试把判定边界钉死。
 *
 * <p><b>怎么做到离线</b>：DSL 加载器被替换成一个只返回内存事实层的匿名子类，
 * L4 试跑依赖的 {@link QualitySqlMapper} 用 Mockito 打桩，因此整个测试
 * 只验证「表达式 → 判定结论 / SQL 文本」，不执行任何 SQL。
 *
 * <p>覆盖：基本字段、枚举取值、率类放大系数的「告警 vs 错误」分界、L1 安全拦截
 * （分隔符 / 子查询 / 括号引号配平）、L3 列名判定与「无从判定时跳过」、
 * L2 编译、L4 试跑的成功 / 空结果 / 异常，以及 OR 条件组的 SQL 优先级。
 */
class QualityConfigValidatorTest {

    /** 测试用事实层名 */
    private static final String FACT = "fact_abx";

    private QualityProperties props;
    private QualitySqlMapper sqlMapper;
    private QualityConfigValidator validator;

    @BeforeEach
    void setUp() {
        props = new QualityProperties();
        sqlMapper = mock(QualitySqlMapper.class);

        Map<String, FactDefinition> facts = new LinkedHashMap<>();
        facts.put(FACT, abxFact());
        facts.put("fact_missing_src", factWithUnregisteredSource());

        validator = validatorOf(facts);
    }

    // ==================================================================
    // 基本字段
    // ==================================================================

    @Test
    @DisplayName("空指标直接拒绝，不做后续校验")
    void nullMetricRejected() {
        QualityConfigValidator.ValidationResult r = validator.validateMetric(null, false, null, null);
        assertFalse(r.isOk());
        assertEquals(1, r.getErrors().size());
        assertTrue(errorsText(r).contains("为空"), errorsText(r));
    }

    @Test
    @DisplayName("合法指标通过校验并产出可执行 SQL；未开试跑时不碰数据库")
    void validMetricProducesSql() {
        QualityConfigValidator.ValidationResult r = validator.validateMetric(metric(), false, null, null);

        assertTrue(r.isOk(), errorsText(r));
        assertNotNull(r.getSql());
        assertTrue(r.getSql().contains("'quality_001' AS metric_code"), r.getSql());
        assertTrue(r.getSql().contains("AS num") && r.getSql().contains("AS den"), r.getSql());
        verify(sqlMapper, never()).query(anyString());
    }

    @Test
    @DisplayName("指标编号必须是合法标识符（它会被拼进 SQL 常量）")
    void illegalCodeRejected() {
        MetricDefinition m = metric();
        m.setCode("1-quality");
        QualityConfigValidator.ValidationResult r = validator.validateMetric(m, false, null, null);

        assertFalse(r.isOk());
        assertTrue(errorsText(r).contains("指标编号"), errorsText(r));
    }

    @Test
    @DisplayName("名称/所属域/事实层缺失时一次报全，而不是只报第一个")
    void missingRequiredFieldsReportedTogether() {
        MetricDefinition m = metric();
        m.setName("  ");
        m.setDomain(null);
        m.setFact("");

        QualityConfigValidator.ValidationResult r = validator.validateMetric(m, false, null, null);

        assertFalse(r.isOk());
        assertEquals(3, r.getErrors().size(), errorsText(r));
    }

    @Test
    @DisplayName("值类型与聚合方式的枚举校验")
    void illegalValueTypeAndAggRejected() {
        MetricDefinition m = metric();
        m.setValueType("PERCENT");
        m.setAgg("MEDIAN");

        QualityConfigValidator.ValidationResult r = validator.validateMetric(m, false, null, null);

        assertFalse(r.isOk());
        assertTrue(errorsText(r).contains("值类型"), errorsText(r));
        assertTrue(errorsText(r).contains("聚合方式"), errorsText(r));
    }

    @Test
    @DisplayName("率类未设放大系数只告警（按 100 算），设为 0 才是错误")
    void rateScaleWarnsThenErrors() {
        MetricDefinition noScale = metric();
        noScale.setScale(null);
        QualityConfigValidator.ValidationResult warn = validator.validateMetric(noScale, false, null, null);
        assertTrue(warn.isOk(), errorsText(warn));
        assertTrue(warningsText(warn).contains("放大系数"), warningsText(warn));

        MetricDefinition zero = metric();
        zero.setScale(0);
        QualityConfigValidator.ValidationResult err = validator.validateMetric(zero, false, null, null);
        assertFalse(err.isOk());
        assertTrue(errorsText(err).contains("放大系数"), errorsText(err));
    }

    @Test
    @DisplayName("SUM 聚合但分子为空只告警（按 1 计算），不阻断保存")
    void sumWithoutNumeratorWarns() {
        MetricDefinition m = metric();
        m.setValueType("SUM");
        m.setAgg("SUM");
        m.setNumerator(null);

        QualityConfigValidator.ValidationResult r = validator.validateMetric(m, false, null, null);

        assertTrue(r.isOk(), errorsText(r));
        assertTrue(warningsText(r).contains("按 1 计算"), warningsText(r));
    }

    // ==================================================================
    // L1 安全
    // ==================================================================

    @Test
    @DisplayName("L1：片段出现语句分隔符被拦下，并指明是哪个字段")
    void semicolonInFragmentRejected() {
        MetricDefinition m = metric();
        m.setWhere("abx_used = 1; DELETE FROM t");

        QualityConfigValidator.ValidationResult r = validator.validateMetric(m, false, null, null);

        assertFalse(r.isOk());
        assertTrue(errorsText(r).contains("分子过滤条件"), errorsText(r));
    }

    @Test
    @DisplayName("L1：指标层禁止子查询（需要子查询的派生列应放到事实层）")
    void subqueryInMetricFragmentRejected() {
        MetricDefinition m = metric();
        m.setNumerator("(SELECT 1)");

        QualityConfigValidator.ValidationResult r = validator.validateMetric(m, false, null, null);

        assertFalse(r.isOk());
        assertTrue(errorsText(r).contains("分子表达式"), errorsText(r));
        assertTrue(errorsText(r).contains("子查询"), errorsText(r));
    }

    @Test
    @DisplayName("L1：括号与引号不配平给出可读报错，而不是等数据库报语法错误")
    void unbalancedFragmentRejected() {
        MetricDefinition brackets = metric();
        brackets.setWhere("(abx_used = 1");
        assertTrue(errorsText(validator.validateMetric(brackets, false, null, null))
                .contains("括号不配平"));

        MetricDefinition quotes = metric();
        quotes.setWhere("abx_used = '1");
        assertTrue(errorsText(validator.validateMetric(quotes, false, null, null))
                .contains("引号"));
    }

    @Test
    @DisplayName("L1：分组维度只能是单列名，组合维度给出替代方案")
    void dimsMustBeSingleColumn() {
        MetricDefinition m = metric();
        m.setDims(new ArrayList<>(Collections.singletonList("depart_code || 'x'")));

        QualityConfigValidator.ValidationResult r = validator.validateMetric(m, false, null, null);

        assertFalse(r.isOk());
        assertTrue(errorsText(r).contains("分组维度"), errorsText(r));
        assertTrue(errorsText(r).contains("单个列名"), errorsText(r));
    }

    // ==================================================================
    // L3 字段
    // ==================================================================

    @Test
    @DisplayName("L3：引用事实层未产出的列，保存前就指出并列出可引用列")
    void unknownColumnRejected() {
        MetricDefinition m = metric();
        m.setWhere("abx_used_rate = 1");

        QualityConfigValidator.ValidationResult r = validator.validateMetric(m, false, null, null);

        assertFalse(r.isOk());
        assertTrue(errorsText(r).contains("abx_used_rate"), errorsText(r));
        assertTrue(errorsText(r).contains("可引用列"), errorsText(r));
    }

    @Test
    @DisplayName("L3：事实层未声明投影列时跳过字段校验（无从判定，宁可放过不误报）")
    void columnCheckSkippedWhenFactDeclaresNoProjection() {
        FactDefinition bare = new FactDefinition();
        bare.setFact("fact_bare");
        bare.setSource("src_abx");

        Map<String, FactDefinition> facts = new LinkedHashMap<>();
        facts.put("fact_bare", bare);

        MetricDefinition m = metric();
        m.setFact("fact_bare");
        m.setWhere("whatever_column = 1");

        QualityConfigValidator.ValidationResult r = validatorOf(facts).validateMetric(m, false, null, null);

        assertTrue(r.isOk(), errorsText(r));
    }

    // ==================================================================
    // 事实层绑定
    // ==================================================================

    @Test
    @DisplayName("声明 IMPL 但事实层不存在 → 错误，并提示可选事实层")
    void implMetricWithUnknownFactRejected() {
        MetricDefinition m = metric();
        m.setFact("fact_not_exist");

        QualityConfigValidator.ValidationResult r = validator.validateMetric(m, false, null, null);

        assertFalse(r.isOk());
        assertTrue(errorsText(r).contains("事实层不存在"), errorsText(r));
        assertTrue(errorsText(r).contains("可选事实层"), errorsText(r));
    }

    @Test
    @DisplayName("空壳指标未绑事实层只告警，不拦成错误；且不做试跑")
    void placeholderMetricWithoutFactOnlyWarns() {
        MetricDefinition m = metric();
        m.setFact("fact_not_exist");
        m.setImplStatus("PLACEHOLDER");

        QualityConfigValidator.ValidationResult r = validator.validateMetric(m, true, null, null);

        assertTrue(r.isOk(), errorsText(r));
        assertTrue(warningsText(r).contains("PLACEHOLDER"), warningsText(r));
        assertTrue(warningsText(r).contains("未做试跑"), warningsText(r));
        verify(sqlMapper, never()).query(anyString());
    }

    @Test
    @DisplayName("事实层非 ACTIVE 时告警：指标不会被计算")
    void inactiveFactWarns() {
        FactDefinition pending = abxFact();
        pending.setStatus("PENDING_SOURCE");

        Map<String, FactDefinition> facts = new LinkedHashMap<>();
        facts.put(FACT, pending);

        QualityConfigValidator.ValidationResult r =
                validatorOf(facts).validateMetric(metric(), false, null, null);

        assertTrue(r.isOk(), errorsText(r));
        assertTrue(warningsText(r).contains("PENDING_SOURCE"), warningsText(r));
    }

    // ==================================================================
    // OR 条件组（简单模式 P4 收口）
    // ==================================================================

    @Test
    @DisplayName("OR 条件组：where 被整体加括号，不与分子附加条件发生优先级错配")
    void orConditionGroupKeepsPrecedence() {
        MetricDefinition m = metric();
        m.setWhere("(abx_used = 1 AND vas_used = 0) OR (vas_used = 1)");
        m.setNumerator("patient_id > 0");

        QualityConfigValidator.ValidationResult r = validator.validateMetric(m, false, null, null);

        assertTrue(r.isOk(), errorsText(r));
        // 若不加括号，SQL 的 AND 优先级高于 OR，会被解析成
        // A OR (B AND 分子条件)，分子被悄悄放大
        assertTrue(r.getSql().contains("((abx_used = 1 AND vas_used = 0) OR (vas_used = 1)) AND (patient_id > 0)"),
                "where 片段未被整体加括号，SQL 优先级已错配：" + r.getSql());
    }

    @Test
    @DisplayName("OR 条件组：患者明细 SQL 走同一套编译，同样保留分组括号")
    void orConditionGroupAlsoUsedByPatientDetailSql() {
        MetricDefinition m = metric();
        m.setWhere("(abx_used = 1 AND vas_used = 0) OR (vas_used = 1)");

        String sql = new SqlCompiler().compilePatients(m, "fact_table", abxFact());

        assertTrue(sql.contains("(abx_used = 1 AND vas_used = 0) OR (vas_used = 1)"), sql);
    }

    // ==================================================================
    // L4 试跑
    // ==================================================================

    @Test
    @DisplayName("L4：试跑成功，并按引擎口径补出全院 ALL 加权行（不是科室率的平均）")
    void trialBuildsWeightedAllRow() {
        when(sqlMapper.query(anyString())).thenReturn(rows(
                row("depart_code", "ICU", "num", 2, "den", 4),
                row("depart_code", "CCU", "num", 1, "den", 1)));

        QualityConfigValidator.ValidationResult r = validator.validateMetric(metric(), true, null, null);

        assertTrue(r.isOk(), errorsText(r));
        assertEquals(2, r.getRows().size());
        assertEquals(3, r.getPreview().size(), "按维度拆行后应补出一行全院汇总");
        assertTrue(r.getDurationMs() >= 0);

        QualityConfigValidator.MetricPreview all = r.getPreview().get(2);
        assertEquals("ALL", all.getDepartCode());
        assertEquals(0, new BigDecimal("60").compareTo(all.getValue()),
                "ALL 应为加权率 3/5*100=60，而不是各科室率的平均 (50+100)/2");
    }

    @Test
    @DisplayName("L4：试跑无任何行时告警而非报错，也不是静默通过")
    void trialWithNoRowsWarns() {
        when(sqlMapper.query(anyString())).thenReturn(new ArrayList<Map<String, Object>>());

        QualityConfigValidator.ValidationResult r = validator.validateMetric(metric(), true, null, null);

        // 窗口内没有数据不等于口径写错，因此只告警：不能因为一个空窗口就阻断保存
        assertTrue(r.isOk(), errorsText(r));
        assertTrue(warningsText(r).contains("没有返回任何行"), warningsText(r));
        assertTrue(r.getPreview().isEmpty());
    }

    @Test
    @DisplayName("L4：试跑异常转成 error，明确指出跑不动")
    void trialFailureBecomesError() {
        when(sqlMapper.query(anyString())).thenThrow(new RuntimeException("无效的列名"));

        QualityConfigValidator.ValidationResult r = validator.validateMetric(metric(), true, null, null);

        assertFalse(r.isOk());
        assertTrue(errorsText(r).contains("试跑失败"), errorsText(r));
    }

    // ==================================================================
    // 事实层校验
    // ==================================================================

    @Test
    @DisplayName("事实层：空内容 / 非法名 / 缺来源表一次报全")
    void factBasicsRejected() {
        QualityConfigValidator.ValidationResult empty = validator.validateFact(null, false, null, null);
        assertFalse(empty.isOk());
        assertTrue(errorsText(empty).contains("为空"), errorsText(empty));

        FactDefinition f = new FactDefinition();
        f.setFact("1bad");
        QualityConfigValidator.ValidationResult r = validator.validateFact(f, false, null, null);

        assertFalse(r.isOk());
        assertTrue(errorsText(r).contains("事实层名"), errorsText(r));
        assertTrue(errorsText(r).contains("来源表"), errorsText(r));
    }

    @Test
    @DisplayName("事实层：来源表未在 sources.yaml 登记 → 编译失败并指出逻辑表名")
    void factWithUnregisteredSourceFailsCompile() {
        QualityConfigValidator.ValidationResult r =
                validator.validateFact(factWithUnregisteredSource(), false, null, null);

        assertFalse(r.isOk());
        assertTrue(errorsText(r).contains("编译失败"), errorsText(r));
        assertTrue(errorsText(r).contains("src_not_registered"), errorsText(r));
    }

    @Test
    @DisplayName("事实层：允许子查询（EXISTS 派生列是既有配置的合法写法）")
    void factAllowsSubquery() {
        FactDefinition f = abxFact();
        f.setDerive(new ArrayList<>(Collections.singletonList(
                "CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"lab_item\" l WHERE l.pid = t.patient_id) "
                        + "THEN 1 ELSE 0 END AS has_lab")));

        QualityConfigValidator.ValidationResult r = validator.validateFact(f, false, null, null);

        assertTrue(r.isOk(), errorsText(r));
        assertTrue(r.getSql().contains("EXISTS"), r.getSql());
    }

    @Test
    @DisplayName("事实层：未声明科室列/对象主键时告警（分组与去重计数会退化）")
    void factWarnsOnMissingKeys() {
        FactDefinition f = abxFact();
        f.setDepartKey(null);
        f.setPatientKey(null);

        QualityConfigValidator.ValidationResult r = validator.validateFact(f, false, null, null);

        assertTrue(r.isOk(), errorsText(r));
        assertTrue(warningsText(r).contains("科室列"), warningsText(r));
        assertTrue(warningsText(r).contains("去重计数"), warningsText(r));
    }

    @Test
    @DisplayName("事实层 L4：产出 0 行时告警并给出统计窗口")
    void factTrialZeroRowsWarns() {
        when(sqlMapper.query(anyString())).thenReturn(rows(row("c", 0)));

        QualityConfigValidator.ValidationResult r = validator.validateFact(abxFact(), true, null, null);

        assertTrue(r.isOk(), errorsText(r));
        assertEquals(0, r.getFactRows());
        assertTrue(warningsText(r).contains("产出 0 行"), warningsText(r));
    }

    @Test
    @DisplayName("事实层 L4：正常产出行数写入 factRows")
    void factTrialCountsRows() {
        when(sqlMapper.query(anyString())).thenReturn(rows(row("c", 42)));

        QualityConfigValidator.ValidationResult r = validator.validateFact(abxFact(), true, null, null);

        assertEquals(42, r.getFactRows());
    }

    @Test
    @DisplayName("事实层 L4：试跑异常转成 error")
    void factTrialFailureBecomesError() {
        when(sqlMapper.query(anyString())).thenThrow(new RuntimeException("表或视图不存在"));

        QualityConfigValidator.ValidationResult r = validator.validateFact(abxFact(), true, null, null);

        assertFalse(r.isOk());
        assertTrue(errorsText(r).contains("试跑失败"), errorsText(r));
    }

    // ==================================================================
    // 测试夹具
    // ==================================================================

    /**
     * 构造一个「事实层与数据源都在内存里」的校验器，彻底摆脱 Spring 与数据库。
     *
     * <p>直接替换加载器的读接口，而不是去改 YAML：测试要能精确控制
     * 「事实层声明了哪些列」，否则 L3 的判定边界没法钉死。
     */
    private QualityConfigValidator validatorOf(Map<String, FactDefinition> facts) {
        QualityDslLoader dsl = new QualityDslLoader(props, null) {
            @Override
            public Map<String, FactDefinition> getFacts() {
                return facts;
            }

            @Override
            public SourceConfig getSourceConfig() {
                return sourceConfig();
            }
        };
        return new QualityConfigValidator(dsl, new SqlCompiler(), new QualitySqlGuard(),
                new QualityExpressionAnalyzer(), sqlMapper, props);
    }

    /** 只登记一张逻辑表 src_abx，用于区分「来源表已登记 / 未登记」两种情形。 */
    private static SourceConfig sourceConfig() {
        SourceConfig cfg = new SourceConfig();

        SourceConfig.DataSourceDef ds = new SourceConfig.DataSourceDef();
        ds.setBean("doctor");
        ds.setSchema("zing_doctor_db_prod");
        cfg.getDatasources().put("doctor", ds);

        SourceConfig.TableDef table = new SourceConfig.TableDef();
        table.setDs("doctor");
        table.setName("patient_info");
        cfg.getTables().put("src_abx", table);

        return cfg;
    }

    private static FactDefinition abxFact() {
        FactDefinition f = new FactDefinition();
        f.setFact(FACT);
        f.setDomain("domain_1");
        f.setSource("src_abx");
        f.setSelect(new ArrayList<>(Arrays.asList(
                "t.patient_id AS patient_id",
                "t.depart_code AS depart_code",
                "t.abx_used AS abx_used",
                "t.vas_used AS vas_used")));
        return f;
    }

    private static FactDefinition factWithUnregisteredSource() {
        FactDefinition f = new FactDefinition();
        f.setFact("fact_missing_src");
        f.setDomain("domain_1");
        f.setSource("src_not_registered");
        f.setSelect(new ArrayList<>(Collections.singletonList("t.patient_id AS patient_id")));
        return f;
    }

    private static MetricDefinition metric() {
        MetricDefinition m = new MetricDefinition();
        m.setCode("quality_001");
        m.setName("抗菌药物使用率");
        m.setDomain("domain_1");
        m.setFact(FACT);
        m.setValueType("RATE");
        m.setAgg("PT_COUNT");
        m.setScale(100);
        m.setWhere("abx_used = 1");
        m.setDenominatorWhere("patient_id IS NOT NULL");
        return m;
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    @SafeVarargs
    private static List<Map<String, Object>> rows(Map<String, Object>... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static String errorsText(QualityConfigValidator.ValidationResult r) {
        return String.join(" | ", r.getErrors());
    }

    private static String warningsText(QualityConfigValidator.ValidationResult r) {
        return String.join(" | ", r.getWarnings());
    }
}
