package com.zing.doctor.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 患者级命中明细（血缘第 4 层）。
 *
 * <p>把「数字」还原到「人」：谁进了分子、谁进了分母、未命中原因。
 * 这是与老系统核对时的核心武器——不是比一个数，而是逐患者比对差异原因。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"quality_metric_patient\"")
public class QualityMetricPatient {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String runId;
    private String metricCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime periodStart;

    private String departCode;
    private String patientId;
    private String inHospitalNo;
    private String patientName;
    /** 1 计入分子 0 否 */
    private Integer inNumerator;
    /** 1 计入分母 0 否 */
    private Integer inDenominator;
    /** 未计入原因 */
    private String excludeReason;
    /** 该患者参与计算的原始取值明细 JSON */
    private String rawJson;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
