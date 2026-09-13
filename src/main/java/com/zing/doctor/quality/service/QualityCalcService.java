package com.zing.doctor.quality.service;

import cn.hutool.core.util.IdUtil;
import com.zing.doctor.quality.config.QualityProperties;
import com.zing.doctor.quality.dsl.MetricDefinition;
import com.zing.doctor.quality.engine.MetricOutcome;
import com.zing.doctor.quality.engine.QualityDslLoader;
import com.zing.doctor.quality.engine.QualityEngine;
import com.zing.doctor.quality.entity.QualityCalcRun;
import com.zing.doctor.quality.entity.QualityCalcTrace;
import com.zing.doctor.quality.entity.QualityMetricPatient;
import com.zing.doctor.quality.entity.QualityMetricResult;
import com.zing.doctor.quality.mapper.QualityCalcRunMapper;
import com.zing.doctor.quality.mapper.QualityCalcTraceMapper;
import com.zing.doctor.quality.mapper.QualityMetricPatientMapper;
import com.zing.doctor.quality.mapper.QualityMetricResultMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 质控计算编排服务。
 *
 * <p>对比老系统「127 段脚本各自拉全量数据在内存里循环」，本服务的做法是：
 * <ol>
 *   <li><b>域级并行</b>：127 条指标并行提交，共享同一批已物化的事实表；
 *       事实层只建一次，因此并行开销只有数据库聚合，没有重复扫描</li>
 *   <li><b>幂等重算</b>：先按 (周期, 部门) 删除旧结果，再插入新结果，重算不留脏数据</li>
 *   <li><b>血缘自动落库</b>：每次计算都产出 run → result → trace，追溯不靠人工标注</li>
 *   <li><b>占位不跳过</b>：PLACEHOLDER / PENDING_SOURCE / MANUAL 也写结果行，
 *       页面 127 行始终完整，口径一定就能出数，无需改接口与前端</li>
 * </ol>
 */
@Service
public class QualityCalcService {

    private static final Logger log = LoggerFactory.getLogger(QualityCalcService.class);

    private static final DateTimeFormatter RUN_ID_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 批量插入分片大小，避免单条 SQL 过长。 */
    private static final int CHUNK = 200;

    private final QualityProperties props;
    private final QualityDslLoader dsl;
    private final QualityEngine engine;
    private final QualityCalcRunMapper runMapper;
    private final QualityMetricResultMapper resultMapper;
    private final QualityCalcTraceMapper traceMapper;
    private final QualityMetricPatientMapper patientMapper;

    private ExecutorService pool;

    public QualityCalcService(QualityProperties props, QualityDslLoader dsl, QualityEngine engine,
                              QualityCalcRunMapper runMapper, QualityMetricResultMapper resultMapper,
                              QualityCalcTraceMapper traceMapper, QualityMetricPatientMapper patientMapper) {
        this.props = props;
        this.dsl = dsl;
        this.engine = engine;
        this.runMapper = runMapper;
        this.resultMapper = resultMapper;
        this.traceMapper = traceMapper;
        this.patientMapper = patientMapper;
    }

