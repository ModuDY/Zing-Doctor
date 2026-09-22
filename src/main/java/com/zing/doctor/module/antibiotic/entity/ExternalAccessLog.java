package com.zing.doctor.module.antibiotic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 外链访问日志。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"sys_access_log\"")
public class ExternalAccessLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String pageCode;

    private String sourceSystem;

    private String ip;

    private String bizParams;

    private LocalDateTime accessTime;
}
