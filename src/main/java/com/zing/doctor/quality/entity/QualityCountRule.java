package com.zing.doctor.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 质控指标规则 —— 这才是「真正的业务指标」。
 *
 * <p>此前系统里被叫作「指标」的 {@code quality_xxx} 其实只是<strong>原子项</strong>：
 * 一个数人头 / 数天数的量，自身成不了率。真正的指标由本表描述：
 *
 * <pre>
 *   指标值 = 原子项(numeratorCode) ÷ 原子项(denominatorCode) × percentRate
 * </pre>
 *
 * <p>例：{@code ICU镇痛评估率 = quality_306 ÷ quality_403 × 100}；
 * {@code VAP发病率 = quality_24 ÷ quality_25 × 1000}（例/千呼吸机日）。
 *
 * <p>原子项之间高度复用（quality_449 被 17 条指标当分母、quality_403 被 13 条当分母），
 * 因此「同期患者总数」这类口径只需在原子项上维护一次，改一次口径不必改 13 处。
 *
 * <p>源数据来自 ICU 侧 {@code zing_icu_db_prod.quality_count_rule}（只读），
 * 通过跨 schema 的 INSERT…SELECT 同步进本表；落在本院库后，
 * {@code targetValue} / {@code warningValue} 等本院自管字段才可配置 ——
 * 质控最关心的「达标与否」必须有个地方存。
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"quality_count_rule\"")
public class QualityCountRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** ICU 侧规则 id；本表唯一约束，用于幂等同步 */
    private String ruleId;
    private String countName;
    private String qualityTypeCode;
    private Integer isVisible;
    /** 0 = 不进看板（如「ICU实际病死率」） */
    private Integer isShowPage;
    private Integer sortNo;
    private String remark;

    // ---- 指标的构成 ----

    /** 分子原子项 code，指向 quality_metric_def.index_code */
    private String numeratorCode;
    /** 分母原子项 code，指向 quality_metric_def.index_code */
    private String denominatorCode;
    /** 取自 ICU 侧，但不可直接当单位用：percent_rate=1000 的两条此处仍写 '%' */
    private String percentUnit;
    /** 放大系数：100 百分比 / 1000 例每千日 / 1 原样 */
    private Integer percentRate;
    private Integer percentPrecision;

    // ---- 本院自管：目标与预警（ICU 侧原表有此四列但当前全 NULL） ----

    private BigDecimal targetValue;
    private BigDecimal warningValue;
    private String targetLineColor;
    private String warningLineColor;

    private String departCode;
    /** 源端 status */
    private Integer sourceStatus;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime syncTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
