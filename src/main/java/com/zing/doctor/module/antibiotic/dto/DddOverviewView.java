package com.zing.doctor.module.antibiotic.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 第三维度：抗菌药物使用强度（DDD）总览仪表盘。
 */
@Data
public class DddOverviewView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 统计开始时间 */
    private String startTime;

    /** 统计结束时间 */
    private String endTime;

    /** 抗菌药物使用率（%） */
    private BigDecimal usageRate;

    /** 抗菌药物使用率目标值（≤60%） */
    private BigDecimal usageRateTarget = new BigDecimal("60");

    /** 是否达标 */
    private Boolean usageRate达标;

    /** 抗菌药物使用强度（DDDs/100床日） */
    private BigDecimal useDensity;

    /** 使用强度目标值（≤40） */
    private BigDecimal useDensityTarget = new BigDecimal("40");

    /** 是否达标 */
    private Boolean useDensity达标;

    /** 特殊使用级抗菌药物使用强度 */
    private BigDecimal specialUseDensity;

    /** 特殊使用级占总使用强度比例（%） */
    private BigDecimal specialRatio;

    /** 总 DDDs */
    private BigDecimal totalDdds;

    /** 特殊使用级 DDDs */
    private BigDecimal specialDdds;

    /** 使用抗菌药物患者数 */
    private Integer usedPatientCount;

    /** 总患者数 */
    private Integer totalPatientCount;

    /** 总床日数 */
    private BigDecimal totalBedDays;

    /** 涉及抗菌药物品种数 */
    private Integer drugKindCount;

    /** 月度趋势（近12个月） */
    private List<DddTrendPoint> monthlyTrend;

    /** 药品分类占比 */
    private List<DddClassRatio> classRatios;

    /** 管理级别占比 */
    private List<DddLevelRatio> levelRatios;

    @Data
    public static class DddTrendPoint implements Serializable {
        private String month;       // 月份，如 2026-01
        private BigDecimal usageRate;   // 使用率
        private BigDecimal useDensity;  // 使用强度
        private BigDecimal totalDdds;   // 总DDDs
    }

    @Data
    public static class DddClassRatio implements Serializable {
        private String drugClass;   // 药物分类
        private BigDecimal ddds;    // DDDs
        private BigDecimal ratio;   // 占比（%）
    }

    @Data
    public static class DddLevelRatio implements Serializable {
        private String manageLevel; // 管理级别
        private BigDecimal ddds;    // DDDs
        private BigDecimal ratio;   // 占比（%）
    }
}
