package com.zing.doctor.module.antibiotic.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.module.antibiotic.entity.AbxDrugDict;
import com.zing.doctor.module.antibiotic.service.AbxDrugDictService;
import com.zing.doctor.module.antibiotic.service.AbxDrugRecognizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 抗菌药物字典 Controller（HIS 药品字典同步与识别词库状态）。
 *
 * <p>路径 {@code /api/antibiotic/drug-dict}，与词库配置 {@code /api/antibiotic/word-config} 并列：
 * <ul>
 *   <li>word-config：人工维护的「广谱白名单 / 非抗菌黑名单」关键词；</li>
 *   <li>drug-dict（本类）：HIS 药品字典自动同步的抗菌药清单（权威来源、无需人工维护）。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/antibiotic/drug-dict")
@RequiredArgsConstructor
public class AbxDrugDictController {

    private final AbxDrugDictService abxDrugDictService;

    private final AbxDrugRecognizer abxDrugRecognizer;

    /** 字典概况：总条数 / 在库 / 已失效 / 最近同步时间 / 识别器快照 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        try {
            return Result.ok(abxDrugDictService.stats());
        } catch (Exception e) {
            log.error("[抗菌药字典] 概况查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /** 在库抗菌药清单（用于页面核对同步结果） */
    @GetMapping("/list")
    public Result<List<AbxDrugDict>> list() {
        try {
            return Result.ok(abxDrugDictService.listActive());
        } catch (Exception e) {
            log.error("[抗菌药字典] 列表查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /** 手动触发同步（幂等，等价于夜间任务，用于上线首日或 HIS 字典更新后立即生效） */
    @PostMapping("/sync")
    public Result<Map<String, Object>> sync() {
        log.info("[抗菌药字典] 手动触发同步");
        try {
            Map<String, Object> stats = abxDrugDictService.syncFromIcu();
            if (Boolean.FALSE.equals(stats.get("success"))) {
                return Result.fail(String.valueOf(stats.get("error")));
            }
            return Result.ok(stats);
        } catch (Exception e) {
            log.error("[抗菌药字典] 手动同步失败", e);
            return Result.fail("同步失败: " + e.getMessage());
        }
    }

    /** 仅刷新识别器内存快照（词库配置改动后立即生效，不查 HIS） */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh() {
        try {
            abxDrugRecognizer.refresh();
            return Result.ok(abxDrugRecognizer.snapshotInfo());
        } catch (Exception e) {
            log.error("[抗菌药字典] 刷新识别词库失败", e);
            return Result.fail("刷新失败: " + e.getMessage());
        }
    }
}
