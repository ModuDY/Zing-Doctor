package com.zing.doctor.module.sepsis.dto;

import lombok.Data;

import java.util.List;

/**
 * 脓毒症集束化治疗总览视图
 */
@Data
public class SepsisBundleView {

    private Long id;

    private String patientId;

    private String inHospitalNo;

    private String patientName;

    private String departCode;

    private String departName;

    private String bedNo;

    private String gender;

    private String age;

    /** 休克类型：感染性休克、脓毒性休克、脓毒症 */
    private String shockType;

    private String diagnosisTime;

    private String inDepartTime;

    private String outDepartTime;

    /** 评估次数 */
    private Integer assessCount;

    private Integer bundle1hCompleted;

    private Integer bundle3hCompleted;

    private Integer bundle6hCompleted;

    /** 1H完成项目 */
    private BundleItem bundle1h;

    /** 3H完成项目 */
    private BundleItem bundle3h;

    /** 6H完成项目 */
    private BundleItem bundle6h;

    /** 感染部位 */
    private String infectionSite;

    /** 致病菌 */
    private String pathogen;

    /** 抗生素 */
    private String antibiotic;

    /** 液体复苏未达30ml/kg原因 */
    private FluidReason fluidReason;

    @Data
    public static class BundleItem {
        /** 测量乳酸水平 */
        private Boolean lactateMeasured;
        private String lactateValue;
        private String lactateTime;

        /** 初始>2.0mmol/L需动态监测 */
        private Boolean lactateMonitor;
        private Integer lactateMonitorCount;

        /** 应用抗菌药物前获取血培养 */
        private Boolean bloodCultureBeforeAntibiotic;
        private String bloodCultureTime;
        private String antibioticStartTime;

        /** 应用广谱抗菌药物 */
        private Boolean broadSpectrumAntibiotic;
        private String antibioticName;

        /** 低血压或乳酸≥4时按30ml/kg晶体液 */
        private Boolean fluidResuscitation;
        private Double fluidAmount;
        private Double fluidTarget;
        private Double weight;
        private Boolean hypotensionOrLactateHigh;

        /** 液体复苏后需去甲肾上腺素维持MAP>65 */
        private Boolean norepinephrine;
        private String norepinephrineDose;

        /** 3小时后评估数据 */
        private String cvp;
        private String map;
        private String norepiDose;
        private String lactate3h;
        private String scvo2;
        private String urineOutput;

        /** 6H项目 */
        private Boolean vasopressor;
        private Boolean reassessVolume;
        private Boolean repeatLactate;

        /** 6H内最新乳酸值（重复测量明细） */
        private String lactate6h;
    }

    @Data
    public static class FluidReason {
        private Boolean volumeOverload;
        private Boolean organInjury;
        private Boolean capillaryLeak;
        private String other;
    }
}
