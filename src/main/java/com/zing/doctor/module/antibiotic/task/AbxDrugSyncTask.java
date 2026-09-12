package com.zing.doctor.module.antibiotic.task;

import com.zing.doctor.module.antibiotic.service.AbxDrugDictService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 抗菌药物字典夜间同步定时任务。
 *
 * <p>把 HIS 药品字典（ICU 库 {@code zing_icu_db_prod.config_drug}）中
 * {@code is_antibiotics = '1'} 的药品同步到医生库 {@code zing_abx_drug_dict}，
 * 供 {@code AbxDrugRecognizer} 做精确匹配，从而：
 * <ul>
 *   <li><b>自动发现 is_antibiotics 变更</b>：HIS 新增抗菌药 → 次日自动进入识别字典；
 *       HIS 取消抗菌药标记 → 本库自动置 status=0（不误判为抗菌药）；</li>
 *   <li>覆盖新药/商品名/复方制剂，弥补关键词白名单的漏判。</li>
 * </ul>
 *
 * <p>调度：默认每日 03:20（{@code 0 20 3 * * ?}），业务低峰期执行；
 * 同步本身幂等（增量比对），手动补跑安全（{@code POST /api/antibiotic/drug-dict/sync}）。
 *
 * <p>失败策略：ICU 库不可达 / 权限不足时只记录日志，不影响医生系统正常功能——
 * 识别会自动降级为「关键词白名单 + 黑名单」模式（快照中 dictDrugCount=0）。
 *
 * <p>可通过配置关闭：{@code abx.drug-sync.enabled=false}；
 * 调整执行时间：{@code abx.drug-sync.cron}。
 */
@Slf4j
@Component
public class AbxDrugSyncTask {

    @Value("${abx.drug-sync.enabled:true}")
    private boolean enabled;

    @Autowired
    private AbxDrugDictService abxDrugDictService;

    /**
     * 每日 03:20 同步 HIS 药品字典中的抗菌药清单。
     * <p>cron 为 6 段式（秒 分 时 日 月 周），默认 {@code 0 20 3 * * ?}。
     */
    @Scheduled(cron = "${abx.drug-sync.cron:0 20 3 * * ?}")
    public void syncAntibioticDrugs() {
        if (!enabled) {
            log.info("[抗菌药字典] 夜间同步已关闭（abx.drug-sync.enabled=false）");
            return;
        }
        long t0 = System.currentTimeMillis();
        try {
            Map<String, Object> stats = abxDrugDictService.syncFromIcu();
            Object success = stats.get("success");
            if (Boolean.FALSE.equals(success)) {
                log.warn("[抗菌药字典] 夜间同步失败：{}", stats.get("error"));
            } else {
                log.info("[抗菌药字典] 夜间同步结束：{}（耗时 {}ms）", stats, System.currentTimeMillis() - t0);
            }
        } catch (Exception e) {
            // 定时任务不允许抛出：单次失败仅记录，等待下一次调度
            log.error("[抗菌药字典] 夜间同步异常", e);
        }
    }
}
