package com.zing.doctor.module.neuro.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.external.ExternalLinkInterceptor;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.icu.service.UserDepartScopeService;
import com.zing.doctor.module.neuro.dto.NeuroMonitorResponse;
import com.zing.doctor.module.neuro.service.NeuroMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 急性脑损伤 / 神经功能监测接口（只读）。
 *
 * <p>权限与镇痛镇静监测、患者诊疗摘要一致：先按 patientId 定位在科患者及所属科室，
 * 再用 {@link UserDepartScopeService#resolveQueryDepart} 校验科室权限；外链免登录放行。
 */
@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
public class NeuroMonitorController {

    private final IcuPatientService icuPatientService;
    private final UserDepartScopeService departScopeService;
    private final NeuroMonitorService neuroMonitorService;

    /**
     * 单患者神经功能监测。
     *
     * @param hours 时间窗小时数（默认 24，最大 720=30 天）；start/end 同时提供时优先自定义起止
     * @param start 自定义窗口开始（含），ISO {@code yyyy-MM-dd'T'HH:mm:ss}
     * @param end   自定义窗口结束（不含），ISO；窗口最长 30 天
     */
    @GetMapping("/patients/{patientId}/neuro")
    public Result<NeuroMonitorResponse> neuro(@PathVariable String patientId,
                                              @RequestParam(required = false) Integer hours,
                                              @RequestParam(required = false) String start,
                                              @RequestParam(required = false) String end,
                                              HttpServletRequest request) {
        // 只读场景放开已出科患者：出院后复盘、死亡病例讨论也要能看得到历史监测
        WorkbenchPatient patient = icuPatientService.getWorkbenchPatientAny(patientId);
        if (patient == null) {
            return Result.fail("患者不存在");
        }
        String allowed = departScopeService.resolveQueryDepart(
                currentUsername(request), patient.getDepartCode());
        if (allowed == null) {
            return Result.fail("无该患者科室权限");
        }
        try {
            return Result.ok(neuroMonitorService.analyze(patient, hours, start, end));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    private static String currentUsername(HttpServletRequest request) {
        Object u = request.getAttribute(ExternalLinkInterceptor.ATTR_LOGIN_USER);
        return u == null ? null : String.valueOf(u);
    }
}
