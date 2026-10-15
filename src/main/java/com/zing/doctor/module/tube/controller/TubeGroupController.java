package com.zing.doctor.module.tube.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.module.tube.entity.TubeGroupConfig;
import com.zing.doctor.module.tube.service.TubeGroupConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 导管类型分组配置接口（自定义导管类型 + 危重等级）。
 * 供导管统计页的「导管类型管理」弹窗使用；保存后统计立即按新配置归类。
 */
@RestController
@RequestMapping("/api/tube/groups")
@RequiredArgsConstructor
public class TubeGroupController {

    private final TubeGroupConfigService service;

    @GetMapping
    public Result<List<TubeGroupConfig>> list() {
        return Result.ok(service.listAll());
    }

    @PostMapping("/save")
    public Result<TubeGroupConfig> save(@RequestBody TubeGroupConfig cfg) {
        try {
            return Result.ok(service.save(cfg));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> disable(@PathVariable Long id) {
        int rows = service.disable(id);
        return rows == 0 ? Result.fail("配置不存在") : Result.ok();
    }
}
