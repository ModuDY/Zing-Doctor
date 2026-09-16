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

    @TableId(type = IdType.ASSIGN_ID)
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

    /**
     * 达标方向：{@code UP} 越高越好 / {@code DOWN} 越低越好。
     *
     * <p>没有这一列就判不出达标：「镇痛评估率 ≥ 90%」和「VAP 发病率 ≤ 5‰」的阈值本身
     * 看不出方向，而同一个数值在两类指标上结论正好相反。靠「目标值与预警值谁大谁小」
     * 去猜也不可靠 —— 只配一个阈值的指标就无从判断。
     */
    private String targetDirection;

    private String targetLineColor;
    private String warningLineColor;

    private String departCode;
    /** 源端 status */
    private Integer sourceStatus;
    private Integer status;

    // ---- 来源（本院自建能力的开关，见 sql/17_quality_rule_local.sql） ----

    /**
     * 规则来源：{@code ICU} 由重症侧同步而来 / {@code LOCAL} 本院自建。
     *
     * <p>本院自建规则一律带 {@code LOCAL_} 前缀的 ruleId，与 ICU 的数字 id 天然隔离，
     * 因此同步的「只插入本地没有的 rule_id」不会误判，也不会互相覆盖。
     */
    private String origin;

    /**
     * {@code 1} = ICU 来源但本院改过口径（分子 / 分母 / 放大系数）。
     *
     * <p>置 1 后同步不再覆盖这几个字段 —— 否则本院好不容易改对的口径，
     * 会被下一次「同步指标规则」按源端的旧值冲回去，且只在源端值真的变了时才发生。
     */
    private Integer localOverride;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime syncTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
