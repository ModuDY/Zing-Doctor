package com.zing.doctor.quality.task;

import com.zing.doctor.module.system.service.SysParamService;
import com.zing.doctor.quality.config.QualityProperties;
import com.zing.doctor.quality.dto.PeriodRange;
import com.zing.doctor.quality.service.QualityCalcService;
import com.zing.doctor.quality.service.QualityMonthlyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 质控每日批算任务。
 *
 * <p>沿用项目既有的 {@code @EnableScheduling} + {@code @Scheduled(cron)} 模式（见
 * {@code SofaAutoScoreTask}、{@code AbxDrugSyncTask}），不引入 XXL-Job 等外部调度组件。
 *
 * <p><b>为什么从每月改成每天：</b>原 {@code QualityMonthlyTask} 每月 1 日才把上月一次性定稿。
 * 月末几天（尤其长假前后）的出院、结算记录在 HIS 侧常常要拖到次月初才补齐，定稿之后再
 * 补录的数据永远进不了已经算完的月份 —— 这正是「最后一天的数据算不准」的来源。
 * 改为每天重算后：月内数据在当月持续滚动刷新（看板当天看到的就是最新的），
 * 跨月之后还会继续滚一段时间才自然停止。
 *
 * <p><b>回溯窗口：</b>每次跑批重算的不是「昨天一整天」，而是
 * {@code [今天 - N 天, 昨天]} 这个区间覆盖到的<b>所有月份</b>。默认 N=3，
 * 可在「参数设置 → 质控配置」页用 {@code QUALITY_BACKFILL_DAYS} 调整。
 * 例：10 月 3 日跑批、N=3 → 窗口 9/30 ~ 10/2 → 命中 9 月与 10 月，
 * 于是上月数据在次月前 3 天仍会被追平，之后自然定稿。
 *
 * <p>只按「昨天所在月」算也能跑，但那样跨月第一天之后上月就再也不动了；
 * 回溯窗口是专门用来吃掉这段补录滞后的。N=1 即退化成「只算前一天所在月」。
 *
 * <p><b>幂等：</b>{@code QualityCalcService#recalc} 在算完之后按
 * {@code (period_type, period_start)} 先删后插，同一月份反复重算只会覆盖、不会堆积；
 * {@code QualityMonthlyService#rebuild} 同样先删后建。
 * 因此重复执行、漏跑一天、手动补跑都不会产生脏数据。
 */
@Component
public class QualityDailyTask {

    private static final Logger log = LoggerFactory.getLogger(QualityDailyTask.class);

    /** 参数未配置 / 配错时的回溯天数 */
    private static final int DEFAULT_BACKFILL_DAYS = 3;

    /**
     * 回溯天数上限。
     *
     * <p>每多跨一个月份就要多跑一次 {@code recalc}，而每次 recalc 都会重建事实层
     * ——窗口不能无限放大，否则一次夜间任务可能拖到白天。
     * 31 天最多跨 2 个月，是可控的上边界。
     */
    private static final int MAX_BACKFILL_DAYS = 31;

    private final QualityProperties props;
    private final QualityCalcService calcService;
    private final QualityMonthlyService monthlyService;
    private final SysParamService sysParamService;

    public QualityDailyTask(QualityProperties props, QualityCalcService calcService,
                            QualityMonthlyService monthlyService, SysParamService sysParamService) {
        this.props = props;
        this.calcService = calcService;
        this.monthlyService = monthlyService;
        this.sysParamService = sysParamService;
    }

    /**
     * cron 为 6 段式（秒 分 时 日 月 周），默认<b>每天 03:30</b>，可用
     * {@code zing.quality.daily-cron} 覆盖。
     *
     * <p>刻意排在凌晨业务低峰：一次批算会重建事实层并重跑全部指标，白天跑会拖慢看板。
     */
    @Scheduled(cron = "${zing.quality.daily-cron:0 30 3 * * ?}")
    public void daily() {
        if (!props.isEnabled()) {
            return;
        }
        int days = backfillDays();
        LocalDate today = LocalDate.now();
        LocalDate windowStart = today.minusDays(days);
        LocalDate windowEnd = today.minusDays(1);

        Set<YearMonth> months = monthsOf(windowStart, windowEnd);
        long t0 = System.currentTimeMillis();
        log.info("[质控] 每日批算开始: 窗口={}~{}（回溯 {} 天）命中月份={}", windowStart, windowEnd, days, months);

        calcByMonth(months);
        Set<Integer> years = rebuildSummary(months);

        log.info("[质控] 每日批算结束: 月份={} 汇总年份={} 耗时={}ms",
                months.size(), years, System.currentTimeMillis() - t0);
    }

    /**
     * 逐月重算。
     *
     * <p>每个月单独兜异常：某一处看板或某个月的数据源抖动，不该让后面几个月的
     * 正常刷新一起停摆。
     */
    private void calcByMonth(Set<YearMonth> months) {
        for (YearMonth ym : months) {
            PeriodRange range = PeriodRange.monthOf(ym.getYear(), ym.getMonthValue());
            try {
                Map<String, Object> run = calcService.recalc("MONTH", range.getStart(), range.getEnd(),
                        "ALL", "SCHEDULED", "system", false);
                log.info("[质控] 月度重算完成: {}-{} runId={}", ym.getYear(), ym.getMonthValue(), run.get("runId"));
            } catch (Exception e) {
                log.error("[质控] 月度重算失败: {}-{} periodStart={}",
                        ym.getYear(), ym.getMonthValue(), range.getStart(), e);
            }
        }
    }

    /**
     * 刷新年度汇总宽表。
     *
     * <p>年份取「命中月份的年份 ∪ 当前年」：前者让补算的数据立即反映到汇总，
     * 后者是为了跨年 —— 元旦那天窗口完全落在去年 12 月，只按命中年份刷的话，
     * 当年的宽表要等到 1 月 2 日才建起来，元旦当天看「本年度汇总」会是空的。
     */
    private Set<Integer> rebuildSummary(Set<YearMonth> months) {
        Set<Integer> years = new TreeSet<>();
        for (YearMonth ym : months) {
            years.add(ym.getYear());
        }
        years.add(YearMonth.now().getYear());

        for (Integer year : years) {
            try {
                Map<String, Object> summary = monthlyService.rebuild(year, "ALL");
                log.info("[质控] 年度汇总刷新完成: year={} {}", year, summary);
            } catch (Exception e) {
                log.error("[质控] 年度汇总刷新失败: year={}", year, e);
            }
        }
        return years;
    }

    /**
     * 窗口覆盖到的月份（去重、升序）。
     *
     * <p>窗口为空（N 配成 0 或负数）时仍至少保留「昨天所在月」，避免配错参数后
     * 整晚什么都不算 —— 那种静默失败最难发现。
     */
    private Set<YearMonth> monthsOf(LocalDate windowStart, LocalDate windowEnd) {
        Set<YearMonth> months = new TreeSet<>();
        months.add(YearMonth.from(windowEnd));
        for (LocalDate d = windowStart; !d.isAfter(windowEnd); d = d.plusDays(1)) {
            months.add(YearMonth.from(d));
        }
        return months;
    }

    /**
     * 回溯天数：取自系统参数 {@code QUALITY_BACKFILL_DAYS}（参数设置 → 质控配置）。
     * 未配置、配成非数字、或超出 {@code [1, 31]} 一律回退默认值 3。
     */
    private int backfillDays() {
        String raw = sysParamService.value(SysParamService.KEY_QUALITY_BACKFILL_DAYS);
        if (raw == null || raw.trim().isEmpty()) {
            return DEFAULT_BACKFILL_DAYS;
        }
        try {
            int days = Integer.parseInt(raw.trim());
            if (days < 1 || days > MAX_BACKFILL_DAYS) {
                log.warn("[质控] 回溯天数超出允许范围，按默认值处理: value={} default={}", raw, DEFAULT_BACKFILL_DAYS);
                return DEFAULT_BACKFILL_DAYS;
            }
            return days;
        } catch (NumberFormatException e) {
            log.warn("[质控] 回溯天数不是数字，按默认值处理: value={} default={}", raw, DEFAULT_BACKFILL_DAYS);
            return DEFAULT_BACKFILL_DAYS;
        }
    }
}
