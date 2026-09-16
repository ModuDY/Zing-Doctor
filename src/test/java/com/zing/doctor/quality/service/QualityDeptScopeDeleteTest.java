package com.zing.doctor.quality.service;

import com.zing.doctor.quality.config.QualityProperties;
import com.zing.doctor.quality.dsl.MetricDefinition;
import com.zing.doctor.quality.engine.MetricOutcome;
import com.zing.doctor.quality.engine.QualityDslLoader;
import com.zing.doctor.quality.engine.QualityEngine;
import com.zing.doctor.quality.entity.QualityMetricResult;
import com.zing.doctor.quality.mapper.QualityCalcRunMapper;
import com.zing.doctor.quality.mapper.QualityCalcTraceMapper;
import com.zing.doctor.quality.mapper.QualityMetricPatientMapper;
import com.zing.doctor.quality.mapper.QualityMetricResultMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 计算批次「删除范围 = 写入范围」的回归测试（不连库）。
 *
 * <p>现场故障：选科室触发计算 → 达梦报违反 {@code uk_quality_result} 唯一性约束，
 * 127 条一起失败。原因是科室批次只删了当前科室那一行，而占位类 / 无科室维度的指标
 * 仍按全院口径算出 {@code depart_code='ALL'} 行，插入即冲突。
 *
 * <p>这里用 mock 断言删除指令本身：科室批次必须「清该科室行 + 把整周期重写的那些指标
 * 按指标清」，全院批次则整周期清空。
 */
