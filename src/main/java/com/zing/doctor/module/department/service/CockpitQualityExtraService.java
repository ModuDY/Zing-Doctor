package com.zing.doctor.module.department.service;

import com.zing.doctor.module.department.dto.CockpitQualityExtra;
import com.zing.doctor.quality.dsl.MetricDefinition;
import com.zing.doctor.quality.dto.PeriodRange;
import com.zing.doctor.quality.engine.MetricOutcome;
import com.zing.doctor.quality.engine.QualityDslLoader;
import com.zing.doctor.quality.engine.QualityEngine;
import com.zing.doctor.quality.entity.QualityMetricResult;
import com.zing.doctor.quality.mapper.QualityMetricResultMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 驾驶舱补充质控数据：近 7 天 48h 重返率 + 近 12 个月 bundle 完成率趋势。
 *
 * <p>两条验收项的共同点是「周期不是自然月」和「需要看长期趋势」，都不能直接照抄
 * 驾驶舱现有做法（只读当月 {@code quality_metric_result}）：
 * <ul>
 *   <li>近 7 天 → 结果表里没有这个周期，改用 {@link QualityEngine#computeMetric} 现算。
 *       引擎内部会按需物化事实层（缓存物理表），口径仍是同一份 DSL，不会出现两套算法；</li>
 *   <li>近 12 个月 → 逐月读已算结果，跑批没覆盖的月份留空（不臆造 0，前端显示断点）。</li>
 * </ul>
 *
 * <p>取数失败只记日志并回填说明文字，绝不抛异常 —— 驾驶舱是挂墙大屏页，
 * 单块取数失败不该让整页 500。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CockpitQualityExtraService {

    /** 转出 ICU 后 48h 内重返人数（COUNT，fact_patient_stay.readmit_48h = 1）。 */
    private static final String READMIT = "quality_22";

    /** 同期转出/出院人数，作为 48h 重返率分母（out_in_period = 1）。 */
    private static final String OUT_TOTAL = "quality_403";

    /** 完成 6h bundle 的感染性休克患者数。 */
    private static final String BUNDLE_DONE = "quality_11";

    /** 入 ICU 诊断为感染性休克的患者数。 */
    private static final String BUNDLE_TOTAL = "quality_10";

    /** 结果表 / 引擎里代表「全院」的科室编码。 */
    private static final String DEPT_ALL = "ALL";

    /** 趋势取几个月。 */
    private static final int TREND_MONTHS = 12;

    private final QualityDslLoader dslLoader;
    private final QualityEngine engine;
    private final QualityMetricResultMapper resultMapper;

    /**
     * 组装补充数据。
     *
     * @param departCode 已由控制器按账号授权收敛过的科室编码；空 = 全院
     */
    public CockpitQualityExtra extra(String departCode) {
        CockpitQualityExtra dto = new CockpitQualityExtra();
        fillReadmit7d(dto, departCode);
        fillBundleTrend(dto, departCode);
        return dto;
    }

    /** 近 7 天（含今天）48h 重返率：按任意窗口现算。 */
    private void fillReadmit7d(CockpitQualityExtra dto, String departCode) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(6).atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        dto.setWindowStart(start.format(PeriodRange.DAY));
        dto.setWindowEnd(today.format(PeriodRange.DAY));
        try {
            BigDecimal num = computeCount(READMIT, start, end, departCode);
            BigDecimal den = computeCount(OUT_TOTAL, start, end, departCode);
            dto.setReadmitNumerator(num == null ? null : num.intValue());
            dto.setReadmitDenominator(den == null ? null : den.intValue());
            dto.setReadmitRate(rate(num, den));
            dto.setReadmitNote("近 7 天窗口实时计算：分子 quality_22（转出后 48h 内重返）、分母 quality_403（同期转出/出院）");
        } catch (Exception e) {
            log.warn("驾驶舱近 7 天 48h 重返率取数失败 depart={} : {}", departCode, e.toString());
            dto.setReadmitNote("取数失败：" + e.getMessage());
        }
    }

    /** 近 12 个月 bundle 完成率：逐月读已算结果。 */
    private void fillBundleTrend(CockpitQualityExtra dto, String departCode) {
        String dept = normalizeDepart(departCode);
        YearMonth now = YearMonth.now();
        int missing = 0;
        try {
            for (int i = TREND_MONTHS - 1; i >= 0; i--) {
                YearMonth ym = now.minusMonths(i);
                PeriodRange range = PeriodRange.monthOf(ym.getYear(), ym.getMonthValue());
                List<QualityMetricResult> rows = resultMapper.selectByPeriod("MONTH", range.getStart(), dept);
                Map<String, QualityMetricResult> byCode = new HashMap<>();
                if (rows != null) {
                    for (QualityMetricResult r : rows) {
                        if (r.getMetricCode() != null) {
                            byCode.putIfAbsent(r.getMetricCode(), r);
                        }
                    }
                }
                BigDecimal done = valueOf(byCode.get(BUNDLE_DONE));
                BigDecimal total = valueOf(byCode.get(BUNDLE_TOTAL));
                if (done == null || total == null) {
                    missing++;
                }
                CockpitQualityExtra.BundlePoint point = new CockpitQualityExtra.BundlePoint();
                point.setMonth(ym.toString());
                point.setNumerator(done == null ? null : done.intValue());
                point.setDenominator(total == null ? null : total.intValue());
                point.setRate(rate(done, total));
                dto.getBundleTrend().add(point);
            }
            dto.setBundleNote(missing > 0
                    ? ("近 " + TREND_MONTHS + " 个月中有 " + missing + " 个月没有已算结果（跑批未覆盖或分母为空）")
                    : "取自已算月度结果：quality_11（完成 6h bundle 人数）÷ quality_10（感染性休克患者数）");
        } catch (Exception e) {
            log.warn("驾驶舱 bundle 年度趋势取数失败 depart={} : {}", departCode, e.toString());
            dto.setBundleNote("取数失败：" + e.getMessage());
        }
    }

    /**
     * 用质控引擎按指定窗口现算一个 COUNT 指标。
     *
     * <p>指标未定义或窗口内无数据时返回 null（不返回 0，避免把「没数据」画成「零发生」）。
     */
    private BigDecimal computeCount(String metricCode, LocalDateTime start, LocalDateTime end, String departCode) {
        MetricDefinition metric = dslLoader.getMetrics().get(metricCode);
        if (metric == null) {
            return null;
        }
        List<MetricOutcome> outcomes = engine.computeMetric(metric, start, end);
        if (outcomes == null || outcomes.isEmpty()) {
            return null;
        }
        String dept = normalizeDepart(departCode);
        MetricOutcome picked = null;
        for (MetricOutcome o : outcomes) {
            if (dept.equals(o.getDepartCode())) {
                picked = o;
                break;
            }
            if (DEPT_ALL.equals(dept) && DEPT_ALL.equals(o.getDepartCode())) {
                picked = o;
            }
        }
        if (picked == null) {
            picked = outcomes.get(0);
        }
        return valueOf(picked);
    }

    private static BigDecimal valueOf(MetricOutcome outcome) {
        if (outcome == null) {
            return null;
        }
        return outcome.getNumerator() != null ? outcome.getNumerator() : outcome.getMetricValue();
    }

    private static BigDecimal valueOf(QualityMetricResult row) {
        if (row == null) {
            return null;
        }
        if (row.getMetricValue() != null && "RATE".equalsIgnoreCase(String.valueOf(row.getUnit()))) {
            return row.getMetricValue();
        }
        return row.getNumerator() != null ? row.getNumerator() : row.getMetricValue();
    }

    /** 百分比，分母为 0 或任一分量缺失时返回 null。 */
    private static BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 1, RoundingMode.HALF_UP);
    }

    private static String normalizeDepart(String departCode) {
        return (departCode == null || departCode.trim().isEmpty()) ? DEPT_ALL : departCode.trim();
    }
}
