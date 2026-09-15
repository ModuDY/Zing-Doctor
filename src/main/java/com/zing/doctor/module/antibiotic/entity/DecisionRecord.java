package com.zing.doctor.module.antibiotic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 抗感染决策记录。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"zing_decision_record\"")
public class DecisionRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String patientId;

    private String patientNo;

    private String pageCode;

    private String sourceSystem;

    private String infectionType;

    private Integer septicShock;

    private Integer mrsaRisk;

    private Integer mdrRisk;

    private Integer fungalRisk;

    private String recommendedPlan;

    private String doctorDecision;

    /** pending / accepted / declined */
    private String decisionStatus;

    private String doctorId;

    private String doctorName;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
