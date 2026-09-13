package com.zing.doctor.quality.engine;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 单条指标的计算结果（含血缘信息），引擎输出、服务层落库。
 */
@Data
public class MetricOutcome {

    private String metricCode;
    private String metricName;
    private String domain;

    /** 维度值：科室编码，或 'ALL' 表示全院 */
    private String departCode = "ALL";

    private BigDecimal numerator;
    private BigDecimal denominator;
    private BigDecimal metricValue;
    private String unit;

    /** OK / PLACEHOLDER / PENDING_SOURCE / MANUAL / NO_DATA / ERROR */
    private String calcStatus;
    private String errorMsg;

    // ---- 血缘 ----

    private String factName;
    private String sqlText;
    private String sqlHash;
    private String operators;
    private String sourceTables;
    private Long scannedRows;
    private Integer numRows;
    private Integer denRows;
    private Long durationMs;
    private Integer expressionVersion;

    /** 患者级命中明细（血缘第 4 层） */
    private List<PatientHit> patients = new ArrayList<>();

    /** 患者级命中明细行。 */
    @Data
    public static class PatientHit {
        private String patientId;
        private String inHospitalNo;
        private String patientName;
        private String departCode;
        private Integer inNumerator;
        private Integer inDenominator;
        private String rawJson;
    }
}
