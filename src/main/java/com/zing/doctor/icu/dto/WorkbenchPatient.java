package com.zing.doctor.icu.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 患者工作台列表的最小展示模型；住院号和姓名均脱敏。 */
@Data
public class WorkbenchPatient {
    private String patientId;
    private String patientNo;
    private String name;
    private Integer age;
    private String gender;
    private String department;
    private String bedNo;
    private LocalDateTime inDepartmentTime;
    private Long icuDays;
}
