package com.zing.doctor.quality.service;

import com.zing.doctor.quality.dsl.MetricDefinition;
import com.zing.doctor.quality.dto.PeriodRange;
import com.zing.doctor.quality.engine.QualityDslLoader;
import com.zing.doctor.quality.entity.QualityCountRule;
import com.zing.doctor.quality.entity.QualityIndex;
import com.zing.doctor.quality.entity.QualityMetricResult;
import com.zing.doctor.quality.entity.QualityMonthlyReport;
import com.zing.doctor.quality.mapper.QualityCountRuleMapper;
import com.zing.doctor.quality.mapper.QualityIndexMapper;
import com.zing.doctor.quality.mapper.QualityMetricResultMapper;
import com.zing.doctor.quality.mapper.QualityMonthlyReportMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 月度汇总宽表服务（1-12 月横排）。
 *
 * <p>为什么要有这层：老系统看全年数据是「现场算 12 次再手工拼表」，本服务把 12 个月的
 * 指标结果<b>折叠成一行</b>，页面和 xlsx 一次查询即得全年对比。
 *
 * <p>季度/全年不是简单把月度值平均：
 * <ul>
 *   <li>率类（RATE）：先汇总分子分母再重算 → 加权率，避免「小分母科室把均值拉偏」</li>
 *   <li>数类（COUNT/DAYS）：直接求和</li>
 *   <li>均值类（AVG）：分子分母分别求和后相除</li>
 * </ul>
 */
@Service
public class QualityMonthlyService {

    private static final Logger log = LoggerFactory.getLogger(QualityMonthlyService.class);

    private final QualityDslLoader dsl;
    private final QualityMetricResultMapper resultMapper;
    private final QualityMonthlyReportMapper monthlyMapper;
    private final QualityCountRuleMapper ruleMapper;
    private final QualityIndexMapper indexMapper;

    public QualityMonthlyService(QualityDslLoader dsl, QualityMetricResultMapper resultMapper,
                                 QualityMonthlyReportMapper monthlyMapper,
                                 QualityCountRuleMapper ruleMapper, QualityIndexMapper indexMapper) {
        this.dsl = dsl;
        this.resultMapper = resultMapper;
        this.monthlyMapper = monthlyMapper;
        this.ruleMapper = ruleMapper;
        this.indexMapper = indexMapper;
    }

