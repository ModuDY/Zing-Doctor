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
 * <p>床位与设备资源来自 ICU 配置表。床位占用按“有效开放床位 ∩ 当前在科患者床号”计算；
 * 设备状态只代表采集连接状态，不等同于设备运行、维修或库存状态。
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

    /** 当前床位开放、占用和可用情况（实时快照，回答「此刻有几张床」） */
    private SnapshotBlock<BedOverview> bedUsage;

    /**
     * 本月床位使用率（质控口径：占用床日 ÷ 开放床日）。
     *
     * <p>与 {@link #bedUsage} 是两种口径、两个答案：前者是「当前时刻的床位数」，
     * 后者是「本月累计的床日比」，取自质控中台的「ICU床位使用率」真指标。
     * 两者不应相等，也不应互相替代 —— 放在一起是为了让管理者能对照判断。
     */
    private SnapshotBlock<BedMonthUsage> bedMonthUsage;

    /** 设备配置、患者绑定及数据接收状态（按设备类型聚合） */
    private SnapshotBlock<List<DeviceOverview>> deviceOverview;

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

    /** 床位资源快照。使用率为 0~1；没有开放床位时为 null。 */
    @Data
    public static class BedOverview {
        private int openBeds;
        private int occupiedBeds;
        private int availableBeds;
        private int unassignedPatients;
        private Double occupancyRate;
    }

    /**
     * 本月床位使用率（质控口径），字段直接对应质控中台「ICU床位使用率」规则的分子分母，
     * 便于使用者把驾驶舱的数与质控看板那一行逐项对上。
     */
    @Data
    public static class BedMonthUsage {
        /** 床位使用率百分数（如 78.35），= 占用床日 ÷ 开放床日 × 100 */
        private Double rate;
        /** 分子：本月 ICU 实际占用总床日数（原子项 quality_2） */
        private Double occupiedDays;
        /** 分母：本月同期 ICU 实际开放总床日数（原子项 quality_301） */
        private Double openDays;
        /** 质控规则 id / 名称，用于与质控看板逐条对照 */
        private String ruleId;
        private String ruleName;
        /** 质控组装状态：OK / NO_DATA */
        private String calcStatus;
    }

    /**
     * 设备接入快照。
     * configured/bound 是设备数，receiving 等字段来自 config_device.device_status。
     */
    @Data
    public static class DeviceOverview {
        private String type;
        private String label;
        private int configured;
        private int bound;
        private int receiving;
        private int connected;
        private int initializing;
        private int stopped;
        private int abnormal;
        private int unknown;
        private String latestUpdate;
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
