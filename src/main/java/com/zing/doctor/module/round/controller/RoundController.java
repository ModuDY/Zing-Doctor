package com.zing.doctor.module.round.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.icu.service.UserDepartScopeService;
import com.zing.doctor.module.round.entity.RoundRecord;
import com.zing.doctor.module.round.service.RoundService;
import com.zing.doctor.external.ExternalLinkInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;

/**
 * 医生查房记录接口
 *
 * <p>查房记录是患者日级的诊疗计划记录，与班次级的交班表分开。
 * 同一患者同一天只保留一份，保存时自动覆盖更新。</p>
 *
 * <p><b>权限校验</b>：所有接口先按 patientId 查患者所属科室，再过 UserDepartScopeService
 * 校验当前账号是否有该科室权限。保存时不采信前端传入的 departCode，
 * 统一从患者基础信息中取，避免越权写入其他科室。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/round")
@RequiredArgsConstructor
public class RoundController {

    private final RoundService roundService;
    private final IcuPatientService icuPatientService;
    private final UserDepartScopeService departScopeService;

    /**
     * 获取指定患者指定日期的查房记录。
     * roundDate 不传时默认今天。
     */
    @GetMapping("/record")
    public Result<RoundRecord> getRecord(
            @RequestParam String patientId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate roundDate,
            HttpServletRequest request) {
        Result<Void> check = checkPatientPermission(patientId, request);
        if (check != null) return Result.fail(check.getMessage());
        if (roundDate == null) roundDate = LocalDate.now();
        RoundRecord record = roundService.getByDate(patientId, roundDate);
        return Result.ok(record);
    }

    /**
     * 获取指定患者的历史查房记录列表（按日期倒序，最多30条）。
     */
    @GetMapping("/history")
    public Result<List<RoundRecord>> listHistory(@RequestParam String patientId,
                                                  HttpServletRequest request) {
        Result<Void> check = checkPatientPermission(patientId, request);
        if (check != null) return Result.fail(check.getMessage());
        List<RoundRecord> list = roundService.listHistory(patientId);
        return Result.ok(list);
    }

    /**
     * 保存查房记录。同一患者同一天只保留一份，已存在则更新。
     * departCode 不从请求体取，统一从患者基础信息中取。
     */
    @PostMapping("/save")
    public Result<RoundRecord> save(@RequestBody RoundRecord record, HttpServletRequest request) {
        if (record.getPatientId() == null || record.getPatientId().isEmpty()) {
            return Result.fail("patientId 不能为空");
        }
        // 权限校验 + 取真实 departCode
        WorkbenchPatient patient = icuPatientService.getWorkbenchPatient(record.getPatientId());
        if (patient == null) {
            return Result.fail("患者不存在或已出科");
        }
        String allowed = departScopeService.resolveQueryDepart(
                currentUsername(request), patient.getDepartCode());
        if (allowed == null) {
            return Result.fail("无该患者科室权限");
        }
        // 不采信前端传入的 departCode / patientName / inHospitalNo，统一从患者信息取
        record.setDepartCode(patient.getDepartCode());
        record.setPatientName(patient.getName());
        record.setInHospitalNo(patient.getInHospitalNo());
        RoundRecord saved = roundService.save(record);
        return Result.ok(saved);
    }

    /**
     * 校验患者科室权限。返回 null 表示通过，返回 Result 表示有权限错误。
     */
    private Result<Void> checkPatientPermission(String patientId, HttpServletRequest request) {
        WorkbenchPatient patient = icuPatientService.getWorkbenchPatient(patientId);
        if (patient == null) {
            return Result.fail("患者不存在或已出科");
        }
        String allowed = departScopeService.resolveQueryDepart(
                currentUsername(request), patient.getDepartCode());
        if (allowed == null) {
            return Result.fail("无该患者科室权限");
        }
        return null;
    }

    /** 当前登录账号；外链免登录时返回 null */
    private static String currentUsername(HttpServletRequest request) {
        Object u = request.getAttribute(ExternalLinkInterceptor.ATTR_LOGIN_USER);
        return u == null ? null : u.toString();
    }
}
