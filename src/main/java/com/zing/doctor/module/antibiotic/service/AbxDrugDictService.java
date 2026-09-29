package com.zing.doctor.module.antibiotic.service;

import com.zing.doctor.module.antibiotic.entity.AbxDrugDict;

import java.util.List;
import java.util.Map;

/**
 * 抗菌药物字典 Service：承接 HIS 药品字典（{@code config_drug.is_antibiotics='1'}）的同步与查询。
 *
 * <p>同步由夜间定时任务 {@code AbxDrugSyncTask} 触发，也可通过
 * {@code POST /api/antibiotic/drug-dict/sync} 手动触发（幂等）。
 */
public interface AbxDrugDictService {

    /**
     * 从 HIS 药品字典同步抗菌药清单（增量：新增 / 更新 / 置失效）。
     *
     * @return 统计信息：success / hisTotal / added / updated / reactivated / disabled / unchanged / durationMs / error
     */
    Map<String, Object> syncFromIcu();

    /** 在库（status=1）抗菌药清单，按通用名/药品名排序 */
    List<AbxDrugDict> listActive();

    /** 字典概况：总条数 / 在库条数 / 已失效条数 / 最近同步时间 */
    Map<String, Object> stats();
}
