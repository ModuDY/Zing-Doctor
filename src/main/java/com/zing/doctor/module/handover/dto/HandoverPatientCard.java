package com.zing.doctor.module.handover.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 交班览表单患者卡片（科室级批量聚合结果）。
 */
@Data
public class HandoverPatientCard {

    // ---------- 患者基础信息（patient_info） ----------
    private String patientId;
    private String inHospitalNo;
    private String name;
    private String gender;
    private String age;
    private String ageUnit;
    private String bedCode;
    private String inDepartTime;
    private String outDepartTime;
    private String diagnosisContent;
    private String allergyContent;
    private String chargeDoctorName;
    private String residentDoctorName;
    private String weight;
    private String wardName;
    private String departCode;

    // ---------- 最新生命体征（截至当前，每类取最新一条） ----------
    /** 体温 ℃ */
    private String temp;
    /** 心率 次/分 */
    private String hr;
    /** 呼吸频率 */
    private String rr;
    /** 血氧饱和度 % */
    private String spo2;
    /** 收缩压（有创优先） */
    private String sbp;
    /** 舒张压（有创优先） */
    private String dbp;
    /** 班内最高体温，用于发热判定 */
    private Double maxTemp;
    /** 体征数据时间（最新一条） */
    private String vitalTime;

    // ---------- 封板班次出入量合计（mL） ----------
    private Double intakeTotal;
    private Double outputTotal;
    private Double urineTotal;
    /** 是否有导尿管（尿量显示为 "xxx/C"） */
    private boolean urineCatheter;
    private Double balanceTotal;

    // ---------- 器官支持 / 风险标记 ----------
    /** 呼吸机（ventilator_code 非空） */
    private boolean ventilator;
    /** CRRT（crrt_device_code 非空） */
    private boolean crrt;
    /** ECMO（ecmo_device_code 非空） */
    private boolean ecmo;
    /** 隔离（isolation_status 非空且非0） */
    private boolean isolation;
    private String isolationValue;
    /** 脓毒性休克标记 */
    private boolean sepsisShock;
    /** ARDS 标记 */
    private boolean ards;
    /** 本班新入科 */
    private boolean newIn;
    /** 班内发热（最高体温≥38.5） */
    private boolean fever;
    /** 在用升压药名称（去重） */
    private List<String> vasopressors = new ArrayList<>();

    // ---------- 本班检验异常 ----------
    private int abnormalLabCount;
    /** 卡片只展示前 3 个异常项目名 */
    private List<String> abnormalLabNames = new ArrayList<>();

    // ---------- 手工交班（病情变化） ----------
    private Long noteId;
    private String conditionChange;
    private String noteCreateBy;
    private String noteCreateTime;
}
