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

    @Autowired
    private com.zing.doctor.icu.service.UserDepartScopeService departScopeService;

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

    /**
     * ARDS 全院筛查：本周期内「在院或已出科」且诊断含 ARDS 的患者（不限科室清单视图）。
     * 科室权限：管理员/空 = 全院（ALL），普通账号收敛到本科室。
     *
     * @param startTime 周期开始（含），yyyy-MM-dd HH:mm:ss
     * @param endTime   周期结束（不含），yyyy-MM-dd HH:mm:ss
     */
    @GetMapping("/hospital-screen")
    public Result<Map<String, Object>> hospitalScreen(@RequestParam String startTime,
                                                      @RequestParam String endTime,
                                                      @RequestParam(required = false) String departCode,
                                                      javax.servlet.http.HttpServletRequest request) {
        try {
            String username = request.getAttribute(com.zing.doctor.external.ExternalLinkInterceptor.ATTR_LOGIN_USER) == null
                    ? null : String.valueOf(request.getAttribute(com.zing.doctor.external.ExternalLinkInterceptor.ATTR_LOGIN_USER));
            String allowed = departScopeService.resolveQueryDepart(username, departCode);
            return Result.ok(ardsService.hospitalScreen(startTime, endTime, allowed));
        } catch (Exception e) {
            log.error("ARDS全院筛查失败: startTime={}, endTime={}", startTime, endTime, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }
}
