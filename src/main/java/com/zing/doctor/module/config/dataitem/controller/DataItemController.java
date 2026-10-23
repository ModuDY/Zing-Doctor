package com.zing.doctor.module.config.dataitem.controller;

import com.zing.doctor.common.BizException;
import com.zing.doctor.common.OperatorContext;
import com.zing.doctor.common.Result;
import com.zing.doctor.module.config.dataitem.entity.DataItemConfig;
import com.zing.doctor.module.config.dataitem.service.DataItemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据项映射配置（换院部署时唯一需要改页面）。
 *
 * <p>所有取数页面共用这一套配置：同一批观察项编码被生命体征、交班、ARDS、SOFA、
 * APACHE II 等 13 个服务引用，按页面拆开必然配漏。
 */
@Slf4j
@RestController
@RequestMapping("/api/config/data-item")
@RequiredArgsConstructor
public class DataItemController {

    private final DataItemConfigService service;

    /** 页面 Tab 元信息（含「是否已接入取数」） */
    @GetMapping("/domains")
    public Result<List<Map<String, Object>>> domains() {
        return Result.ok(service.domains());
    }

    /** 配置列表：domain 可空 = 全部 */
    @GetMapping("/list")
    public Result<List<DataItemConfig>> list(@RequestParam(required = false) String domain) {
        return Result.ok(service.list(domain));
    }

    /** 新增 / 编辑（id 为空即新增） */
    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody DataItemConfig row) {
        try {
            String operator = OperatorContext.current();
            if (row.getId() == null) {
                row.setCreateBy(operator);
            }
            row.setCreateTime(row.getId() == null ? LocalDateTime.now() : null);
            row.setUpdateBy(operator);
            return Result.ok(service.save(row));
        } catch (BizException e) {
            return Result.fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[数据项配置] 保存失败", e);
            return Result.fail("保存失败: " + e.getMessage());
        }
    }

    /** 启用 / 停用 */
    @PostMapping("/status")
    public Result<Boolean> status(@RequestParam Long id, @RequestParam Integer status) {
        try {
            return Result.ok(service.setStatus(id, status));
        } catch (BizException e) {
            return Result.fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[数据项配置] 启停失败 id={}", id, e);
            return Result.fail("操作失败: " + e.getMessage());
        }
    }

    /** 删除（配置行，不是业务数据） */
    @PostMapping("/delete")
    public Result<Boolean> delete(@RequestParam Long id) {
        try {
            return Result.ok(service.remove(id));
        } catch (BizException e) {
            return Result.fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[数据项配置] 删除失败 id={}", id, e);
            return Result.fail("删除失败: " + e.getMessage());
        }
    }

    /** 候选值：从 ICU 源库拉真实存在的项目，现场不用猜编码 */
    @GetMapping("/candidates")
    public Result<List<Map<String, Object>>> candidates(@RequestParam(required = false) String domain,
                                                        @RequestParam(required = false) String itemKey,
                                                        @RequestParam(required = false) String keyword) {
        try {
            return Result.ok(service.candidates(domain, itemKey, keyword));
        } catch (Exception e) {
            log.error("[数据项配置] 候选值查询失败 domain={}", domain, e);
            return Result.fail("候选值查询失败: " + e.getMessage());
        }
    }

    /** 试跑：按匹配值统计源库命中条数并返回最新样例 */
    @GetMapping("/probe")
    public Result<Map<String, Object>> probe(@RequestParam(required = false) String domain,
                                             @RequestParam(required = false) String itemKey,
                                             @RequestParam String value,
                                             @RequestParam(required = false) Integer days) {
        try {
            return Result.ok(service.probe(domain, itemKey, value, days));
        } catch (BizException e) {
            return Result.fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[数据项配置] 试跑失败 domain={} value={}", domain, value, e);
            return Result.fail("试跑失败: " + e.getMessage());
        }
    }
}
