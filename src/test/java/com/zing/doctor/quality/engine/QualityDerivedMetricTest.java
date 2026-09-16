package com.zing.doctor.quality.engine;

import com.zing.doctor.quality.config.QualityProperties;
import com.zing.doctor.quality.dsl.MetricDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 引用型指标（率 = 指标A ÷ 指标B）测试（离线，不连库、不启动 Spring）。
 *
 * <p>守的是两条病死率指标的口径：分子分母分别落在评分层与患者流转层，一条 SQL 串不起来，
 * 只能「先算被引用指标、再按科室取行相除」。这里最容易出的三个错是：
 * <ol>
 *   <li>取错字段 —— 引用一条率却取了它未换算的分子；</li>
 *   <li>科室模式与全院模式的取值不一致，导致全院率与各科室对不上；</li>
 *   <li>被引用指标没算出数时静默缺行，页面上看起来像「这条指标停了」。</li>
 * </ol>
 */
class QualityDerivedMetricTest {

    private static final String DEPT = "A001";

    @Test
    @DisplayName("配置：两条病死率已声明为引用型，放大系数各自正确")
    void fatalityMetricsAreDerived() {
        QualityDslLoader dsl = load();

        MetricDefinition actual = dsl.getMetrics().get("quality_30");
        assertNotNull(actual, "缺少 ICU实际病死率");
        assertTrue(actual.isDerived(), "ICU实际病死率应改为引用型自动计算");
        assertEquals("quality_16", actual.getNumeratorMetric(), "分子应为 ICU死亡患者数");
        assertEquals("quality_403", actual.getDenominatorMetric(), "分母应为 同期患者总数（转出+出院）");
        assertEquals(100, actual.getScale(), "分子是人数比人数，必须 ×100 才是百分数");

        MetricDefinition expected = dsl.getMetrics().get("quality_31");
        assertNotNull(expected, "缺少 ICU患者预计病死率");
        assertTrue(expected.isDerived(), "ICU患者预计病死率应改为引用型自动计算");
        assertEquals("quality_15", expected.getNumeratorMetric(), "分子应为 预计病死率之和");
        assertEquals("quality_403", expected.getDenominatorMetric());
        assertEquals(1, expected.getScale(), "分子本身已是百分数之和，系数只能是 1");
    }

    @Test
    @DisplayName("配置：医师/护士总数已改为人工填报")
    void staffMetricsAreManual() {
        QualityDslLoader dsl = load();
        assertEquals("MANUAL", dsl.getMetrics().get("quality_302").getImplStatus());
        assertEquals("MANUAL", dsl.getMetrics().get("quality_303").getImplStatus());
    }

    @Test
    @DisplayName("全院模式：逐科室取同科室的行，ALL 行取被引用指标的 ALL 行")
    void composeInAllMode() {
        List<MetricOutcome> base = new ArrayList<>();
        base.add(outcome("quality_15", "A001", "30.0000"));
        base.add(outcome("quality_15", "A002", "10.0000"));
        base.add(outcome("quality_15", "ALL", "40.0000"));
        base.add(outcome("quality_403", "A001", "10.0000"));
        base.add(outcome("quality_403", "A002", "10.0000"));
        base.add(outcome("quality_403", "ALL", "20.0000"));

        List<MetricOutcome> composed = engine().composeDerived(derived(1), base, null);

        assertEquals(3, composed.size(), "应出两个科室行 + 一行全院汇总");
        assertEquals("3.0000", value(composed, "A001"));
        assertEquals("1.0000", value(composed, "A002"));
        assertEquals("2.0000", value(composed, "ALL"), "40 ÷ 20：全院行取被引用指标的 ALL 行");
        assertEquals("ALL", composed.get(composed.size() - 1).getDepartCode(), "ALL 行应排在最后");
    }

    @Test
    @DisplayName("科室模式：只取该科室的行，不补全院行（补了就是用科室值冒充全院值）")
    void composeInDeptMode() {
        List<MetricOutcome> base = new ArrayList<>();
        base.add(outcome("quality_15", DEPT, "30.0000"));
        base.add(outcome("quality_403", DEPT, "10.0000"));

        List<MetricOutcome> composed = engine().composeDerived(derived(1), base, DEPT);

        assertEquals(1, composed.size());
        assertEquals(DEPT, composed.get(0).getDepartCode());
        assertEquals("3.0000", value(composed, DEPT));
    }

    @Test
    @DisplayName("被引用指标没有结果行 → 出 ERROR 行，而不是静默缺行")
    void missingRefYieldsError() {
        List<MetricOutcome> composed = engine().composeDerived(derived(1), new ArrayList<>(), null);

        assertEquals(1, composed.size());
        assertEquals("ERROR", composed.get(0).getCalcStatus());
        assertTrue(composed.get(0).getErrorMsg().contains("quality_15"),
                "错误信息里要带上没算出来的那条指标，否则无从排查");
    }

    @Test
    @DisplayName("引用型指标不参与 SQL 计算，直接调 computeMetric 应当场报错")
    void derivedIsNotComputedBySql() {
        MetricDefinition m = derived(1);
        LocalDateTime now = LocalDateTime.now();

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> engine().computeMetric(m, now, now, false, null));
        assertTrue(e.getMessage().contains("引用型指标"));
    }

    // ------------------------------------------------------------------
    // internal
    // ------------------------------------------------------------------

    /** 只用到判定与组合逻辑，mapper / props / guard 传 null。 */
    private static QualityEngine engine() {
        return new QualityEngine(load(), new SqlCompiler(), null, null, null);
    }

    private static QualityDslLoader load() {
        QualityDslLoader dsl = new QualityDslLoader(new QualityProperties(), null);
        dsl.reload();
        return dsl;
    }

    /** 被测指标：分子 quality_15、分母 quality_403（与线上的 quality_31 同形）。 */
    private static MetricDefinition derived(int scale) {
        MetricDefinition m = new MetricDefinition();
        m.setCode("ut_derived");
        m.setName("ut_derived");
        m.setDomain("患者流转");
        m.setUnit("%");
        m.setValueType("RATE");
        m.setScale(scale);
        m.setNumeratorMetric("quality_15");
        m.setDenominatorMetric("quality_403");
        m.setDims(new ArrayList<>(Arrays.asList("depart_code")));
        return m;
    }

    private static MetricOutcome outcome(String code, String dept, String value) {
        MetricOutcome o = new MetricOutcome();
        o.setMetricCode(code);
        o.setDepartCode(dept);
        o.setMetricValue(new BigDecimal(value));
        return o;
    }

    private static String value(List<MetricOutcome> list, String dept) {
        for (MetricOutcome o : list) {
            if (dept.equals(o.getDepartCode())) {
                return o.getMetricValue() == null ? null : o.getMetricValue().toPlainString();
            }
        }
        return null;
    }
}
