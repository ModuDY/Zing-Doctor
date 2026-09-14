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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Data;

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

    /**
     * 批次执行器：单线程。
     *
     * <p>串行是刻意的：事实层是进程内共享状态（物化表缓存），两个批次同时
     * {@code resetFacts()} 会互相踩掉对方刚建好的表。异步化后不再有 HTTP 请求串行化保护，
     * 必须靠单线程队列保证「一次只跑一个批次」。
     */
    private ExecutorService batchPool;

    /**
     * 计算互斥锁：批次计算与单指标重算共用。
     *
     * <p>单指标重算走 HTTP 线程，与 {@code batchPool} 里的批次不在同一线程体系，
     * 因此需要显式加锁；用 tryLock 而非 lock —— 批算期间点单指标应立刻得到「忙」的反馈，
     * 而不是让浏览器干等几分钟。
     */
    private final ReentrantLock calcLock = new ReentrantLock();

    /** runId → 实时进度（内存态，供前端轮询，不落库：每完成一条就写库太重）。 */
    private final Map<String, RunProgress> progressMap = new ConcurrentHashMap<>();

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
        this.batchPool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "quality-batch");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    public void destroy() {
        if (pool != null) {
            pool.shutdownNow();
        }
        if (batchPool != null) {
            batchPool.shutdownNow();
        }
    }

    /**
     * 执行一次计算（同步：跑完才返回）。
     *
     * <p>适合定时任务等无人值守场景。<b>页面「触发计算」请用 {@link #submit(...)}</b>：
     * 127 条指标要重建全部事实层，生产上常以分钟计，同步等待会撞上前端 60s 超时——
     * 用户看到的是假的「失败」，后端其实还在跑，还容易引发「以为没跑又点一次」的重复批次。
     *
     * @param periodType  MONTH / QUARTER / YEAR / CUSTOM
     * @param departCode  科室编码；null 或 ALL 表示计算全部科室
     * @param withPatients 是否顺带预落库患者明细（默认 false，页面下钻时按需生成）
     */
    public Map<String, Object> recalc(String periodType, LocalDateTime start, LocalDateTime end,
                                      String departCode, String triggerType, String operator,
                                      boolean withPatients) {
        QualityCalcRun run = createRun(periodType, start, end, departCode, triggerType, operator);
        return execute(run, start, end, departCode, withPatients);
    }

    /**
     * 异步提交一次计算：立即返回 runId，页面轮询 {@link #getRun(String)} 取进度与结论。
     *
     * <p>批次在单线程 {@code batchPool} 上排队执行，因此并发提交也只会一个一个跑，
     * 不会互相踩事实层。
     */
    public Map<String, Object> submit(String periodType, LocalDateTime start, LocalDateTime end,
                                      String departCode, String triggerType, String operator,
                                      boolean withPatients) {
        QualityCalcRun run = createRun(periodType, start, end, departCode, triggerType, operator);
        RunProgress p = new RunProgress();
        p.setTotal(run.getMetricTotal() == null ? 0 : run.getMetricTotal());
        progressMap.put(run.getRunId(), p);
        batchPool.submit(() -> {
            try {
                execute(run, start, end, departCode, withPatients);
            } catch (Throwable t) {
                log.error("[质控] 异步批次异常: runId={}", run.getRunId(), t);
            }
        });
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", run.getRunId());
        out.put("status", "RUNNING");
        out.put("metricTotal", p.getTotal());
        out.put("periodStart", start);
        out.put("periodEnd", end);
        return out;
    }

    /**
     * 单指标重算：只算这一条，秒级返回。
     *
     * <p>典型场景是「只改了一条指标的口径，只想看它」——不必再等 127 条跑完。
     * 只重建该指标依赖的那一张事实表（refreshFact=true），代价远小于整批重算。
     *
     * <p>与批次计算互斥：批算期间点单指标会立即得到 BUSY，而不是干等到超时。
     *
     * @return 含 result（当前筛选科室的结果行），页面可就地更新这一行
     */
    public Map<String, Object> recalcOne(String code, String periodType, LocalDateTime start,
                                         LocalDateTime end, String departCode, String operator) {
        MetricDefinition m = dsl.getMetrics().get(code);
        if (m == null) {
            throw new IllegalArgumentException("未定义的指标: " + code);
        }
        String dept = departCode == null || departCode.trim().isEmpty() ? "ALL" : departCode.trim();
        if (!calcLock.tryLock()) {
            return busy(code, "已有计算任务在执行，请稍候再试");
        }
        long t0 = System.currentTimeMillis();
        try {
            List<MetricOutcome> outcomes;
            try {
                outcomes = engine.computeMetric(m, start, end, true);
            } catch (Exception e) {
                log.error("[质控] 单指标计算异常: code={}", code, e);
                MetricOutcome o = new MetricOutcome();
                o.setMetricCode(code);
                o.setMetricName(m.getName());
                o.setDomain(m.getDomain());
                o.setUnit(m.getUnit());
                o.setCalcStatus("ERROR");
                o.setErrorMsg(e.getMessage());
                outcomes = Collections.singletonList(o);
            }

            // 幂等：该指标在本周期下的所有科室行一并覆盖，避免新旧结果混杂
            String runId = "QS" + RUN_ID_FMT.format(LocalDateTime.now());
            resultMapper.deleteByMetricPeriod(code, periodType, start);

            int ok = 0, fail = 0, placeholder = 0;
            List<QualityMetricResult> results = new ArrayList<>();
            List<QualityCalcTrace> traces = new ArrayList<>();
            for (MetricOutcome o : outcomes) {
                if ("ERROR".equals(o.getCalcStatus())) {
                    fail++;
                } else if ("OK".equals(o.getCalcStatus())) {
                    ok++;
                } else {
                    placeholder++;
                }
                results.add(toResult(runId, periodType, start, end, o));
                traces.add(toTrace(runId, o));
            }
            saveInChunks(results);
            saveTraces(traces);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("code", code);
            out.put("runId", runId);
            out.put("departCode", dept);
            out.put("metricTotal", outcomes.size());
            out.put("metricOk", ok);
            out.put("metricFail", fail);
            out.put("metricPlaceholder", placeholder);
            out.put("durationMs", System.currentTimeMillis() - t0);
            out.put("periodStart", start);
            out.put("periodEnd", end);
            out.put("result", pick(outcomes, dept));
            return out;
        } finally {
            calcLock.unlock();
        }
    }

    /** 计算进行中：给页面一个确定状态，而不是让它干等到超时。 */
    private Map<String, Object> busy(String code, String msg) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        out.put("calcStatus", "BUSY");
        out.put("errorMsg", msg);
        return out;
    }

    /** 从多条结果里挑出当前筛选科室那一行（没有则退第一条）。 */
    private Map<String, Object> pick(List<MetricOutcome> outcomes, String dept) {
        MetricOutcome hit = null;
        for (MetricOutcome o : outcomes) {
            String d = o.getDepartCode() == null ? "ALL" : o.getDepartCode();
            if (dept.equals(d)) {
                hit = o;
                break;
            }
        }
        if (hit == null && !outcomes.isEmpty()) {
            hit = outcomes.get(0);
        }
        if (hit == null) {
            return null;
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", hit.getMetricCode());
        r.put("departCode", hit.getDepartCode() == null ? "ALL" : hit.getDepartCode());
        r.put("value", hit.getMetricValue());
        r.put("numerator", hit.getNumerator());
        r.put("denominator", hit.getDenominator());
        r.put("unit", hit.getUnit());
        r.put("calcStatus", hit.getCalcStatus());
        r.put("errorMsg", hit.getErrorMsg());
        return r;
    }

    // ------------------------------------------------------------------
    // internal
    // ------------------------------------------------------------------

    /** 建批次记录（RUNNING）并落库；总数在这里就写入，页面轮询时进度条才有分母。 */
    private QualityCalcRun createRun(String periodType, LocalDateTime start, LocalDateTime end,
                                     String departCode, String triggerType, String operator) {
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
        run.setMetricTotal(dsl.sortedMetrics().size());
        runMapper.insert(run);
        return run;
    }

    /**
     * 批次执行主体：重建事实层 → 并行算 → 落库 → 收尾。同步与异步两个入口共用。
     *
     * <p>持 {@code calcLock} 执行，与单指标重算互斥。
     */
    private Map<String, Object> execute(QualityCalcRun run, LocalDateTime start, LocalDateTime end,
                                        String departCode, boolean withPatients) {
        String runId = run.getRunId();
        RunProgress progress = progressMap.get(runId);
        long t0 = System.currentTimeMillis();
        int ok = 0, fail = 0, placeholder = 0;
        List<QualityMetricResult> results = new ArrayList<>();
        List<QualityCalcTrace> traces = new ArrayList<>();
        List<QualityMetricPatient> patients = new ArrayList<>();

        calcLock.lock();
        try {
            // 事实层按当前周期重建
            engine.resetFacts();
            // 幂等：清掉同周期同部门的旧结果
            resultMapper.deleteByPeriod(run.getPeriodType(), start, departCode);

            List<MetricDefinition> metrics = dsl.sortedMetrics();
            List<MetricOutcome> outcomes = computeParallel(metrics, start, end, progress);

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
                results.add(toResult(runId, run.getPeriodType(), start, end, o));
                traces.add(toTrace(runId, o));

                if ((withPatients || props.isKeepPatientDetail()) && "OK".equals(o.getCalcStatus())) {
                    collectPatients(runId, start, end, o, patients);
                }
                // 实时进度：写内存而不写库，避免每完成一条就 update 一次批次表
                if (progress != null) {
                    progress.setOk(ok);
                    progress.setFail(fail);
                    progress.setPlaceholder(placeholder);
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
            if (progress != null) {
                progress.setOk(ok);
                progress.setFail(fail);
                progress.setPlaceholder(placeholder);
                progress.setDurationMs(cost);
                progress.setStatus(run.getStatus());
                progress.setFinishAt(System.currentTimeMillis());
            }
            calcLock.unlock();
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

    /**
     * 查询批次进度（供页面轮询）。
     *
     * <p>RUNNING 时以内存进度为准——此时库里计数还是 0，读库只会一直显示 0%；
     * 终态后库与内存一致，直接取库里的结论。
     */
    public Map<String, Object> getRun(String runId) {
        Map<String, Object> out = new LinkedHashMap<>();
        // 注意：实体主键是自增 id，不是 runId，因此不能用 selectById
        QualityCalcRun run = runMapper.selectByRunId(runId);
        if (run == null) {
            out.put("exists", false);
            return out;
        }
        out.put("exists", true);
        out.put("runId", run.getRunId());
        out.put("status", run.getStatus());
        out.put("metricTotal", run.getMetricTotal());
        out.put("metricOk", run.getMetricOk());
        out.put("metricFail", run.getMetricFail());
        out.put("metricPlaceholder", run.getMetricPlaceholder());
        out.put("durationMs", run.getDurationMs());
        out.put("message", run.getMessage());
        out.put("periodStart", run.getPeriodStart());
        out.put("periodEnd", run.getPeriodEnd());

        RunProgress p = progressMap.get(runId);
        if (p == null) {
            out.put("running", "RUNNING".equals(run.getStatus()));
            out.put("done", run.getMetricOk() == null ? 0 : run.getMetricOk());
            return out;
        }
        out.put("running", "RUNNING".equals(p.getStatus()));
        out.put("done", p.getDone());
        if ("RUNNING".equals(p.getStatus())) {
            out.put("metricTotal", p.getTotal());
            out.put("metricOk", p.getOk());
            out.put("metricFail", p.getFail());
            out.put("metricPlaceholder", p.getPlaceholder());
            out.put("durationMs", System.currentTimeMillis() - p.getStartAt());
        }
        // 终态条目留 10 分钟供最后一两次轮询取到结论，之后清理，避免无限增长
        if (p.getFinishAt() > 0 && System.currentTimeMillis() - p.getFinishAt() > 10 * 60 * 1000) {
            progressMap.remove(runId);
        }
        return out;
    }

    /** 指标并行计算；单条指标异常不影响其他指标（异常已转成 ERROR 结果行）。 */
    private List<MetricOutcome> computeParallel(List<MetricDefinition> metrics,
                                                LocalDateTime start, LocalDateTime end,
                                                RunProgress progress) {
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
            }, pool).whenComplete((r, ex) -> {
                // 每算完一条就推进进度：前端进度条依据的是这里，不是落库结果
                if (progress != null) {
                    progress.incrementDone();
                }
            }));
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

    /**
     * 批次实时进度（内存态）。
     *
     * <p>不落库的原因：一条指标算完就 update 一次批次表，127 条就是 127 次写，
     * 而这张表同时被页面轮询读——用一次 HTTP 轮询的代价换掉上百次写库更划算。
     * 进程重启会丢失进度，但 run 记录的终态仍在库里，页面只会看到「进度卡住」而非结论错误。
     */
    @Data
    public static class RunProgress {
        private final AtomicInteger done = new AtomicInteger();
        private final long startAt = System.currentTimeMillis();
        private int total;
        private int ok;
        private int fail;
        private int placeholder;
        private String status = "RUNNING";
        private long durationMs;
        /** 终态时间戳（0 表示仍在跑），用于延迟清理 */
        private long finishAt;

        public int getDone() {
            return done.get();
        }

        public void incrementDone() {
            done.incrementAndGet();
        }
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
