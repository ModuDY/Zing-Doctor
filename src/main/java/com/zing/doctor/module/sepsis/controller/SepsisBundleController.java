package com.zing.doctor.module.sepsis.controller;

import com.zing.doctor.common.BizException;
import com.zing.doctor.common.Result;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.service.DepartScopeAsserts;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.icu.service.UserDepartScopeService;
import com.zing.doctor.module.sepsis.dto.SepsisBundleView;
import com.zing.doctor.module.sepsis.entity.SepsisBundleRecord;
import com.zing.doctor.module.sepsis.service.SepsisBundleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 脓毒症休克集束化治疗 Controller
 *
 * <p><b>科室收敛（security-audit 2026-10：patient-endpoint-no-depart-scope）</b>：
 * 所有患者级端点（按住院号 / 按记录 id）均按患者所属科室过
 * {@link UserDepartScopeService#resolveQueryDepart} —— 普通直连账号仅能访问其授权科室
 * （越权 403 / 未选科室 400）；管理员全院；外链维持既有语义。
 */
@Slf4j
@RestController
@RequestMapping("/api/sepsis/bundle")
public class SepsisBundleController {

    @Autowired
    private SepsisBundleService sepsisBundleService;

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

    /** 患者级科室收敛：按住院号定位患者后过 resolveQueryDepart */
    private void assertPatientAccessByNo(HttpServletRequest request, String inHospitalNo) {
        String patientId = icuPatientService.resolvePatientIdByInHospitalNo(inHospitalNo);
        if (patientId == null) {
            throw new BizException(404, "患者不存在：" + inHospitalNo);
        }
        assertPatientAccess(request, patientId);
    }

    /** 记录级科室收敛：按记录所属患者收敛，防止按 id 跨科室读写 */
    private void assertRecordAccess(HttpServletRequest request, Long recordId) {
        SepsisBundleView view = sepsisBundleService.getBundleDetailById(recordId);
        if (view == null) {
            throw new BizException(404, "记录不存在：" + recordId);
        }
        if (view.getPatientId() != null) {
            assertPatientAccess(request, view.getPatientId());
        } else if (view.getInHospitalNo() != null) {
            assertPatientAccessByNo(request, view.getInHospitalNo());
        }
    }

    /**
     * 获取患者集束化治疗详情（自动判断1H/3H/6H项目完成情况）
     */
    @GetMapping("/detail")
    public Result<SepsisBundleView> getBundleDetail(@RequestParam String inHospitalNo,
                                                    HttpServletRequest request) {
        try {
            assertPatientAccessByNo(request, inHospitalNo);
            SepsisBundleView view = sepsisBundleService.getBundleDetail(inHospitalNo);
            return Result.ok(view);
        } catch (Exception e) {
            log.error("获取脓毒症集束化治疗详情失败: inHospitalNo={}", inHospitalNo, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID获取评估记录详情
     */
    @GetMapping("/detail/{id}")
    public Result<SepsisBundleView> getBundleDetailById(@PathVariable Long id,
                                                        HttpServletRequest request) {
        try {
            assertRecordAccess(request, id);
            SepsisBundleView view = sepsisBundleService.getBundleDetailById(id);
            if (view == null) {
                return Result.fail("记录不存在");
            }
            return Result.ok(view);
        } catch (Exception e) {
            log.error("获取脓毒症集束化治疗详情失败: id={}", id, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 预览计算（不落库）：新建评估、或修改记录时间/确诊时间后重新计算 1H/3H/6H 与三块系统参考
     */
    @GetMapping("/calculate")
    public Result<SepsisBundleView> calculate(@RequestParam String inHospitalNo,
                                              @RequestParam(required = false) String recordTime,
                                              @RequestParam(required = false) String diagnosisTime,
                                              @RequestParam(required = false) Long id,
                                              HttpServletRequest request) {
        try {
            assertPatientAccessByNo(request, inHospitalNo);
            SepsisBundleView view = sepsisBundleService.calculateBundle(inHospitalNo, recordTime, diagnosisTime, id);
            return Result.ok(view);
        } catch (Exception e) {
            log.error("预览计算脓毒症集束化治疗失败: inHospitalNo={}", inHospitalNo, e);
            return Result.fail("计算失败: " + e.getMessage());
        }
    }

    /**
     * 获取患者历史评估记录列表
     */
    @GetMapping("/history")
    public Result<List<SepsisBundleRecord>> getHistoryList(@RequestParam String inHospitalNo,
                                                           HttpServletRequest request) {
        try {
            assertPatientAccessByNo(request, inHospitalNo);
            List<SepsisBundleRecord> list = sepsisBundleService.getHistoryList(inHospitalNo);
            return Result.ok(list);
        } catch (Exception e) {
            log.error("获取脓毒症集束化治疗历史记录失败: inHospitalNo={}", inHospitalNo, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 保存集束化治疗记录
     */
    @PostMapping("/save")
    public Result<SepsisBundleRecord> saveBundle(@RequestBody SepsisBundleRecord record,
                                                 HttpServletRequest request) {
        try {
            if (record != null && record.getPatientId() != null) {
                assertPatientAccess(request, record.getPatientId());
            } else if (record != null && record.getInHospitalNo() != null) {
                assertPatientAccessByNo(request, record.getInHospitalNo());
            }
            SepsisBundleRecord saved = sepsisBundleService.saveBundle(record);
            return Result.ok(saved);
        } catch (Exception e) {
            log.error("保存脓毒症集束化治疗记录失败", e);
            return Result.fail("保存失败: " + e.getMessage());
        }
    }

    /**
     * 更新集束化治疗记录
     */
    @PostMapping("/update")
    public Result<SepsisBundleRecord> updateBundle(@RequestBody SepsisBundleRecord record,
                                                   HttpServletRequest request) {
        try {
            if (record != null && record.getPatientId() != null) {
                assertPatientAccess(request, record.getPatientId());
            } else if (record != null && record.getInHospitalNo() != null) {
                assertPatientAccessByNo(request, record.getInHospitalNo());
            } else if (record != null && record.getId() != null) {
                assertRecordAccess(request, record.getId());
            }
            SepsisBundleRecord updated = sepsisBundleService.updateBundle(record);
            return Result.ok(updated);
        } catch (Exception e) {
            log.error("更新脓毒症集束化治疗记录失败", e);
            return Result.fail("更新失败: " + e.getMessage());
        }
    }

    /**
     * 根据住院号查询记录
     */
    @GetMapping("/record")
    public Result<SepsisBundleRecord> getRecord(@RequestParam String inHospitalNo,
                                                HttpServletRequest request) {
        try {
            assertPatientAccessByNo(request, inHospitalNo);
            SepsisBundleRecord record = sepsisBundleService.getByInHospitalNo(inHospitalNo);
            return Result.ok(record);
        } catch (Exception e) {
            log.error("查询脓毒症集束化治疗记录失败: inHospitalNo={}", inHospitalNo, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /**
     * 删除评估记录（逻辑删除，支持多次评估逐条删除）
     */
    @PostMapping("/delete")
    public Result<Boolean> deleteBundle(@RequestParam Long id, HttpServletRequest request) {
        try {
            assertRecordAccess(request, id);
            boolean ok = sepsisBundleService.deleteById(id);
            if (!ok) {
                return Result.fail("记录不存在");
            }
            return Result.ok(true);
        } catch (Exception e) {
            log.error("删除脓毒症集束化治疗记录失败: id={}", id, e);
            return Result.fail("删除失败: " + e.getMessage());
        }
    }
}
