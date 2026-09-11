package com.zing.doctor.module.antibiotic.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 第四维度：细菌培养检出菌株排名 DTO。
 */
@Data
public class MdroBacteriaRank implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 排名 */
    private Integer rank;

    /** 细菌名称 */
    private String bacteriaName;

    /** 检出次数 */
    private Integer detectCount;

    /** 检出患者数 */
    private Integer patientCount;

    /** 占比（检出次数/总阳性次数） */
    private Double percentage;

    /** 细菌分类：gram_positive革兰阳性, gram_negative革兰阴性, fungi真菌, other其他 */
    private String bacteriaClass;

    /** 细菌分类名称 */
    private String bacteriaClassName;

    /** 是否高风险细菌：1是0否 */
    private Integer isHighRisk;

    /** 最常见标本类型 */
    private String topSpecimenType;

    /** 首次检出时间 */
    private String firstDetectTime;

    /** 最近检出时间 */
    private String lastDetectTime;
}
