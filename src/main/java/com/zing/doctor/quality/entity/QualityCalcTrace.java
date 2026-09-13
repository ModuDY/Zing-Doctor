package com.zing.doctor.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 算子 / SQL 追溯（血缘第 3 层）。
 *
 * <p>因为所有计算都经统一引擎，血缘是引擎自动捕获的，不靠人工标注。
 * {@code sqlHash} 可在口径变更时对比出「哪条指标的 SQL 变了」。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"quality_calc_trace\"")
public class QualityCalcTrace {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String runId;
    private String metricCode;
    private String dimKey;
    private String factName;
    private Integer expressionVersion;
    private String sqlHash;
    /** 编译后的 SQL 原文 */
    private String sqlText;
    private String sourceTables;
    /** 算子链，如 filter>derive>aggregate>metric */
    private String operators;
    /** 事实层扫描行数，用于定位性能瓶颈 */
    private Long scannedRows;
    private Integer numRows;
    private Integer denRows;
    private Long durationMs;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime calcTime;
}
