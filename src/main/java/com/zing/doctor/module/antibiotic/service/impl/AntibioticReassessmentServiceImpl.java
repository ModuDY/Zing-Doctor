package com.zing.doctor.module.antibiotic.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zing.doctor.common.BizException;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.module.antibiotic.dto.AntibioticReassessmentRequest;
import com.zing.doctor.module.antibiotic.entity.AntibioticReassessment;
import com.zing.doctor.module.antibiotic.mapper.AntibioticReassessmentMapper;
import com.zing.doctor.module.antibiotic.service.AntibioticReassessmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/** 抗感染复评任务实现。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AntibioticReassessmentServiceImpl implements AntibioticReassessmentService {

    public static final String PENDING = "PENDING";
    public static final String COMPLETED = "COMPLETED";
    public static final String SKIPPED = "SKIPPED";
    public static final String VOID = "VOID";

    private final AntibioticReassessmentMapper reassessmentMapper;
    private final IcuPatientService icuPatientService;

    @Override
    public List<AntibioticReassessment> listByPatient(String patientId) {
        if (StrUtil.isBlank(patientId)) {
            return Collections.emptyList();
        }
        return reassessmentMapper.selectList(new LambdaQueryWrapper<AntibioticReassessment>()
                .eq(AntibioticReassessment::getPatientId, patientId)
                .orderByDesc(AntibioticReassessment::getReviewDueTime)
                .orderByDesc(AntibioticReassessment::getCreateTime));
    }

    @Override
    public AntibioticReassessment getById(Long id) {
        return id == null ? null : reassessmentMapper.selectById(id);
    }

    @Override
    public List<AntibioticReassessment> listPending(String departCode) {
        // 复评任务属于已采纳决策的患者，即使 48～72 小时后感染风险规则不再命中，
        // 也不能从待办中消失；因此这里必须以全量在科患者为边界，而不是疑似感染集合。
        List<com.zing.doctor.icu.dto.WorkbenchPatient> patients = icuPatientService.listInpatients(departCode);
        if (patients == null || patients.isEmpty()) {
            return Collections.emptyList();
        }
        java.util.ArrayList<String> patientIds = new java.util.ArrayList<>();
        for (com.zing.doctor.icu.dto.WorkbenchPatient patient : patients) {
            if (patient != null && StrUtil.isNotBlank(patient.getPatientId())) {
                patientIds.add(patient.getPatientId());
            }
        }
        if (patientIds.isEmpty()) {
            return Collections.emptyList();
        }
        return reassessmentMapper.selectList(new LambdaQueryWrapper<AntibioticReassessment>()
                .in(AntibioticReassessment::getPatientId, patientIds)
                .eq(AntibioticReassessment::getReviewStatus, PENDING)
                .eq(AntibioticReassessment::getVoidFlag, 0)
                .orderByAsc(AntibioticReassessment::getReviewDueTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AntibioticReassessment complete(AntibioticReassessmentRequest request) {
        AntibioticReassessment record = getPending(request);
        if (StrUtil.isBlank(request.getDoctorName())) {
            throw new BizException(400, "请先选择复评医生");
        }
        if (StrUtil.isBlank(request.getDecisionAction())) {
            throw new BizException(400, "请选择复评动作");
        }
        copyRequest(record, request);
        record.setReviewStatus(COMPLETED);
        record.setReviewTime(LocalDateTime.now());
        record.setVoidFlag(0);
        record.setUpdateTime(LocalDateTime.now());
        reassessmentMapper.updateById(record);
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AntibioticReassessment skip(AntibioticReassessmentRequest request) {
        AntibioticReassessment record = getPending(request);
        if (StrUtil.isBlank(request.getDoctorName())) {
            throw new BizException(400, "请先选择复评医生");
        }
        if (StrUtil.isBlank(request.getRemark())) {
            throw new BizException(400, "跳过复评时请填写原因");
        }
        copyRequest(record, request);
        record.setReviewStatus(SKIPPED);
        record.setReviewTime(LocalDateTime.now());
        record.setVoidFlag(0);
        record.setUpdateTime(LocalDateTime.now());
        reassessmentMapper.updateById(record);
        return record;
    }

    @Override
    public AntibioticReassessment createPending(Long decisionRecordId, String patientId,
                                                 String patientNo, String inHospitalNo,
                                                 String departCode) {
        if (decisionRecordId == null || StrUtil.isBlank(patientId)) {
            throw new BizException(400, "缺少决策记录或患者标识，无法创建复评任务");
        }
        List<AntibioticReassessment> existingRows = reassessmentMapper.selectList(new LambdaQueryWrapper<AntibioticReassessment>()
                .eq(AntibioticReassessment::getDecisionRecordId, decisionRecordId)
                .eq(AntibioticReassessment::getReviewStatus, PENDING)
                .eq(AntibioticReassessment::getVoidFlag, 0)
                .orderByAsc(AntibioticReassessment::getCreateTime));
        if (!existingRows.isEmpty()) {
            return existingRows.get(0);
        }
        AntibioticReassessment record = new AntibioticReassessment();
        record.setDecisionRecordId(decisionRecordId);
        record.setPatientId(patientId);
        record.setPatientNo(patientNo);
        record.setInHospitalNo(inHospitalNo);
        record.setDepartCode(departCode);
        record.setReviewDueTime(LocalDateTime.now().plusHours(48));
        record.setReviewStatus(PENDING);
        record.setVoidFlag(0);
        reassessmentMapper.insert(record);
        return record;
    }

    @Override
    public void voidPendingByDecision(Long decisionRecordId) {
        if (decisionRecordId == null) {
            return;
        }
        AntibioticReassessment update = new AntibioticReassessment();
        update.setReviewStatus(VOID);
        update.setVoidFlag(1);
        update.setUpdateTime(LocalDateTime.now());
        reassessmentMapper.update(update, new LambdaQueryWrapper<AntibioticReassessment>()
                .eq(AntibioticReassessment::getDecisionRecordId, decisionRecordId)
                .eq(AntibioticReassessment::getReviewStatus, PENDING));
    }

    private AntibioticReassessment getPending(AntibioticReassessmentRequest request) {
        if (request == null || request.getId() == null) {
            throw new BizException(400, "缺少复评任务 ID");
        }
        AntibioticReassessment record = reassessmentMapper.selectById(request.getId());
        if (record == null || !PENDING.equals(record.getReviewStatus()) || Integer.valueOf(1).equals(record.getVoidFlag())) {
            throw new BizException(404, "复评任务不存在或已处理");
        }
        return record;
    }

    private void copyRequest(AntibioticReassessment record, AntibioticReassessmentRequest request) {
        record.setCultureSummary(trimToNull(request.getCultureSummary()));
        record.setClinicalResponse(trimToNull(request.getClinicalResponse()));
        record.setPctTrend(trimToNull(request.getPctTrend()));
        record.setDecisionAction(trimToNull(request.getDecisionAction()));
        record.setDoctorDecision(trimToNull(request.getDoctorDecision()));
        record.setDoctorId(trimToNull(request.getDoctorId()));
        record.setDoctorName(trimToNull(request.getDoctorName()));
        record.setRemark(trimToNull(request.getRemark()));
    }

    private String trimToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }
}
