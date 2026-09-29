package com.zing.doctor.module.antibiotic.dto;

import lombok.Data;

import java.util.List;

/**
 * 单个药物的剂量优化建议。
 */
@Data
public class DoseRecommendation {

    /** 药物名称 */
    private String drugName;

    /** 推荐单次剂量 */
    private String recommendedDose;

    /** 推荐给药间隔 */
    private String recommendedInterval;

    /** 推荐输注方式 */
    private String recommendedInfusion;

    /** 调整依据（为什么这么调） */
    private String adjustmentReason;

    /** 证据来源（说明书/指南） */
    private String evidenceSource;

    /** 肾功能剂量调整表（按肾功能分级列出剂量） */
    private List<RenalDoseRow> renalDoseTable;

    /** 是否需要调整（与当前方案对比） */
    private boolean needAdjustment;

    /** 调整类型：renal / weight / hypoalbuminemia / loading / extended_infusion / crrt / none */
    private String adjustmentType;

    @Data
    public static class RenalDoseRow {
        /** 肾功能分级 */
        private String stage;
        /** 分级描述 */
        private String stageText;
        /** 肌酐清除率范围 */
        private String crclRange;
        /** 推荐剂量 */
        private String dose;
    }
}
