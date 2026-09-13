package com.zing.doctor.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 质控指标定义（配置真源）。
 *
 * <p>对应 {@code quality/metrics/*.yaml} 的 {@code MetricDefinition}，但落到了可写的主库里 ——
 * 这是「可视化配置」得以成立的前提：原先配置只存在于 jar 内的 classpath，运行时不可写。
 *
 * <p>页面编辑的就是 {@code exprWhere} / {@code exprNumerator} / {@code exprDenominatorWhere} / {@code dims}
 * 这四个口径字段；其余为展示与映射信息。
 *
 * <p>{@code exprVersion} 由服务端在口径变化时自动 +1，页面不手填 —— 这是最容易忘、
 * 后果最严重（历史值口径无法回溯）的一个动作。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"quality_metric_def\"")
public class QualityMetricDef {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 指标编号 quality_xxx */
    private String indexCode;
    private String indexName;
    /** 所属域 */
    private String domainCode;
    /** 绑定事实层（quality_fact_def.fact_name） */
    private String factName;
    private String unit;
    /** COUNT / RATE / AVG / SUM */
    private String valueType;
    /** PT_COUNT / SUM / AVG */
    private String agg;
    /** DSL / MANUAL / CUSTOM_SQL */
    private String calcMode;
    /** IMPL / PLACEHOLDER / PENDING_SOURCE / MANUAL */
    private String implStatus;
    /** 率类放大系数，默认 100 */
    private Integer scale;

    // ---- 口径三要素（页面可编辑） ----

    /** 分子过滤条件 */
    private String exprWhere;
    /** 分子表达式：PT_COUNT 时布尔条件；SUM/AVG 时数值表达式；空则默认 1 */
    private String exprNumerator;
    /** 分母过滤条件；为空时分母 = 同期全部对象 */
    private String exprDenominatorWhere;
    /** 分组维度列名，JSON 数组字符串，如 ["depart_code"] */
    private String dims;

    /** 口径版本：口径变化时服务端自动 +1 */
    private Integer exprVersion;
    private Integer sortNo;
    private String remark;

    // ---- 老系统映射信息（只读展示） ----
    private String categoryCode;
    private String groupCode;
    private String qualityTypeCode;
    private String indexStandardCode;
    private String amountShowType;
    private String analysisCountType;
    private String legacyScript;
    private String legacySource;
    private String newTarget;
    private String reuseLevel;

    /** 1 启用 / 0 停用（停用保留历史结果可回溯） */
    private Integer status;
    private String operator;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
