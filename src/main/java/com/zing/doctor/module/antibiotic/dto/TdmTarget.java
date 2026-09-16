package com.zing.doctor.module.antibiotic.dto;

import lombok.Data;

/**
 * TDM（治疗药物监测）目标值。
 */
@Data
public class TdmTarget {

    /** 药物名称 */
    private String drugName;

    /** 监测指标：谷浓度 / 峰浓度 / AUC/MIC */
    private String monitorParam;

    /** 目标值范围 */
    private String targetRange;

    /** 严重感染目标值 */
    private String severeTarget;

    /** 一般感染目标值 */
    private String generalTarget;

    /** 监测时机（第几剂前采样） */
    private String monitorTiming;

    /** 采样时间说明 */
    private String samplingNote;

    /** 毒性阈值（超过此值毒性增加） */
    private String toxicityThreshold;

    /** 备注 */
    private String remark;
}
