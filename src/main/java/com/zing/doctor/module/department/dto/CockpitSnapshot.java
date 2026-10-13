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

    /** 当前使用血管活性药患者数 */
    private SnapshotBlock<Integer> vasopressorCount;

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

    // ---------------- C/E. 质控指标与评分分布（阶段2） ----------------

    /** SOFA 分值分布（每患者取周期内最新一条，按患者计数） */
    private SnapshotBlock<List<ScoreBucket>> sofaDist;

    /** APACHE II 分值分布（同上；预测病死率单列，不与实际病死率合并） */
    private SnapshotBlock<List<ScoreBucket>> apache2Dist;

    /** 质控指标卡（病死率 / 48h 重返 / 集束化完成人数，复用质控中台结果） */
    private SnapshotBlock<List<QualityCard>> qualityCards;

    /** 快照口径说明，前端展示在"口径说明"入口里 */
    private List<String> notes = new ArrayList<>();

    /** 分值分布桶：区间标签 + 患者数 */
    @Data
    public static class ScoreBucket {
        private String label;
        private int count;

        public ScoreBucket() {}

        public ScoreBucket(String label, int count) {
            this.label = label;
            this.count = count;
        }
    }

    /** 质控指标卡：复用质控中台的计算结果，驾驶舱不重算 */
    @Data
    public static class QualityCard {
        private String code;
        private String name;
        private String unit;
        /** 指标值；PENDING_SOURCE / NO_DATA 时为 null */
        private Double value;
        /** 分子 / 分母（率类指标展示用，COUNT 类分母为 null） */
        private Double numerator;
        private Double denominator;
        /** 质控引擎状态：OK / PLACEHOLDER / PENDING_SOURCE / MANUAL / NO_DATA / ERROR */
        private String calcStatus;
        private String note;

        public static QualityCard pending(String code, String name, String note) {
            QualityCard c = new QualityCard();
            c.code = code;
            c.name = name;
            c.calcStatus = "PENDING_SOURCE";
            c.note = note;
            return c;
        }
    }

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
