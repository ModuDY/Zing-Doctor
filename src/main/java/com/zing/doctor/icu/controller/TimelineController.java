package com.zing.doctor.icu.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.icu.dto.TimelineEvent;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.icu.service.TimelineService;
import com.zing.doctor.icu.service.UserDepartScopeService;
import com.zing.doctor.external.ExternalLinkInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 临床时间线接口
 *
 * <p>聚合患者的关键临床事件：评分、抗感染决策/复评、脓毒症集束化、
 * 俯卧位、查房记录等，按时间倒序展示。</p>
 *
 * <p><b>权限校验</b>：先按 patientId 查患者所属科室，再过 UserDepartScopeService
 * 校验当前账号是否有该科室权限。外链（username=null）不套科室限制。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
public class TimelineController {

    private final TimelineService timelineService;
    private final IcuPatientService icuPatientService;
    private final UserDepartScopeService departScopeService;

    @GetMapping("/patients/{patientId}/timeline")
    public Result<List<TimelineEvent>> getTimeline(@PathVariable String patientId,
                                                    HttpServletRequest request) {
        // 权限校验：先查患者所属科室
        WorkbenchPatient patient = icuPatientService.getWorkbenchPatient(patientId);
        if (patient == null) {
            return Result.fail("患者不存在或已出科");
        }
        String allowed = departScopeService.resolveQueryDepart(
                currentUsername(request), patient.getDepartCode());
        if (allowed == null) {
            return Result.fail("无该患者科室权限");
        }
        List<TimelineEvent> events = timelineService.getPatientTimeline(patientId);
        return Result.ok(events);
    }

    /** 当前登录账号；外链免登录时返回 null */
    private static String currentUsername(HttpServletRequest request) {
        Object u = request.getAttribute(ExternalLinkInterceptor.ATTR_LOGIN_USER);
        return u == null ? null : u.toString();
    }
}
