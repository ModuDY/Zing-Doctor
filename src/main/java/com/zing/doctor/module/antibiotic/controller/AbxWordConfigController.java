package com.zing.doctor.module.antibiotic.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.module.antibiotic.entity.AbxWordConfig;
import com.zing.doctor.module.antibiotic.service.AbxWordConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 抗菌药物识别词库配置 Controller（脓毒症集束化：广谱白名单/非抗菌黑名单）
 */
@Slf4j
@RestController
@RequestMapping("/api/antibiotic/word-config")
@RequiredArgsConstructor
public class AbxWordConfigController {

    private final AbxWordConfigService abxWordConfigService;

    @GetMapping("/list")
    public Result<List<AbxWordConfig>> list() {
        try {
            return Result.ok(abxWordConfigService.listAllActive());
        } catch (Exception e) {
            log.error("[词库] 列表查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/categories")
    public Result<List<String>> categories() {
        try {
            return Result.ok(abxWordConfigService.listAllCategories());
        } catch (Exception e) {
            log.error("[词库] 分类查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/add")
    public Result<Boolean> add(@RequestBody AbxWordConfig config) {
        log.info("[词库] 新增: type={}, keyword={}", config.getWordType(), config.getKeyword());
        try {
            return Result.ok(abxWordConfigService.addConfig(config));
        } catch (Exception e) {
            log.error("[词库] 新增失败", e);
            return Result.fail("新增失败: " + e.getMessage());
        }
    }

    @PostMapping("/update")
    public Result<Boolean> update(@RequestBody AbxWordConfig config) {
        log.info("[词库] 修改: id={}, keyword={}", config.getId(), config.getKeyword());
        try {
            return Result.ok(abxWordConfigService.updateConfig(config));
        } catch (Exception e) {
            log.error("[词库] 修改失败", e);
            return Result.fail("修改失败: " + e.getMessage());
        }
    }

    @PostMapping("/toggle")
    public Result<Boolean> toggle(@RequestParam Long id, @RequestParam Integer status) {
        log.info("[词库] 切换状态: id={}, status={}", id, status);
        try {
            return Result.ok(abxWordConfigService.toggleStatus(id, status));
        } catch (Exception e) {
            log.error("[词库] 切换状态失败", e);
            return Result.fail("操作失败: " + e.getMessage());
        }
    }
}
