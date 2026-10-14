package com.zing.doctor.module.vitals.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 生命体征趋势（docs/25 §1：数据回顾工具，不是早期预警评分）。
 *
 * <p>每个指标独立带 dataStatus：AVAILABLE / NO_RECORD / PARTIAL / UNKNOWN。
 * NO_RECORD 是"查到了但没有记录"，UNKNOWN 是"查询失败"—— 两者语义不同，
 * 前端展示不同（NO_RECORD 不表示"正常"或"未发生"）。
 */
@Data
public class VitalsResponse {

    private String patientId;
    private String patientName;
    private String inHospitalNo;
    private String bedNo;
    private String rangeStart;
    private String rangeEnd;

    /** 指标序列（按指标分组） */
    private List<Series> series = new ArrayList<>();

    /** 原始记录明细（时间 / 项目 / 值 / 单位 / 来源 / 记录人） */
    private List<Row> records = new ArrayList<>();

    private Integer recordCount;

    /** 窗口内记录超过 2000 条上限时为 true（仅保留最新 2000 条，更早记录已省略） */
    private Boolean truncated;

    /** 截断提示文案（truncated=true 时前端展示） */
    private String truncatedNote;

    @Data
    public static class Series {
        /** 指标 key，如 hr / rr / spo2 / sbp / dbp / map / temp */
        private String key;
        private String name;
        /** 单位（生产库无独立单位字段，取配置或按本院惯例标注） */
        private String unit;
        /** 来源：无创 / 有创 / 未标明 */
        private String source;
        private String dataStatus = "NO_RECORD";
        private List<Point> points = new ArrayList<>();
        private String note;
    }

    @Data
    public static class Point {
        private String time;
        private Double value;
        private String staffName;
    }

    @Data
    public static class Row {
        private String time;
        private String itemName;
        private String value;
        private String itemCode;
        private String staffName;
    }
}