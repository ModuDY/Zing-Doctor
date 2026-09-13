package com.zing.doctor.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 质控计算批次（血缘第 1 层）。
 *
 * <p>一次计算一条记录：谁触发、什么周期、引擎版本、数据快照、成功/失败/占位条数。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"quality_calc_run\"")
public class QualityCalcRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 批次号 */
    private String runId;
    /** MONTH / QUARTER / YEAR / CUSTOM */
    private String periodType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime periodStart;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime periodEnd;

    private String departCode;
    /** MANUAL / SCHEDULED */
    private String triggerType;
    private String engineVersion;
    /** 数据快照指纹，用于增量跳过 */
    private String dataSnapshotId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private Long durationMs;
    private Integer metricTotal;
    private Integer metricOk;
    private Integer metricFail;
    private Integer metricPlaceholder;
    /** RUNNING / SUCCESS / PARTIAL / FAILED */
    private String status;
    private String operator;
    private String message;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