@ExtendWith(MockitoExtension.class)
class QualityDeptScopeDeleteTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 10, 1, 0, 0, 0);

    @Mock
    private QualityProperties props;
    @Mock
    private QualityDslLoader dsl;
    @Mock
    private QualityEngine engine;
    @Mock
    private QualityCalcRunMapper runMapper;
    @Mock
    private QualityMetricResultMapper resultMapper;
    @Mock
    private QualityCalcTraceMapper traceMapper;
    @Mock
    private QualityMetricPatientMapper patientMapper;

    private QualityCalcService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.destroy();
        }
    }

    @Test
    @DisplayName("科室批次：清该科室行，且把按全院口径重写的指标整周期清掉")
    @SuppressWarnings("unchecked")
    void deptBatchClearsMatchingScope() {
        MetricDefinition scoped = metric("quality_1", "depart_code");
        MetricDefinition dimless = metric("quality_2");
        MetricDefinition placeholder = metric("quality_3", "depart_code");

        when(dsl.sortedMetrics()).thenReturn(Arrays.asList(scoped, dimless, placeholder));
        when(engine.deptFilterOf(scoped, "A001")).thenReturn("A001");
        when(engine.deptFilterOf(dimless, "A001")).thenReturn(null);
        when(engine.deptFilterOf(placeholder, "A001")).thenReturn(null);
        when(engine.computeMetric(any(MetricDefinition.class), any(), any(), anyBoolean(), any()))
                .thenReturn(new ArrayList<>());

        run("A001");

        verify(resultMapper).deleteByPeriod("MONTH", START, "A001");
        ArgumentCaptor<List<String>> codes = ArgumentCaptor.forClass(List.class);
        verify(resultMapper).deleteByMetricCodes(eq("MONTH"), eq(START), codes.capture());
        assertEquals(Arrays.asList("quality_2", "quality_3"), codes.getValue(),
                "无科室维度的指标必须整周期清，否则它写出的 ALL 行会与库里旧行撞唯一键");
        verify(resultMapper, never()).deleteByPeriodAllDept(any(), any());
    }

    @Test
    @DisplayName("科室批次：占位指标的全院行已在库里 → 整条跳过，既不计算也不删")
    void deptBatchSkipsPlaceholderWhenAllRowExists() {
        MetricDefinition scoped = metric("quality_1", "depart_code");
        MetricDefinition placeholder = metric("quality_3", "depart_code");

        when(dsl.sortedMetrics()).thenReturn(Arrays.asList(scoped, placeholder));
        when(engine.deptFilterOf(scoped, "A001")).thenReturn("A001");
        when(engine.isDeptIrrelevant(scoped)).thenReturn(false);
        when(engine.isDeptIrrelevant(placeholder)).thenReturn(true);
        when(resultMapper.selectCodesWithAll("MONTH", START))
                .thenReturn(Arrays.asList("quality_3"));
        when(engine.computeMetric(any(MetricDefinition.class), any(), any(), anyBoolean(), any()))
                .thenReturn(new ArrayList<>());

        Map<String, Object> out = run("A001");

        verify(engine, never()).computeMetric(eq(placeholder), any(), any(), anyBoolean(), any());
        assertEquals(1, out.get("metricSkipped"), "占位指标的全院行已在库里，本批次应整条跳过");
        assertEquals(1, out.get("metricTotal"), "分母应为实际计算的条数，否则进度条走不满");
        verify(resultMapper).deleteByPeriod("MONTH", START, "A001");
        // 跳过的指标不进删除范围：不必再为它整周期清一遍
        verify(resultMapper, never()).deleteByMetricCodes(any(), any(), any());
    }

    @Test
    @DisplayName("科室批次：该周期还没有全院行 → 占位指标照常计算，不让全院视图缺行")
    @SuppressWarnings("unchecked")
    void deptBatchKeepsPlaceholderWhenNoAllRowYet() {
        MetricDefinition placeholder = metric("quality_3", "depart_code");

        when(dsl.sortedMetrics()).thenReturn(Collections.singletonList(placeholder));
        when(engine.isDeptIrrelevant(placeholder)).thenReturn(true);
        when(resultMapper.selectCodesWithAll("MONTH", START)).thenReturn(Collections.emptyList());
        when(engine.deptFilterOf(placeholder, "A001")).thenReturn(null);
        when(engine.computeMetric(any(MetricDefinition.class), any(), any(), anyBoolean(), any()))
                .thenReturn(new ArrayList<>());

        Map<String, Object> out = run("A001");

        assertEquals(0, out.get("metricSkipped"), "还没有全院行时不能跳过，否则全院视图缺行");
        ArgumentCaptor<List<String>> codes = ArgumentCaptor.forClass(List.class);
        verify(resultMapper).deleteByMetricCodes(eq("MONTH"), eq(START), codes.capture());
        assertEquals(Collections.singletonList("quality_3"), codes.getValue());
    }

    @Test
    @DisplayName("科室批次：全院行存在性查询失败 → 不跳过（宁可多算 42 条也不缺行）")
    void deptBatchFallsBackWhenLookupFails() {
        MetricDefinition placeholder = metric("quality_3", "depart_code");

        when(dsl.sortedMetrics()).thenReturn(Collections.singletonList(placeholder));
        when(engine.isDeptIrrelevant(placeholder)).thenReturn(true);
        when(resultMapper.selectCodesWithAll("MONTH", START))
                .thenThrow(new RuntimeException("DB down"));
        when(engine.deptFilterOf(placeholder, "A001")).thenReturn(null);
        when(engine.computeMetric(any(MetricDefinition.class), any(), any(), anyBoolean(), any()))
                .thenReturn(new ArrayList<>());

        Map<String, Object> out = run("A001");

        assertEquals(0, out.get("metricSkipped"), "查询失败应退回照常计算");
        assertNotEquals("FAILED", out.get("status"), "存在性查询失败不应让整批失败");
    }

    @Test
    @DisplayName("全院批次：整周期清空，不做按指标清理")
    void allDeptBatchClearsWholePeriod() {
        MetricDefinition scoped = metric("quality_1", "depart_code");

        when(dsl.sortedMetrics()).thenReturn(Collections.singletonList(scoped));
        when(engine.computeMetric(any(MetricDefinition.class), any(), any(), anyBoolean(), any()))
                .thenReturn(new ArrayList<>());

        run("ALL");

        verify(resultMapper).deleteByPeriodAllDept("MONTH", START);
        verify(resultMapper, never()).deleteByPeriod(any(), any(), any());
        verify(resultMapper, never()).deleteByMetricCodes(any(), any(), any());
    }

    @Test
    @DisplayName("同一指标同科室出现两行 → 落库前去重，不让整批 insert 撞唯一键")
    @SuppressWarnings("unchecked")
    void duplicateRowsAreDroppedBeforeInsert() {
        MetricDefinition m = metric("quality_1", "depart_code");
        when(dsl.sortedMetrics()).thenReturn(Collections.singletonList(m));
        when(engine.computeMetric(any(MetricDefinition.class), any(), any(), anyBoolean(), any()))
                .thenReturn(new ArrayList<>(Arrays.asList(outcome(m, "ALL"), outcome(m, "ALL"))));

        run("ALL");

        ArgumentCaptor<List<QualityMetricResult>> rows = ArgumentCaptor.forClass(List.class);
        verify(resultMapper).batchInsert(rows.capture());
        assertEquals(1, rows.getValue().size(),
                "同 (指标, 科室) 两行必须去重：达梦会让整批 insert 一起失败，一条都写不进去");
    }

    @Test
    @DisplayName("整批插入撞唯一键 → 降级逐条插入，只丢冲突行，不让整批结果消失")
    @SuppressWarnings("unchecked")
    void chunkFallsBackToRowByRowOnUniqueViolation() {
        MetricDefinition ok = metric("quality_1", "depart_code");
        MetricDefinition dup = metric("quality_2", "depart_code");
        when(dsl.sortedMetrics()).thenReturn(Arrays.asList(ok, dup));
        when(engine.computeMetric(any(MetricDefinition.class), any(), any(), anyBoolean(), any()))
                .thenAnswer(inv -> new ArrayList<>(Collections.singletonList(
                        outcome((MetricDefinition) inv.getArgument(0), "ALL"))));
        // 整批失败 → 第一行逐条成功 → 第二行逐条冲突
        when(resultMapper.batchInsert(any()))
                .thenThrow(new DataIntegrityViolationException("uk_quality_result"))
                .thenReturn(1)
                .thenThrow(new DataIntegrityViolationException("uk_quality_result"));

        Map<String, Object> out = run("ALL");

        assertNotEquals("FAILED", out.get("status"), "个别行冲突不该让这一批结果全部消失");
        verify(resultMapper, times(3)).batchInsert(any());
    }

    // ------------------------------------------------------------------
    // internal
    // ------------------------------------------------------------------

    private Map<String, Object> run(String departCode) {
        service = new QualityCalcService(props, dsl, engine, runMapper, resultMapper,
                traceMapper, patientMapper);
        service.init();
        return service.recalc("MONTH", START, END, departCode, "MANUAL", "tester", false);
    }

    private static MetricDefinition metric(String code, String... dims) {
        MetricDefinition m = new MetricDefinition();
        m.setCode(code);
        m.setName(code);
        m.setDims(new ArrayList<>(Arrays.asList(dims)));
        return m;
    }

    private static MetricOutcome outcome(MetricDefinition m, String dept) {
        MetricOutcome o = new MetricOutcome();
        o.setMetricCode(m.getCode());
        o.setMetricName(m.getName());
        o.setDepartCode(dept);
        o.setMetricValue(BigDecimal.ONE);
        o.setCalcStatus("OK");
        return o;
    }
}
