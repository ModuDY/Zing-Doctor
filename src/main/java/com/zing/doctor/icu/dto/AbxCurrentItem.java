package com.zing.doctor.icu.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 当前抗菌药物条目（决策页「当前抗菌药物」块用）。
 * 来源：patient_advice_pda JOIN patient_advice（按 group_id_mark + in_hospital_serial_no）。
 */
@Data
public class AbxCurrentItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 药品名称（patient_advice.name，纯溶质，已排除溶媒） */
    private String name;

    /** 给药方式（patient_advice.drug_method_name，如 静脉推注/静脉输液/微泵推注） */
    private String method;

    /** 频次（patient_advice.freq_name，如 bid/q8h/qd） */
    private String freq;

    /** 开始时间（优先 pda_advice_start_time，其次 pda_advice_plan_time，最后 plan_start_time；已格式化为 MM-dd HH:mm） */
    private String startTime;

    /** 执行状态：running-执行中 / finished-完成 / pending-待执行 */
    private String statusCode;

    /** 执行状态中文描述 */
    private String statusText;
}
