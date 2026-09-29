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
import org.springframework.web.bind.annotation.PathVariable;
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
        // 抗感染 48～72 小时复评待办（本系统库批量）
        workbenchEnrichService.enrichReassessmentTodos(patients);
        // 感染维度（ICU 库批量；只对疑似患者取 PCT/培养/抗菌药明细，其余标记"已查过、没有"）
        workbenchEnrichService.enrichInfection(patients, allowed);
        return Result.ok(patients);
    }

    /**
     * 单患者诊疗摘要：把工作台列表的 enrich 流水线收敛到一个患者上。
     *
     * <p>与 {@link #patients} 的区别是输入从「科室」变成「患者」：先按 patientId 取基础信息
     * 确认在科及所属科室，再用患者所属科室过一遍授权校验，最后走同一套 enrich。
     * 外链（username=null）不套科室限制，由 extToken 体系控制。
     *
     * <p>部分失败不整体报错：感染维度 / 复评待办查询失败时模型内 dataStatus 标记 UNKNOWN，
     * 前端按块渲染「数据暂不可用」；评分查询失败时降级为不生成待办（不制造假催办）。
     */
    @GetMapping("/patients/{patientId}/summary")
    public Result<WorkbenchPatient> summary(@PathVariable String patientId,
                                            HttpServletRequest request) {
        WorkbenchPatient patient = icuPatientService.getWorkbenchPatient(patientId);
        if (patient == null) {
            return Result.fail("患者不存在或已出科");
        }
        String allowed = departScopeService.resolveQueryDepart(
                currentUsername(request), patient.getDepartCode());
        if (allowed == null) {
            return Result.fail("无该患者科室权限");
        }
        List<WorkbenchPatient> list = java.util.Collections.singletonList(patient);
        icuPatientService.enrichCrisisFlags(list, patient.getDepartCode());
        workbenchEnrichService.enrich(list);
        workbenchEnrichService.enrichReassessmentTodos(list);
        workbenchEnrichService.enrichInfection(list, patient.getDepartCode());
        // 二期第一批：24h 检验 / 培养药敏 / 脓毒症集束化
        workbenchEnrichService.enrichLabs24h(list);
        workbenchEnrichService.enrichCulture(list);
        workbenchEnrichService.enrichSepsisBundle(list);
        return Result.ok(patient);
    }

    /** 当前登录账号；外链免登录时返回 null */
    private static String currentUsername(HttpServletRequest request) {
        Object u = request.getAttribute(ExternalLinkInterceptor.ATTR_LOGIN_USER);
        return u == null ? null : String.valueOf(u);
    }
}
