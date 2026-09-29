package com.zing.doctor.module.antibiotic.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.module.antibiotic.entity.AbxPkpdKnowledge;
import com.zing.doctor.module.antibiotic.service.AbxPkpdKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PK/PD 抗菌药物知识库配置 Controller。
 * 路径 /api/antibiotic/pkpd-knowledge
 */
@Slf4j
@RestController
@RequestMapping("/api/antibiotic/pkpd-knowledge")
@RequiredArgsConstructor
public class AbxPkpdKnowledgeController {

    private final AbxPkpdKnowledgeService service;

    @GetMapping("/list")
    public Result<List<AbxPkpdKnowledge>> list() {
        try {
            return Result.ok(service.listAll());
        } catch (Exception e) {
            log.error("[PKPD知识库] 列表查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result<AbxPkpdKnowledge> get(@PathVariable Long id) {
        try {
            return Result.ok(service.getById(id));
        } catch (Exception e) {
            log.error("[PKPD知识库] 详情查询失败 id={}", id, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    @PostMapping
    public Result<String> save(@RequestBody AbxPkpdKnowledge entity) {
        try {
            if (entity.getDrugName() == null || entity.getDrugName().trim().isEmpty()) {
                return Result.fail("药品名称不能为空");
            }
            if (entity.getPkpdType() == null || entity.getPkpdType().trim().isEmpty()) {
                return Result.fail("PK/PD类型不能为空");
            }
            service.save(entity);
            return Result.ok("保存成功");
        } catch (Exception e) {
            log.error("[PKPD知识库] 保存失败", e);
            return Result.fail("保存失败: " + e.getMessage());
        }
    }

    @PutMapping
    public Result<String> update(@RequestBody AbxPkpdKnowledge entity) {
        try {
            if (entity.getId() == null) return Result.fail("ID不能为空");
            service.update(entity);
            return Result.ok("更新成功");
        } catch (Exception e) {
            log.error("[PKPD知识库] 更新失败 id={}", entity.getId(), e);
            return Result.fail("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        try {
            service.removeById(id);
            return Result.ok("删除成功");
        } catch (Exception e) {
            log.error("[PKPD知识库] 删除失败 id={}", id, e);
            return Result.fail("删除失败: " + e.getMessage());
        }
    }
}
