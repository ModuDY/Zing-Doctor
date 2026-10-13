package com.zing.doctor.module.labtrend.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 检验趋势（肝功能 / 凝血功能共用）。
 *
 * <p>docs/25 §4/§5：独立检验趋势页，展示历史结果与参考范围，
 * **不由趋势直接诊断**肝衰竭 / DIC / 出血风险。
 */
@Data
public class LabTrendResponse {

    private String patientId;
    private String patientName;
    private String inHospitalNo;
    private String rangeStart;
    private String rangeEnd;

    /** 分组（如 肝细胞损伤 / 胆红素 / 合成功能；凝血一组） */
    private List<Group> groups = new ArrayList<>();

    /** 明细记录 */
    private List<Row> records = new ArrayList<>();

    private Integer recordCount;

    @Data
    public static class Group {
        private String name;
        private List<Series> series = new ArrayList<>();
    }

    @Data
    public static class Series {
        private String name;
        private String unit;
        /** AVAILABLE / NO_RECORD / UNKNOWN */
        private String dataStatus = "NO_RECORD";
        private List<Point> points = new ArrayList<>();
        private String note;
    }

    @Data
    public static class Point {
        private String time;
        private Double value;
        private String refLow;
        private String refHigh;
        private String refText;
    }

    @Data
    public static class Row {
        private String time;
        private String itemName;
        private String value;
        private String unit;
        private String refLow;
        private String refHigh;
        private String refText;
        private String alarmFlag;
    }
}