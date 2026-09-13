package com.zing.doctor.quality.engine;

import com.zing.doctor.quality.config.QualityProperties;
import com.zing.doctor.quality.dsl.FactDefinition;
import com.zing.doctor.quality.dsl.MetricDefinition;
import com.zing.doctor.quality.dsl.SourceConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 质控 DSL 离线冒烟测试（<b>不需要数据库、不启动 Spring</b>）。
 *
 * <p><b>为什么要有这个测试</b>：质控中台的口径全部写在
 * {@code quality/sources.yaml} + {@code quality/facts/*.yaml} + {@code quality/metrics/*.yaml}
 * 里，Java 侧只有一套与具体指标无关的通用引擎。配置里写错——引用了不存在的事实层、
 * 逻辑表未在 sources.yaml 登记、表达式为空、含 {@code QualityEngine.guard()} 会拒绝的字符——
 * <b>编译期完全看不出来</b>，要等真正跑批算时才炸。
 *
 * <p><b>为什么不能靠跑一次计算来验证</b>：外网开发环境连不上内网正式库。本测试只做
 * 「YAML → DSL → SQL 字符串」的编译，<b>不执行任何 SQL</b>，因此完全离线可跑，
 * 与 {@code MapperSqlParseTest} 是同一思路：把只能在运行期暴露的问题提前到构建期。
 *
 * <p><b>覆盖范围</b>：三层配置可加载、指标编号唯一、事实层引用的逻辑表已登记、
 * 事实层与指标均可编译出 SQL 且通过引擎的危险字符拦截。不覆盖：SQL 在达梦上的
 * 语法与语义正确性（那只能连库验证）。
 */
class QualityDslSmokeTest {

    /** 编译期用的占位事实表名：只验证 SQL 文本生成，不求真实存在。 */
    private static final String FACT_PLACEHOLDER = "\"zing_doctor_db_prod\".\"qc_fact_placeholder\"";

    private static final LocalDateTime PERIOD_START = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
    private static final LocalDateTime PERIOD_END = LocalDateTime.of(2026, 1, 31, 23, 59, 59);

    @Test
    @DisplayName("DSL 三层配置均可从 classpath 加载（数据源 / 事实层 / 指标）")
    void dslLoadsFromClasspath() {
        QualityDslLoader dsl = load();
        SourceConfig cfg = dsl.getSourceConfig();

        System.out.printf("[质控冒烟] 数据源 %d 个 / 逻辑表 %d 张 / 事实层 %d 个 / 指标 %d 条%n",
                cfg.getDatasources().size(), cfg.getTables().size(),
                dsl.getFacts().size(), dsl.getMetrics().size());

        assertFalse(cfg.getDatasources().isEmpty(), "sources.yaml 未加载到任何数据源");
        assertFalse(cfg.getTables().isEmpty(), "sources.yaml 未加载到任何逻辑表");
        assertFalse(dsl.getFacts().isEmpty(), "未加载到任何事实层配置");
        assertFalse(dsl.getMetrics().isEmpty(), "未加载到任何指标配置");
    }

    @Test
    @DisplayName("指标编号全局唯一（YAML 内重复 code 会被静默覆盖、指标凭空少一条）")
    void metricCodesAreUnique() {
        QualityDslLoader dsl = load();
        int declared = countDeclaredMetricNodes();

        assertEquals(declared, dsl.getMetrics().size(),
                "YAML 中声明的指标条目数与加载结果不一致，通常存在重复的 code："
                        + "loader 以 code 为键，重复会被静默覆盖");
    }

    @Test
    @DisplayName("每个事实层引用的逻辑表都已在 sources.yaml 登记")
    void everyFactSourceIsRegistered() {
        QualityDslLoader dsl = load();
        SqlCompiler compiler = new SqlCompiler();

        for (FactDefinition f : dsl.getFacts().values()) {
            assertTrue(StringUtils.hasText(f.getSource()),
                    "事实层 " + f.getFact() + " 未声明 source（逻辑表名）");

            String physical;
            try {
                physical = compiler.resolveTable(f.getSource(), dsl.getSourceConfig());
            } catch (RuntimeException e) {
                throw new AssertionError("事实层 " + f.getFact()
                        + " 引用的逻辑表未在 sources.yaml 登记: " + f.getSource(), e);
            }
            assertTrue(physical.contains("\""),
                    "事实层 " + f.getFact() + " 解析出的物理表名未加双引号: " + physical);
        }
    }

    @Test
    @DisplayName("每个事实层都能编译成 SQL，且周期占位符已绑定")
    void everyFactCompilesToSql() {
        QualityDslLoader dsl = load();
        SqlCompiler compiler = new SqlCompiler();

        for (FactDefinition f : dsl.getFacts().values()) {
            String sql = compiler.compileFact(f, dsl.getSourceConfig(), PERIOD_START, PERIOD_END);

            String banned = bannedChar(sql);
            assertNull(banned, "事实层 " + f.getFact() + " 编译出的 SQL 含引擎会拒绝的字符 '" + banned + "': " + sql);
            assertTrue(sql.startsWith("SELECT "),
                    "事实层 " + f.getFact() + " 编译结果不是 SELECT: " + sql);
            assertFalse(sql.contains(":periodStart"),
                    "事实层 " + f.getFact() + " 的 :periodStart 未被绑定: " + sql);
            assertFalse(sql.contains(":periodEnd"),
                    "事实层 " + f.getFact() + " 的 :periodEnd 未被绑定: " + sql);
            assertFalse(compiler.operatorsOf(f).isEmpty(),
                    "事实层 " + f.getFact() + " 未产出算子链（血缘会缺失）");
        }
    }

    @Test
    @DisplayName("声明为 IMPL 的指标必须绑定存在的事实层")
    void implMetricsReferenceExistingFact() {
        QualityDslLoader dsl = load();

        for (MetricDefinition m : dsl.sortedMetrics()) {
            if (!isImpl(m)) {
                continue;
            }
            assertTrue(StringUtils.hasText(m.getFact()),
                    "指标 " + m.getCode() + " 声明 IMPL 但未绑定事实层");
            assertTrue(dsl.getFacts().containsKey(m.getFact()),
                    "指标 " + m.getCode() + " 引用了不存在的事实层: " + m.getFact());
        }
    }

    @Test
    @DisplayName("事实层为 ACTIVE 的 IMPL 指标均可编译出指标 SQL 与患者明细 SQL")
    void activeMetricsCompileToSql() {
        QualityDslLoader dsl = load();
        SqlCompiler compiler = new SqlCompiler();

        List<String> problems = new ArrayList<>();
        int compiled = 0;

        for (MetricDefinition m : dsl.sortedMetrics()) {
            if (!isImpl(m)) {
                continue;
            }
            FactDefinition f = dsl.factOf(m);
            if (f == null) {
                continue;   // 已由 implMetricsReferenceExistingFact 断言
            }
            // 事实层待接数据源时引擎不会编译它（见 QualityEngine.effectiveStatus），此处同样跳过
            if (!"ACTIVE".equalsIgnoreCase(status(f.getStatus()))) {
                continue;
            }

            String metricSql;
            try {
                metricSql = compiler.compileMetric(m, FACT_PLACEHOLDER, f);
            } catch (RuntimeException e) {
                problems.add(m.getCode() + " 指标 SQL 编译抛异常: " + e.getMessage());
                continue;
            }
            if (!StringUtils.hasText(metricSql)) {
                problems.add(m.getCode() + " 编译出的指标 SQL 为空");
                continue;
            }
            String banned = bannedChar(metricSql);
            if (banned != null) {
                problems.add(m.getCode() + " 指标 SQL 含引擎会拒绝的字符 '" + banned + "': " + metricSql);
                continue;
            }
            if (!metricSql.contains("'" + m.getCode() + "' AS metric_code")) {
                problems.add(m.getCode() + " 指标 SQL 未输出 metric_code 常量");
                continue;
            }

            String patientSql;
            try {
                patientSql = compiler.compilePatients(m, FACT_PLACEHOLDER, f);
            } catch (RuntimeException e) {
                problems.add(m.getCode() + " 患者明细 SQL 编译抛异常: " + e.getMessage());
                continue;
            }
            String bannedPatient = bannedChar(patientSql);
            if (bannedPatient != null) {
                problems.add(m.getCode() + " 患者明细 SQL 含引擎会拒绝的字符 '" + bannedPatient + "'");
                continue;
            }
            compiled++;
        }

        System.out.printf("[质控冒烟] 已通过编译的可计算指标 %d 条%n", compiled);

        assertTrue(compiled > 0, "没有任何指标编译成功，配置可能未正确加载");
        assertTrue(problems.isEmpty(),
                "以下 " + problems.size() + " 条指标编译失败：\n  " + String.join("\n  ", problems));
    }

    // ------------------------------------------------------------------
    // internal
    // ------------------------------------------------------------------

    /** 不启动 Spring 直接构造加载器：三个 location 都有默认值，指向 classpath 下的 YAML。 */
    private QualityDslLoader load() {
        QualityDslLoader dsl = new QualityDslLoader(new QualityProperties());
        dsl.reload();
        return dsl;
    }

    private static boolean isImpl(MetricDefinition m) {
        return "IMPL".equalsIgnoreCase(status(m.getImplStatus()));
    }

    private static String status(String s) {
        return s == null || s.trim().isEmpty() ? "IMPL" : s.trim().toUpperCase();
    }

    /** 与 QualityEngine.guard() 保持同一套判定：语句分隔符与注释一律拒绝。 */
    private static String bannedChar(String sql) {
        if (sql == null) {
            return null;
        }
        if (sql.contains(";")) {
            return ";";
        }
        if (sql.contains("--")) {
            return "--";
        }
        if (sql.contains("/*")) {
            return "/*";
        }
        return null;
    }

    /** 重读 YAML 统计带 code 的指标条目数，用于与 loader 的 Map 大小比对以发现重复 code。 */
    private int countDeclaredMetricNodes() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Yaml yaml = new Yaml();
        int n = 0;
        try {
            for (Resource r : resolver.getResources("classpath*:quality/metrics/*.yaml")) {
                if (r == null || !r.exists()) {
                    continue;
                }
                try (InputStream in = r.getInputStream()) {
                    n += countNodesWithCode(yaml.load(in));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("重读指标 YAML 失败: " + e.getMessage(), e);
        }
        return n;
    }

    @SuppressWarnings("unchecked")
    private int countNodesWithCode(Object root) {
        if (root instanceof List) {
            int n = 0;
            for (Object item : (List<Object>) root) {
                n += countNodesWithCode(item);
            }
            return n;
        }
        if (root instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) root;
            if (m.get("metrics") instanceof List) {
                return countNodesWithCode(m.get("metrics"));
            }
            if (m.get("facts") instanceof List) {
                return countNodesWithCode(m.get("facts"));
            }
            Object code = m.get("code");
            return code != null && !String.valueOf(code).trim().isEmpty() ? 1 : 0;
        }
        return 0;
    }
}
