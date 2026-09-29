package com.zing.doctor.module.antibiotic.service;

import com.zing.doctor.module.antibiotic.dto.PkpdAssessmentView;

/**
 * 第二维度：PK/PD 抗菌药物剂量优化 Service。
 */
public interface PkpdService {

    /**
     * 获取患者 PK/PD 剂量优化完整评估（按 patientId）。
     */
    PkpdAssessmentView getPkpdAssessment(String patientId);

    /**
     * 获取患者 PK/PD 剂量优化完整评估（按 ICU 外链住院号 inHospitalNo）。
     */
    PkpdAssessmentView getPkpdAssessmentByInHospitalNo(String inHospitalNo);
}
