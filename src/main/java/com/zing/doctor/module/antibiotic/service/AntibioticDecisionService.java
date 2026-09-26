package com.zing.doctor.module.antibiotic.service;

import com.zing.doctor.icu.dto.IcuPatientBrief;
import com.zing.doctor.module.antibiotic.dto.AdviceItem;
import com.zing.doctor.module.antibiotic.dto.PatientAssessmentView;

import java.util.List;

/**
 * 第一维度：经验性抗感染治疗决策服务。
 */
public interface AntibioticDecisionService {

    /**
     * 疑似感染/脓毒症患者列表。
     *
     * @param departCode 已通过科室授权校验的科室编码（{@code sys_depart.org_code}）；
     *                   管理员 / 外链可为 null 或 "ALL"（不限科室）。
     */
    List<IcuPatientBrief> listSuspectPatients(String departCode);

    /** 单患者决策页视图（评估数据 + 推荐方案） */
    PatientAssessmentView getAssessmentView(String patientId);

    /**
     * 生成经验性抗感染方案（规则引擎，基于 SSC 2021 分层思路）。
     * P0 为可解释规则；后续可扩展为可配置规则库/ML 模型。
     */
    List<AdviceItem> generateAdvice(IcuPatientBrief patient);

    /**
     * 保存医生决策记录（含推荐明细）。
     *
     * @return 决策记录 ID
     */
    Long saveDecision(String patientId, String doctorDecision, String decisionStatus,
                      String doctorId, String doctorName);

    /**
     * 更新医生决策记录（只更新医生决策/状态/医生，不覆盖评估快照）。
     *
     * @return 决策记录 ID
     */
    Long updateDecisionRecord(Long id, String doctorDecision, String decisionStatus,
                              String doctorId, String doctorName);

    /** 删除医生决策记录（物理删除，级联删除推荐明细） */
    void deleteDecisionRecord(Long id);
}
