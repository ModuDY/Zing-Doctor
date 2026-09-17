package com.zing.doctor.quality.service;

import com.zing.doctor.quality.entity.QualityCountRule;
import com.zing.doctor.quality.entity.QualityMonthlyReport;
import com.zing.doctor.quality.mapper.QualityCountRuleMapper;
import com.zing.doctor.quality.mapper.QualityIndexMapper;
import com.zing.doctor.quality.mapper.QualityMonthlyReportMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 月度汇总「指标行组装」的单元测试（<b>不连数据库、不启动 Spring</b>）。
 *
 * <p><b>为什么必须给这里补测试</b>：宽表以前直接读汇总表里的原子项，现在改成在读取时
 * 把原子项除成一个率（{@code 分子 ÷ 分母 × 放大系数}）。这段逻辑有两个特点：
 * <ol>
 *   <li>它是<b>纯计算</b>，错了不会报任何错 —— 页面照常显示一个数，只是那个数不对；</li>
 *   <li>加权口径（分子分母先汇总再相除）与「各月的率求平均」在<b>多数月份分母相近时结果极其接近</b>，
 *       靠肉眼看页面根本区分不出来，只有在分母差异大的月份才会露出来。</li>
 * </ol>
 * 所以必须用构造出来的、能放大差异的数据把口径钉死。
 *
 * <p><b>怎么做到离线</b>：三个 Mapper 全部用 Mockito 打桩，直接喂构造好的
 * 原子项月度值与规则定义；DSL 加载器传 {@code null}（它只用于取名称/单位，本测试不依赖）。
 */
class QualityMonthlyServiceTest {

    private static final int YEAR = 2026;
    private static final String DEPT = "ALL";

    private QualityMonthlyReportMapper monthlyMapper;
    private QualityCountRuleMapper ruleMapper;
    private QualityIndexMapper indexMapper;
    private QualityMonthlyService service;

    @BeforeEach
    void setUp() {
        monthlyMapper = mock(QualityMonthlyReportMapper.class);
        ruleMapper = mock(QualityCountRuleMapper.class);
        indexMapper = mock(QualityIndexMapper.class);
        service = new QualityMonthlyService(null, null, monthlyMapper, ruleMapper, indexMapper);
    }

    // ------------------------------------------------------------------
    // 指标值：分子 ÷ 分母 × 放大系数
    // ------------------------------------------------------------------

    @Test
    @DisplayName("单月值 = 分子 ÷ 分母 × 放大系数，按 percentPrecision 定小数位")
    void metricValuePerMonth() {
        givenAtoms(atom("n", "分子", bd(42)), atom("d", "分母", bd(48)));
        givenRules(rule("100", "某率", "n", "d", 100, 2));

        BigDecimal[] months = monthsOf(firstRow(false));

        assertEquals(0, new BigDecimal("87.50").compareTo(months[0]));
    }

    @Test
    @DisplayName("放大系数 1000 时单位显示 ‰（不能沿用 ICU 侧写的 %）")
    void thousandRateUsesPerMilleUnit() {
        givenAtoms(atom("n", "分子", bd(3)), atom("d", "分母", bd(1000)));
        givenRules(rule("100", "发病率", "n", "d", 1000, 2));

        assertEquals("‰", firstRow(false).get("unit"));
    }

    @Test
    @DisplayName("percentPrecision=0 时按整数位舍入（42/48×100=87.5 → 88）")
    void precisionZeroRoundsToInteger() {
        givenAtoms(atom("n", "分子", bd(42)), atom("d", "分母", bd(48)));
        givenRules(rule("100", "某率", "n", "d", 100, 0));

        assertEquals(0, BigDecimal.valueOf(88).compareTo(monthsOf(firstRow(false))[0]));
    }

    @Test
    @DisplayName("分母为 0 的月份留空（null）—— 不是 0、也不是 Infinity")
    void zeroDenominatorIsNull() {
        givenAtoms(atom("n", "分子", bd(5)), atom("d", "分母", BigDecimal.ZERO));
        givenRules(rule("100", "某率", "n", "d", 100, 2));

        assertNull(monthsOf(firstRow(false))[0]);
    }

    @Test
    @DisplayName("分母原子项整条没有数据时，指标仍出行但值全空（不能因为算不出就把指标吞掉）")
    void missingDenominatorAtomKeepsRow() {
        givenAtoms(atom("n", "分子", bd(5)));
        givenRules(rule("100", "某率", "n", "d", 100, 2));

        List<Map<String, Object>> rows = service.rows(YEAR, DEPT, false);

        assertEquals(1, rows.size());
        assertNull(monthsOf(rows.get(0))[0]);
    }

