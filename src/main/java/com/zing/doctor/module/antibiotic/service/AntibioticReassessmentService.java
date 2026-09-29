package com.zing.doctor.module.antibiotic.service;

import com.zing.doctor.module.antibiotic.dto.AntibioticReassessmentRequest;
import com.zing.doctor.module.antibiotic.entity.AntibioticReassessment;

import java.util.List;

/** 抗感染 48～72 小时复评任务服务。 */
public interface AntibioticReassessmentService {

    /** 查询患者的全部复评记录，按计划复评时间倒序。 */
    List<AntibioticReassessment> listByPatient(String patientId);

    /** 查询单条复评任务，供接口层做患者科室权限校验。 */
    AntibioticReassessment getById(Long id);

    /** 查询指定科室当前待复评任务；科室边界由 ICU 在科患者集合确定。 */
    List<AntibioticReassessment> listPending(String departCode);

    /** 保存复评结果并将任务置为 COMPLETED。 */
    AntibioticReassessment complete(AntibioticReassessmentRequest request);

    /** 跳过复评任务并保留原因。 */
    AntibioticReassessment skip(AntibioticReassessmentRequest request);

    /** 为一次新建的抗感染决策创建待复评任务。 */
    AntibioticReassessment createPending(Long decisionRecordId, String patientId,
                                          String patientNo, String inHospitalNo,
                                          String departCode);

    /** 决策记录作废时同步作废其尚未完成的复评任务。 */
    void voidPendingByDecision(Long decisionRecordId);
}
