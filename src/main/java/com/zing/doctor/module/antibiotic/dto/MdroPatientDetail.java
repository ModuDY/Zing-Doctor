package com.zing.doctor.module.antibiotic.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 第四维度：细菌培养检出患者明细 DTO。
 */
@Data
public class MdroPatientDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 患者ID */
    private String patientId;

    /** 住院号 */
    private String inHospitalNo;

    /** 患者姓名 */
    private String name;

    /** 性别 */
    private String gender;

    /** 年龄 */
    private String age;

    /** 科室 */
    private String department;

    /** 床位 */
    private String bedNo;

    /** 入科时间 */
    private String inDepartTime;

    /** 出科时间 */
    private String outDepartTime;

    /** 是否在科：1在科0已出科 */
    private Integer isInDepart;

    /** 在科天数 */
    private Integer inDepartDays;

    /** 细菌培养送检次数 */
    private Integer cultureCount;

    /** 细菌培养阳性次数 */
    private Integer positiveCount;

    /** 阳性率 */
    private Double positiveRate;

    /** 检出细菌种类数 */
    private Integer bacteriaSpeciesCount;

    /** 是否检出高风险细菌：1是0否 */
    private Integer hasHighRiskBacteria;

    /** 高风险细菌名称列表（逗号分隔） */
    private String highRiskBacteriaNames;

    /** 首次阳性检出时间 */
    private String firstPositiveTime;

    /** 最近阳性检出时间 */
    private String lastPositiveTime;

    /** 检出细菌明细列表 */
    private List<BacteriaDetectItem> bacteriaList;

    /**
     * 细菌检出明细项
     */
    @Data
    public static class BacteriaDetectItem implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 细菌名称 */
        private String bacteriaName;

        /** 细菌分类 */
        private String bacteriaClass;

        /** 细菌分类名称 */
        private String bacteriaClassName;

        /** 是否高风险细菌 */
        private Integer isHighRisk;

        /** 检出次数 */
        private Integer detectCount;

        /** 标本类型列表（逗号分隔） */
        private String specimenTypes;

        /** 首次检出时间 */
        private String firstDetectTime;

        /** 最近检出时间 */
        private String lastDetectTime;
    }
}
