package com.zing.doctor.icu.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * 感染类型的判定依据（给医生的"为什么是这个类"）。
     *
     * <p>列表页里感染类型是一个标签，医生看到标签后第一反应是"凭什么这么判"。
     * 没有依据的话，医生要么不信、要么得点进决策页自己翻诊断；
     * 这里把命中的诊断原文与时间一并带回，标签才站得住。
     */
    private String infectionEvidence;

    /** 入科时间（列表展示"住了几天"，也是默认排序的次要键） */
    private LocalDateTime inDepartTime;

    /** 当前在用抗菌药名称（已排除溶媒，按 group 去重） */
    private List<String> currentAbx = new ArrayList<>();

    /** 最近一次抗感染决策状态：pending / accepted / declined；null 表示从未决策 */
    private String decisionStatus;

    /** 最近一次决策时间（判断"今日是否已决策"） */
    private LocalDateTime decisionTime;

    /** 最近一次决策医生 */
    private String decisionDoctor;

    /**
     * 是否"待决策"（口径由参数 ABX_PENDING_DECISION_RULE 决定）。
     *
     * <p>放在 DTO 里而不是前端算：口径要全院统一，前端各算各的会出现
     * 「同一份列表，两个医生看到的人数不一样」。
     */
    private Boolean pendingDecision;

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