    // ------------------------------------------------------------------
    // 季度 / 年度口径：加权，不是简单平均
    // ------------------------------------------------------------------

    @Test
    @DisplayName("季度按「分子分母先汇总再相除」加权，不是把各月的率求平均")
    void quarterIsWeightedNotAveraged() {
        // 1月 10/100 = 10%，2月 20/1000 = 2%
        // 加权：(10+20) ÷ (100+1000) × 100 = 2.73
        // 简单平均：(10 + 2) ÷ 2 = 6.00 —— 差别足够大，写错一定被这条抓住
        givenAtoms(
                atom("n", "分子", bd(10), bd(20)),
                atom("d", "分母", bd(100), bd(1000)));
        givenRules(rule("100", "某率", "n", "d", 100, 2));

        Map<String, Object> row = firstRow(false);

        assertEquals(0, new BigDecimal("2.73").compareTo((BigDecimal) row.get("q1")));
        assertEquals(0, new BigDecimal("2.73").compareTo((BigDecimal) row.get("yearTotal")));
    }

    @Test
    @DisplayName("月均是「有值月份的平均值」，与加权后的年合计不同")
    void yearAvgIsPlainAverageOfMonths() {
        // 1月 10/100 = 10%，2月 20/1000 = 2% → 月均 = 6.00（年合计是加权 2.73）
        givenAtoms(
                atom("n", "分子", bd(10), bd(20)),
                atom("d", "分母", bd(100), bd(1000)));
        givenRules(rule("100", "某率", "n", "d", 100, 2));

        Map<String, Object> row = firstRow(false);

        assertEquals(0, new BigDecimal("6.00").compareTo((BigDecimal) row.get("yearAvg")));
    }

    @Test
    @DisplayName("最高 / 最低月按指标值判定，不是按分子大小")
    void extremeMonthsUseRateNotNumerator() {
        // 1月 10/100 = 10%；2月 1/2 = 50%
        // 分子最大在 1 月（10 > 1），但率最大在 2 月 —— 取错就会把 1 月标成最高
        givenAtoms(
                atom("n", "分子", bd(10), bd(1)),
                atom("d", "分母", bd(100), bd(2)));
        givenRules(rule("100", "某率", "n", "d", 100, 2));

        Map<String, Object> row = firstRow(false);

        assertEquals(2, row.get("maxMonth"));
        assertEquals(1, row.get("minMonth"));
    }

    // ------------------------------------------------------------------
    // 结构：指标行 + 分子/分母子行
    // ------------------------------------------------------------------

    @Test
    @DisplayName("每个指标行带两个子行：分子、分母；rowKey 带规则前缀")
    void childrenAreNumeratorAndDenominator() {
        givenAtoms(atom("n", "分子项名", bd(1)), atom("d", "分母项名", bd(2)));
        givenRules(rule("100", "某率", "n", "d", 100, 2));

        Map<String, Object> row = firstRow(false);

        assertEquals("METRIC", row.get("rowType"));
        assertEquals("100", row.get("code"));
        List<Map<String, Object>> kids = childrenOf(row);
        assertEquals(2, kids.size());
        assertEquals("numerator", kids.get(0).get("role"));
        assertEquals("分子", kids.get(0).get("roleLabel"));
        assertEquals("100:numerator", kids.get(0).get("rowKey"));
        assertEquals("denominator", kids.get(1).get("role"));
        assertEquals("100:denominator", kids.get(1).get("rowKey"));
        // 子行带原始月度值，供展开后核对分子/分母到底是多少
        assertEquals(0, BigDecimal.ONE.compareTo(monthsOf(kids.get(0))[0]));
    }

    @Test
    @DisplayName("两条指标共用同一原子项时 rowKey 仍互不相同（否则树形展开会互相串）")
    void sharedAtomKeepsRowKeyUnique() {
        givenAtoms(atom("shared", "共用分母", bd(100)), atom("n1", "分子1", bd(1)),
                atom("n2", "分子2", bd(2)));
        givenRules(
                rule("100", "指标A", "n1", "shared", 100, 2),
                rule("200", "指标B", "n2", "shared", 100, 2));

        Set<String> keys = new HashSet<>();
        for (Map<String, Object> row : service.rows(YEAR, DEPT, false)) {
            keys.add(String.valueOf(row.get("rowKey")));
            for (Map<String, Object> kid : childrenOf(row)) {
                keys.add(String.valueOf(kid.get("rowKey")));
            }
        }

        // 2 个指标行 + 4 个子行，共 6 个互不相同的 key
        assertEquals(6, keys.size());
    }

