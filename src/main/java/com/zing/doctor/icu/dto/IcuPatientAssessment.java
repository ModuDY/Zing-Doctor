package com.zing.doctor.icu.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ICU 单患者抗感染决策评估数据（第一维度决策页用）。
 * P0 由 Mock 提供；ICU 表结构提供后由 SQL 实现从 zing_icu_db_prod 组装。
 */
@Data
public class IcuPatientAssessment implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 患者基本信息（复用摘要结构） */
    private IcuPatientBrief patient;

    /** 过敏史（如 青霉素过敏） */
    private List<String> allergies = new ArrayList<>();

    /** 最近 90 天既往培养/药敏结论（如 "MRSA（鼻腔拭子）"） */
    private List<String> pastCultures = new ArrayList<>();

    /** 当前正在使用的抗菌药物（patient_advice_pda JOIN patient_advice，带执行状态） */
    private List<AbxCurrentItem> currentAntibiotics = new ArrayList<>();

    /** 关键检验指标：key 为指标名，value 为结果描述 */
    private Map<String, String> labs;

    /** 关键检验指标趋势（按时间升序，供趋势图展示；可能为空） */
    private List<LabTrend> labTrends = new ArrayList<>();

    /** 最近肌酐值（μmol/L，用于肾功能/剂量） */
    private String creatinine;

    /** 体重（kg） */
    private String weight;

    /** 备注/待补信息 */
    private String remark;
}
