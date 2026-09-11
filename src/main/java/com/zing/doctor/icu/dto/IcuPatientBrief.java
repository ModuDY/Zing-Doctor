package com.zing.doctor.icu.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ICU 疑似感染患者摘要（第一维度列表用）。
 * 字段对应 ICU 系统 zing_icu_db_prod 中患者/医嘱/检验数据，表结构提供后由 SQL 实现映射。
 */
@Data
public class IcuPatientBrief implements Serializable {

    private static final long serialVersionUID = 1L;

    /** ICU 患者 ID */
    private String patientId;

    /** 住院号/就诊号 */
    private String patientNo;

    /** 姓名（脱敏展示） */
    private String name;

    private Integer age;

    private String gender;

    private String department;

    private String bedNo;

    /** 体重 kg（第二维度 PK/PD 剂量计算用） */
    private BigDecimal weight;

    /** 身高 cm（第二维度 BMI/IBW 计算用） */
    private BigDecimal height;

    /** 疑似感染类型/部位，如 肺炎、腹腔感染、血流感染 */
    private String infectionType;

    /** 是否脓毒性休克（保留布尔兼容，true=任何休克类型） */
    private Boolean septicShock;

    /** 休克类型：septic=脓毒性休克 / infectious=感染性休克 / none=非休克 */
    private String shockType = "none";

    /** MRSA 高风险 */
    private Boolean mrsaRisk;

    /** MDR 高风险 */
    private Boolean mdrRisk;

    /** 真菌高风险 */
    private Boolean fungalRisk;

    /** 降钙素原 ng/mL */
    private BigDecimal pct;

    /** 白细胞 ×10⁹/L */
    private BigDecimal wbc;

    /** 体温 ℃ */
    private BigDecimal temperature;

    /** 最近一次抗菌药物开始时间（用于判断经验性用药窗口） */
    private LocalDateTime abxStartTime;

    /** 风险分层：高风险 / 中风险 / 低风险 */
    private String riskLevel;
}
