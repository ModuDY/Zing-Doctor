package com.zing.doctor.module.hemodynamics.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.external.ExternalLinkInterceptor;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.icu.service.UserDepartScopeService;
import com.zing.doctor.module.hemodynamics.entity.HemodynamicsRecord;
import com.zing.doctor.module.hemodynamics.service.HemodynamicsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 血流动力学 / 心功能手工录入接口。
 *
 * <p>权限与导管感染确诊同一套：读不限在科（历史回顾可查），写仅限在科患者；
 * departCode / patientName / inHospitalNo 一律从患者基础信息覆写，不采信前端。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/hemodynamics")
@RequiredArgsConstructor
public class HemodynamicsController {

    private final HemodynamicsService service;
    private final IcuPatientService icuPatientService;
    private final UserDepartScopeService departScopeService;

    /** 患者全部有效记录（时间升序，供趋势图）。 */
    @GetMapping("/patients/{patientId}/records")
    public Result<List<HemodynamicsRecord>> list(@PathVariable String patientId,
                                                 HttpServletRequest request) {
        Permission check = checkRead(patientId, request);
        if (check.error != null) return Result.fail(check.error);
        return Result.ok(service.listByPatient(patientId));
    }

    /** 新增 / 更新一条测量记录（仅限在科患者）。 */
    @PostMapping("/save")
    public Result<HemodynamicsRecord> save(@RequestBody HemodynamicsRecord record,
                                           HttpServletRequest request) {
        if (record.getPatientId() == null || record.getPatientId().isEmpty()) {
            return Result.fail("patientId 不能为空");
        }
        Permission perm = checkWrite(record.getPatientId(), request);
        if (perm.error != null) return Result.fail(perm.error);
        WorkbenchPatient patient = perm.patient;
        record.setDepartCode(patient.getDepartCode());
        record.setPatientName(patient.getName());
        record.setInHospitalNo(patient.getInHospitalNo());
        return Result.ok(service.save(record));
    }

    /** 逻辑删除（id + patientId 归属校验）。 */
    @DeleteMapping("/records/{id}")
    public Result<Void> delete(@PathVariable Long id,
                               @RequestParam String patientId,
                               HttpServletRequest request) {
        Permission perm = checkWrite(patientId, request);
        if (perm.error != null) return Result.fail(perm.error);
        return service.deleteById(id, patientId) == 0 ? Result.fail("记录不存在、已删除或不属于该患者") : Result.ok();
    }

    // ---------------- 权限（与 DeviceInfectionController 同套路） ----------------

    private Permission checkRead(String patientId, HttpServletRequest request) {
        WorkbenchPatient patient = icuPatientService.getWorkbenchPatientAny(patientId);
        if (patient == null) return Permission.deny("患者不存在");
        String allowed = departScopeService.resolveQueryDepart(username(request), patient.getDepartCode());
        if (allowed == null) return Permission.deny("无该患者科室权限");
        return Permission.ok(patient);
    }

    private Permission checkWrite(String patientId, HttpServletRequest request) {
        WorkbenchPatient patient = icuPatientService.getWorkbenchPatient(patientId);
        if (patient == null) return Permission.deny("患者不存在或已出科");
        String allowed = departScopeService.resolveQueryDepart(username(request), patient.getDepartCode());
        if (allowed == null) return Permission.deny("无该患者科室权限");
        return Permission.ok(patient);
    }

    private static final class Permission {
        private final WorkbenchPatient patient;
        private final String error;

        private Permission(WorkbenchPatient patient, String error) {
            this.patient = patient;
            this.error = error;
        }

        private static Permission ok(WorkbenchPatient patient) { return new Permission(patient, null); }

        private static Permission deny(String error) { return new Permission(null, error); }
    }

    private static String username(HttpServletRequest request) {
        Object u = request.getAttribute(ExternalLinkInterceptor.ATTR_LOGIN_USER);
        return u == null ? null : u.toString();
    }
}
