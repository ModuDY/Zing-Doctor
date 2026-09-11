package com.zing.doctor.module.handover.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 医生交班览表 - 手工交班记录（一个患者一个封板班次一条）。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"zing_doctor_handover\"")
public class HandoverNote {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("patient_id")
    private String patientId;

    @TableField("in_hospital_no")
    private String inHospitalNo;

    @TableField("patient_name")
    private String patientName;

    @TableField("depart_code")
    private String departCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("shift_begin_time")
    private LocalDateTime shiftBeginTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("shift_end_time")
    private LocalDateTime shiftEndTime;

    /** 本班病情变化（医生手工录入，P0） */
    @TableField("condition_change")
    private String conditionChange;

    /** 下一班诊疗计划（P1 预留） */
    @TableField("plan_next")
    private String planNext;

    /** 待办事项（P1 预留） */
    @TableField("todo_note")
    private String todoNote;

    /** 状态：1 正常 0 已删除 */
    private Integer status;

    @TableField("create_by")
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_by")
    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("update_time")
    private LocalDateTime updateTime;
}
