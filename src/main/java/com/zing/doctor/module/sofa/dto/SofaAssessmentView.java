package com.zing.doctor.module.sofa.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SOFA 评估视图：6 个器官项 + 总分 + 与上次评分对比。
 *
 * <p>前端据此直接渲染 6 张卡片；每项的「来源」弹窗可展示 {@code valueText}（原始值）、
 * {@code rangeText}（命中区间）、{@code dataTime}（取值时间）与 {@code note}（降级/提示）。
 */
@Data
public class SofaAssessmentView {

    /** 患者基础信息（patientId / inHospitalNo / name / bedCode / age / gender / departCode / inDepartTime） */
    private Map<String, Object> patient;

    /** 6 个器官项（固定顺序：resp / coag / liver / cardio / neuro / renal） */
    private List<SofaItem> items;

    /** SOFA 总分 0~24 */
    private Integer totalScore;

    /** 上一次评分总分（无历史为 null） */
    private Integer lastScore;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastScoreTime;

    /** 较上次变化（正数=恶化）；无历史为 null */
    private Integer deltaSofa;

    /** 本次剂量换算所用体重（kg） */
    private BigDecimal weightUsed;

    /** 体重来源：actual / ibw / default_age_sex / fallback70 */
    private String weightSource;

    /** 体重来源说明（如「IBW 估算，身高 168cm」「年龄性别默认值，待确认」） */
    private String weightNote;

    /** 是否有呼吸支持：1是 0否 */
    private Integer respiratorySupport;

    /** 血管活性药明细（药名 / 归一剂量 / 泵速 / 浓度 / 是否降级） */
    private List<Map<String, Object>> vasopressors;

    /** 窗口内尿量合计（mL）；窗口不足 24h 时为 null */
    private BigDecimal urineMl;

    /** GCS 总分（未评全为 null）与明细 */
    private Integer gcsTotal;
    private String gcsDetail;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dataStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dataEndTime;

    /** 汇总提示（缺数据项、剂量降级、体重来源等） */
    private String remark;

    /**
     * 单个器官项。
     */
    @Data
    public static class SofaItem {

        /** resp / coag / liver / cardio / neuro / renal */
        private String key;

        /** 中文名（呼吸 / 凝血 / 肝 / 循环 / 神经 / 肾） */
        private String label;

        /** 0~4 */
        private Integer score;

        /** 归一后的数值（如 PaO2/FiO2、血小板、胆红素 mg/dL、剂量 µg/kg/min） */
        private Double value;

        /** 展示用原始值文本（如「260（PaO2/FiO2）」「62 / 0.476 µg/kg/min」） */
        private String valueText;

        private String unit;

        /** 命中区间文本（如「<300」「<100」） */
        private String rangeText;

        /** 取数时间（原始记录时间） */
        private String dataTime;

        /** 是否未取到数据（score 按 0 计） */
        private Boolean missing;

        /** 提示：剂量未归一 / 窗口不足 24h / 体重为默认值 等 */
        private String note;

        /** 原始值 JSON（落库与追溯用） */
        private String rawJson;
    }
}