    @PostConstruct
    public void init() {
        int n = Math.max(1, props.getCalcThreads());
        this.pool = Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(r, "quality-calc");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    public void destroy() {
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    /**
     * 执行一次计算。
     *
     * @param periodType  MONTH / QUARTER / YEAR / CUSTOM
     * @param departCode  科室编码；null 或 ALL 表示计算全部科室
     * @param withPatients 是否顺带预落库患者明细（默认 false，页面下钻时按需生成）
     */
    public Map<String, Object> recalc(String periodType, LocalDateTime start, LocalDateTime end,
                                      String departCode, String triggerType, String operator,
                                      boolean withPatients) {
        String runId = "QR" + RUN_ID_FMT.format(LocalDateTime.now())
                + IdUtil.fastSimpleUUID().substring(0, 4).toUpperCase();

        QualityCalcRun run = new QualityCalcRun();
        run.setRunId(runId);
        run.setPeriodType(periodType);
        run.setPeriodStart(start);
        run.setPeriodEnd(end);
        run.setDepartCode(departCode);
        run.setTriggerType(triggerType);
        run.setEngineVersion(props.getEngineVersion());
        run.setDataSnapshotId(DAY_FMT.format(start));
        run.setStartTime(LocalDateTime.now());
        run.setStatus("RUNNING");
        run.setOperator(operator);
        runMapper.insert(run);

        long t0 = System.currentTimeMillis();
        int ok = 0, fail = 0, placeholder = 0;
        List<QualityMetricResult> results = new ArrayList<>();
        List<QualityCalcTrace> traces = new ArrayList<>();
        List<QualityMetricPatient> patients = new ArrayList<>();

        try {
            // 事实层按当前周期重建
            engine.resetFacts();
            // 幂等：清掉同周期同部门的旧结果
            resultMapper.deleteByPeriod(periodType, start, departCode);

            List<MetricDefinition> metrics = dsl.sortedMetrics();
            List<MetricOutcome> outcomes = computeParallel(metrics, start, end);

            for (MetricOutcome o : outcomes) {
                if (o.getMetricValue() == null && "OK".equals(o.getCalcStatus())) {
                    o.setCalcStatus("NO_DATA");
                }
                if ("ERROR".equals(o.getCalcStatus())) {
                    fail++;
                } else if ("OK".equals(o.getCalcStatus())) {
                    ok++;
                } else {
                    placeholder++;
                }
                results.add(toResult(runId, periodType, start, end, o));
                traces.add(toTrace(runId, o));

                if ((withPatients || props.isKeepPatientDetail()) && "OK".equals(o.getCalcStatus())) {
                    collectPatients(runId, start, end, o, patients);
                }
            }

            saveInChunks(results);
            saveTraces(traces);
            savePatients(patients);

            run.setStatus(fail == 0 ? "SUCCESS" : (ok > 0 ? "PARTIAL" : "FAILED"));
        } catch (Exception e) {
            log.error("[质控] 计算批次失败: runId={}", runId, e);
            run.setStatus("FAILED");
            run.setMessage(trim(e.getMessage(), 900));
        } finally {
            long cost = System.currentTimeMillis() - t0;
            run.setEndTime(LocalDateTime.now());
            run.setDurationMs(cost);
            run.setMetricTotal(dsl.sortedMetrics().size());
            run.setMetricOk(ok);
            run.setMetricFail(fail);
            run.setMetricPlaceholder(placeholder);
            runMapper.updateById(run);
            // 清理非当前周期的事实表，避免逐月累积
            engine.purgeOldFacts(DAY_FMT.format(start));
            log.info("[质控] 计算完成 runId={} 状态={} 成功={} 失败={} 占位={} 耗时={}ms",
                    runId, run.getStatus(), ok, fail, placeholder, cost);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", runId);
        out.put("status", run.getStatus());
        out.put("metricTotal", run.getMetricTotal());
        out.put("metricOk", ok);
        out.put("metricFail", fail);
        out.put("metricPlaceholder", placeholder);
        out.put("durationMs", run.getDurationMs());
        out.put("periodStart", start);
        out.put("periodEnd", end);
        return out;
    }

    // ------------------------------------------------------------------
    // internal
    // ------------------------------------------------------------------

    /** 指标并行计算；单条指标异常不影响其他指标（异常已转成 ERROR 结果行）。 */
    private List<MetricOutcome> computeParallel(List<MetricDefinition> metrics,
                                                LocalDateTime start, LocalDateTime end) {
        List<CompletableFuture<List<MetricOutcome>>> futures = new ArrayList<>(metrics.size());
        for (MetricDefinition m : metrics) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return engine.computeMetric(m, start, end);
                } catch (Exception e) {
                    log.error("[质控] 指标计算异常: {}", m.getCode(), e);
                    MetricOutcome o = new MetricOutcome();
                    o.setMetricCode(m.getCode());
                    o.setMetricName(m.getName());
                    o.setDomain(m.getDomain());
                    o.setUnit(m.getUnit());
                    o.setCalcStatus("ERROR");
                    o.setErrorMsg(e.getMessage());
                    return Collections.singletonList(o);
                }
            }, pool));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<MetricOutcome> all = new ArrayList<>();
        for (CompletableFuture<List<MetricOutcome>> f : futures) {
            try {
                all.addAll(f.get());
            } catch (Exception e) {
                log.warn("[质控] 汇总结果异常: {}", e.getMessage());
            }
        }
        return all;
    }

    private void collectPatients(String runId, LocalDateTime start, LocalDateTime end,
                                 MetricOutcome o, List<QualityMetricPatient> sink) {
        MetricDefinition m = dsl.getMetrics().get(o.getMetricCode());
        if (m == null) {
            return;
        }
        try {
            List<MetricOutcome.PatientHit> hits = engine.loadPatients(m, start, end);
            for (MetricOutcome.PatientHit h : hits) {
                QualityMetricPatient p = new QualityMetricPatient();
                p.setRunId(runId);
                p.setMetricCode(o.getMetricCode());
                p.setPeriodStart(start);
                p.setDepartCode(o.getDepartCode());
                p.setPatientId(h.getPatientId());
                p.setInHospitalNo(h.getInHospitalNo());
                p.setPatientName(h.getPatientName());
                p.setInNumerator(h.getInNumerator());
                p.setInDenominator(h.getInDenominator());
                p.setExcludeReason(h.getInNumerator() != null && h.getInNumerator() == 1 ? null : "未计入分子");
                sink.add(p);
            }
        } catch (Exception e) {
            log.warn("[质控] 患者明细生成失败: metric={}, msg={}", o.getMetricCode(), e.getMessage());
        }
    }

    private QualityMetricResult toResult(String runId, String periodType, LocalDateTime start,
                                         LocalDateTime end, MetricOutcome o) {
        QualityMetricResult r = new QualityMetricResult();
        r.setRunId(runId);
        r.setMetricCode(o.getMetricCode());
        r.setMetricName(o.getMetricName());
        r.setDomainCode(o.getDomain());
        r.setPeriodType(periodType);
        r.setPeriodStart(start);
        r.setPeriodEnd(end);
        r.setDepartCode(o.getDepartCode() == null ? "ALL" : o.getDepartCode());
        r.setNumerator(o.getNumerator());
        r.setDenominator(o.getDenominator());
        r.setMetricValue(o.getMetricValue());
        r.setUnit(o.getUnit());
        r.setCalcStatus(o.getCalcStatus());
        r.setErrorMsg(trim(o.getErrorMsg(), 900));
        r.setExpressionVersion(o.getExpressionVersion());
        r.setCalcTime(LocalDateTime.now());
        return r;
    }

    private QualityCalcTrace toTrace(String runId, MetricOutcome o) {
        QualityCalcTrace t = new QualityCalcTrace();
        t.setRunId(runId);
        t.setMetricCode(o.getMetricCode());
        t.setDimKey(o.getDepartCode() == null ? "ALL" : o.getDepartCode());
        t.setFactName(o.getFactName());
        t.setExpressionVersion(o.getExpressionVersion());
        t.setSqlHash(o.getSqlHash());
        t.setSqlText(o.getSqlText());
        t.setSourceTables(trim(o.getSourceTables(), 900));
        t.setOperators(trim(o.getOperators(), 450));
        t.setScannedRows(o.getScannedRows());
        t.setNumRows(o.getNumRows());
        t.setDenRows(o.getDenRows());
        t.setDurationMs(o.getDurationMs());
        t.setCalcTime(LocalDateTime.now());
        return t;
    }

    /**
     * 人工录入指标值（MANUAL 类指标，如「ICU 实际病死率」「全院收治人数」）。
     *
     * <p>这类指标数据不在 ICU 库里，只能由科室上报。录入后与自动计算的指标
     * 存在同一张结果表、同一套查询与导出里，因此页面上看不出差别。
     */
    public Map<String, Object> saveManual(String code, String periodType, LocalDateTime start,
                                          LocalDateTime end, String departCode,
                                          BigDecimal value, String operator) {
        MetricDefinition m = dsl.getMetrics().get(code);
        String dept = departCode == null || departCode.trim().isEmpty() ? "ALL" : departCode.trim();

        resultMapper.deleteOne(code, periodType, start, dept);

        QualityMetricResult r = new QualityMetricResult();
        r.setMetricCode(code);
        r.setMetricName(m == null ? code : m.getName());
        r.setDomainCode(m == null ? null : m.getDomain());
        r.setPeriodType(periodType);
        r.setPeriodStart(start);
        r.setPeriodEnd(end);
        r.setDepartCode(dept);
        r.setMetricValue(value);
        r.setUnit(m == null ? null : m.getUnit());
        r.setCalcStatus("MANUAL");
        r.setExpressionVersion(m == null ? null : m.getVersion());
        r.setCalcTime(LocalDateTime.now());
        resultMapper.insert(r);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        out.put("departCode", dept);
        out.put("periodStart", start);
        out.put("value", value);
        out.put("calcStatus", "MANUAL");
        out.put("operator", operator);
        return out;
    }

    private void saveInChunks(List<QualityMetricResult> list) {
        for (int i = 0; i < list.size(); i += CHUNK) {
            resultMapper.batchInsert(list.subList(i, Math.min(i + CHUNK, list.size())));
        }
    }

    private void saveTraces(List<QualityCalcTrace> list) {
        for (int i = 0; i < list.size(); i += CHUNK) {
            traceMapper.batchInsert(list.subList(i, Math.min(i + CHUNK, list.size())));
        }
    }

    private void savePatients(List<QualityMetricPatient> list) {
        for (int i = 0; i < list.size(); i += CHUNK) {
            patientMapper.batchInsert(list.subList(i, Math.min(i + CHUNK, list.size())));
        }
    }

    private String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 供定时任务复用：等待正在执行的并行任务结束（优雅停机）。 */
    public void awaitIdle(long timeoutSeconds) {
        try {
            pool.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
