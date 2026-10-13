package com.zing.doctor.module.department.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 科室驾驶舱今日快照（阶段1 MVP）。
 *
 * <p>每个区块独立带 {@code dataStatus}：某一块查失败时只把该块标 UNKNOWN，
 * 其它块照常返回 —— 不能因为一个源挂掉就让整页空白（与工作台各摘要块一致的原则）。
 *
 * <p>床位使用率刻意不带"算出来的百分比"：质控事实层 fact_bed 的状态是 PENDING_SOURCE
 * （quality_301 / quality_304 均待接床位开放记录源），没有可靠分母前显示状态而非 0。
 */
@Data
public class CockpitSnapshot {

    /** 科室编码（已按账号授权收敛后的值）；空串表示全院 */
    private String departCode;

    /** 科室名称，前端展示用 */
    private String departName;

    /** 数据日期（今日），ISO yyyy-MM-dd */
    private String date;

    // ---------------- B. 今日科室快照 ----------------

    /** 当前在科患者数（is_in_depart=1，按住院号去重） */
    private SnapshotBlock<Integer> inCount;

    /** 今日入科 / 转入人数 */
    private SnapshotBlock<Integer> todayInCount;

    /** 今日转出 / 出科人数 */
    private SnapshotBlock<Integer> todayOutCount;

    /** 危重患者数（窄口径：机械通气 / 血管活性药 / CRRT / ECMO 任一） */
    private SnapshotBlock<Integer> criticalCount;

    /** 危重占在科比例（0~1 保留 3 位）；在科数为 0 时为 null，不造 0% */
    private SnapshotBlock<Double> criticalRatio;

    /** 当前机械通气患者数 */
    private SnapshotBlock<Integer> ventilatedCount;

    /** 当前 CRRT 患者数 */
    private SnapshotBlock<Integer> crrtCount;

    /** 当前 ECMO 患者数 */
    private SnapshotBlock<Integer> ecmoCount;

    /** 床位使用率区块：首版恒为 PENDING_SOURCE，等床位数据源接入 */
    private SnapshotBlock<String> bedUsage;

    // ---------------- D. 患者流转（近 N 天） ----------------

    /** 流转趋势（按日，缺失日期已补零） */
    private SnapshotBlock<List<DayFlow>> dailyFlow;

    /** 近 N 天已出科患者的平均 ICU 住院日 */
    private SnapshotBlock<Double> avgStayDays;

    /** 平均住院日参与统计的患者数（已出科者） */
    private Integer avgStayPatientCount;

    /** 快照口径说明，前端展示在"口径说明"入口里 */
    private List<String> notes = new ArrayList<>();

    /** 单个数据区块：值 + 状态 */
    @Data
    public static class SnapshotBlock<T> {
        public static final String OK = "OK";
        public static final String EMPTY = "EMPTY";
        public static final String PENDING_SOURCE = "PENDING_SOURCE";
        public static final String UNKNOWN = "UNKNOWN";

        private T value;
        private String status;
        private String note;

        public static <T> SnapshotBlock<T> of(T value) {
            SnapshotBlock<T> b = new SnapshotBlock<>();
            b.value = value;
            b.status = OK;
            return b;
        }

        public static <T> SnapshotBlock<T> of(T value, String status, String note) {
            SnapshotBlock<T> b = new SnapshotBlock<>();
            b.value = value;
            b.status = status;
            b.note = note;
            return b;
        }

        public static <T> SnapshotBlock<T> pending(String note) {
            return of(null, PENDING_SOURCE, note);
        }

        public static <T> SnapshotBlock<T> unknown(String note) {
            return of(null, UNKNOWN, note);
        }
    }

    /** 单日流转：日期 + 入科人数 + 出科人数 */
    @Data
    public static class DayFlow {
        /** 日期 ISO yyyy-MM-dd */
        private String day;
        /** 当日入科 / 转入人数 */
        private int inCount;
        /** 当日转出 / 出科人数 */
        private int outCount;
    }
}
