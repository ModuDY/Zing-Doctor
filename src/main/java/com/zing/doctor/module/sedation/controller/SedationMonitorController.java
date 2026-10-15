package com.zing.doctor.module.sedation.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.external.ExternalLinkInterceptor;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.icu.service.UserDepartScopeService;
import com.zing.doctor.module.sedation.dto.SedationMonitorResponse;
import com.zing.doctor.module.sedation.service.SedationMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 镇痛镇静监测分析接口（只读）。
 *
 * <p>与患者诊疗摘要共用同一套患者定位 + 科室权限：先按 patientId 取在科患者及其所属科室，
 * 再用 {@link UserDepartScopeService#resolveQueryDepart} 校验当前账号是否有权访问该科室；
 * 外链免登录（username=null）不套此限制，由 extToken 体系控制边界。
 */
@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
public class SedationMonitorController {

    private final IcuPatientService icuPatientService;
    private final UserDepartScopeService departScopeService;
    private final SedationMonitorService sedationMonitorService;

    /**
     * 单患者镇痛镇静 / 谵妄监测。
     *
     * @param hours 时间窗小时数，可空（默认 24，最大 720=30 天）；start/end 同时提供时优先用自定义起止
     * @param start 自定义窗口开始时间（含），ISO {@code yyyy-MM-dd'T'HH:mm:ss}，与 end 同时提供
     * @param end   自定义窗口结束时间（不含），ISO，与 start 同时提供；窗口最长 30 天
     */
    @GetMapping("/patients/{patientId}/sedation")
    public Result<SedationMonitorResponse> sedation(@PathVariable String patientId,
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
            return Result.ok(sedationMonitorService.analyze(patient, hours, start, end));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 窗口内阿片类镇痛药执行医嘱（与镇痛评分同窗口对照）。
     * 参数口径与 {@link #sedation} 完全一致，前端把两块放同一时间轴。
     */
    @GetMapping("/patients/{patientId}/analgesia")
    public Result<java.util.List<java.util.Map<String, Object>>> analgesia(@PathVariable String patientId,
                                                                           @RequestParam(required = false) Integer hours,
                                                                           @RequestParam(required = false) String start,
                                                                           @RequestParam(required = false) String end,
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
        try {
            return Result.ok(sedationMonitorService.analgesiaDrugs(patient, hours, start, end));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 当前登录账号；外链免登录时返回 null */
    private static String currentUsername(HttpServletRequest request) {
        Object u = request.getAttribute(ExternalLinkInterceptor.ATTR_LOGIN_USER);
        return u == null ? null : String.valueOf(u);
    }
}
