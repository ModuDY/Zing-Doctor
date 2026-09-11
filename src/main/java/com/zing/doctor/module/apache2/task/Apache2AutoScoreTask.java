package com.zing.doctor.module.apache2.task;

import com.zing.doctor.module.apache2.service.Apache2Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * APACHE II 自动初评定时任务。
 *
 * <p>周期性为“在科且入科超过 24 小时、尚无任何评分记录”的患者自动生成一份初评记录。
 * Service 层按住院号做了幂等（已有正常记录即跳过），因此任务可安全重复执行，不会重复建档。
 * 执行周期可通过配置 {@code apache2.auto-generate.cron} 覆盖，默认每 6 小时一次
 * （01:00/07:00/13:00/19:00）；入科小时阈值由 {@code apache2.auto-generate.over-hours} 配置，默认 24。
 */
@Slf4j
@Component
public class Apache2AutoScoreTask {

    @Autowired
    private Apache2Service apache2Service;

    /** 入科超过多少小时才自动初评，默认 24 小时 */
    @org.springframework.beans.factory.annotation.Value("${apache2.auto-generate.over-hours:24}")
    private int overHours;

    /**
     * fixedDelayString 设为 -1 时可通过配置关闭；默认走 cron。
     * cron 可被 application.yml 的 apache2.auto-generate.cron 覆盖。
     */
    @Scheduled(cron = "${apache2.auto-generate.cron:0 0 1,7,13,19 * * ?}")
    public void autoGenerateForInDepart() {
        long t0 = System.currentTimeMillis();
        try {
            Map<String, Object> r = apache2Service.autoGenerateScores(null, overHours <= 0 ? 24 : overHours);
            log.info("[定时]APACHE2自动初评完成，耗时{}ms，扫描{} 新增{} 跳过{} 失败{}",
                    System.currentTimeMillis() - t0,
                    r.get("scanned"), r.get("created"), r.get("skipped"), r.get("failed"));
        } catch (Exception e) {
            // 单次失败不影响下一次调度
            log.error("[定时]APACHE2自动初评执行失败，耗时{}ms", System.currentTimeMillis() - t0, e);
        }
    }
}
