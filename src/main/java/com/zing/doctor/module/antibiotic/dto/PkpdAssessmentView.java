package com.zing.doctor.module.antibiotic.dto;

import com.zing.doctor.icu.dto.IcuPatientBrief;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 第二维度：PK/PD 抗菌药物剂量优化完整视图。
 */
@Data
public class PkpdAssessmentView {

    /** 患者基本信息 */
    private IcuPatientBrief patient;

    /** 肾功能评估 */
    private RenalAssessment renal;

    /** 营养状态 */
    private NutritionAssessment nutrition;

    /** 肝功能评估（简化版） */
    private LiverAssessment liver;

    /** 特殊状态标记 */
    private SpecialStatus specialStatus;

    /** 当前抗菌药物列表（结构化，同第一维度） */
    private List<com.zing.doctor.icu.dto.AbxCurrentItem> currentAbx;

    /** 各药物 PK/PD 分析 */
    private List<PkpdDrugAnalysis> drugAnalysis;

    /** 剂量优化建议 */
    private List<DoseRecommendation> recommendations;

    /** TDM 目标值（仅当前在用药物中需要 TDM 的） */
    private List<TdmTarget> tdmTargets;

    /** 药物相互作用提醒（简化版，先做肾毒性叠加） */
    private List<String> interactionAlerts;

    @Data
    public static class NutritionAssessment {
        /** 体重 kg */
        private BigDecimal weight;
        /** 身高 cm */
        private BigDecimal height;
        /** BMI */
        private BigDecimal bmi;
        /** BMI 分级：偏瘦/正常/超重/肥胖/重度肥胖 */
        private String bmiCategory;
        /** 理想体重 IBW（kg） */
        private BigDecimal ibw;
        /** 调整体重 AdjBW（kg，肥胖患者用） */
        private BigDecimal adjBw;
        /** 是否肥胖（实际体重 > 1.3×IBW） */
        private boolean obese;
    }

    @Data
    public static class LiverAssessment {
        /** 总胆红素 */
        private BigDecimal totalBilirubin;
        /** 直接胆红素 */
        private BigDecimal directBilirubin;
        /** 白蛋白 */
        private BigDecimal albumin;
        /** INR */
        private BigDecimal inr;
        /** 是否肝功能异常（胆红素>34.2 或 ALT/AST>3倍上限） */
        private boolean abnormal;
        /** 肝功能异常描述 */
        private String abnormalText;
    }

    @Data
    public static class SpecialStatus {
        /** 是否低蛋白血症（白蛋白<25g/L） */
        private boolean hypoalbuminemia;
        /** 白蛋白值 */
        private BigDecimal albumin;
        /** 是否 CRRT（持续肾脏替代治疗） */
        private boolean crrt;
        /** CRRT 开始时间 */
        private String crrtStartTime;
        /** CRRT 方案 */
        private String crrtPlan;
        /** CRRT 血流速度 */
        private String crrtBloodFlow;
        /** CRRT 置换液流速 */
        private String crrtExchangeRate;
        /** CRRT 透析液流速 */
        private String crrtDialysisRate;
        /** 是否 ECMO */
        private boolean ecmo;
        /** ECMO 开始时间 */
        private String ecmoStartTime;
        /** ECMO 辅助模式 */
        private String ecmoMode;
        /** 特殊状态标记列表（用于前端展示标签） */
        private List<String> flags;
    }
}
