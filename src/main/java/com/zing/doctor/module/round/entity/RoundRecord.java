package com.zing.doctor.module.round.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 医生查房记录表
 *
 * <p>与交班表（patient_doc_handover_record）分开：交班是班次级、面向交接；
 * 查房是患者日级、面向当日诊疗计划。不能复用同一张表，否则交班内容被修改后
 * 查房历史也会变，无法追溯。</p>
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"patient_doc_round_record\"")
public class RoundRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String patientId;

    private String inHospitalNo;

    private String patientName;

    private String departCode;

    /** 查房日期（一天一份，同一患者同一天只保留一份，更新覆盖） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate roundDate;

    /** 今日主要问题 */
    private String mainProblem;

    /** 感染判断 */
    private String infectionJudgment;

    /** 呼吸支持计划 */
    private String respiratoryPlan;

    /** 循环支持计划 */
    private String circulatoryPlan;

    /** 镇静镇痛 / 肾脏支持计划 */
    private String renalSedationPlan;

    /** 抗菌药调整计划 */
    private String abxPlan;

    /** 今日复查项目 */
    private String recheckItems;

    /** 治疗目标 */
    private String treatmentGoal;

    /** 明日重点 */
    private String tomorrowFocus;

    /** 状态：1正常 0已删除 */
    private Integer status;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
