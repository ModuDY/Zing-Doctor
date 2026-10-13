package com.zing.doctor.module.vitals.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.external.ExternalLinkInterceptor;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.icu.service.UserDepartScopeService;
import com.zing.doctor.module.vitals.dto.VitalsResponse;
import com.zing.doctor.module.vitals.service.VitalsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 生命体征趋势接口（只读）。
 *
 * <p>权限与其它患者级接口一致：先按 patientId 取患者确认存在及其所属科室，
 * 再用 {@link UserDepartScopeService#resolveQueryDepart} 校验科室权限。
 * 已出科患者同样可查 —— 生命体征是回顾性数据（getWorkbenchPatientAny）。
 */
@RestController
@RequestMapping("/api/vitals")
@RequiredArgsConstructor
public class VitalsController {

    private final IcuPatientService icuPatientService;
    private final UserDepartScopeService departScopeService;
    private final VitalsService vitalsService;

    /**
     * @param patientId 患者 patient_info.id
     * @param hours     回溯小时数，1~168（默认 24，最大 7 天）
     */
    @GetMapping("/patients/{patientId}")
    public Result<VitalsResponse> trend(@PathVariable String patientId,
                                        @RequestParam(required = false, defaultValue = "24") int hours,
                                        HttpServletRequest request) {
        WorkbenchPatient patient = icuPatientService.getWorkbenchPatientAny(patientId);
        if (patient == null) {
            return Result.fail("患者不存在");
        }
        String allowed = departScopeService.resolveQueryDepart(
                currentUsername(request), patient.getDepartCode());
        if (allowed == null) {
            return Result.fail("无该患者科室权限");
        }
        return Result.ok(vitalsService.trend(patientId, hours));
    }

    /** 当前登录账号；外链免登录时返回 null（与其它控制器同一取法）。 */
    private static String currentUsername(HttpServletRequest request) {
        Object u = request.getAttribute(ExternalLinkInterceptor.ATTR_LOGIN_USER);
        return u == null ? null : String.valueOf(u);
    }
}