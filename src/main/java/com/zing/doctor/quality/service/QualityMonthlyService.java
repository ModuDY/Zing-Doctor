package com.zing.doctor.quality.service;

import com.zing.doctor.quality.dsl.MetricDefinition;
import com.zing.doctor.quality.dto.PeriodRange;
import com.zing.doctor.quality.engine.QualityDslLoader;
import com.zing.doctor.quality.entity.QualityMetricResult;
import com.zing.doctor.quality.entity.QualityMonthlyReport;
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
import java.util.List;
import java.util.Map;

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

    public QualityMonthlyService(QualityDslLoader dsl, QualityMetricResultMapper resultMapper,
                                 QualityMonthlyReportMapper monthlyMapper) {
        this.dsl = dsl;
        this.resultMapper = resultMapper;
        this.monthlyMapper = monthlyMapper;
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

    /** 表格视图：按月数组展开，前端与导出直接遍历。 */
    public Map<String, Object> view(int year, String departCode) {
        List<QualityMonthlyReport> rows = list(year, departCode);
        List<Map<String, Object>> list = new ArrayList<>(rows.size());
        for (QualityMonthlyReport r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", r.getIndexCode());
            m.put("name", r.getIndexName());
            m.put("domain", r.getDomainCode());
            m.put("unit", r.getUnit());
            m.put("valueType", r.getValueType());
            m.put("months", r.getMonths());
            m.put("q1", r.getQ1());
            m.put("q2", r.getQ2());
            m.put("q3", r.getQ3());
            m.put("q4", r.getQ4());
            m.put("yearTotal", r.getYearTotal());
            m.put("yearAvg", r.getYearAvg());
            m.put("maxMonth", r.getMaxMonth());
            m.put("minMonth", r.getMinMonth());
            list.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year);
        out.put("departCode", departCode == null ? "ALL" : departCode);
        out.put("header", new String[]{"1月", "2月", "3月", "4月", "5月", "6月",
                "7月", "8月", "9月", "10月", "11月", "12月"});
        out.put("rows", list);
        return out;
    }

    // ------------------------------------------------------------------
    // internal
    // ------------------------------------------------------------------

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
