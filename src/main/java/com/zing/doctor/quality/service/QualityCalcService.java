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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * 人工录入值的上限，只用于拦「多按了几个零」这类笔误，不是业务口径约束。
     *
     * <p>取 1 亿：质控指标无论计数还是率都不该到这个量级，超了必然是输入错误。
     */
    private static final BigDecimal MANUAL_VALUE_CEILING = new BigDecimal("100000000");

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
        // 科室模式：只算该科室。能否按科室算由引擎判定，判不过（无科室维度的指标）自动退回全院。
        // 必须取引擎的判定结果而不是只看入参：删除范围要与写入范围一致，
        // 若按入参只删该科室那一行，而引擎实际按全院算（写出 ALL 行），插入时即撞唯一键
        String filter = engine.deptFilterOf(m, deptFilter(dept));
        if (!calcLock.tryLock()) {
            return busy(code, "已有计算任务在执行，请稍候再试");
        }
        long t0 = System.currentTimeMillis();
        try {
            List<MetricOutcome> outcomes;
            try {
                if (engine.isDerived(m)) {
                    // 派生指标不跑自己的 SQL：先把被引用指标算一遍（内存态、不落库），再按科室组合。
                    // 复用的是「刚算出来的值」而不是上一批的旧行 —— 重算的语义就是看到最新源数据
                    outcomes = engine.composeDerived(m, computeRefs(m, start, end, dept), filter);
                } else {
                    outcomes = engine.computeMetric(m, start, end, true, filter);
                }
            } catch (Exception e) {
                log.error("[质控] 单指标计算异常: code={}", code, e);
                MetricOutcome o = new MetricOutcome();
                o.setMetricCode(code);
                o.setMetricName(m.getName());
                o.setDomain(m.getDomain());
                o.setUnit(m.getUnit());
                // 与删除范围对齐：删除按 filter 删，兜底行也必须写回同一个科室。
                // 留 null 会被 toResult 折成 'ALL'，写出删除范围之外的行 —— 那正是
                // 「科室重算报撞唯一键、日志却是 depart=ALL」的另一半成因。
                o.setDepartCode(filter == null ? "ALL" : filter);
                o.setCalcStatus("ERROR");
                o.setErrorMsg(e.getMessage());
                outcomes = Collections.singletonList(o);
            }

            // 幂等范围必须与写入范围一致：按科室算只写该科室一行，
            // 若清掉全部科室，其他科室的结果就凭空消失了
            String runId = "QS" + RUN_ID_FMT.format(LocalDateTime.now());
            if (filter == null) {
                resultMapper.deleteByMetricPeriod(code, periodType, start);
            } else {
                resultMapper.deleteByMetricPeriodDept(code, periodType, start, filter);
            }

            // 上面两条删除语句都带 MANUAL_GUARD：人工录入过的行被留在库里、没被删掉。
            // 本次就绝不能再写同 key 的行 —— 否则插入必然撞 uk_quality_result。
            // 批次计算有这道保护，单指标重算同样必须有：少了它，「人工录入过的指标一点重算
            // 就报『结果行全部撞唯一键』」，而那行数据其实根本没坏，是重算自己撞上去的。
            // 跳过而不是覆盖：把人工确认过的真值换成计算值，属于静默的数据丢失。
            Set<String> protectedManual = manualProtectedKeys(periodType, start);

            int ok = 0, fail = 0, placeholder = 0, preserved = 0;
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
                // 人工录入过的指标：保留库里的真值，跳过本次写入
                if (protectedManual.contains(metricDeptKey(o.getMetricCode(), o.getDepartCode()))) {
                    preserved++;
                    continue;
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
            // 被人工录入保护跳过的行数：>0 表示这次重算没覆盖任何值，
            // 页面据此说明「该指标已人工录入，重算不覆盖」，而不是让用户以为算完了
            out.put("metricPreserved", preserved);
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

    /**
     * 科室过滤值：ALL / 空 / null 一律表示全院（返回 null），其余原样用于过滤。
     *
     * <p>集中归一，避免各处自行判断 "ALL" 造成语义分叉 —— 那类分叉最终表现为
     * 「某条路径按全院算、另一条按科室算」，值对不上却很难查。
     */
    private String deptFilter(String departCode) {
        if (departCode == null) {
            return null;
        }
        String d = departCode.trim();
        return d.isEmpty() || "ALL".equalsIgnoreCase(d) ? null : d;
    }

    /**
     * 科室批次里「删除范围要扩到整个周期」的指标编码。
     *
     * <p>判据直接取引擎的 {@link QualityEngine#deptFilterOf}：拿不到生效科室值的指标，
     * 本批次写的是全院口径的行（ALL 或各科室），而不只是当前科室这一行，旧结果必须整周期清掉。
     * 刻意不在别处复制一份判定逻辑 —— 两处判断一旦漂移，唯一键冲突就会以
     * 「偶发的整批失败」形式回来，而那时已很难想起它和科室开关有关。
     */
    private List<String> fullScopeCodes(List<MetricDefinition> metrics, String dept) {
        List<String> codes = new ArrayList<>();
        for (MetricDefinition m : metrics) {
            if (engine.deptFilterOf(m, dept) == null) {
                codes.add(m.getCode());
            }
        }
        return codes;
    }

    /**
     * 科室批次实际要算的指标：跳过「与科室无关、且该周期全院行已在库里」的占位指标。
     *
     * <p><b>为什么敢跳</b>：占位指标不跑 SQL，值恒为一行 {@code depart_code='ALL'}，
     * 与科室无关。科室批次为它做的只有两件事 —— 把整周期的旧行删掉、再把同样内容
     * 写回去。跳过后看板上这些行仍是上一次全院计算留下的值，数字不变，
     * 也不会再出现「删了还没写回」的空窗（现场那次失败就让该科室 85 条结果凭空消失）。
     *
     * <p><b>为什么还要确认 ALL 行已存在</b>：若该周期从未跑过全院计算（新周期第一次
     * 就只选科室算），跳过会让全院视图缺这些行。查一次库远比缺 42 行数据便宜。
     */
    private List<MetricDefinition> deptTargets(List<MetricDefinition> metrics,
                                               String periodType, LocalDateTime start) {
        Set<String> hasAll = existingAllCodes(periodType, start);
        List<MetricDefinition> targets = new ArrayList<>(metrics.size());
        for (MetricDefinition m : metrics) {
            if (engine.isDeptIrrelevant(m) && hasAll.contains(m.getCode())) {
                continue;
            }
            targets.add(m);
        }
        return targets;
    }

    /**
     * 该周期已有全院行的指标编码。
     *
     * <p>查询失败返回空集 —— 调用方据此判定「没有 ALL 行」，于是不跳过、照常计算，
     * 宁可多算 42 条也不让全院视图缺行。
     */
    private Set<String> existingAllCodes(String periodType, LocalDateTime start) {
        try {
            List<String> codes = resultMapper.selectCodesWithAll(periodType, start);
            return codes == null ? Collections.emptySet() : new HashSet<>(codes);
        } catch (Exception e) {
            log.warn("[质控] 全院行存在性查询失败，本次不跳过占位指标: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * 单指标重算派生指标时，临时把被引用指标算一遍（内存态，不落库）。
     *
     * <p>不复用批次结果是有意的：单指标重算的语义是「按最新源数据再算一次」，
     * 拿上一批的旧行来除，页面会显示一个新旧混在一起的数，比慢几秒危险得多。
     */
    private List<MetricOutcome> computeRefs(MetricDefinition m, LocalDateTime start, LocalDateTime end,
                                            String dept) {
        List<MetricOutcome> refs = new ArrayList<>();
        for (String code : new String[]{m.getNumeratorMetric(), m.getDenominatorMetric()}) {
            MetricDefinition ref = code == null ? null : dsl.getMetrics().get(code);
            if (ref == null) {
                continue;
            }
            refs.addAll(engine.computeMetric(ref, start, end, true, engine.deptFilterOf(ref, deptFilter(dept))));
        }
        return refs;
    }

    /** 一条指标的失败结果行：让页面看到原因，而不是静默缺行。 */
    private MetricOutcome errorOutcome(MetricDefinition m, String dept, String msg) {
        MetricOutcome o = new MetricOutcome();
        o.setMetricCode(m.getCode());
        o.setMetricName(m.getName());
        o.setDomain(m.getDomain());
        o.setUnit(m.getUnit());
        o.setDepartCode(dept == null ? "ALL" : dept);
        o.setCalcStatus("ERROR");
        o.setErrorMsg(msg);
        return o;
    }

    /**
     * 从多条结果里挑出当前筛选科室那一行。
     *
     * <p>顺序：精确匹配科室 → 全院(ALL) 行 → 第一条。
     * 中间的「优先 ALL」是必要的：该指标若没有科室维度（引擎会退回全院计算），
     * 返回的可能是多条维度行，直接取第一条会让页面把一个维度值当成用户的科室值。
     */
    private Map<String, Object> pick(List<MetricOutcome> outcomes, String dept) {
        MetricOutcome hit = null;
        MetricOutcome all = null;
        for (MetricOutcome o : outcomes) {
            String d = o.getDepartCode() == null ? "ALL" : o.getDepartCode();
            if ("ALL".equals(d) && all == null) {
                all = o;
            }
            if (dept.equals(d)) {
                hit = o;
                break;
            }
        }
        if (hit == null) {
            hit = all;
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
        // 本次因「人工录入值受保护」而未写回的指标数。单列一个计数器是为了让日志能直说
        // 「批次跑过了，但有几条人工值被保留」，而不是让人对着条数差额猜原因。
        int preserved = 0;
        List<QualityMetricResult> results = new ArrayList<>();
        List<QualityCalcTrace> traces = new ArrayList<>();
        List<QualityMetricPatient> patients = new ArrayList<>();

        String dept = deptFilter(departCode);
        List<MetricDefinition> metrics = dsl.sortedMetrics();
        // 科室批次里被跳过的指标不参与计算，也就不进删除范围；targets 必须早于 try 定义，
        // 因为 finally 里的 run.metricTotal 要用它（异常时它仍是「全部指标」）
        List<MetricDefinition> targets = metrics;
        int skipped = 0;

        calcLock.lock();
        try {
            // 事实层按当前周期重建
            engine.resetFacts();

            if (dept != null) {
                targets = deptTargets(metrics, run.getPeriodType(), start);
                skipped = metrics.size() - targets.size();
                // 进度条分母跟着实际要算的条数走：否则会停在 85/127，看起来像卡住
                if (progress != null) {
                    progress.setTotal(targets.size());
                }
            }

            // 派生指标（引用其它指标结果）必须等被引用指标算完，因此分两阶段：
            // 先并行算有 SQL 的基础指标，再逐条组合派生指标
            List<MetricDefinition> base = new ArrayList<>();
            List<MetricDefinition> derived = new ArrayList<>();
            for (MetricDefinition m : targets) {
                (engine.isDerived(m) ? derived : base).add(m);
            }

            List<MetricOutcome> outcomes = new ArrayList<>(computeParallel(base, start, end, progress, dept));
            for (MetricDefinition m : derived) {
                try {
                    outcomes.addAll(engine.composeDerived(m, outcomes, dept));
                } catch (Exception e) {
                    log.error("[质控] 派生指标组合失败: {}", m.getCode(), e);
                    outcomes.add(errorOutcome(m, dept, e.getMessage()));
                }
                if (progress != null) {
                    progress.incrementDone();
                }
            }

            // 幂等：删除范围必须逐条对齐写入范围，否则会撞唯一键 uk_quality_result（现场已踩）。
            //  · 全院批次：所有指标都写「全院 + 各科室」全量行 → 整周期清空
            //  · 科室批次：有科室维度的指标只写该科室一行 → 只清该科室那一行；
            //    但占位类、dims 为空 / 非 depart_code 维度的指标仍按全院口径算，会写出
            //    ALL 乃至各科室行，这些指标必须按「指标 × 整周期」清，否则插入 ALL 行即冲突
            //
            // 位置刻意放在算完之后、插入之前：先删后算的话，中途失败（唯一键冲突、源库抖动、
            // 进程被杀）会留下「删了却没写回去」的空窗 —— 现场那次失败就让该科室 85 条结果凭空消失。
            if (dept == null) {
                resultMapper.deleteByPeriodAllDept(run.getPeriodType(), start);
            } else {
                resultMapper.deleteByPeriod(run.getPeriodType(), start, dept);
                List<String> fullScope = fullScopeCodes(targets, dept);
                if (!fullScope.isEmpty()) {
                    resultMapper.deleteByMetricCodes(run.getPeriodType(), start, fullScope);
                }
            }

            // 上面各条删除语句都排除了 value_source='MANUAL' 的行，它们仍留在库里。
            // 本批次绝不能再写同 key 的行：撞唯一键只是表象，真正危险的是把人工录入的
            // 真值替换成 value=null 的占位行 —— 那是静默的数据丢失。
            Set<String> protectedManual = manualProtectedKeys(run.getPeriodType(), start);

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
                // 人工录入过的指标：保留库里的真值，跳过本次写入
                if (protectedManual.contains(metricDeptKey(o.getMetricCode(), o.getDepartCode()))) {
                    preserved++;
                    continue;
                }
                results.add(toResult(runId, run.getPeriodType(), start, end, o));
                traces.add(toTrace(runId, o));

                if ((withPatients || props.isKeepPatientDetail()) && "OK".equals(o.getCalcStatus())) {
                    collectPatients(runId, start, end, o, patients, dept);
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
            run.setMetricTotal(targets.size());
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
            log.info("[质控] 计算完成 runId={} 状态={} 成功={} 失败={} 占位={} 保留人工值={} 耗时={}ms",
                    runId, run.getStatus(), ok, fail, placeholder, preserved, cost);
            if (fail > 0) {
                // 用固定关键字标记，便于日志平台配一条告警规则。
                // 计算失败此前只落库、没有任何主动提示：源表改结构导致某条指标连续几个月算不出来，
                // 会一直到上报时才发现。质控数据宁可吵，也不能静默缺失。
                log.error("[质控] QC-CALC-ERROR 批次存在计算失败 runId={} 失败={} 周期={} 科室={}",
                        runId, fail, start, dept == null ? "ALL" : dept);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", runId);
        out.put("status", run.getStatus());
        out.put("metricTotal", run.getMetricTotal());
        out.put("metricOk", ok);
        out.put("metricFail", fail);
        out.put("metricPlaceholder", placeholder);
        out.put("metricSkipped", skipped);
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
                                                RunProgress progress, String dept) {
        List<CompletableFuture<List<MetricOutcome>>> futures = new ArrayList<>(metrics.size());
        for (MetricDefinition m : metrics) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return engine.computeMetric(m, start, end, false, dept);
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
                                 MetricOutcome o, List<QualityMetricPatient> sink, String dept) {
        MetricDefinition m = dsl.getMetrics().get(o.getMetricCode());
        if (m == null) {
            return;
        }
        try {
            List<MetricOutcome.PatientHit> hits = engine.loadPatients(m, start, end, dept);
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
        // 引擎产出的行显式标 AUTO：与人工录入的 MANUAL 形成完整二分，
        // 重算保护只需判断一个字段，不必再推断「这行到底是不是人写的」
        r.setValueSource("AUTO");
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
     *
     * <p>下面三件事必须一起做，缺任何一件都会留下真实后果：
     * <ol>
     *   <li><b>事务</b>：先删后插必须原子。此前没有事务，insert 一旦失败（连接抖动、
     *       值超长、唯一键）就留下「删了却没写回」的空窗 —— 录入值当场永久丢失。
     *       批算路径早就为同一问题做了防护，唯独这条人工路径漏了。</li>
     *   <li><b>值域校验</b>：录入是纯人工动作，笔误会直接进报表，而重算不会纠正它
     *       （人工值受保护），所以必须在入口拦住。</li>
     *   <li><b>归属与留痕</b>：写 {@code value_source='MANUAL'} 与操作人。前者是重算保护的
     *       判据，后者是上报被追问「谁录的、什么时候录的」时的唯一凭据。</li>
     * </ol>
     *
     * @param note 录入备注（说明取数依据），可为空
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveManual(String code, String periodType, LocalDateTime start,
                                          LocalDateTime end, String departCode,
                                          BigDecimal value, String operator, String note) {
        MetricDefinition m = dsl.getMetrics().get(code);
        String dept = departCode == null || departCode.trim().isEmpty() ? "ALL" : departCode.trim();

        validateManualValue(m, value);

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
        // 值归属必须显式写：它是重算时「不要动这一行」的判据
        r.setValueSource("MANUAL");
        r.setOperator(normalizeOperator(operator));
        r.setManualNote(trim(note, 480));
        r.setExpressionVersion(m == null ? null : m.getVersion());
        r.setCalcTime(LocalDateTime.now());
        r.setUpdateTime(LocalDateTime.now());
        resultMapper.insert(r);

        log.info("[质控] 人工录入: code={} dept={} period={} value={} operator={}",
                code, dept, start, value, r.getOperator());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        out.put("departCode", dept);
        out.put("periodStart", start);
        out.put("value", value);
        out.put("calcStatus", "MANUAL");
        out.put("valueSource", "MANUAL");
        out.put("operator", r.getOperator());
        out.put("note", r.getManualNote());
        out.put("updateTime", r.getUpdateTime());
        return out;
    }

    /**
     * 录入值域校验。
     *
     * <p>放在服务层而不是页面：页面校验挡得住手滑，挡不住直接调接口；而错值一旦落库，
     * 会沿「结果表 → 月度宽表 → 上报导出」一路传下去，且重算不会纠正它。
     *
     * <p>只判断「明显不可能」，不做过细的业务口径约束 —— 过严会把真实但极端的值挡在外面，
     * 使用者只能绕过系统手工改报表，反而更糟。
     */
    private void validateManualValue(MetricDefinition m, BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("录入值不能为空");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("录入值不能为负数："
                    + value.stripTrailingZeros().toPlainString());
        }
        String type = m == null || m.getValueType() == null ? "" : m.getValueType().trim().toUpperCase();
        if ("RATE".equals(type) && value.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("率类指标的录入值应是百分数（0-100），当前为 "
                    + value.stripTrailingZeros().toPlainString()
                    + "；若源数据是小数，请先乘以 100 再录入");
        }
        if (("COUNT".equals(type) || "DAYS".equals(type)) && value.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("计数 / 天数类指标的录入值应为整数，当前为 "
                    + value.stripTrailingZeros().toPlainString());
        }
        if (value.compareTo(MANUAL_VALUE_CEILING) > 0) {
            throw new IllegalArgumentException("录入值超出合理上限（1 亿），请确认是否多输了数位："
                    + value.stripTrailingZeros().toPlainString());
        }
    }

    /** 操作人兜底：为空时写 unknown，保证审计列非空，且与真人姓名一眼可区分。 */
    private String normalizeOperator(String operator) {
        if (operator == null || operator.trim().isEmpty()) {
            return "unknown";
        }
        String v = operator.replaceAll("[\\r\\n\\t]", " ").trim();
        return v.length() > 64 ? v.substring(0, 64) : v;
    }

    /**
     * 本周期受保护的人工录入行（指标 × 科室）。
     *
     * <p>查询异常时返回空集合而不是抛出：宁可退回「照常写占位行」（结果与升级前一致），
     * 也不要让一次查询失败把整批重算变成全周期数据缺失。
     */
    private Set<String> manualProtectedKeys(String periodType, LocalDateTime start) {
        Set<String> keys = new HashSet<>();
        try {
            for (QualityMetricResult r : resultMapper.selectManualProtected(periodType, start)) {
                keys.add(metricDeptKey(r.getMetricCode(), r.getDepartCode()));
            }
        } catch (Exception e) {
            log.warn("[质控] 查询受保护的人工录入行失败，本次不跳过任何指标: {}", e.getMessage());
        }
        return keys;
    }

    /** 结果行的业务主键（与唯一索引 uk_quality_result 一致）：指标 × 科室。 */
    private String metricDeptKey(String code, String dept) {
        return (code == null ? "" : code.trim()) + "|"
                + (dept == null || dept.trim().isEmpty() ? "ALL" : dept.trim());
    }

    private void saveInChunks(List<QualityMetricResult> list) {
        List<QualityMetricResult> uniq = dedupeByKey(list);
        for (int i = 0; i < uniq.size(); i += CHUNK) {
            insertChunk(uniq.subList(i, Math.min(i + CHUNK, uniq.size())));
        }
    }

    /**
     * 先整批插入；撞上唯一键时降级为逐条插入，只丢掉真正冲突的那几行。
     *
     * <p>达梦对多值 insert 是「整条语句一起回滚」：批里只要有一行撞 {@code uk_quality_result}，
     * 这一批（现场 128 行）全部写不进去，页面只看到「批次失败」，看不出是哪条指标引起的。
     * 降级逐条插入能把脏行之外的结果都保住，并把冲突行精确记进日志。
     *
     * <p>刻意不做 insert ignore / merge：那会静默覆盖掉旧值，一条错误的率值比缺一行危险得多。
     */
    private void insertChunk(List<QualityMetricResult> chunk) {
        try {
            resultMapper.batchInsert(chunk);
            return;
        } catch (DataIntegrityViolationException e) {
            log.warn("[质控] 结果行撞唯一键 uk_quality_result，降级为逐条插入以定位冲突行: 本批 {} 行", chunk.size());
        }
        int skipped = 0;
        for (QualityMetricResult r : chunk) {
            try {
                resultMapper.batchInsert(Collections.singletonList(r));
            } catch (DataIntegrityViolationException ignore) {
                skipped++;
                log.warn("[质控] 冲突行已跳过: metric={} depart={} period_start={} period_type={}",
                        r.getMetricCode(), r.getDepartCode(), r.getPeriodStart(), r.getPeriodType());
            }
        }
        if (skipped == chunk.size()) {
            // 整批都冲突说明不是个别脏行，而是删除范围整体没覆盖到写入范围：
            // 此时静默跳过会让页面凭空少一大批指标，不如让批次明确失败
            throw new DataIntegrityViolationException("结果行全部撞唯一键，疑似删除范围与写入范围不一致");
        }
    }

    /**
     * 同一 (指标, 科室) 只保留第一条。
     *
     * <p>这是落库前的最后一道兜底，不是正常路径。唯一键 {@code uk_quality_result} 为
     * (metric_code, period_type, period_start, depart_code)：一批里只要出现两行同键，
     * 达梦不是只丢弃冲突的那一条，而是让整条 insert 一起失败 —— 现场表现是 127 条结果
     * 一条都写不进去，页面只看到「批次失败」，看不出是哪条指标引起的。
     * 去重先保住整批，再用 warn 留下「哪个指标、哪个科室重复」的线索，便于回溯真因。
     */
    private List<QualityMetricResult> dedupeByKey(List<QualityMetricResult> list) {
        // 同一批内 period_type 与 period_start 恒定，键里不必重复带上
        Set<String> seen = new HashSet<>();
        List<QualityMetricResult> out = new ArrayList<>(list.size());
        for (QualityMetricResult r : list) {
            String key = r.getMetricCode() + "|" + (r.getDepartCode() == null ? "ALL" : r.getDepartCode());
            if (!seen.add(key)) {
                log.warn("[质控] 同周期同科室出现重复结果行，保留第一条: metric={} depart={} period_start={}",
                        r.getMetricCode(), r.getDepartCode(), r.getPeriodStart());
                continue;
            }
            out.add(r);
        }
        return out;
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
