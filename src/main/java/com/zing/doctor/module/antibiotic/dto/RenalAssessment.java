package com.zing.doctor.module.antibiotic.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 肾功能评估结果。
 */
@Data
public class RenalAssessment {

    /** 最新肌酐值 */
    private BigDecimal creatinine;

    /** 肌酐单位 */
    private String creatinineUnit;

    /** 肌酐值 mg/dL（用于计算，从 μmol/L 换算） */
    private BigDecimal creatinineMgDl;

    /** 肌酐清除率（Cockcroft-Gault，mL/min） */
    private BigDecimal crcl;

    /** eGFR（CKD-EPI，mL/min/1.73m²） */
    private BigDecimal egfr;

    /** 肾功能分级：normal / mild / moderate / severe / failure */
    private String renalStage;

    /** 肾功能分级中文描述 */
    private String renalStageText;

    /** 肌酐趋势（最近7天） */
    private List<LabTrendPoint> creatinineTrend;

    /** 计算公式说明 */
    private String formulaNote;

    @Data
    public static class LabTrendPoint {
        private String time;
        private BigDecimal value;
        private String unit;
    }
}
