package com.zing.doctor.module.antibiotic.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zing.doctor.icu.mapper.IcuDrugMapper;
import com.zing.doctor.module.antibiotic.entity.AbxDrugDict;
import com.zing.doctor.module.antibiotic.mapper.AbxDrugDictMapper;
import com.zing.doctor.module.antibiotic.service.AbxDrugDictService;
import com.zing.doctor.module.antibiotic.service.AbxDrugRecognizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 抗菌药物字典 Service 实现：HIS 药品字典 → 医生库的增量同步。
 *
 * <h3>同步语义（幂等，可反复执行）</h3>
 * <ul>
 *   <li><b>新增</b>：HIS 有、本库无（drug_code 不存在）→ INSERT；</li>
 *   <li><b>更新</b>：drug_code 存在但名称/简称/通用名/规格/剂量/厂家 有变化 → UPDATE 并置 status=1；</li>
 *   <li><b>重新启用</b>：本库 status=0（曾失效）但 HIS 又标记为抗菌药 → 恢复 status=1；</li>
 *   <li><b>置失效</b>：本库 status=1 但 HIS 结果集中已无该 drug_code（is_antibiotics 被改为 0）
 *       → status=0 <b>不物理删除</b>，保留历史可追溯，这是"自动发现 is_antibiotics 变更"的落点；</li>
 *   <li><b>未变化</b>：不写库，不刷新 sync_time（sync_time 语义 = 最近一次实际写入时间）。</li>
 * </ul>
 *
 * <h3>事务与数据源</h3>
 * 本方法跨两个数据源（读 ICU、写医生库），因此<b>刻意不加 {@code @Transactional}</b>：
 * 动态数据源下把 read-only 的 icu 连接纳入同一个事务既无意义也易出错。
 * 逐条写入依赖 JDBC 自动提交；字典量级 ~100 行，性能无压力。
 * 全流程 try-catch，单条失败不影响整体，最终返回统计便于排查。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbxDrugDictServiceImpl implements AbxDrugDictService {

    private final AbxDrugDictMapper abxDrugDictMapper;

    private final IcuDrugMapper icuDrugMapper;

    private final AbxDrugRecognizer abxDrugRecognizer;

    @Override
    public Map<String, Object> syncFromIcu() {
        long t0 = System.currentTimeMillis();
        Map<String, Object> stats = new LinkedHashMap<>();

        // 1. 读 HIS 药品字典（is_antibiotics = '1'）
        List<Map<String, Object>> hisRows;
        try {
            hisRows = icuDrugMapper.selectHisAntibiotics();
        } catch (Exception e) {
            log.error("[抗菌药字典] 读取 HIS 药品字典失败", e);
            stats.put("success", false);
            stats.put("error", "读取 HIS 药品字典失败：" + e.getMessage());
            stats.put("durationMs", System.currentTimeMillis() - t0);
            return stats;
        }
        if (hisRows == null) {
            hisRows = Collections.emptyList();
        }
        Map<String, Map<String, Object>> hisMap = new LinkedHashMap<>();
        for (Map<String, Object> raw : hisRows) {
            // 达梦返回的列名大小写随环境配置而异，统一转小写后再按字段名取值，避免整列为空
            Map<String, Object> row = lowerKeys(raw);
            String code = trim(row.get("drug_code"));
            if (!code.isEmpty()) {
                hisMap.put(code, row);
            }
        }

        // 2. 读医生库现状
        List<AbxDrugDict> exists = abxDrugDictMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, AbxDrugDict> existMap = new LinkedHashMap<>();
        if (exists != null) {
            for (AbxDrugDict e : exists) {
                if (e.getDrugCode() != null && !e.getDrugCode().trim().isEmpty()) {
                    existMap.put(e.getDrugCode().trim(), e);
                }
            }
        }

        // 3. 增量比对
        LocalDateTime now = LocalDateTime.now();
        int added = 0;
        int updated = 0;
        int reactivated = 0;
        int unchanged = 0;
        int disabled = 0;
        int failed = 0;

        for (Map.Entry<String, Map<String, Object>> entry : hisMap.entrySet()) {
            String code = entry.getKey();
            Map<String, Object> row = entry.getValue();
            AbxDrugDict old = existMap.get(code);
            try {
                if (old == null) {
                    AbxDrugDict dict = new AbxDrugDict();
                    fill(dict, row);
                    dict.setSyncTime(now);
                    dict.setStatus(1);
                    dict.setDelFlag(0);
                    dict.setCreateTime(now);
                    dict.setUpdateTime(now);
                    abxDrugDictMapper.insert(dict);
                    added++;
                } else {
                    boolean reactivate = old.getStatus() == null || old.getStatus() != 1;
                    boolean changed = !nzEquals(old.getDrugName(), trim(row.get("drug_name")))
                            || !nzEquals(old.getDrugShortName(), trim(row.get("drug_short_name")))
                            || !nzEquals(old.getDrugNormalName(), trim(row.get("drug_normal_name")))
                            || !nzEquals(old.getSpec(), trim(row.get("spec")))
                            || !nzEquals(old.getDose(), trim(row.get("dose")))
                            || !nzEquals(old.getUnitCode(), trim(row.get("unit_code")))
                            || !nzEquals(old.getDrugFactoryName(), trim(row.get("drug_factory_name")));
                    if (changed || reactivate) {
                        fill(old, row);
                        old.setStatus(1);
                        old.setSyncTime(now);
                        old.setUpdateTime(now);
                        abxDrugDictMapper.updateById(old);
                        updated++;
                        if (reactivate) {
                            reactivated++;
                        }
                    } else {
                        unchanged++;
                    }
                }
            } catch (Exception e) {
                failed++;
                log.warn("[抗菌药字典] 同步单条失败 drugCode={}, drugName={}", code, row.get("drug_name"), e);
            }
        }

        // 4. HIS 已取消抗菌药标记的 → 置失效（保留行，不物理删除）
        for (AbxDrugDict old : existMap.values()) {
            String code = old.getDrugCode() == null ? "" : old.getDrugCode().trim();
            if (!hisMap.containsKey(code) && (old.getStatus() == null || old.getStatus() == 1)) {
                try {
                    old.setStatus(0);
                    old.setUpdateTime(now);
                    abxDrugDictMapper.updateById(old);
                    disabled++;
                } catch (Exception e) {
                    failed++;
                    log.warn("[抗菌药字典] 置失效失败 drugCode={}", code, e);
                }
            }
        }

        // 5. 刷新识别快照（页面与各模块立即生效，无需重启）
        abxDrugRecognizer.refresh();

        long duration = System.currentTimeMillis() - t0;
        stats.put("success", true);
        stats.put("hisTotal", hisMap.size());
        stats.put("added", added);
        stats.put("updated", updated);
        stats.put("reactivated", reactivated);
        stats.put("unchanged", unchanged);
        stats.put("disabled", disabled);
        stats.put("failed", failed);
        stats.put("durationMs", duration);
        stats.put("syncTime", now);
        log.info("[抗菌药字典] 同步完成：HIS={}条，新增={}，更新={}（其中重新启用={}），未变化={}，置失效={}，失败={}，耗时={}ms",
                hisMap.size(), added, updated, reactivated, unchanged, disabled, failed, duration);
        return stats;
    }

    @Override
    public List<AbxDrugDict> listActive() {
        LambdaQueryWrapper<AbxDrugDict> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AbxDrugDict::getStatus, 1)
                .eq(AbxDrugDict::getDelFlag, 0)
                .orderByAsc(AbxDrugDict::getDrugNormalName)
                .orderByAsc(AbxDrugDict::getDrugName);
        return abxDrugDictMapper.selectList(wrapper);
    }

    @Override
    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        Long total = abxDrugDictMapper.selectCount(new LambdaQueryWrapper<>());
        Long active = abxDrugDictMapper.selectCount(
                new LambdaQueryWrapper<AbxDrugDict>().eq(AbxDrugDict::getStatus, 1));
        Long disabled = abxDrugDictMapper.selectCount(
                new LambdaQueryWrapper<AbxDrugDict>().eq(AbxDrugDict::getStatus, 0));
        m.put("total", total == null ? 0 : total);
        m.put("active", active == null ? 0 : active);
        m.put("disabled", disabled == null ? 0 : disabled);
        // 最近同步时间：取 sync_time 最大值（字典量级百行，Java 端求最大，避开达梦方言差异）
        LocalDateTime lastSync = null;
        List<AbxDrugDict> times = abxDrugDictMapper.selectList(
                new LambdaQueryWrapper<AbxDrugDict>().select(AbxDrugDict::getSyncTime));
        if (times != null) {
            for (AbxDrugDict d : times) {
                if (d.getSyncTime() != null && (lastSync == null || d.getSyncTime().isAfter(lastSync))) {
                    lastSync = d.getSyncTime();
                }
            }
        }
        m.put("lastSyncTime", lastSync);
        m.put("recognizer", abxDrugRecognizer.snapshotInfo());
        return m;
    }

    // ------------------------------------------------------------------

    private void fill(AbxDrugDict d, Map<String, Object> row) {
        d.setDrugCode(trim(row.get("drug_code")));
        d.setDrugName(trim(row.get("drug_name")));
        d.setDrugShortName(trim(row.get("drug_short_name")));
        d.setDrugNormalName(trim(row.get("drug_normal_name")));
        d.setDrugPinyin(trim(row.get("drug_pinyin")));
        d.setSpec(trim(row.get("spec")));
        d.setDose(trim(row.get("dose")));
        d.setUnitCode(trim(row.get("unit_code")));
        d.setDrugFactoryName(trim(row.get("drug_factory_name")));
        d.setIsAntibiotics(trim(row.get("is_antibiotics")));
        d.setAntibioticsColor(trim(row.get("antibiotics_color")));
    }

    /**
     * 达梦返回的列名大小写随环境配置（CASE_SENSITIVE）而异，
     * 这里统一转小写，保证 {@code row.get("drug_code")} 这类取值的稳定性。
     */
    private static Map<String, Object> lowerKeys(Map<String, Object> raw) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (raw != null) {
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                if (e.getKey() != null) {
                    m.put(e.getKey().toLowerCase(), e.getValue());
                }
            }
        }
        return m;
    }

    private static String trim(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static boolean nzEquals(String a, String b) {
        return Objects.equals(a == null ? "" : a.trim(), b == null ? "" : b.trim());
    }
}
