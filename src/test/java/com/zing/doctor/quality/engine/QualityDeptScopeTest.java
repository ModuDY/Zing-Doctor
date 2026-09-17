package com.zing.doctor.quality.engine;

import com.zing.doctor.quality.config.QualityProperties;
import com.zing.doctor.quality.dsl.FactDefinition;
import com.zing.doctor.quality.dsl.MetricDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 科室批次的「写入范围」判定测试（离线，不连库、不启动 Spring）。
 *
 * <p><b>它守的是现场那次整批失败</b>：选科室触发计算 → 127 条一起失败，达梦报
 * 「违反表[quality_metric_result]唯一性约束条件[uk_quality_result]」。根因是删除范围
 * 与写入范围不一致 —— 科室批次只删了「当前科室」那一行，而没有科室维度的指标
 * （占位类、dims 为空的）仍按全院口径算，会写出 {@code depart_code='ALL'} 的行，
 * 于是插入时与库里旧行撞唯一键。
 *
 * <p>{@link QualityEngine#deptFilterOf} 是删除与写入共用的唯一判据：返回 null 的指标，
 * 调用方必须把删除范围扩到整周期。本测试把这条判定钉住，防止被改回「只看科室开关」。
 */
class QualityDeptScopeTest {

    private static final String DEPT = "A001";

    @Test
    @DisplayName("只有「IMPL + 事实层可用 + 单 depart_code 维度」的指标才按科室单独算")
    void onlyDeptDimensionedImplMetricsAreDeptScoped() {
        QualityDslLoader dsl = load();
        QualityEngine engine = engine(dsl);

        int scoped = 0;
        List<String> fullScope = new ArrayList<>();
        for (MetricDefinition m : dsl.sortedMetrics()) {
            String filter = engine.deptFilterOf(m, DEPT);
            if (filter == null) {
                fullScope.add(m.getCode());
                continue;
            }
            assertEquals(DEPT, filter, m.getCode() + " 返回的过滤值应为科室编码本身");
            assertTrue(isDeptDimensioned(m),
                    m.getCode() + " 不是单 depart_code 维度，却被判为可按科室单独算");
            assertTrue(isImplWithActiveFact(dsl, m),
                    m.getCode() + " 声明未实现或事实层不可用，却被判为可按科室单独算");
            scoped++;
        }

        System.out.printf("[质控科室] 可按科室单独算 %d 条；科室批次里仍按全院口径写行 %d 条%n",
                scoped, fullScope.size());

        assertTrue(scoped > 0, "没有任何指标可按科室计算，科室计算功能等于没接上");
        assertFalse(fullScope.isEmpty(),
                "应存在无科室维度的指标：它们正是科室批次里写 ALL 行的来源，"
                        + "删除范围没跟着扩就会撞 uk_quality_result");
    }

    @Test
    @DisplayName("非科室维度、占位类、空入参一律回退全院（返回 null）")
    void nonDeptDimensionAndPlaceholdersFallBackToAll() {
        QualityDslLoader dsl = load();
        QualityEngine engine = engine(dsl);

        // 单维度但不是科室：拿科室编码过滤会一行都筛不出来，必须回退
        MetricDefinition byOutcome = new MetricDefinition();
        byOutcome.setCode("ut_by_outcome");
        byOutcome.setName("ut_by_outcome");
        byOutcome.setFact("fact_patient_stay");
        byOutcome.setDims(new ArrayList<>(Arrays.asList("out_vest_type")));
        assertNull(engine.deptFilterOf(byOutcome, DEPT), "非 depart_code 维度不应按科室过滤");

        // dims 为空：算出来天然只有一行 ALL，拿科室数据填就是把科室值冒充全院值
        MetricDefinition dimless = new MetricDefinition();
        dimless.setCode("ut_dimless");
        dimless.setName("ut_dimless");
        dimless.setFact("fact_patient_stay");
        dimless.setDims(new ArrayList<>());
        assertNull(engine.deptFilterOf(dimless, DEPT), "无维度指标不应按科室过滤");

        // 占位类指标不跑 SQL，只写一行 ALL，与科室无关
        MetricDefinition pending = new MetricDefinition();
        pending.setCode("ut_pending");
        pending.setName("ut_pending");
        pending.setFact("fact_patient_stay");
        pending.setImplStatus("PENDING_SOURCE");
        pending.setDims(new ArrayList<>(Arrays.asList("depart_code")));
        assertNull(engine.deptFilterOf(pending, DEPT), "占位类指标不应按科室过滤");

        // 入参本身不是科室
        assertNull(engine.deptFilterOf(byOutcome, null), "空科室入参应回退全院");
        assertNull(engine.deptFilterOf(byOutcome, "  "), "空白科室入参应回退全院");
        assertNull(engine.deptFilterOf(null, DEPT), "空指标不应抛异常");
    }

    // ------------------------------------------------------------------
    // internal
    // ------------------------------------------------------------------

    private static QualityDslLoader load() {
        QualityDslLoader dsl = new QualityDslLoader(new QualityProperties(), null);
        dsl.reload();
        return dsl;
    }

    /** 只用到判定逻辑，mapper / props / guard 传 null —— deptFilterOf 不碰它们。 */
    private static QualityEngine engine(QualityDslLoader dsl) {
        return new QualityEngine(dsl, new SqlCompiler(), null, null, null, new QualityExpressionAnalyzer());
    }

    private static boolean isDeptDimensioned(MetricDefinition m) {
        List<String> dims = m.getDims();
        return dims != null && dims.size() == 1 && "depart_code".equalsIgnoreCase(dims.get(0).trim());
    }

    private static boolean isImplWithActiveFact(QualityDslLoader dsl, MetricDefinition m) {
        String impl = m.getImplStatus() == null ? "IMPL" : m.getImplStatus().trim().toUpperCase();
        if (!"IMPL".equals(impl)) {
            return false;
        }
        FactDefinition f = dsl.factOf(m);
        if (f == null) {
            return false;
        }
        String fs = f.getStatus() == null ? "ACTIVE" : f.getStatus().trim().toUpperCase();
        return "ACTIVE".equals(fs);
    }
}
