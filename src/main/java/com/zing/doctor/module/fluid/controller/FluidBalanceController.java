package com.zing.doctor.module.fluid.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.external.ExternalLinkInterceptor;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.icu.service.UserDepartScopeService;
import com.zing.doctor.module.fluid.dto.FluidBalanceResponse;
import com.zing.doctor.module.fluid.service.FluidBalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 体液平衡接口（只读）。
 *
 * <p>docs/25 §2：按来源分列（出量 / 尿量 / 医嘱入量 / 其他入量），
 * 不提供跨来源的净平衡合计数（去重关系未经证明）。
 */
@RestController
@RequestMapping("/api/fluid-balance")
@RequiredArgsConstructor
public class FluidBalanceController {

    private final IcuPatientService icuPatientService;
    private final UserDepartScopeService departScopeService;
    private final FluidBalanceService fluidBalanceService;

    /**
     * @param patientId 患者 patient_info.id
     * @param hours     回溯小时数，1~72（默认 24）
     */
    @GetMapping("/patients/{patientId}")
    public Result<FluidBalanceResponse> balance(@PathVariable String patientId,
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
        return Result.ok(fluidBalanceService.balance(patientId, hours));
    }

    /** 当前登录账号；外链免登录时返回 null（与其它控制器同一取法）。 */
    private static String currentUsername(HttpServletRequest request) {
        Object u = request.getAttribute(ExternalLinkInterceptor.ATTR_LOGIN_USER);
        return u == null ? null : String.valueOf(u);
    }
}
