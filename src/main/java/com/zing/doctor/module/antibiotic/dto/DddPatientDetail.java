package com.zing.doctor.module.antibiotic.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 第三维度：抗菌药物使用患者明细。
 */
@Data
public class DddPatientDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 住院号 */
    private String inHospitalNo;

    /** 患者ID */
    private String patientId;

    /** 姓名 */
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

    /** 住院天数 */
    private BigDecimal stayDays;

    /** 使用抗菌药物种数 */
    private Integer drugKindCount;

    /** 总 DDDs */
    private BigDecimal totalDdds;

    /** 主要用药（前3种，逗号分隔） */
    private String mainDrugs;

    /** 是否在科 */
    private Boolean inDepart;

    /** 抗菌药物使用明细 */
    private List<DrugUsageItem> drugUsages;

    @Data
    public static class DrugUsageItem implements Serializable {
        private String drugName;        // 药品名称
        private String drugClass;       // 药物分类
        private String manageLevel;     // 管理级别
        private String freq;            // 频次
        private String method;          // 给药方式
        private String startTime;       // 开始时间
        private String endTime;         // 结束时间
        private BigDecimal useDays;     // 使用天数
        private BigDecimal singleDose;  // 单次剂量（g）
        private BigDecimal totalDose;   // 总剂量（g）
        private BigDecimal dddValue;    // DDD值
        private BigDecimal ddds;        // DDDs
        private String openStaffName;   // 开立医生
    }
}
