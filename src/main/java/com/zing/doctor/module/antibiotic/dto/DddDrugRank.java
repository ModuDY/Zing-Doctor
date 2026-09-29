package com.zing.doctor.module.antibiotic.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 第三维度：抗菌药物消耗排名（TOP20）。
 */
@Data
public class DddDrugRank implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 排名 */
    private Integer rank;

    /** 药品名称 */
    private String drugName;

    /** 药物分类 */
    private String drugClass;

    /** 管理级别 */
    private String manageLevel;

    /** DDD 值 */
    private BigDecimal dddValue;

    /** DDD 单位 */
    private String dddUnit;

    /** 总消耗量（g） */
    private BigDecimal totalDose;

    /** 总 DDDs */
    private BigDecimal totalDdds;

    /** 占总 DDDs 比例（%） */
    private BigDecimal ratio;

    /** 使用患者数 */
    private Integer patientCount;

    /** 医嘱条数 */
    private Integer adviceCount;
}
