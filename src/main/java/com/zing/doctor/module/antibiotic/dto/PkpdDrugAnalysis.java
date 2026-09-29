package com.zing.doctor.module.antibiotic.dto;

import lombok.Data;

/**
 * 单个抗菌药物的 PK/PD 分析结果。
 */
@Data
public class PkpdDrugAnalysis {

    /** 药物名称 */
    private String drugName;

    /** PK/PD 分类：TIME_DEPENDENT / CONCENTRATION_DEPENDENT / TIME_DEPENDENT_LONG_PAE */
    private String pkpdType;

    /** PK/PD 分类中文 */
    private String pkpdTypeText;

    /** 目标参数：%T>MIC / Cmax/MIC / AUC/MIC */
    private String targetParam;

    /** 目标值 */
    private String targetValue;

    /** 蛋白结合率（%） */
    private Integer proteinBinding;

    /** 是否高蛋白结合率（>80%） */
    private boolean highProteinBinding;

    /** 清除途径：renal / hepatic / dual */
    private String clearanceRoute;

    /** 清除途径中文 */
    private String clearanceRouteText;

    /** 当前单次剂量 */
    private String currentDose;

    /** 当前给药间隔 */
    private String currentInterval;

    /** 当前输注方式 */
    private String infusionMethod;

    /** 开始时间 */
    private String startTime;

    /** 执行状态：running / finished / pending */
    private String statusCode;

    /** 执行状态中文 */
    private String statusText;

    /** 是否需要 TDM */
    private boolean tdmRequired;

    /** 备注 */
    private String remark;

    /** 是否匹配到知识库（未匹配时为 false，仅展示医嘱信息） */
    private boolean knowledgeMatched;
}
