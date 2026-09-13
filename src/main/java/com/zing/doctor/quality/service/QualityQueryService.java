package com.zing.doctor.quality.service;

import com.zing.doctor.quality.config.QualityProperties;
import com.zing.doctor.quality.dsl.FactDefinition;
import com.zing.doctor.quality.dsl.MetricDefinition;
import com.zing.doctor.quality.dto.PeriodRange;
import com.zing.doctor.quality.engine.MetricOutcome;
import com.zing.doctor.quality.engine.QualityDslLoader;
import com.zing.doctor.quality.engine.QualityEngine;
import com.zing.doctor.quality.entity.QualityCalcRun;
import com.zing.doctor.quality.entity.QualityCalcTrace;
import com.zing.doctor.quality.entity.QualityIndex;
import com.zing.doctor.quality.entity.QualityMetricResult;
import com.zing.doctor.quality.mapper.QualityCalcRunMapper;
import com.zing.doctor.quality.mapper.QualityCalcTraceMapper;
import com.zing.doctor.quality.mapper.QualityIndexMapper;
import com.zing.doctor.quality.mapper.QualityMetricPatientMapper;
import com.zing.doctor.quality.mapper.QualityMetricResultMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 质控查询服务：看板、指标详情、血缘下钻、配置审阅。
 */
@Service
public class QualityQueryService {

    private final QualityProperties props;
    private final QualityDslLoader dsl;
    private final QualityEngine engine;
    private final QualityIndexMapper indexMapper;
    private final QualityMetricResultMapper resultMapper;
    private final QualityCalcTraceMapper traceMapper;
    private final QualityMetricPatientMapper patientMapper;
    private final QualityCalcRunMapper runMapper;

    public QualityQueryService(QualityProperties props, QualityDslLoader dsl, QualityEngine engine,
                              QualityIndexMapper indexMapper, QualityMetricResultMapper resultMapper,
                              QualityCalcTraceMapper traceMapper,
                              QualityMetricPatientMapper patientMapper,
                              QualityCalcRunMapper runMapper) {
        this.props = props;
        this.dsl = dsl;
        this.engine = engine;
        this.indexMapper = indexMapper;
        this.resultMapper = resultMapper;
        this.traceMapper = traceMapper;
        this.patientMapper = patientMapper;
        this.runMapper = runMapper;
    }

