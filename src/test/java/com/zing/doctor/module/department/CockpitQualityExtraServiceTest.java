package com.zing.doctor.module.department;

import com.zing.doctor.module.department.dto.CockpitQualityExtra;
import com.zing.doctor.module.department.service.CockpitQualityExtraService;
import com.zing.doctor.quality.dsl.MetricDefinition;
import com.zing.doctor.quality.engine.MetricOutcome;
import com.zing.doctor.quality.engine.QualityDslLoader;
import com.zing.doctor.quality.engine.QualityEngine;
import com.zing.doctor.quality.entity.QualityMetricResult;
import com.zing.doctor.quality.mapper.QualityMetricResultMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 驾驶舱补充质控块（48h 重返率 + bundle 年度趋势）。
 *
 * <p>钉住三条容易被写错、且错了会直接误导临床的口径：
 * <ol>
 *   <li><b>窗口是「最近 7 天含今天」</b>：传给引擎的区间必须是 [今天-6 日 00:00, 明天 00:00)，
 *       少算一天就会在大屏上长期低估重返率；</li>
 *   <li><b>分母 0 不给 0%</b>：近 7 天没有转出时率是 null，页面上「0% 重返」会被读成质量极好，
 *       实际是这段时间没有可观察人群；</li>
 *   <li><b>缺月断开而不是补 0</b>：跑批没覆盖的月份在趋势里保持 null，
 *       连成直线等于把「没算」画成「完成率 0%」。</li>
 * </ol>
 *
 * <p>另有一条底线：任一取数失败都不能抛异常 —— 这是挂墙大屏页，单块失败要降级成说明文字。
 */
class CockpitQualityExtraServiceTest {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private QualityDslLoader dslLoader;
    private QualityEngine engine;
    private QualityMetricResultMapper resultMapper;
    private CockpitQualityExtraService service;

    private MetricDefinition metricReadmit;
    private MetricDefinition metricOut;

