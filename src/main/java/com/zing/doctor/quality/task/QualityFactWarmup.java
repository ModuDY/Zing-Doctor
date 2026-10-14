package com.zing.doctor.quality.task;

import com.zing.doctor.quality.config.QualityProperties;
import com.zing.doctor.quality.dsl.FactDefinition;
import com.zing.doctor.quality.dto.PeriodRange;
import com.zing.doctor.quality.engine.QualityDslLoader;
import com.zing.doctor.quality.engine.QualityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 事实层物化预热。
 *
 * <p><b>解决的问题：</b>事实层物化表是按需生成的 —— 第一张用到某事实层的 SQL 会现场
 * {@code CREATE TABLE AS SELECT}，之后同周期同科室的指标直接复用。缓存是 JVM 内存里的
 * {@code factTableCache}，<b>服务一重启就空</b>。
 *
 * <p>于是「重启后的第一个下钻请求」要独自承担全部事实层的重建：实测 96 张表、106 秒，
 * 而网关的读超时是 120 秒 —— 同一份代码时而 200、时而 504，全看这次重建有没有踩线。
 * 批算任务（03:30）自己也会建一次缓存，但它跑在启动之前的进程里，重启后并不留存。
 *
 * <p><b>做法：</b>启动就绪后在守护线程里，按「当月往前 N 个月 × 全部可用事实层」把表建好。
 * 与按需生成走的是同一个 {@link QualityEngine#materializeFact}，物化结果完全一致，
 * 因此不改变任何口径，只是把耗时从用户等待里挪到启动后台。
 *
 * <p><b>刻意不做的事：</b>
 * <ul>
 *   <li>不阻塞启动：失败只记 WARN/ERROR。预热是加速手段、不是运行前提，
 *       真出错时按需生成那条老路依然可用。</li>
 *   <li>不 {@code refresh=true} 强制重建：避免和正在跑的批算抢同一张表。</li>
 *   <li>不做失败重试：源库抖动导致的失败，交给用户首次下钻时的按需生成兜底。</li>
 * </ul>
 *
 * <p>可用 {@code zing.quality.warmup-on-startup=false} 整体关闭。
 */
@Component
public class QualityFactWarmup {

    private static final Logger log = LoggerFactory.getLogger(QualityFactWarmup.class);

    /** 进度日志的输出间隔（每建成多少张表打一条） */
    private static final int LOG_EVERY = 10;

    /** 整体等待上限：超过就放弃剩余任务，不能因为预热把守护线程永久占住 */
    private static final int AWAIT_MINUTES = 30;

    private final QualityProperties props;
    private final QualityDslLoader dsl;
    private final QualityEngine engine;

    public QualityFactWarmup(QualityProperties props, QualityDslLoader dsl, QualityEngine engine) {
        this.props = props;
        this.dsl = dsl;
        this.engine = engine;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmOnStartup() {
        if (!props.isEnabled() || !props.isWarmupOnStartup()) {
            return;
        }
        Thread t = new Thread(this::warm, "quality-fact-warmup");
        t.setDaemon(true);
        t.start();
    }

    private void warm() {
        // 最外层兜底：预热纯属加速手段，任何意外都不该冒泡成未捕获异常刷日志
        try {
            doWarm();
        } catch (Exception e) {
            log.error("[质控] 事实层预热异常终止（下钻仍按需生成，功能不受影响）", e);
        }
    }

    private void doWarm() {
        if (!sleepQuietly()) {
            return;
        }
        List<String> facts = usableFacts();
        if (facts.isEmpty()) {
            log.warn("[质控] 事实层预热跳过：没有可用的事实层（DSL 未加载或全部未就绪）");
            return;
        }

        List<YearMonth> targets = targetMonths();
        int total = facts.size() * targets.size();
        int threads = Math.max(1, Math.min(8, props.getCalcThreads()));
        long t0 = System.currentTimeMillis();
        log.info("[质控] 事实层预热开始: 事实层={}个 月份={} 合计={}张表 并发={}（后台执行，不阻塞接口）",
                facts.size(), targets.size(), total, threads);

        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        // 并发度刻意对齐批算的 calcThreads：这是本项目里已经被夜间任务验证过的负载水平，
        // 不另外引入一个新数量级；同时把串行要跑十几分钟的窗口压到分钟级。
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "quality-fact-warmup");
            t.setDaemon(true);
            return t;
        });
        try {
            for (YearMonth ym : targets) {
                for (String fact : facts) {
                    pool.submit(() -> warmOne(fact, ym, ok, fail, total));
                }
            }
            pool.shutdown();
            if (!pool.awaitTermination(AWAIT_MINUTES, TimeUnit.MINUTES)) {
                log.warn("[质控] 事实层预热超时（>{}分钟），放弃剩余任务", AWAIT_MINUTES);
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
        log.info("[质控] 事实层预热结束: 成功={} 失败={} 耗时={}ms{}", ok.get(), fail.get(),
                System.currentTimeMillis() - t0,
                fail.get() > 0 ? "（失败项留给按需生成兜底）" : "");
    }

    /**
     * 建一张表。
     *
     * <p>{@link PeriodRange} 在任务内部现取而不是外部共享：预热是并发的，
     * 不假设周期对象对多线程安全。
     */
    private void warmOne(String fact, YearMonth ym, AtomicInteger ok, AtomicInteger fail, int total) {
        PeriodRange range = PeriodRange.monthOf(ym.getYear(), ym.getMonthValue());
        try {
            engine.materializeFact(fact, range.getStart(), range.getEnd(), false, null);
            int done = ok.incrementAndGet();
            if (done % LOG_EVERY == 0) {
                log.info("[质控] 事实层预热进行中: 已建={} 失败={} 合计={}", done, fail.get(), total);
            }
        } catch (Exception e) {
            fail.incrementAndGet();
            log.warn("[质控] 事实层预热失败: fact={} period={}-{} 原因={}",
                    fact, ym.getYear(), ym.getMonthValue(), e.getMessage());
        }
    }

    /** 预热覆盖的月份：当月往前推，个数由 {@code zing.quality.warmup-months} 决定。 */
    private List<YearMonth> targetMonths() {
        int months = Math.max(1, Math.min(6, props.getWarmupMonths()));
        List<YearMonth> out = new ArrayList<>(months);
        YearMonth now = YearMonth.now();
        for (int i = 0; i < months; i++) {
            out.add(now.minusMonths(i));
        }
        return out;
    }

    /**
     * 可用事实层：状态为空或 ACTIVE 的才建。
     *
     * <p>判定口径与 {@code QualityEngine#effectiveStatus} 保持一致 —— 未就绪的事实层
     * （源表缺失、PENDING_SOURCE）本来就不会被任何指标引用，提前建只会白跑一遍 SQL 再报错。
     */
    private List<String> usableFacts() {
        List<String> names = new ArrayList<>();
        Map<String, FactDefinition> all = dsl.getFacts();
        if (all == null || all.isEmpty()) {
            return names;
        }
        for (Map.Entry<String, FactDefinition> e : all.entrySet()) {
            FactDefinition f = e.getValue();
            if (f == null) {
                continue;
            }
            String status = f.getStatus() == null ? "ACTIVE" : f.getStatus().toUpperCase();
            if ("ACTIVE".equals(status)) {
                names.add(e.getKey());
            }
        }
        return names;
    }

    /**
     * 启动后先静默一段时间再动手。
     *
     * <p>一是避开连接池初始化、DSL 装载的争抢；二是 {@code ApplicationReadyEvent} 上还挂着
     * 配置同步（{@code QualityIndexSyncService}），它一旦重载 DSL 就会让事实表缓存按新版本失效
     * —— 等它跑完再预热，才不会被随后的版本比对把刚建好的表清掉。
     *
     * @return 未被打断返回 true
     */
    private boolean sleepQuietly() {
        int delay = Math.max(0, props.getWarmupDelaySeconds());
        if (delay == 0) {
            return true;
        }
        try {
            Thread.sleep(delay * 1000L);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
