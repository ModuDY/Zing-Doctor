package com.zing.doctor.module.vasoactive.controller;

import com.zing.doctor.common.BizException;
import com.zing.doctor.common.Result;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.service.DepartScopeAsserts;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.icu.service.UserDepartScopeService;
import com.zing.doctor.module.vasoactive.service.VasoactiveService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 血管活性药分析 Controller。
 *
 * <p>升压药执行链路（patient_advice_execute + _drug + _step）的剂量与调速还原 SOFA 循环项已在用；
 * 本页按「患者维度」把执行时间轴、当前在用（开立链路）、MAP 趋势、乳酸趋势放到一个页面，
 * 供休克患者滴定治疗时对照阅读。只读，不改任何医嘱数据。
 *
 * <p><b>科室收敛（security-audit 2026-10：patient-endpoint-no-depart-scope）</b>：
 * 患者级端点按患者所属科室过 {@link UserDepartScopeService#resolveQueryDepart}，
 * 普通直连账号仅能访问其授权科室（越权 403 / 未选科室 400）；管理员全院；外链维持既有语义。
 */
@Slf4j
@RestController
@RequestMapping("/api/vasoactive")
public class VasoactiveController {

    @Autowired
    private VasoactiveService vasoactiveService;

    @Autowired
    private IcuPatientService icuPatientService;

    @Autowired
    private UserDepartScopeService departScopeService;

    /** 患者级科室收敛：按 patientId 取患者所属科室后过 resolveQueryDepart */
    private void assertPatientAccess(HttpServletRequest request, String patientId) {
        WorkbenchPatient patient = icuPatientService.getWorkbenchPatientAny(patientId);
        if (patient == null) {
            throw new BizException(404, "患者不存在：" + patientId);
        }
        DepartScopeAsserts.assertPatientDepart(departScopeService, request, patient);
    }

    /**
     * 患者血管活性药分析：执行时间轴 + 调速步骤 + 当前在用 + MAP/乳酸趋势。
     *
     * @param patientId ICU 患者主键（与体液平衡页同一入参习惯；住院号由服务端解析）
     * @param hours     回看窗口（1～72，默认 24）
     */
    @GetMapping("/patients/{patientId}/analysis")
    public Result<Map<String, Object>> analysis(@PathVariable String patientId,
                                                @RequestParam(defaultValue = "24") int hours,
                                                HttpServletRequest request) {
        try {
            assertPatientAccess(request, patientId);
            return Result.ok(vasoactiveService.analysis(patientId, hours));
        } catch (Exception e) {
            log.error("血管活性药分析失败: patientId={}, hours={}", patientId, hours, e);
            return Result.fail("分析失败: " + e.getMessage());
        }
    }
}
