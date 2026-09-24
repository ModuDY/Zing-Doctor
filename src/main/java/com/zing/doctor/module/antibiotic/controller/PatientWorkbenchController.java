package com.zing.doctor.module.antibiotic.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.external.ExternalLinkInterceptor;
import com.zing.doctor.icu.dto.DepartScope;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.icu.service.UserDepartScopeService;
import com.zing.doctor.icu.service.WorkbenchEnrichService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/** ICU 全量在科患者工作台接口。 */
@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
public class PatientWorkbenchController {
    private final IcuPatientService icuPatientService;
    private final UserDepartScopeService departScopeService;
    private final WorkbenchEnrichService workbenchEnrichService;

    /**
     * 当前账号的科室可见范围 —— 页面据此决定「默认进哪个科室 / 要不要让用户选 / 有没有权限」。
     *
     * <p>放在独立接口里而不是随列表一起返回：列表接口 poliomyelitis 可能因为越权直接报错，
     * 而科室范围本身是「能不能查」的前提，必须能在报错之前先拿到。
     */
    @GetMapping("/scope")
    public Result<DepartScope> scope(HttpServletRequest request) {
        return Result.ok(departScopeService.resolve(currentUsername(request)));
    }

    /**
     * 在科患者列表。
     *
     * <p>departCode 是科室边界，必须是 sys_depart.org_code —— 不认 ward_name（病区名），
     * 两者不是同一套编码，拿病区名来查既过滤不出东西，也没法和质控 / DDD 对齐。
     *
     * <p>直连登录时 departCode 要先过一遍账号的科室授权（见
     * {@link UserDepartScopeService#resolveQueryDepart}）：管理员可传任意值或留空按全院查，
     * 普通账号只能查自己有权限的科室，留空不会退回全院而是报错 —— 避免绕过前端
     * 直接调接口就看到全科患者。外链免登录时不套这套限制，仍按外链参数走。
     */
    @GetMapping("/patients")
    public Result<List<WorkbenchPatient>> patients(@RequestParam(required = false) String departCode,
                                                   HttpServletRequest request) {
        String allowed = departScopeService.resolveQueryDepart(currentUsername(request), departCode);
        List<WorkbenchPatient> patients = icuPatientService.listInpatients(allowed);
        // 危重标签（机械通气/血管活性药/CRRT，ICU 库批量）
        icuPatientService.enrichCrisisFlags(patients, allowed);
        // 当日待办（SOFA/APACHE 未评，本系统库批量）
        workbenchEnrichService.enrich(patients);
        return Result.ok(patients);
    }

    /** 当前登录账号；外链免登录时返回 null */
    private static String currentUsername(HttpServletRequest request) {
        Object u = request.getAttribute(ExternalLinkInterceptor.ATTR_LOGIN_USER);
        return u == null ? null : String.valueOf(u);
    }
}
