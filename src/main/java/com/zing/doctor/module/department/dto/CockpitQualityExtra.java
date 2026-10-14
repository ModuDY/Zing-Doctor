package com.zing.doctor.module.department.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 驾驶舱质控补充数据（按需实时计算 / 读已算结果，独立于 {@link CockpitSnapshot}）。
 *
 * <p>对应验收清单两条：
 * <ul>
 *   <li>「近七天内转出 ICU 后 48 小时内重返率」——{@link #readmitNumerator} /
 *       {@link #readmitDenominator} / {@link #readmitRate}，窗口为最近 7 天（含今天），
 *       由质控引擎按任意窗口现算，口径与质控中台 quality_22 / quality_403 完全同源；</li>
 *   <li>「近一年内感染性休克患者集束化治疗（bundle）完成率」——{@link #bundleTrend}，
 *       最近 12 个月逐月取已算结果（quality_11 ÷ quality_10）。</li>
 * </ul>
 *
 * <p>任一段取数失败都不抛异常，改为在 {@link #readmitNote} / {@link #bundleNote}
 * 里说明原因，页面显示「—」而不是整页 500。
 */
@Data
public class CockpitQualityExtra {

    /** 窗口开始（yyyy-MM-dd），便于前端标注「近 7 天」的准确范围。 */
    private String windowStart;

    /** 窗口结束（yyyy-MM-dd，不含当天之后）。 */
    private String windowEnd;

    /** 近 7 天转出后 48h 内重返 ICU 人数（quality_22 分子）。 */
    private Integer readmitNumerator;

    /** 近 7 天转出/出院人数（quality_403 分母）。 */
    private Integer readmitDenominator;

    /** 近 7 天 48h 重返率（%），分母为 0 时为 null。 */
    private BigDecimal readmitRate;

    /** 取数说明：命中为「近 7 天实时计算」，失败/无数据时写明原因。 */
    private String readmitNote;

    /** 近 12 个月 6h bundle 完成率趋势。 */
    private List<BundlePoint> bundleTrend = new ArrayList<>();

    /** bundle 趋势取数说明。 */
    private String bundleNote;

    /** 单月 bundle 完成率。 */
    @Data
    public static class BundlePoint {

        /** 月份，格式 yyyy-MM。 */
        private String month;

        /** 完成 6h bundle 的感染性休克患者数（quality_11）。 */
        private Integer numerator;

        /** 入 ICU 诊断为感染性休克的患者数（quality_10）。 */
        private Integer denominator;

        /** 完成率（%），分母为 0 时为 null。 */
        private BigDecimal rate;
    }
}