    // ------------------------------------------------------------------
    // 显示原子项开关
    // ------------------------------------------------------------------

    @Test
    @DisplayName("开关关闭时只出指标行")
    void atomsHiddenByDefault() {
        givenAtoms(atom("n", "分子", bd(1)), atom("d", "分母", bd(2)), atom("orphan", "没人引用"));
        givenRules(rule("100", "某率", "n", "d", 100, 2));

        List<Map<String, Object>> rows = service.rows(YEAR, DEPT, false);

        assertEquals(1, rows.size());
        assertEquals("METRIC", rows.get(0).get("rowType"));
    }

    @Test
    @DisplayName("开关打开时只追加「未被任何指标引用」的原子项，已被引用的不重复出现")
    void showAtomsAppendsOnlyUnreferenced() {
        givenAtoms(atom("n", "分子", bd(1)), atom("d", "分母", bd(2)), atom("orphan", "没人引用"));
        givenRules(rule("100", "某率", "n", "d", 100, 2));

        List<Map<String, Object>> rows = service.rows(YEAR, DEPT, true);

        assertEquals(2, rows.size());
        assertEquals("METRIC", rows.get(0).get("rowType"));
        Map<String, Object> orphan = rows.get(1);
        assertEquals("ATOM", orphan.get("rowType"));
        assertEquals("orphan", orphan.get("code"));
        assertNotNull(orphan.get("months"));
    }

    @Test
    @DisplayName("原子项名称取字典，字典没有才退回汇总行上的名称")
    void atomNameFallsBackToSummaryRow() {
        givenAtoms(atom("n", "分子行上的名字", bd(1)), atom("d", "分母行上的名字", bd(2)));
        givenRules(rule("100", "某率", "n", "d", 100, 2));

        List<Map<String, Object>> kids = childrenOf(firstRow(false));

        assertEquals("分子行上的名字", kids.get(0).get("name"));
        assertEquals("分母行上的名字", kids.get(1).get("name"));
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }

    private static BigDecimal[] monthsOf(Map<String, Object> row) {
        assertNotNull(row, "行不应为 null");
        return (BigDecimal[]) row.get("months");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> childrenOf(Map<String, Object> row) {
        Object c = row.get("children");
        assertTrue(c instanceof List, "指标行必须带 children");
        return (List<Map<String, Object>>) c;
    }

    private Map<String, Object> firstRow(boolean showAtoms) {
        List<Map<String, Object>> rows = service.rows(YEAR, DEPT, showAtoms);
        assertTrue(!rows.isEmpty(), "至少应有一行");
        return rows.get(0);
    }

    private void givenAtoms(QualityMonthlyReport... atoms) {
        when(monthlyMapper.selectByYearAndDepart(anyInt(), anyString()))
                .thenReturn(new ArrayList<>(Arrays.asList(atoms)));
    }

    private void givenRules(QualityCountRule... rules) {
        when(ruleMapper.selectForBoard()).thenReturn(new ArrayList<>(Arrays.asList(rules)));
    }

    /** 造一条原子项的年度汇总行：只填月份值（季度/年合计由 list() 内部算，本测试用不到）。 */
    private static QualityMonthlyReport atom(String code, String name, BigDecimal... months) {
        QualityMonthlyReport r = new QualityMonthlyReport();
        r.setYear(YEAR);
        r.setIndexCode(code);
        r.setIndexName(name);
        r.setDomainCode("ICU");
        r.setUnit("人");
        r.setValueType("COUNT");
        r.setDepartCode(DEPT);
        BigDecimal[] m = new BigDecimal[12];
        for (int i = 0; i < months.length && i < 12; i++) {
            m[i] = months[i];
        }
        r.setM01(m[0]);
        r.setM02(m[1]);
        r.setM03(m[2]);
        r.setM04(m[3]);
        r.setM05(m[4]);
        r.setM06(m[5]);
        r.setM07(m[6]);
        r.setM08(m[7]);
        r.setM09(m[8]);
        r.setM10(m[9]);
        r.setM11(m[10]);
        r.setM12(m[11]);
        return r;
    }

    /** 造一条指标规则。 */
    private static QualityCountRule rule(String ruleId, String name, String num, String den,
                                         int rate, int precision) {
        QualityCountRule r = new QualityCountRule();
        r.setRuleId(ruleId);
        r.setCountName(name);
        r.setQualityTypeCode("ICU");
        r.setNumeratorCode(num);
        r.setDenominatorCode(den);
        r.setPercentRate(rate);
        r.setPercentPrecision(precision);
        r.setIsShowPage(1);
        r.setStatus(1);
        return r;
    }
}
