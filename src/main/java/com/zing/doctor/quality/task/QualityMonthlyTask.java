package com.zing.doctor.quality.task;

import com.zing.doctor.quality.config.QualityProperties;
import com.zing.doctor.quality.dto.PeriodRange;
import com.zing.doctor.quality.service.QualityCalcService;
import com.zing.doctor.quality.service.QualityMonthlyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/**
 * 质控月度批算任务。
 *
 * <p>沿用项目既有的 {@code @EnableScheduling} + {@code @Scheduled(cron)} 模式（见
 * {@code SofaAutoScoreTask}、{@code AbxDrugSyncTask}），不引入 XXL-Job 等外部调度组件。
 *
 * <p>每月 1 日凌晨执行两步：先补算上月指标，再刷新当年 1-12 月汇总宽表。
 * 两步都幂等，重复执行不会产生脏数据。
 */
@Component
public class QualityMonthlyTask {

    private static final Logger log = LoggerFactory.getLogger(QualityMonthlyTask.class);

    private final QualityProperties props;
    private final QualityCalcService calcService;
    private final QualityMonthlyService monthlyService;

    public QualityMonthlyTask(QualityProperties props, QualityCalcService calcService,
                              QualityMonthlyService monthlyService) {
        this.props = props;
        this.calcService = calcService;
        this.monthlyService = monthlyService;
    }

    /**
     * cron 为 6 段式（秒 分 时 日 月 周），默认每月 1 日 03:30，可用
     * {@code zing.quality.monthly-cron} 覆盖。
     */
    @Scheduled(cron = "${zing.quality.monthly-cron:0 30 3 1 * ?}")
    public void monthly() {
        if (!props.isEnabled()) {
            return;
        }
        LocalDate lastMonth = LocalDate.now().withDayOfMonth(1).minusMonths(1);
        PeriodRange range = PeriodRange.monthOf(lastMonth.getYear(), lastMonth.getMonthValue());
        try {
            Map<String, Object> run = calcService.recalc("MONTH", range.getStart(), range.getEnd(),
                    "ALL", "SCHEDULED", "system", false);
            log.info("[质控] 月度批算完成: {}", run.get("runId"));
            Map<String, Object> summary = monthlyService.rebuild(lastMonth.getYear(), "ALL");
            log.info("[质控] 年度汇总刷新完成: {}", summary);
        } catch (Exception e) {
            log.error("[质控] 月度批算失败: {}", range.getStart(), e);
        }
    }
}
