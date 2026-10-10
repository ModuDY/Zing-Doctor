package com.zing.doctor.module.sedation.dto;

import lombok.Data;

import java.util.Map;

/**
 * 数值型量表（NRS / CPOT / RASS）的单次评估点。
 *
 * <p>三类量表共用此模型，靠 {@link #docCode} 区分；{@link #score} 为可绘图的数值总分，
 * 原始值 {@link #rawScore} 与原始 JSON {@link #rawJson} 始终保留，派生值不覆盖源数据。
 *
 * <ul>
 *   <li>NRS：score 0-10（能自述患者）。</li>
 *   <li>RASS：score -5~+4，{@link #levelText} 为分级文字。</li>
 *   <li>CPOT：score 0-8（无法自述 / 机械通气患者），四个维度放 {@link #dimensions}，
 *       {@link #ventilated} 标识第 4 维度走通气顺应性（item4）还是发声（item5）。</li>
 * </ul>
 */
@Data
public class SedationPoint {
    /** 量表 doc_code：Z_ICU_SCORE_NRS / Z_ICU_SCORE_CPOT / Z_ICU_RASS */
    private String docCode;
    /** 评估时间（ISO 本地时间 yyyy-MM-ddTHH:mm:ss），每条数据独立采集时间 */
    private String time;
    /** 评估记录人（patient_doc_record.record_staff_name） */
    private String staffName;
    /** 数值总分；无法从原文解析时为 null（此时 dataStatus 可能为 PARTIAL） */
    private Integer score;
    /** score_value 原文（如 GCS 式 "1+VT+2" 不适用本量表，此处主要保留 NRS/RASS/CPOT 原文） */
    private String rawScore;
    /** RASS 分级文字（清醒平静 / 中度镇静 …），仅 RASS 填充 */
    private String levelText;
    /** CPOT 四维度分项：face / movement / muscle / ventOrVocal，值域 0-2；仅 CPOT 填充 */
    private Map<String, Object> dimensions;
    /** CPOT 第 4 维度口径：true=插管通气顺应性(item4)，false=拔管发声(item5)，null=未知 */
    private Boolean ventilated;
    /** 原始 score_json_value，供展开核对，前端默认不直接展示 */
    private String rawJson;
    /** 是否已审核 */
    private Boolean audited;
}
