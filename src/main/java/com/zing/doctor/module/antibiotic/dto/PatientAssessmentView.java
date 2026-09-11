package com.zing.doctor.module.antibiotic.dto;

import com.zing.doctor.icu.dto.IcuPatientAssessment;
import com.zing.doctor.icu.dto.IcuPatientBrief;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 决策页视图：患者评估 + 系统推荐方案。
 */
@Data
public class PatientAssessmentView implements Serializable {

    private static final long serialVersionUID = 1L;

    private IcuPatientBrief patient;

    private IcuPatientAssessment assessment;

    /** 系统推荐的方案明细 */
    private List<AdviceItem> adviceList = new ArrayList<>();

    /** 推荐方案摘要（用于落库） */
    private String planSummary;
}
