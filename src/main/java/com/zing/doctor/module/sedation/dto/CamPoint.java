package com.zing.doctor.module.sedation.dto;

import lombok.Data;

/**
 * CAM-ICU 谵妄评估的单次记录点（分类型量表，不参与求均值）。
 *
 * <p>关键安全口径（见 docs/20、docs/21）：
 * <ul>
 *   <li>源系统只有「无谵妄 / 谵妄」两个结果、<b>没有「无法评估(UTA)」选项</b>，
 *       因此源原值 {@link #rawResult} 必须原样保留，不被派生状态覆盖。</li>
 *   <li>按 CAM-ICU 流程，RASS 为 -4 / -5（深镇静 / 不可唤醒）时<b>无法评估</b>谵妄；
 *       此时若源记录为「无谵妄」，派生为 {@code UTA_SUSPECT}（建议核查），
 *       而不是直接当作有效阴性。RASS -3 仍可评估，不算 UTA。</li>
 *   <li>配对不到评估时间之前最近、有效时间窗内的 RASS 时为 {@code PAIR_UNKNOWN}，
 *       既不算阳性也不算阴性。</li>
 * </ul>
 */
@Data
public class CamPoint {
    /** 评估时间（ISO 本地时间） */
    private String time;
    /** 评估记录人 */
    private String staffName;
    /** 源系统原始结论：无谵妄 / 谵妄（原样保留） */
    private String rawResult;
    /**
     * 派生状态：POSITIVE（谵妄）/ NEGATIVE（有效阴性）/ UTA_SUSPECT（深镇静下阴性，建议核查）
     * / PAIR_UNKNOWN（缺可配对 RASS）/ UNPARSED（源值无法识别）。
     */
    private String derivedStatus;
    /** 配对到的 RASS 分值（评估时间之前最近、有效窗内），可能为 null */
    private Integer pairedRass;
    /** 配对 RASS 的评估时间 */
    private String pairedRassTime;
    /** 核查 / 说明提示，与源结果并存，不覆盖源值 */
    private String hint;
    /** 原始 score_json_value */
    private String rawJson;
    /** 是否已审核 */
    private Boolean audited;
}