    /**
     * 指标看板：127 行按域分组，含本期值与实现状态。
     *
     * <p>占位指标（PLACEHOLDER / PENDING_SOURCE / MANUAL）同样返回元信息，
     * 前端渲染成正常行、数值列显示「—」，因此页面永远是完整的 127 行。
     */
    public Map<String, Object> overview(String periodType, String periodStart, String departCode) {
        PeriodRange range = PeriodRange.of(periodType, periodStart);
        String dept = normalizeDepart(departCode);

        List<QualityIndex> indices = indexMapper.selectAllOrdered();
        Map<String, QualityMetricResult> resultMap = new LinkedHashMap<>();
        try {
            for (QualityMetricResult r : resultMapper.selectByPeriod(range.getStart(), dept)) {
                resultMap.put(r.getMetricCode(), r);
            }
        } catch (Exception e) {
            // 未执行过计算的周期：返回纯字典视图，便于前端先行展示
        }

        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        int ok = 0, placeholder = 0, noData = 0;
        for (QualityIndex idx : indices) {
            QualityMetricResult r = resultMap.get(idx.getIndexCode());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", idx.getIndexCode());
            row.put("name", idx.getIndexName());
            row.put("domain", idx.getDomainCode());
            row.put("unit", idx.getUnit());
            row.put("valueType", idx.getValueType());
            row.put("implStatus", idx.getImplStatus());
            row.put("factName", idx.getFactName());
            row.put("expressionVersion", idx.getExpressionVersion());
            row.put("computable", "IMPL".equals(idx.getImplStatus()) ? 1 : 0);
            row.put("remark", idx.getRemark());
            row.put("value", r == null ? null : r.getMetricValue());
            row.put("numerator", r == null ? null : r.getNumerator());
            row.put("denominator", r == null ? null : r.getDenominator());
            row.put("calcStatus", r == null ? "NOT_CALC" : r.getCalcStatus());
            row.put("runId", r == null ? null : r.getRunId());

            groups.computeIfAbsent(idx.getDomainCode() == null ? "其他" : idx.getDomainCode(),
                    k -> new ArrayList<>()).add(row);

            String st = r == null ? "NOT_CALC" : r.getCalcStatus();
            if ("OK".equals(st)) {
                ok++;
            } else if ("NO_DATA".equals(st)) {
                noData++;
            } else {
                placeholder++;
            }
        }

        List<Map<String, Object>> groupList = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : groups.entrySet()) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("domain", e.getKey());
            g.put("metrics", e.getValue());
            groupList.add(g);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", indices.size());
        summary.put("ok", ok);
        summary.put("noData", noData);
        summary.put("placeholder", placeholder);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("periodType", range.getPeriodType());
        out.put("periodStart", range.getStart());
        out.put("periodEnd", range.getEnd());
        out.put("departCode", dept);
        out.put("summary", summary);
        out.put("groups", groupList);
        return out;
    }

    /** 单指标详情：口径 + 本期值 + 追溯摘要 + 患者明细。 */
    public Map<String, Object> metricDetail(String code, String periodStart, String departCode) {
        MetricDefinition m = dsl.getMetrics().get(code);
        QualityIndex idx = indexMapper.selectByCode(code);
        PeriodRange range = PeriodRange.of("MONTH", periodStart);
        String dept = normalizeDepart(departCode);

        QualityMetricResult result = null;
        try {
            for (QualityMetricResult r : resultMapper.selectByPeriod(range.getStart(), dept)) {
                if (code.equals(r.getMetricCode())) {
                    result = r;
                    break;
                }
            }
        } catch (Exception ignore) {
            // 未计算过
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        out.put("name", idx != null ? idx.getIndexName() : (m != null ? m.getName() : code));
        out.put("domain", idx != null ? idx.getDomainCode() : (m != null ? m.getDomain() : null));
        out.put("unit", idx != null ? idx.getUnit() : (m != null ? m.getUnit() : null));
        out.put("implStatus", idx != null ? idx.getImplStatus()
                : (m != null ? m.getImplStatus() : null));
        out.put("factName", m != null ? m.getFact() : (idx != null ? idx.getFactName() : null));
        out.put("expressionVersion", m != null ? m.getVersion() : null);
        out.put("remark", idx != null ? idx.getRemark() : null);
        out.put("periodStart", range.getStart());
        out.put("periodEnd", range.getEnd());
        out.put("departCode", dept);
        out.put("result", result);
        if (result != null && result.getRunId() != null) {
            out.put("traces", traceMapper.selectByMetric(result.getRunId(), code));
            out.put("patients", patients(code, periodStart, departCode));
        }
        return out;
    }

    /** 追溯第 3 层：算子链 + SQL 原文 + 扫描行数。 */
    public Map<String, Object> trace(String runId, String code) {
        List<QualityCalcTrace> traces = traceMapper.selectByMetric(runId, code);
        List<Map<String, Object>> list = new ArrayList<>();
        for (QualityCalcTrace t : traces) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dimKey", t.getDimKey());
            row.put("factName", t.getFactName());
            row.put("operators", t.getOperators());
            row.put("sourceTables", t.getSourceTables());
            row.put("sqlHash", t.getSqlHash());
            row.put("scannedRows", t.getScannedRows());
            row.put("numRows", t.getNumRows());
            row.put("denRows", t.getDenRows());
            row.put("durationMs", t.getDurationMs());
            row.put("expressionVersion", t.getExpressionVersion());
            row.put("sql", traceMapper.selectSqlText(runId, code, t.getDimKey()));
            list.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", runId);
        out.put("code", code);
        out.put("traces", list);
        return out;
    }

    /**
     * 追溯第 4 层：患者级命中明细。
     *
     * <p>按需生成而非预计算——下钻是人工节奏、一次一条指标，因此这里直接复用事实层重算，
     * 既省掉月度批算的巨大开销，又不牺牲「点数字能看到人」的能力。
     */
    public Map<String, Object> patients(String code, String periodStart, String departCode) {
        MetricDefinition m = dsl.getMetrics().get(code);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        if (m == null) {
            out.put("message", "指标未在配置中定义");
            out.put("patients", new ArrayList<>());
            return out;
        }
        PeriodRange range = PeriodRange.of("MONTH", periodStart);
        try {
            List<MetricOutcome.PatientHit> hits = engine.loadPatients(m, range.getStart(), range.getEnd());
            List<Map<String, Object>> list = new ArrayList<>();
            int num = 0;
            for (MetricOutcome.PatientHit h : hits) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("patientId", h.getPatientId());
                row.put("inHospitalNo", h.getInHospitalNo());
                row.put("patientName", h.getPatientName());
                row.put("departCode", h.getDepartCode());
                row.put("inNumerator", h.getInNumerator());
                row.put("inDenominator", h.getInDenominator());
                if (h.getInNumerator() != null && h.getInNumerator() == 1) {
                    num++;
                }
                list.add(row);
            }
            out.put("patients", list);
            out.put("size", list.size());
            out.put("numeratorSize", num);
        } catch (Exception e) {
            out.put("message", "明细生成失败: " + e.getMessage());
            out.put("patients", new ArrayList<>());
        }
        return out;
    }

    /** 覆盖率报告：配置层对 127 条指标的可实现性承诺。 */
    public Map<String, Object> coverage() {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, int[]> stat = new LinkedHashMap<>();
        for (MetricDefinition m : dsl.sortedMetrics()) {
            FactDefinition f = dsl.factOf(m);
            String impl = m.getImplStatus() == null ? "IMPL" : m.getImplStatus().toUpperCase();
            String effective = impl;
            if ("IMPL".equals(impl)) {
                if (f == null) {
                    effective = "PENDING_SOURCE";
                } else if (f.getStatus() != null && !"ACTIVE".equalsIgnoreCase(f.getStatus())) {
                    effective = f.getStatus().toUpperCase();
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", m.getCode());
            row.put("name", m.getName());
            row.put("domain", m.getDomain());
            row.put("fact", m.getFact());
            row.put("factStatus", f == null ? null : f.getStatus());
            row.put("implStatus", effective);
            row.put("valueType", m.getValueType());
            row.put("expressionVersion", m.getVersion());
            row.put("remark", m.getRemark());
            list.add(row);

            String domain = m.getDomain() == null ? "其他" : m.getDomain();
            int[] c = stat.computeIfAbsent(domain, k -> new int[4]);
            c[0]++;
            if ("IMPL".equals(effective)) {
                c[1]++;
            } else if ("PLACEHOLDER".equals(effective)) {
                c[2]++;
            } else {
                c[3]++;
            }
        }
        List<Map<String, Object>> summary = new ArrayList<>();
        int t = 0, i = 0, p = 0, o = 0;
        for (Map.Entry<String, int[]> e : stat.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("domain", e.getKey());
            row.put("total", e.getValue()[0]);
            row.put("impl", e.getValue()[1]);
            row.put("placeholder", e.getValue()[2]);
            row.put("pending", e.getValue()[3]);
            summary.add(row);
            t += e.getValue()[0];
            i += e.getValue()[1];
            p += e.getValue()[2];
            o += e.getValue()[3];
        }
        Map<String, Object> total = new LinkedHashMap<>();
        total.put("domain", "合计");
        total.put("total", t);
        total.put("impl", i);
        total.put("placeholder", p);
        total.put("pending", o);
        summary.add(total);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("metrics", list);
        out.put("summary", summary);
        return out;
    }

    /** 事实层定义与编译后 SQL，供业务审口径（改 YAML 前先在页面上看 SQL）。 */
    public List<Map<String, Object>> facts(String periodStart) {
        PeriodRange range = PeriodRange.of("MONTH", periodStart);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, FactDefinition> e : dsl.getFacts().entrySet()) {
            FactDefinition f = e.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fact", e.getKey());
            row.put("domain", f.getDomain());
            row.put("status", f.getStatus());
            row.put("source", f.getSource());
            row.put("note", f.getNote());
            row.put("sourceTables", engine.currentFactSql(e.getKey(), range.getStart(), range.getEnd()));
            list.add(row);
        }
        return list;
    }

    public List<QualityCalcRun> recentRuns() {
        try {
            return runMapper.selectRecent();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 已预落库的患者明细（仅当开启 keepPatientDetail 时有值）。 */
    public List<Map<String, Object>> storedPatients(String code, LocalDateTime periodStart, String departCode) {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            for (com.zing.doctor.quality.entity.QualityMetricPatient p
                    : patientMapper.selectByMetric(code, periodStart, normalizeDepart(departCode))) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("inHospitalNo", p.getInHospitalNo());
                row.put("patientName", p.getPatientName());
                row.put("inNumerator", p.getInNumerator());
                row.put("inDenominator", p.getInDenominator());
                row.put("excludeReason", p.getExcludeReason());
                list.add(row);
            }
        } catch (Exception ignore) {
            // 未预落库时返回空
        }
        return list;
    }

    public QualityMetricResult findResult(String code, LocalDateTime periodStart, String departCode) {
        try {
            for (QualityMetricResult r : resultMapper.selectByPeriod(periodStart, normalizeDepart(departCode))) {
                if (code.equals(r.getMetricCode())) {
                    return r;
                }
            }
        } catch (Exception ignore) {
            // ignore
        }
        return null;
    }

    public boolean enabled() {
        return props.isEnabled();
    }

    private String normalizeDepart(String departCode) {
        return departCode == null || departCode.trim().isEmpty() ? "ALL" : departCode.trim();
    }

    /** 两值相对差异率，供双跑核对。 */
    public static BigDecimal diff(BigDecimal mine, BigDecimal other) {
        if (mine == null || other == null || other.signum() == 0) {
            return null;
        }
        return mine.subtract(other).abs()
                .divide(other.abs(), 6, RoundingMode.HALF_UP);
    }
}
