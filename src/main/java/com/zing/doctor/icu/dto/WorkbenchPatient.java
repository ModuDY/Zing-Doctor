package com.zing.doctor.icu.dto;

import lombok.Data;

import java.time.LocalDateTime;

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
}
