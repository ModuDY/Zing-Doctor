package com.zing.doctor.module.antibiotic.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 第四维度：细菌培养检出监测总览仪表盘视图。
 */
@Data
public class MdroOverviewView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 统计周期开始时间 */
    private String startTime;

    /** 统计周期结束时间 */
    private String endTime;

    /** 科室编码 */
    private String departCode;

    /** 科室名称 */
    private String departName;

    // ==================================================================
    // 核心指标
    // ==================================================================

    /** 在科患者总数（统计周期内在科的患者数） */
    private Integer totalPatients;

    /** 细菌培养送检患者数 */
    private Integer culturePatients;

    /** 细菌培养阳性患者数（检出细菌的患者数） */
    private Integer positivePatients;

    /** 细菌培养阳性率（阳性患者数/送检患者数） */
    private Double positiveRate;

    /** 高风险细菌检出患者数（鲍曼/铜绿/克雷伯/嗜麦芽/金葡/肠球菌等） */
    private Integer highRiskPatients;

    /** 高风险细菌检出率 */
    private Double highRiskRate;

    /** 细菌培养总送检次数 */
    private Integer totalCultureCount;

    /** 细菌培养阳性次数 */
    private Integer positiveCultureCount;

    // ==================================================================
    // 分类统计
    // ==================================================================

    /** 革兰阳性菌检出次数 */
    private Integer gramPositiveCount;

    /** 革兰阴性菌检出次数 */
    private Integer gramNegativeCount;

    /** 真菌检出次数 */
    private Integer fungiCount;

    /** 其他细菌检出次数 */
    private Integer otherCount;

    /** 细菌分类占比（用于饼图） */
    private List<Map<String, Object>> classDistribution;

    /** 标本类型分布（痰/血/尿/分泌物等，用于饼图） */
    private List<Map<String, Object>> specimenDistribution;

    // ==================================================================
    // 月度趋势（内嵌类）
    // ==================================================================

    @Data
    public static class MdroTrendPoint implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 月份（如 2026-09） */
        private String month;
        /** 送检患者数 */
        private Integer culturePatients;
        /** 阳性患者数 */
        private Integer positivePatients;
        /** 阳性率 */
        private Double positiveRate;
        /** 高风险细菌检出数 */
        private Integer highRiskCount;
        /** 革兰阳性菌检出数 */
        private Integer gramPositiveCount;
        /** 革兰阴性菌检出数 */
        private Integer gramNegativeCount;
        /** 真菌检出数 */
        private Integer fungiCount;
    }
}