    @BeforeEach
    void setUp() {
        dslLoader = mock(QualityDslLoader.class);
        engine = mock(QualityEngine.class);
        resultMapper = mock(QualityMetricResultMapper.class);
        service = new CockpitQualityExtraService(dslLoader, engine, resultMapper);

        metricReadmit = mock(MetricDefinition.class);
        metricOut = mock(MetricDefinition.class);
        Map<String, MetricDefinition> metrics = new HashMap<>();
        metrics.put("quality_22", metricReadmit);
        metrics.put("quality_403", metricOut);
        metrics.put("quality_11", mock(MetricDefinition.class));
        metrics.put("quality_10", mock(MetricDefinition.class));
        when(dslLoader.getMetrics()).thenReturn(metrics);

        when(resultMapper.selectByPeriod(anyString(), any(LocalDateTime.class), anyString()))
                .thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("近 7 天窗口 = [今天-6 00:00, 明天 00:00)，率取 quality_22 ÷ quality_403")
    void readmitUsesSevenDayWindowIncludingToday() {
        MetricOutcome readmit = outcome("ICU", "1");
        MetricOutcome out = outcome("ICU", "20");
        when(engine.computeMetric(eq(metricReadmit), any(), any()))
                .thenReturn(Collections.singletonList(readmit));
        when(engine.computeMetric(eq(metricOut), any(), any()))
                .thenReturn(Collections.singletonList(out));

        CockpitQualityExtra dto = service.extra("ICU");

        LocalDate today = LocalDate.now();
        assertEquals(today.minusDays(6).format(DAY), dto.getWindowStart());
        assertEquals(today.format(DAY), dto.getWindowEnd());
        assertEquals(1, dto.getReadmitNumerator());
        assertEquals(20, dto.getReadmitDenominator());
        assertEquals(0, new BigDecimal("5.0").compareTo(dto.getReadmitRate()));

        // 窗口起止必须精确到「今天-6 日零点」与「明天零点」，否则天数会漂
        verify(engine).computeMetric(eq(metricReadmit),
                eq(today.minusDays(6).atStartOfDay()), eq(today.plusDays(1).atStartOfDay()));
    }

    @Test
    @DisplayName("近 7 天没有转出（分母 0）时率是 null，不是 0%")
    void zeroDenominatorYieldsNullRate() {
        MetricOutcome readmit = outcome("ICU", "3");
        MetricOutcome out = outcome("ICU", "0");
        when(engine.computeMetric(eq(metricReadmit), any(), any()))
                .thenReturn(Collections.singletonList(readmit));
        when(engine.computeMetric(eq(metricOut), any(), any()))
                .thenReturn(Collections.singletonList(out));

        CockpitQualityExtra dto = service.extra("ICU");

        assertNull(dto.getReadmitRate());
        assertEquals(0, dto.getReadmitDenominator());
    }

    @Test
    @DisplayName("取数抛异常不冒泡，降级为 readmitNote 说明")
    void readmitFailureDegradesToNote() {
        when(engine.computeMetric(any(), any(), any()))
                .thenThrow(new IllegalStateException("事实层物化失败"));

        CockpitQualityExtra dto = service.extra("ICU");

        assertNotNull(dto.getReadmitNote());
        assertTrue(dto.getReadmitNote().contains("取数失败"), dto.getReadmitNote());
        assertNull(dto.getReadmitRate());
    }

    @Test
    @DisplayName("科室为空按 ALL 取数，避免各科室数字相加")
    void blankDepartFallsBackToAllScope() {
        List<MetricOutcome> readmit = Arrays.asList(outcome("ICU", "9"), outcome("ALL", "4"));
        List<MetricOutcome> out = Arrays.asList(outcome("ICU", "90"), outcome("ALL", "40"));
        when(engine.computeMetric(eq(metricReadmit), any(), any())).thenReturn(readmit);
        when(engine.computeMetric(eq(metricOut), any(), any())).thenReturn(out);

        CockpitQualityExtra dto = service.extra(null);

        assertEquals(4, dto.getReadmitNumerator());
        assertEquals(40, dto.getReadmitDenominator());
        assertEquals(0, new BigDecimal("10.0").compareTo(dto.getReadmitRate()));
    }

    @Test
    @DisplayName("bundle 趋势固定 12 个月、升序、缺月为 null 不断线")
    void bundleTrendIsTwelveAscendingMonthsWithGaps() {
        YearMonth now = YearMonth.now();
        when(resultMapper.selectByPeriod(eq("MONTH"), any(LocalDateTime.class), eq("ICU")))
                .thenAnswer(invocation -> {
                    YearMonth month = YearMonth.from(invocation.getArgument(1, LocalDateTime.class));
                    long away = ChronoUnit.MONTHS.between(month, now);
                    if (away % 2 != 0) {
                        return Collections.emptyList();
                    }
                    return Arrays.asList(result("quality_11", "1"), result("quality_10", "4"));
                });

        CockpitQualityExtra dto = service.extra("ICU");
        List<CockpitQualityExtra.BundlePoint> trend = dto.getBundleTrend();

        assertEquals(12, trend.size());
        assertEquals(now.minusMonths(11).toString(), trend.get(0).getMonth());
        assertEquals(now.toString(), trend.get(11).getMonth());
        // 最近一个月（away=0）有值，倒数第二个月（away=1）缺结果
        assertEquals(1, trend.get(11).getNumerator());
        assertEquals(4, trend.get(11).getDenominator());
        assertEquals(0, new BigDecimal("25.0").compareTo(trend.get(11).getRate()));
        assertNull(trend.get(10).getRate());
        assertTrue(dto.getBundleNote().contains("没有已算结果"), dto.getBundleNote());
    }

    @Test
    @DisplayName("趋势查询失败不冒泡，趋势为空且 bundleNote 说明原因")
    void bundleFailureDegradesToNote() {
        when(resultMapper.selectByPeriod(anyString(), any(LocalDateTime.class), anyString()))
                .thenThrow(new RuntimeException("结果表不可用"));

        CockpitQualityExtra dto = service.extra("ICU");

        assertTrue(dto.getBundleTrend().isEmpty());
        assertTrue(dto.getBundleNote().contains("取数失败"), dto.getBundleNote());
    }

    private static MetricOutcome outcome(String departCode, String numerator) {
        MetricOutcome outcome = mock(MetricOutcome.class);
        when(outcome.getDepartCode()).thenReturn(departCode);
        when(outcome.getNumerator()).thenReturn(new BigDecimal(numerator));
        return outcome;
    }

    private static QualityMetricResult result(String metricCode, String numerator) {
        QualityMetricResult row = new QualityMetricResult();
        row.setMetricCode(metricCode);
        row.setNumerator(new BigDecimal(numerator));
        return row;
    }
}