    /**
     * 重建某年汇总宽表（幂等：先删后建）。
     */
    public Map<String, Object> rebuild(int year, String departCode) {
        String dept = departCode == null || departCode.trim().isEmpty() ? "ALL" : departCode.trim();
        PeriodRange y = PeriodRange.yearOf(year);

        List<QualityMetricResult> yearResults = resultMapper.selectYear(y.getStart(), y.getEnd(), dept);

        Map<String, List<QualityMetricResult>> byMetric = new LinkedHashMap<>();
        for (QualityMetricResult r : yearResults) {
            byMetric.computeIfAbsent(r.getMetricCode(), k -> new ArrayList<>()).add(r);
        }

        monthlyMapper.deleteByYear(year, dept);

        int saved = 0;
        for (Map.Entry<String, List<QualityMetricResult>> e : byMetric.entrySet()) {
            try {
                QualityMonthlyReport row = buildRow(year, dept, e.getKey(), e.getValue());
                monthlyMapper.insert(row);
                saved++;
            } catch (Exception ex) {
                log.warn("[质控] 月度汇总落库失败: code={}, msg={}", e.getKey(), ex.getMessage());
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year);
        out.put("departCode", dept);
        out.put("metrics", saved);
        out.put("sourceResults", yearResults.size());
        log.info("[质控] 月度汇总重建 year={} dept={} 指标 {} 条", year, dept, saved);
        return out;
    }

    /** 查询全年汇总（宽表直读）。 */
    public List<QualityMonthlyReport> list(int year, String departCode) {
        String dept = departCode == null || departCode.trim().isEmpty() ? "ALL" : departCode.trim();
        List<QualityMonthlyReport> rows = monthlyMapper.selectByYearAndDepart(year, dept);
        for (QualityMonthlyReport r : rows) {
            r.setMonths(new BigDecimal[]{r.getM01(), r.getM02(), r.getM03(), r.getM04(),
                    r.getM05(), r.getM06(), r.getM07(), r.getM08(), r.getM09(),
                    r.getM10(), r.getM11(), r.getM12()});
        }
        return rows;
    }

    /** 表格视图：指标行（含分子/分母子行），前端与导出直接遍历。 */
    public Map<String, Object> view(int year, String departCode) {
        return view(year, departCode, false);
    }

    /**
     * @param showAtoms true = 追加「没有被任何指标引用」的原子项行。
     *        被引用的原子项已经出现在指标的分子/分母子行里，再单列一遍纯属重复；
     *        真正需要单独看的是没有任何指标在用的那些（排查「这个数为什么没人用」）。
     */
    public Map<String, Object> view(int year, String departCode, boolean showAtoms) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year);
        out.put("departCode", normDept(departCode));
        out.put("header", new String[]{"1月", "2月", "3月", "4月", "5月", "6月",
                "7月", "8月", "9月", "10月", "11月", "12月"});
        out.put("rows", rows(year, departCode, showAtoms));
        return out;
    }

    /**
     * 组装年度行：**每行是一个质控指标**（分子 ÷ 分母 × 系数），
     * 其 {@code children} 是构成它的分子、分母两个原子项 —— 页面与导出共用这一份。
     * 两处各写一套的话，迟早出现「屏幕上第 3 列和导出的第 3 列对不上」，
     * 而这种差异会被当成数据问题来查。
     *
     * <p>为什么在这里做除法、而不是直接读汇总表：汇总表存的是原子项 ——
     * {@code quality_xxx} 是「多少人 / 多少天」的原子量，自身成不了率；
     * 指标在 ICU 侧是 {@code quality_count_rule}：分子原子项 ÷ 分母原子项 × 放大系数。
     * 该口径与看板「质控指标」页逐字一致，改这里必须同步改那边。
     *
     * <p>指标行不落库、读取时才算：历史年份因此立即生效，不必重跑「重建汇总」。
     */
    public List<Map<String, Object>> rows(int year, String departCode, boolean showAtoms) {
        String dept = normDept(departCode);
        List<QualityMonthlyReport> atomRows = list(year, dept);
        Map<String, QualityMonthlyReport> atomByCode = new LinkedHashMap<>();
        for (QualityMonthlyReport r : atomRows) {
            if (r.getIndexCode() != null) {
                atomByCode.put(r.getIndexCode(), r);
            }
        }
        Map<String, QualityIndex> indexByCode = loadIndex();

        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> referenced = new LinkedHashSet<>();
        for (QualityCountRule rule : ruleMapper.selectForBoard()) {
            if (rule.getNumeratorCode() != null) {
                referenced.add(rule.getNumeratorCode());
            }
            if (rule.getDenominatorCode() != null) {
                referenced.add(rule.getDenominatorCode());
            }
            out.add(metricRow(rule, atomByCode, indexByCode));
        }
        if (showAtoms) {
            for (QualityMonthlyReport r : atomRows) {
                if (referenced.contains(r.getIndexCode())) {
                    continue;
                }
                out.add(atomRowMap(r));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // internal
    // ------------------------------------------------------------------

    /** 一个指标行：值 = 分子 ÷ 分母 × 放大系数，children = 分子项 / 分母项。 */
    private Map<String, Object> metricRow(QualityCountRule rule,
                                          Map<String, QualityMonthlyReport> atomByCode,
                                          Map<String, QualityIndex> indexByCode) {
        int rate = rule.getPercentRate() == null ? 100 : rule.getPercentRate();
        int prec = rule.getPercentPrecision() == null ? 2 : rule.getPercentPrecision();
        String ruleId = rule.getRuleId();

        QualityMonthlyReport numRow = atomByCode.get(rule.getNumeratorCode());
        QualityMonthlyReport denRow = atomByCode.get(rule.getDenominatorCode());
        BigDecimal[] nums = monthsOf(numRow);
        BigDecimal[] dens = monthsOf(denRow);

        BigDecimal[] values = new BigDecimal[12];
        for (int i = 0; i < 12; i++) {
            values[i] = metricValue(nums[i], dens[i], rate, prec);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rowKey", ruleId);
        m.put("rowType", "METRIC");
        m.put("code", ruleId);
        m.put("name", rule.getCountName());
        m.put("domain", rule.getQualityTypeCode());
        m.put("unit", displayUnit(rule));
        m.put("precision", prec);
        m.put("months", values);
        m.put("q1", metricValue(sum(slice(nums, 0, 2)), sum(slice(dens, 0, 2)), rate, prec));
        m.put("q2", metricValue(sum(slice(nums, 3, 5)), sum(slice(dens, 3, 5)), rate, prec));
        m.put("q3", metricValue(sum(slice(nums, 6, 8)), sum(slice(dens, 6, 8)), rate, prec));
        m.put("q4", metricValue(sum(slice(nums, 9, 11)), sum(slice(dens, 9, 11)), rate, prec));
        // 年度合计：分子分母先汇总再相除（加权），不是把 12 个月的率求平均 ——
        // 后者会让小分母的月份（如 1/1 = 100%）把全年值拉高，正是「小分母拉偏均值」的老问题
        m.put("yearTotal", metricValue(sum(nums), sum(dens), rate, prec));
        m.put("yearAvg", avgNonNull(values));
        m.put("maxMonth", extremeMonth(values, true));
        m.put("minMonth", extremeMonth(values, false));

        List<Map<String, Object>> children = new ArrayList<>(2);
        children.add(atomChild(ruleId, "numerator", "分子", rule.getNumeratorCode(), numRow, indexByCode));
        children.add(atomChild(ruleId, "denominator", "分母", rule.getDenominatorCode(), denRow, indexByCode));
        m.put("children", children);
        return m;
    }

    /**
     * 指标下的原子项子行。
     *
     * <p>rowKey 必须带上规则前缀（{@code ruleId:role}）：同一个原子项常被多条指标共用
     * （quality_403 被 13 条当分母），子行若直接用原子项 code 当 key，
     * 树形表格的展开状态会互相串 —— 点开一条，另外十几条跟着一起展开。
     */
    private Map<String, Object> atomChild(String ruleId, String role, String roleLabel, String code,
                                          QualityMonthlyReport r, Map<String, QualityIndex> indexByCode) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("rowKey", ruleId + ":" + role);
        c.put("rowType", "ATOM");
        c.put("role", role);
        c.put("roleLabel", roleLabel);
        c.put("code", code);
        c.put("name", nameOf(indexByCode, code, r));
        c.put("domain", r == null ? null : r.getDomainCode());
        c.put("unit", r == null ? null : r.getUnit());
        c.put("precision", 2);
        c.put("months", monthsOf(r));
        c.put("q1", r == null ? null : r.getQ1());
        c.put("q2", r == null ? null : r.getQ2());
        c.put("q3", r == null ? null : r.getQ3());
        c.put("q4", r == null ? null : r.getQ4());
        c.put("yearTotal", r == null ? null : r.getYearTotal());
        c.put("yearAvg", r == null ? null : r.getYearAvg());
        c.put("maxMonth", r == null ? null : r.getMaxMonth());
        c.put("minMonth", r == null ? null : r.getMinMonth());
        return c;
    }

    /** 未被任何指标引用的原子项行（开关打开时追加），字段与原宽表保持一致。 */
    private Map<String, Object> atomRowMap(QualityMonthlyReport r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rowKey", r.getIndexCode());
        m.put("rowType", "ATOM");
        m.put("code", r.getIndexCode());
        m.put("name", r.getIndexName());
        m.put("domain", r.getDomainCode());
        m.put("unit", r.getUnit());
        m.put("precision", 2);
        m.put("months", r.getMonths());
        m.put("q1", r.getQ1());
        m.put("q2", r.getQ2());
        m.put("q3", r.getQ3());
        m.put("q4", r.getQ4());
        m.put("yearTotal", r.getYearTotal());
        m.put("yearAvg", r.getYearAvg());
        m.put("maxMonth", r.getMaxMonth());
        m.put("minMonth", r.getMinMonth());
        return m;
    }

    /**
     * 指标单月值 = 分子 ÷ 分母 × 放大系数，按 percentPrecision 定小数位。
     *
     * <p>与看板「质控指标」页的算法逐字一致（QualityCountRuleService）：
     * 两处各算各的，同一指标在月度和看板会显示成两个数，属于最难解释的那类问题。
     * 分母为 0 或缺值 → null（页面显示「—」）——那不是错误，是「这个月没有分母人群」。
     */
    private BigDecimal metricValue(BigDecimal num, BigDecimal den, int rate, int prec) {
        if (num == null || den == null || den.signum() == 0) {
            return null;
        }
        return num.divide(den, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(rate))
                .setScale(prec, RoundingMode.HALF_UP);
    }

    private BigDecimal[] monthsOf(QualityMonthlyReport r) {
        return r == null || r.getMonths() == null ? new BigDecimal[12] : r.getMonths();
    }

    /** 原子项名称：字典优先，其次结果行上的名称，最后退回 code（绝不回空，否则页面列名会消失）。 */
    private String nameOf(Map<String, QualityIndex> indexByCode, String code, QualityMonthlyReport r) {
        QualityIndex i = code == null ? null : indexByCode.get(code);
        if (i != null && i.getIndexName() != null && !i.getIndexName().trim().isEmpty()) {
            return i.getIndexName();
        }
        if (r != null && r.getIndexName() != null && !r.getIndexName().trim().isEmpty()) {
            return r.getIndexName();
        }
        return code;
    }

    /** 单位：系数 1000 显 ‰、100 显 %；其余用 ICU 侧给的（如「例/千日」）。 */
    private String displayUnit(QualityCountRule rule) {
        Integer rate = rule.getPercentRate();
        if (rate != null && rate == 1000) {
            return "‰";
        }
        if (rate != null && rate == 100) {
            return "%";
        }
        return rule.getPercentUnit() == null ? "" : rule.getPercentUnit();
    }

    private Map<String, QualityIndex> loadIndex() {
        Map<String, QualityIndex> byCode = new LinkedHashMap<>();
        List<QualityIndex> all = indexMapper.selectAllOrdered();
        if (all == null) {
            return byCode;
        }
        for (QualityIndex i : all) {
            if (i.getIndexCode() != null) {
                byCode.put(i.getIndexCode(), i);
            }
        }
        return byCode;
    }

    private String normDept(String departCode) {
        return departCode == null || departCode.trim().isEmpty() ? "ALL" : departCode.trim();
    }

    private QualityMonthlyReport buildRow(int year, String dept, String code,
                                          List<QualityMetricResult> list) {
        BigDecimal[] months = new BigDecimal[12];
        BigDecimal[] numS = new BigDecimal[12];
        BigDecimal[] denS = new BigDecimal[12];
        String name = code;
        String domain = null;
        String unit = null;
        String valueType = "COUNT";

        for (QualityMetricResult r : list) {
            LocalDateTime ps = r.getPeriodStart();
            int idx = ps == null ? -1 : ps.getMonthValue() - 1;
            if (idx < 0 || idx > 11) {
                continue;
            }
            months[idx] = r.getMetricValue();
            numS[idx] = r.getNumerator();
            denS[idx] = r.getDenominator();
            if (r.getMetricName() != null) {
                name = r.getMetricName();
            }
            if (r.getDomainCode() != null) {
                domain = r.getDomainCode();
            }
            if (r.getUnit() != null) {
                unit = r.getUnit();
            }
        }

        // 口径元信息以配置为准（更权威、且含 valueType）
        MetricDefinition m = dsl.getMetrics().get(code);
        if (m != null) {
            name = m.getName() == null ? name : m.getName();
            domain = m.getDomain() == null ? domain : m.getDomain();
            unit = m.getUnit() == null ? unit : m.getUnit();
            valueType = m.getValueType() == null ? "COUNT" : m.getValueType().toUpperCase();
        }

        QualityMonthlyReport row = new QualityMonthlyReport();
        row.setYear(year);
        row.setIndexCode(code);
        row.setIndexName(name);
        row.setDomainCode(domain);
        row.setUnit(unit);
        row.setValueType(valueType);
        row.setDepartCode(dept);
        row.setM01(months[0]);
        row.setM02(months[1]);
        row.setM03(months[2]);
        row.setM04(months[3]);
        row.setM05(months[4]);
        row.setM06(months[5]);
        row.setM07(months[6]);
        row.setM08(months[7]);
        row.setM09(months[8]);
        row.setM10(months[9]);
        row.setM11(months[10]);
        row.setM12(months[11]);

        row.setQ1(combine(valueType, 0, 2, numS, denS, months));
        row.setQ2(combine(valueType, 3, 5, numS, denS, months));
        row.setQ3(combine(valueType, 6, 8, numS, denS, months));
        row.setQ4(combine(valueType, 9, 11, numS, denS, months));

        BigDecimal yearNum = sum(numS);
        BigDecimal yearDen = sum(denS);
        if ("RATE".equals(valueType)) {
            row.setYearTotal(rate(yearNum, yearDen, BigDecimal.valueOf(100)));
            row.setYearAvg(avgNonNull(months));
        } else if ("AVG".equals(valueType)) {
            row.setYearTotal(rate(yearNum, yearDen, BigDecimal.ONE));
            row.setYearAvg(rate(yearNum, yearDen, BigDecimal.ONE));
        } else {
            BigDecimal total = sum(months);
            row.setYearTotal(total);
            int cnt = countNonNull(months);
            row.setYearAvg(cnt == 0 ? null
                    : total.divide(BigDecimal.valueOf(cnt), 2, RoundingMode.HALF_UP));
        }

        row.setMaxMonth(extremeMonth(months, true));
        row.setMinMonth(extremeMonth(months, false));
        row.setStatus(1);
        row.setUpdateTime(LocalDateTime.now());
        return row;
    }

    /** 区间合并：率类重算、数类求和、均值类分子分母相除。 */
    private BigDecimal combine(String valueType, int from, int to,
                               BigDecimal[] numS, BigDecimal[] denS, BigDecimal[] months) {
        BigDecimal[] nums = slice(numS, from, to);
        BigDecimal[] dens = slice(denS, from, to);
        if ("RATE".equals(valueType)) {
            return rate(sum(nums), sum(dens), BigDecimal.valueOf(100));
        }
        if ("AVG".equals(valueType)) {
            return rate(sum(nums), sum(dens), BigDecimal.ONE);
        }
        return sum(slice(months, from, to));
    }

    private BigDecimal[] slice(BigDecimal[] src, int from, int to) {
        BigDecimal[] out = new BigDecimal[to - from + 1];
        for (int i = from; i <= to && i < src.length; i++) {
            out[i - from] = src[i];
        }
        return out;
    }

    private BigDecimal sum(BigDecimal[] arr) {
        BigDecimal s = BigDecimal.ZERO;
        boolean any = false;
        for (BigDecimal v : arr) {
            if (v != null) {
                s = s.add(v);
                any = true;
            }
        }
        return any ? s : null;
    }

    private BigDecimal rate(BigDecimal num, BigDecimal den, BigDecimal scale) {
        if (num == null || den == null || den.signum() == 0) {
            return null;
        }
        return num.multiply(scale).divide(den, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal avgNonNull(BigDecimal[] arr) {
        BigDecimal s = BigDecimal.ZERO;
        int c = 0;
        for (BigDecimal v : arr) {
            if (v != null) {
                s = s.add(v);
                c++;
            }
        }
        return c == 0 ? null : s.divide(BigDecimal.valueOf(c), 4, RoundingMode.HALF_UP);
    }

    private int countNonNull(BigDecimal[] arr) {
        int c = 0;
        for (BigDecimal v : arr) {
            if (v != null) {
                c++;
            }
        }
        return c;
    }

    /** 极值月份（1-12）；无数据返回 null。 */
    private Integer extremeMonth(BigDecimal[] arr, boolean max) {
        Integer idx = null;
        BigDecimal best = null;
        for (int i = 0; i < arr.length; i++) {
            BigDecimal v = arr[i];
            if (v == null) {
                continue;
            }
            if (best == null || (max ? v.compareTo(best) > 0 : v.compareTo(best) < 0)) {
                best = v;
                idx = i + 1;
            }
        }
        return idx;
    }
}
