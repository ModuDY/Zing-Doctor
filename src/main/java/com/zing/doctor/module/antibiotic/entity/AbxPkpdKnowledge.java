package com.zing.doctor.module.antibiotic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * PK/PD 抗菌药物知识库（界面化配置，优先于内置枚举）。
 *
 * <p>匹配逻辑见 {@code AbxDrugKnowledge#match}：优先查本表 status=1 记录，
 * 未命中再 fallback 到内置枚举。加新药无需改代码。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"config_abx_pkpd_knowledge\"")
public class AbxPkpdKnowledge implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 药品通用名（匹配关键词，如 头孢呋辛） */
    private String drugName;

    /** 药品全称（展示用，如 注射用头孢呋辛钠） */
    private String drugFullName;

    /** PK/PD类型：TIME_DEPENDENT / CONCENTRATION_DEPENDENT / TIME_DEPENDENT_LONG_PAE */
    private String pkpdType;

    /** 目标参数：%T>MIC / Cmax/MIC / AUC/MIC */
    private String targetParam;

    /** 目标值，如 ≥50-70% */
    private String targetValue;

    /** 蛋白结合率(%) */
    private Integer proteinBinding;

    /** 清除途径：renal / hepatic / dual */
    private String clearanceRoute;

    /** 常用剂量 */
    private String usualDose;

    /** 剂量调整说明 */
    private String doseAdjust;

    /** 高蛋白结合率（≥80%）：0否 1是 */
    private Integer highProteinBinding;

    /** 需要TDM监测：0否 1是 */
    private Integer tdmRequired;

    /** 备注 */
    private String remark;

    /** 状态：1启用 0停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
