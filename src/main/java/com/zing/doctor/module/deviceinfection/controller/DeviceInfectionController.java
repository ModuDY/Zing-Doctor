package com.zing.doctor.module.deviceinfection.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.external.ExternalLinkInterceptor;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.icu.service.UserDepartScopeService;
import com.zing.doctor.module.deviceinfection.entity.DeviceInfectionRecord;
import com.zing.doctor.module.deviceinfection.service.DeviceInfectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 导管相关感染确诊录入接口（VAP / CRBSI / CAUTI）。
 *
 * <p><b>权限校验</b>：与查房记录一致 —— 先按 patientId 查患者所属科室，再过
 * UserDepartScopeService 校验账号科室权限； departCode / patientName / inHospitalNo
 * 一律从患者基础信息取，不采信前端传入，避免越权写入其他科室。</p>
 *
 * <p><b>与质控的联动</b>：保存/删除后需重算导管域指标才会反映到看板，
 * 由 QualityDailyTask 每日批算兜底，也可即时 POST /api/quality/recalc/metric。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/device-infection")
@RequiredArgsConstructor
public class DeviceInfectionController {

    /** 允许的感染类型（与事实层 fact_device.device_infection 的判定一一对应） */
    private static final List<String> TYPES = Arrays.asList("VAP", "CRBSI", "CAUTI");

    private final DeviceInfectionService service;
    private final IcuPatientService icuPatientService;
    private final UserDepartScopeService departScopeService;

    /** 患者的确诊记录列表（含历史；只读不限在科） */
    @GetMapping("/records")
    public Result<List<DeviceInfectionRecord>> listRecords(
            @RequestParam String patientId,
            @RequestParam(required = false) String infectionType,
            HttpServletRequest request) {
        Permission check = checkReadPermission(patientId, request);
        if (check.error != null) return Result.fail(check.error);
        return Result.ok(service.listByPatient(patientId, infectionType));
    }

    /**
     * 近期确诊记录（科室维度，供质控核对页展示）。
     * days 不传默认 30；上限 365。
     */
    @GetMapping("/recent")
    public Result<List<DeviceInfectionRecord>> listRecent(
            @RequestParam(required = false) String departCode,
            @RequestParam(required = false) String infectionType,
            @RequestParam(required = false) Integer days,
            HttpServletRequest request) {
        int d = days == null || days <= 0 ? 30 : Math.min(days, 365);
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(d);
        // 科室范围由账号可见范围收敛；传 ALL 或不传时按账号权限解析
        String allowed = departScopeService.resolveQueryDepart(currentUsername(request), departCode);
        if (allowed == null && departCode != null && !departCode.isEmpty() && !"ALL".equalsIgnoreCase(departCode)) {
            return Result.fail("无该科室权限");
        }
        return Result.ok(service.listRecent(allowed, infectionType, start, end, 500));
    }

    /** 新增/更新确诊记录（仅限在科患者） */
    @PostMapping("/save")
    public Result<DeviceInfectionRecord> save(@RequestBody DeviceInfectionRecord record,
                                              HttpServletRequest request) {
        if (record.getPatientId() == null || record.getPatientId().isEmpty()) {
            return Result.fail("patientId 不能为空");
        }
        if (record.getInfectionType() == null || !TYPES.contains(record.getInfectionType())) {
            return Result.fail("infectionType 必须是 VAP / CRBSI / CAUTI");
        }
        // 一次查询同时完成「在科 + 科室权限」校验并拿到患者对象，供下方回填字段使用
        Permission perm = checkWritePermission(record.getPatientId(), request);
        if (perm.error != null) return Result.fail(perm.error);

        // 不采信前端传入的科室/姓名/住院号，一律从患者基础信息覆写
        WorkbenchPatient patient = perm.patient;
        record.setDepartCode(patient.getDepartCode());
        record.setPatientName(patient.getName());
        record.setInHospitalNo(patient.getInHospitalNo());
        return Result.ok(service.save(record));
    }

    /**
     * 逻辑删除确诊记录（仅限在科患者；历史确诊的更正应重新录入而非物理删除）。
     *
     * <p>patientId 除了过科室权限，还用于<b>归属校验</b>：记录必须属于该患者。
     * 否则「传一个自己有权限的 patientId + 别人的记录 id」就能跨科室删数据。</p>
     */
    @DeleteMapping("/records/{id}")
    public Result<Void> delete(@PathVariable Long id,
                               @RequestParam String patientId,
                               HttpServletRequest request) {
        Permission perm = checkWritePermission(patientId, request);
        if (perm.error != null) return Result.fail(perm.error);
        int rows = service.deleteById(id, patientId);
        if (rows == 0) {
            return Result.fail("记录不存在、已删除或不属于该患者");
        }
        return Result.ok();
    }

    /** 读权限校验（含已出科患者：历史确诊记录可查） */
    private Permission checkReadPermission(String patientId, HttpServletRequest request) {
        WorkbenchPatient patient = icuPatientService.getWorkbenchPatientAny(patientId);
        if (patient == null) {
            return Permission.deny("患者不存在");
        }
        String allowed = departScopeService.resolveQueryDepart(
                currentUsername(request), patient.getDepartCode());
        if (allowed == null) {
            return Permission.deny("无该患者科室权限");
        }
        return Permission.ok(patient);
    }

    /** 写权限校验（仅限在科患者），返回命中的患者对象供调用方复用，避免重复查患者 */
    private Permission checkWritePermission(String patientId, HttpServletRequest request) {
        WorkbenchPatient patient = icuPatientService.getWorkbenchPatient(patientId);
        if (patient == null) {
            return Permission.deny("患者不存在或已出科");
        }
        String allowed = departScopeService.resolveQueryDepart(
                currentUsername(request), patient.getDepartCode());
        if (allowed == null) {
            return Permission.deny("无该患者科室权限");
        }
        return Permission.ok(patient);
    }

    /** 权限校验结果：error 非空 = 拒绝原因；patient = 命中的患者（写场景用于回填字段） */
    private static final class Permission {

        private final WorkbenchPatient patient;
        private final String error;

        private Permission(WorkbenchPatient patient, String error) {
            this.patient = patient;
            this.error = error;
        }

        private static Permission ok(WorkbenchPatient patient) {
            return new Permission(patient, null);
        }

        private static Permission deny(String error) {
            return new Permission(null, error);
        }
    }

    private static String currentUsername(HttpServletRequest request) {
        Object u = request.getAttribute(ExternalLinkInterceptor.ATTR_LOGIN_USER);
        return u == null ? null : u.toString();
    }
}
