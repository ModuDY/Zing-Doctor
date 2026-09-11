package com.zing.doctor.module.ards.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.module.ards.service.ArdsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ARDS 监测 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/ards")
public class ArdsController {

    @Autowired
    private ArdsService ardsService;

    /**
     * ARDS总览：统计数据 + 患者列表
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview(
            @RequestParam String departCode,
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false, defaultValue = "") String grade) {
        try {
            Map<String, Object> result = ardsService.getArdsOverview(departCode, startTime, endTime, grade);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("获取ARDS总览失败: departCode={}, startTime={}, endTime={}", departCode, startTime, endTime, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 患者呼吸机参数趋势
     */
    @GetMapping("/patient/{patientId}/ventilator-trend")
    public Result<List<Map<String, Object>>> getVentilatorTrend(
            @PathVariable String patientId,
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            List<Map<String, Object>> list = ardsService.getVentilatorTrend(patientId, startTime, endTime);
            return Result.ok(list);
        } catch (Exception e) {
            log.error("获取呼吸机参数趋势失败: patientId={}", patientId, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 患者氧合指数历史（血气时间点）
     */
    @GetMapping("/patient/{patientId}/oxygenation-history")
    public Result<List<Map<String, Object>>> getOxygenationHistory(@PathVariable String patientId) {
        try {
            List<Map<String, Object>> list = ardsService.getOxygenationHistory(patientId);
            return Result.ok(list);
        } catch (Exception e) {
            log.error("获取氧合指数历史失败: patientId={}", patientId, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }
}
