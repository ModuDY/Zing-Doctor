package com.zing.doctor.module.antibiotic.controller;

import com.zing.doctor.common.BizException;
import com.zing.doctor.common.Result;
import com.zing.doctor.icu.dto.IcuPatientBrief;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.module.antibiotic.dto.PatientAssessmentView;
import com.zing.doctor.module.antibiotic.dto.PkpdAssessmentView;
import com.zing.doctor.module.antibiotic.entity.DecisionRecord;
import com.zing.doctor.module.antibiotic.service.AntibioticDecisionService;
import com.zing.doctor.module.antibiotic.service.PkpdService;
import com.zing.doctor.module.antibiotic.service.impl.AntibioticDecisionServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 第一维度：经验性抗感染治疗决策 API。
 * 所有 /api 请求需携带外链签名请求头（X-External-PageCode/X-External-Expire/X-External-Sign）。
 */
@RestController
@RequestMapping("/api/antibiotic")
@RequiredArgsConstructor
public class AntibioticController {

    private final AntibioticDecisionService decisionService;
    private final IcuPatientService icuPatientService;
    private final PkpdService pkpdService;

    /** 疑似感染/脓毒症患者列表 */
    @GetMapping("/patients")
    public Result<List<IcuPatientBrief>> patients() {
        return Result.ok(decisionService.listSuspectPatients());
    }

    /** 职工字典搜索（医生下拉框用）：支持拼音首字母/工号/姓名模糊匹配 */
    @GetMapping("/staff/search")
    public Result<List<Map<String, Object>>> staffSearch(@RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(icuPatientService.searchStaff(keyword));
    }

    /** 单患者决策页：评估数据 + 推荐方案（按 ICU 内部 patientId） */
    @GetMapping("/patients/{patientId}/assessment")
    public Result<PatientAssessmentView> assessment(@PathVariable("patientId") String patientId) {
        return Result.ok(decisionService.getAssessmentView(patientId));
    }

    /** 单患者决策页：评估数据 + 推荐方案（按 ICU 外链住院号 inHospitalNo 定位患者） */
    @GetMapping("/patients/by-no/assessment")
    public Result<PatientAssessmentView> assessmentByNo(@RequestParam("inHospitalNo") String inHospitalNo) {
        String patientId = icuPatientService.resolvePatientIdByInHospitalNo(inHospitalNo);
        if (patientId == null) {
            throw new BizException(404, "未找到住院号对应的在科患者：" + inHospitalNo);
        }
        return Result.ok(decisionService.getAssessmentView(patientId));
    }

    /** 保存医生决策记录 */
    @PostMapping("/decision-record")
    public Result<Long> saveDecision(@RequestParam("patientId") String patientId,
                                     @RequestParam(value = "doctorDecision", required = false) String doctorDecision,
                                     @RequestParam(value = "decisionStatus", defaultValue = "accepted") String decisionStatus,
                                     @RequestParam(value = "doctorId", required = false) String doctorId,
                                     @RequestParam(value = "doctorName", required = false) String doctorName) {
        return Result.ok(decisionService.saveDecision(
                patientId, doctorDecision, decisionStatus, doctorId, doctorName));
    }

    /** 更新医生决策记录（编辑历史决策） */
    @PostMapping("/decision-record/update")
    public Result<Long> updateDecision(@RequestParam("id") Long id,
                                       @RequestParam(value = "doctorDecision", required = false) String doctorDecision,
                                       @RequestParam(value = "decisionStatus", defaultValue = "accepted") String decisionStatus,
                                       @RequestParam(value = "doctorId", required = false) String doctorId,
                                       @RequestParam(value = "doctorName", required = false) String doctorName) {
        return Result.ok(decisionService.updateDecisionRecord(
                id, doctorDecision, decisionStatus, doctorId, doctorName));
    }

    /** 删除医生决策记录 */
    @PostMapping("/decision-record/delete")
    public Result<Void> deleteDecision(@RequestParam("id") Long id) {
        decisionService.deleteDecisionRecord(id);
        return Result.ok();
    }

    /** 患者决策历史 */
    @GetMapping("/patients/{patientId}/records")
    public Result<List<DecisionRecord>> records(@PathVariable("patientId") String patientId) {
        if (decisionService instanceof AntibioticDecisionServiceImpl) {
            return Result.ok(((AntibioticDecisionServiceImpl) decisionService).listByPatient(patientId));
        }
        return Result.ok(java.util.Collections.emptyList());
    }

    // ==================== 第二维度：PK/PD 剂量优化 ====================

    /** 第二维度：PK/PD 抗菌药物剂量优化（按 patientId） */
    @GetMapping("/patients/{patientId}/pkpd")
    public Result<PkpdAssessmentView> pkpd(@PathVariable("patientId") String patientId) {
        return Result.ok(pkpdService.getPkpdAssessment(patientId));
    }

    /** 第二维度：PK/PD 抗菌药物剂量优化（按 ICU 外链住院号 inHospitalNo） */
    @GetMapping("/patients/by-no/pkpd")
    public Result<PkpdAssessmentView> pkpdByNo(@RequestParam("inHospitalNo") String inHospitalNo) {
        return Result.ok(pkpdService.getPkpdAssessmentByInHospitalNo(inHospitalNo));
    }
}
