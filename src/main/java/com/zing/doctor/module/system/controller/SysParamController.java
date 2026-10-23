package com.zing.doctor.module.system.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.module.system.entity.SysParam;
import com.zing.doctor.module.system.service.SysParamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /** 按 key 读单个参数值；不存在或已停用返回 null（前端按默认值处理） */
    @GetMapping("/get")
    public Result<String> get(@RequestParam String key) {
        try {
            return Result.ok(sysParamService.value(key));
        } catch (Exception e) {
            log.error("系统参数查询失败: key={}", key, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /**
     * 未登录可读的品牌信息：院徽 + 院名（登录页用）。
     *
     * <p>为什么单开一个：{@code /api/**} 全部要令牌，登录页拿不到令牌，前端只能回退
     * 内置 /logo.png —— 现场把院徽换成自己的图以后，登录页永远是默认图，看着像
     * 「保存没成功」。
     *
     * <p>只放这两个键：{@code /sys-param/get} 是通用接口，匿名放开会把消息推送授权码、
     * ICU 外链令牌这类参数一起露出去。
     */
    @GetMapping("/public/doc-header")
    public Result<Map<String, String>> publicDocHeader() {
        Map<String, String> data = new HashMap<>(4);
        try {
            data.put("logo", sysParamService.value(SysParamService.KEY_DOC_HOSPITAL_LOGO));
            data.put("name", sysParamService.value(SysParamService.KEY_DOC_HOSPITAL_NAME));
        } catch (Exception e) {
            // 品牌参数属非关键增强：读不到就让前端用内置抬头，不能让登录页报错
            log.warn("登录页品牌参数读取失败，前端将回退默认抬头", e);
        }
        return Result.ok(data);
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
