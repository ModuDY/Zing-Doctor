package com.zing.doctor.module.system.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.module.system.entity.ParamGroup;
import com.zing.doctor.module.system.service.ParamGroupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 参数分组（参数设置页左侧导航，页面可维护）。
 */
@Slf4j
@RestController
@RequestMapping("/api/param-group")
public class ParamGroupController {

    @Autowired
    private ParamGroupService paramGroupService;

    /** 分组列表；onlyEnabled=true 只返回启用中的 */
    @GetMapping("/list")
    public Result<List<ParamGroup>> list(@RequestParam(defaultValue = "true") boolean onlyEnabled) {
        try {
            return Result.ok(paramGroupService.list(onlyEnabled));
        } catch (Exception e) {
            log.error("参数分组查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /** 保存（id 为空新增，否则更新） */
    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody ParamGroup group) {
        try {
            return paramGroupService.save(group)
                    ? Result.ok(true)
                    : Result.fail("保存失败：分组编码为空或已存在");
        } catch (Exception e) {
            log.error("参数分组保存失败", e);
            return Result.fail("保存失败: " + e.getMessage());
        }
    }

    /** 删除；分组下仍有参数时拒绝 */
    @PostMapping("/delete")
    public Result<Boolean> delete(@RequestParam Long id) {
        try {
            return paramGroupService.delete(id)
                    ? Result.ok(true)
                    : Result.fail("删除失败：分组不存在或其下仍挂着参数");
        } catch (Exception e) {
            log.error("参数分组删除失败: id={}", id, e);
            return Result.fail("删除失败: " + e.getMessage());
        }
    }
}
