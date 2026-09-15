package com.zing.doctor.module.antibiotic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 抗感染方案推荐日志。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"zing_advice_log\"")
public class AdviceLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long decisionRecordId;

    private String drugName;

    private String dosePlan;

    private String route;

    private String adviceLevel;

    private String reason;

    private String evidence;

    private LocalDateTime createTime;
}
