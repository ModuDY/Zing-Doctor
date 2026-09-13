package com.zing.doctor.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 质控事实层（DWD）定义（配置真源）。
 *
 * <p>对应 {@code quality/facts/*.yaml} 的 {@code FactDefinition}。四个集合字段（select / derive / where / group）
 * 在 DB 中以 JSON 数组存 TEXT，由 {@code QualityConfigRepository} 负责与 {@code List<String>} 互转。
 *
 * <p>改事实层的影响面远大于改指标（一个域几十条指标共享同一事实层），
 * 因此保存前应先查「影响面」，页面也要二次确认。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"quality_fact_def\"")
public class QualityFactDef {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事实层名，同时作为物化表名后缀 */
    private String factName;
    private String domainCode;
    /** ACTIVE / PENDING_SOURCE / PLACEHOLDER */
    private String status;
    /** 来源逻辑表名（对应 quality/sources.yaml 的 tables key） */
    private String sourceTable;
    private String alias;
    /** 计数对象主键表达式（患者/医师/床位，取决于本事实层声明） */
    private String patientKey;
    private String inHospitalNoKey;
    private String patientNameKey;
    private String departKey;

    /** 选列表达式，JSON 数组 */
    private String selectCols;
    /** 派生列表达式，JSON 数组 */
    private String deriveCols;
    /** 过滤条件，JSON 数组，逐条 AND */
    private String whereConds;
    /** 分组列，JSON 数组，空表示患者级 */
    private String groupCols;

    private String note;
    private String operator;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
