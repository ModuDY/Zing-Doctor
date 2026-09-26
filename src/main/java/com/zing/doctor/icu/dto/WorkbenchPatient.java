package com.zing.doctor.icu.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 患者工作台列表的最小展示模型；姓名脱敏，住院号保留前后段用于人工核对。
 *
 * <p>标识口径（三个字段各司其职，不要互相替代）：
 * <ul>
 *   <li>{@code patientId} —— patient_info.id，库内唯一，本系统内部跳转用它。</li>
 *   <li>{@code inHospitalNo} —— 住院号原文。跨模块聚合（待办、评分、培养等）
 *       都以住院号关联（那些表的关联列是 in_hospital_no），所以这里必须给原文。</li>
 *   <li>{@code patientNo} —— 住院号的展示用脱敏值，仅用于页面呈现。</li>
 * </ul>
 *
 * <p>科室口径：{@code departCode} 是 sys_depart.org_code，与外链 / 质控 / DDD 同一套编码，
 * 是筛选与聚合的唯一维度；{@code wardName} 只是病区名，供医生辨认，不参与任何匹配。
 *
 * <p>危重标签（ventilated / onVasopressor / onCrrt）来自 ICU 库批量查询，
 * 不是逐患者 N+1；待办（todos）来自本系统评分记录的当日批量比对。
 */
@Data
public class WorkbenchPatient {
    private String patientId;
    private String inHospitalNo;
    private String patientNo;
    private String name;
    private Integer age;
    private String gender;
    /** sys_depart.org_code，与外链 / 质控同一体系 */
    private String departCode;
    /** 病区名，仅展示 */
    private String wardName;
    private String bedNo;
    private LocalDateTime inDepartmentTime;
    private Long icuDays;

    // ---- 危重标签（批量查询填充，默认 false）----
    /** 机械通气（有呼吸机参数记录） */
    private Boolean ventilated = false;
    /** 正在使用血管活性药（去甲肾上腺素/多巴胺/肾上腺素等） */
    private Boolean onVasopressor = false;
    /** 正在 CRRT（连续性肾脏替代治疗） */
    private Boolean onCrrt = false;

    // ---- 评分 / 待办 ----
    /** 最近一次 SOFA 总分（0-24），未评过为 null */
    private Integer lastSofaScore;
    /**
     * 当日待办 code 列表，如 SOFA_NOT_TODAY / APACHE_NOT_TODAY。
     * 空列表表示当日无待办。
     */
    private List<String> todos = java.util.Collections.emptyList();
    /** 待办数（=todos.size()，前端直接用） */
    private Integer todoCount = 0;

    // ---- 感染维度（批量回填到全量在科患者，左连接语义：没有就是没有）----

    /**
     * 是否疑似感染（与「感染风险」视图同一集合，判定口径见 InfectionRules）。
     */
    private Boolean suspectedInfection = false;

    /**
     * 感染数据的获取状态，取值：
     * <ul>
     *   <li>{@code FOUND} —— 已查过 ICU 库，字段为空就是"确实没有"；</li>
     *   <li>{@code UNKNOWN} —— 查询失败（ICU 库不可用 / 表缺失），字段为空是"不知道"。</li>
     * </ul>
     *
     * <p><b>这两者绝不能混为一谈</b>：把"查不到"显示成"未发现感染证据"，
     * 医生会据此认为患者安全。工作台是全量患者的主视图，一旦这样显示，
     * 错的是全科而不只是感染那一列。
     */
    private String infectionDataStatus = "FOUND";

    /** 感染类型/部位（如 医院获得性肺炎（HAP/VAP）），非疑似感染时为 null */
    private String infectionType;

    /** 感染类型的判定依据（命中的诊断原文与时间），供页面解释"凭什么这么判" */
    private String infectionEvidence;

    /** 脓毒性/感染性休克 */
    private Boolean septicShock = false;

    /** 休克类型：septic / infectious / none */
    private String shockType = "none";

    /** MRSA 风险（培养或药敏提示） */
    private Boolean mrsaRisk = false;

    /** MDR 风险（耐碳青霉烯 / ESBL / 泛耐药等） */
    private Boolean mdrRisk = false;

    /** 真菌风险（念珠菌 / 曲霉等） */
    private Boolean fungalRisk = false;

    /** 降钙素原 PCT（ng/mL），无结果为 null */
    private java.math.BigDecimal pct;

    /** 白细胞计数（10^9/L），无结果为 null */
    private java.math.BigDecimal wbc;

    /** 最近体温（℃），无记录为 null */
    private java.math.BigDecimal temperature;

    /** 当前在用抗菌药的开始时间（最早一条） */
    private LocalDateTime abxStartTime;

    /** 当前在用抗菌药名称（已排除溶媒） */
    private List<String> currentAbx = java.util.Collections.emptyList();

    /** 感染风险等级：高风险 / 中风险 / 低风险；非疑似感染时为 null */
    private String infectionRiskLevel;
}
