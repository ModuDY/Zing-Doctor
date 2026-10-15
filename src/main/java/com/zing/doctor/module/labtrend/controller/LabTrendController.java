package com.zing.doctor.module.labtrend.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.external.ExternalLinkInterceptor;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.icu.service.UserDepartScopeService;
import com.zing.doctor.module.labtrend.dto.LabTrendResponse;
import com.zing.doctor.module.labtrend.service.LabTrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 检验趋势接口（肝功能 / 凝血功能 / 肾功能与电解质，只读）。
 */
@RestController
@RequestMapping("/api/lab-trend")
@RequiredArgsConstructor
public class LabTrendController {

    /** 支持的面板类型；不在白名单内直接挡回，不让未知 type 落到 Service 的兜底分支上。 */
    private static final Set<String> TYPES =
            new HashSet<>(Arrays.asList("liver", "coag", "renal", "infection"));

    private final IcuPatientService icuPatientService;
    private final UserDepartScopeService departScopeService;
    private final LabTrendService labTrendService;

    /**
     * @param type liver（肝功能）/ coag（凝血）/ renal（肾功能与电解质）
     * @param hours 回溯小时数，1~720（默认 168=7天）
     */
    @GetMapping("/patients/{patientId}")
    public Result<LabTrendResponse> trend(@PathVariable String patientId,
                                          @RequestParam(defaultValue = "liver") String type,
                                          @RequestParam(required = false, defaultValue = "168") int hours,
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
        if (!TYPES.contains(type)) {
            return Result.fail("type 仅支持 liver / coag / renal / infection");
        }
        return Result.ok(labTrendService.trend(patientId, type, hours));
    }

    private static String currentUsername(HttpServletRequest request) {
        Object u = request.getAttribute(ExternalLinkInterceptor.ATTR_LOGIN_USER);
        return u == null ? null : String.valueOf(u);
    }
}