package com.zing.doctor.module.antibiotic.dto;

import lombok.Data;

import java.io.Serializable;

/** 保存/跳过抗感染复评结果的请求模型。 */
@Data
public class AntibioticReassessmentRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String cultureSummary;
    private String clinicalResponse;
    private String pctTrend;
    private String decisionAction;
    private String doctorDecision;
    private String doctorId;
    private String doctorName;
    private String remark;
}
