package com.zing.doctor.module.nutrition.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.external.ExternalLinkInterceptor;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.icu.service.UserDepartScopeService;
import com.zing.doctor.module.nutrition.dto.NutritionResponse;
import com.zing.doctor.module.nutrition.service.NutritionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 营养支持分析接口（只读）。与镇痛镇静/神经监测共用同一套患者定位 + 科室权限。
 */
@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
public class NutritionController {

    private final IcuPatientService icuPatientService;
    private final UserDepartScopeService departScopeService;
    private final NutritionService nutritionService;

    @GetMapping("/patients/{patientId}/nutrition")
    public Result<NutritionResponse> nutrition(@PathVariable String patientId,
                                              @RequestParam(required = false) Integer hours,
                                              @RequestParam(required = false) String start,
                                              @RequestParam(required = false) String end,
                                              HttpServletRequest request) {
        WorkbenchPatient patient = icuPatientService.getWorkbenchPatientAny(patientId);
        if (patient == null) return Result.fail("患者不存在");
        String allowed = departScopeService.resolveQueryDepart(
                currentUsername(request), patient.getDepartCode());
        if (allowed == null) return Result.fail("无该患者科室权限");
        try {
            return Result.ok(nutritionService.analyze(patient, hours, start, end));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    private static String currentUsername(HttpServletRequest request) {
        Object u = request.getAttribute(ExternalLinkInterceptor.ATTR_LOGIN_USER);
        return u == null ? null : String.valueOf(u);
    }
}
