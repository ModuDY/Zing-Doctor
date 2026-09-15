package com.zing.doctor.module.system.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.module.system.entity.SysParam;
import com.zing.doctor.module.system.service.SysParamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统参数（参数设置页面）。
 */
@Slf4j
@RestController
@RequestMapping("/api/sys-param")
public class SysParamController {

    @Autowired
    private SysParamService sysParamService;

    /** 参数列表；group 为空返回全部 */
    @GetMapping("/list")
    public Result<List<SysParam>> list(@RequestParam(required = false) String group) {
        try {
            return Result.ok(sysParamService.list(group));
        } catch (Exception e) {
            log.error("系统参数查询失败: group={}", group, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /** 保存（id 为空新增，否则更新） */
    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody SysParam param) {
        try {
            return sysParamService.save(param) ? Result.ok(true) : Result.fail("保存失败：参数键为空或已存在");
        } catch (Exception e) {
            log.error("系统参数保存失败", e);
            return Result.fail("保存失败: " + e.getMessage());
        }
    }

    /** 删除 */
    @PostMapping("/delete")
    public Result<Boolean> delete(@RequestParam Long id) {
        try {
            return sysParamService.delete(id) ? Result.ok(true) : Result.fail("参数不存在");
        } catch (Exception e) {
            log.error("系统参数删除失败: id={}", id, e);
            return Result.fail("删除失败: " + e.getMessage());
        }
    }
}
