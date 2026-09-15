package com.zing.doctor.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 质控指标结果（周期 × 指标 × 部门），血缘第 2 层。
 *
 * <p>同时承载「老系统同周期值」（compareValue/compareDiff），使双跑核对内建在结果表里。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"quality_metric_result\"")
public class QualityMetricResult {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String runId;
    private String metricCode;
    private String metricName;
    private String domainCode;
    private String periodType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime periodStart;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime periodEnd;

    private String departCode;
    private BigDecimal numerator;
    private BigDecimal denominator;
    private BigDecimal metricValue;
    private String unit;
    private String extJson;
    /** OK / PLACEHOLDER / PENDING_SOURCE / MANUAL / NO_DATA / ERROR */
    private String calcStatus;
    private String errorMsg;

    /** 老系统同周期值 */
    private BigDecimal compareValue;
    /** 与服务端值的相对差异率 */
    private BigDecimal compareDiff;

    private Integer expressionVersion;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime calcTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
