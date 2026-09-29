package com.zing.doctor.module.ards.prone.dict;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ARDS 俯卧位通气治疗记录参数字典（37 项 × N 时点）。
 *
 * <p>与纸质原表一致：7 个分组共 37 项参数。字典在服务端统一维护，
 * 填写页、打印文书、回传文书共用同一份，避免前后端各写一套导致打印与屏幕不一致。
 *
 * <p>取值口径（设计方案 v4.1）：
 * <ul>
 *   <li>连续类（生命体征 / 呼吸机）：采集窗口 ±15 min，取窗口内最近一条</li>
 *   <li>检验类（血气）：采集窗口 ±60 min，取窗口内最近一条报告</li>
 *   <li>窗口内无数据：置空标「未采集」转手工录入，<b>不沿用历史值</b></li>
 *   <li>观察项/检验中无对应数据元的参数（Ppeak、Pplat、HCO₃⁻、BE 等）来源为手工；
 *       若后续接入数据元，只需在此补 keywords，页面与打印自动变为自动值</li>
 * </ul>
 *
 * <p>参考区间：内置固定、随版本受控（不做阈值配置），仅用于界面标红，不做任何告警与阻断。
 */
public final class ArdsProneDict {

    /** 采集窗口：连续类（生命体征 / 呼吸机）±15 min */
    public static final int WINDOW_CONTINUOUS = 15;

    /** 采集窗口：检验类（血气）±60 min */
    public static final int WINDOW_LAB = 60;

    /** 来源：自动采集（监护仪 / 呼吸机） */
    public static final String SRC_AUTO = "auto";

    /** 来源：LIS 检验同步 */
    public static final String SRC_LIS = "lis";

    /** 来源：系统计算（不可手改） */
    public static final String SRC_CALC = "calc";

    /** 来源：手工录入 */
    public static final String SRC_MAN = "man";

    private ArdsProneDict() {
    }

    @Data
    public static class ParamDef {
        /** 参数编码（落库 key） */
        private String key;
        /** 参数名称（页面与打印展示） */
        private String name;
        private String unit;
        private String group;
        /** 默认来源：auto / lis / calc / man */
        private String source;
        /** 输入方式：num 数值 / txt 文本 / sel 下拉 / chip 状态标签 */
        private String inputType;
        /** chip 与 sel 的可选项 */
        private List<String> options;
        /** 采集窗口（分钟） */
        private Integer windowMin;
        /** 内置参考区间下限（低于标红，仅视觉） */
        private BigDecimal refLow;
        /** 内置参考区间上限（高于标红，仅视觉） */
        private BigDecimal refHigh;
        /** 观察项名称匹配关键字（任一命中即取该值） */
        private List<String> observeKeywords;
        /** 检验项目名称匹配关键字 */
        private List<String> lisKeywords;
        /** 是否系统计算项 */
        private boolean calc;

        public ParamDef key(String v) { this.key = v; return this; }
        public ParamDef name(String v) { this.name = v; return this; }
        public ParamDef unit(String v) { this.unit = v; return this; }
        public ParamDef group(String v) { this.group = v; return this; }
        public ParamDef source(String v) { this.source = v; this.calc = SRC_CALC.equals(v); return this; }
        public ParamDef inputType(String v) { this.inputType = v; return this; }
        public ParamDef options(String... v) { this.options = Arrays.asList(v); return this; }
        public ParamDef windowMin(int v) { this.windowMin = v; return this; }
        public ParamDef refLow(double v) { this.refLow = BigDecimal.valueOf(v); return this; }
        public ParamDef refHigh(double v) { this.refHigh = BigDecimal.valueOf(v); return this; }
        public ParamDef observe(String... v) { this.observeKeywords = Arrays.asList(v); return this; }
        public ParamDef lis(String... v) { this.lisKeywords = Arrays.asList(v); return this; }
    }

    private static final List<ParamDef> PARAMS = new ArrayList<>();
    private static final Map<String, ParamDef> INDEX = new LinkedHashMap<>();

    static {
        // ---------------- 生命体征（监护仪自动采集，±15 min） ----------------
        add(new ParamDef().key("hr").name("心率 (HR)").unit("次/min").group("生命体征")
                .source(SRC_AUTO).inputType("num").windowMin(WINDOW_CONTINUOUS).refLow(50).refHigh(120)
                .observe("心率", "脉搏", "HR"));
        add(new ParamDef().key("sbp").name("收缩压 (SBP)").unit("mmHg").group("生命体征")
                .source(SRC_AUTO).inputType("num").windowMin(WINDOW_CONTINUOUS).refLow(90)
                .observe("收缩压", "无创收缩压", "有创收缩压", "SBP"));
        add(new ParamDef().key("dbp").name("舒张压 (DBP)").unit("mmHg").group("生命体征")
                .source(SRC_AUTO).inputType("num").windowMin(WINDOW_CONTINUOUS)
                .observe("舒张压", "无创舒张压", "有创舒张压", "DBP"));
        add(new ParamDef().key("map").name("平均动脉压 (MAP)").unit("mmHg").group("生命体征")
                .source(SRC_AUTO).inputType("num").windowMin(WINDOW_CONTINUOUS).refLow(65)
                .observe("平均动脉压", "平均压", "无创平均压", "有创平均压", "MAP"));
        add(new ParamDef().key("rr").name("呼吸频率 (RR)").unit("次/min").group("生命体征")
                .source(SRC_AUTO).inputType("num").windowMin(WINDOW_CONTINUOUS).refHigh(35)
                .observe("呼吸频率", "呼吸", "RR"));
        add(new ParamDef().key("t").name("体温 (T)").unit("℃").group("生命体征")
                .source(SRC_AUTO).inputType("num").windowMin(WINDOW_CONTINUOUS).refHigh(38.5)
                .observe("体温", "T", "TEMP"));
        add(new ParamDef().key("spo2").name("脉搏血氧 (SpO₂)").unit("%").group("生命体征")
                .source(SRC_AUTO).inputType("num").windowMin(WINDOW_CONTINUOUS).refLow(90)
                .observe("血氧饱和度", "脉搏血氧", "SpO2", "SPO2", "血氧"));

        // ---------------- 呼吸机参数（±15 min；Ppeak / Pplat 暂无数据元 → 手工） ----------------
        add(new ParamDef().key("vent_mode").name("通气模式").unit("—").group("呼吸机参数")
                .source(SRC_MAN).inputType("sel").windowMin(WINDOW_CONTINUOUS)
                .options("PCV", "VCV", "PSV", "SIMV", "CPAP", "APRV", "其他"));
        add(new ParamDef().key("vt").name("潮气量 (Vt)").unit("ml/kg").group("呼吸机参数")
                .source(SRC_AUTO).inputType("num").windowMin(WINDOW_CONTINUOUS).refHigh(6.5)
                .observe("潮气量", "VT", "Vt"));
        add(new ParamDef().key("set_rr").name("设定呼吸频率").unit("次/min").group("呼吸机参数")
                .source(SRC_AUTO).inputType("num").windowMin(WINDOW_CONTINUOUS)
                .observe("设定呼吸频率", "机控呼吸频率", "设置呼吸频率", "呼吸机频率"));
        add(new ParamDef().key("peep").name("PEEP").unit("cmH₂O").group("呼吸机参数")
                .source(SRC_AUTO).inputType("num").windowMin(WINDOW_CONTINUOUS)
                .observe("PEEP", "呼气末正压"));
        add(new ParamDef().key("fio2").name("吸入氧浓度 (FiO₂)").unit("%").group("呼吸机参数")
                .source(SRC_AUTO).inputType("num").windowMin(WINDOW_CONTINUOUS)
                .observe("吸入氧浓度", "FiO2", "氧浓度"));
        add(new ParamDef().key("ppeak").name("气道峰压 (Ppeak)").unit("cmH₂O").group("呼吸机参数")
                .source(SRC_MAN).inputType("num").windowMin(WINDOW_CONTINUOUS).refHigh(35)
                .observe("气道峰压", "峰压", "Ppeak"));
        add(new ParamDef().key("pplat").name("平台压 (Pplat)").unit("cmH₂O").group("呼吸机参数")
                .source(SRC_MAN).inputType("num").windowMin(WINDOW_CONTINUOUS).refHigh(30)
                .observe("平台压", "Pplat"));
        add(new ParamDef().key("dp").name("驱动压 (ΔP)").unit("cmH₂O").group("呼吸机参数")
                .source(SRC_CALC).inputType("num").windowMin(WINDOW_CONTINUOUS).refHigh(15));

        // ---------------- 氧合参数（系统计算） ----------------
        add(new ParamDef().key("pf").name("氧合指数 (PaO₂/FiO₂)").unit("mmHg").group("氧合参数")
                .source(SRC_CALC).inputType("num").refLow(150));
        add(new ParamDef().key("pf_delta").name("氧合指数变化").unit("△mmHg").group("氧合参数")
                .source(SRC_CALC).inputType("num"));
        add(new ParamDef().key("oi").name("氧指数 (OI)").unit("—").group("氧合参数")
                .source(SRC_CALC).inputType("num"));

        // ---------------- 血气分析（LIS 同步，±60 min） ----------------
        add(new ParamDef().key("ph").name("pH").unit("—").group("血气分析")
                .source(SRC_LIS).inputType("num").windowMin(WINDOW_LAB).refLow(7.30).refHigh(7.45)
                .lis("酸碱度", "PH值", "PH", "pH"));
        add(new ParamDef().key("pao2").name("动脉血氧分压 (PaO₂)").unit("mmHg").group("血气分析")
                .source(SRC_LIS).inputType("num").windowMin(WINDOW_LAB)
                .lis("氧分压", "PO2", "PaO2"));
        add(new ParamDef().key("paco2").name("动脉血CO₂分压 (PaCO₂)").unit("mmHg").group("血气分析")
                .source(SRC_LIS).inputType("num").windowMin(WINDOW_LAB).refHigh(50)
                .lis("二氧化碳分压", "PCO2", "PaCO2"));
        add(new ParamDef().key("hco3").name("碳酸氢根 (HCO₃⁻)").unit("mmol/L").group("血气分析")
                .source(SRC_MAN).inputType("num").windowMin(WINDOW_LAB)
                .lis("碳酸氢根", "实际碳酸氢盐", "标准碳酸氢盐", "HCO3"));
        add(new ParamDef().key("be").name("碱剩余 (BE)").unit("mmol/L").group("血气分析")
                .source(SRC_MAN).inputType("num").windowMin(WINDOW_LAB)
                .lis("碱剩余", "剩余碱", "BE"));
        add(new ParamDef().key("lac").name("乳酸 (Lac)").unit("mmol/L").group("血气分析")
                .source(SRC_LIS).inputType("num").windowMin(WINDOW_LAB).refHigh(2.0)
                .lis("乳酸", "LAC", "Lac"));

        // ---------------- 镇静镇痛（手工） ----------------
        add(new ParamDef().key("rass").name("RASS 评分").unit("分").group("镇静镇痛")
                .source(SRC_MAN).inputType("num").refLow(-5).refHigh(2));
        add(new ParamDef().key("cpot").name("CPOT/BPS 评分").unit("分").group("镇静镇痛")
                .source(SRC_MAN).inputType("num").refHigh(3));
        add(new ParamDef().key("sed_drug").name("镇静药物及剂量").unit("—").group("镇静镇痛")
                .source(SRC_MAN).inputType("txt"));
        add(new ParamDef().key("analgesia_drug").name("镇痛药物及剂量").unit("—").group("镇静镇痛")
                .source(SRC_MAN).inputType("txt"));
        add(new ParamDef().key("muscle_relaxant").name("肌松药使用").unit("—").group("镇静镇痛")
                .source(SRC_MAN).inputType("txt"));

        // ---------------- 安全评估（状态标签 / 文本） ----------------
        add(new ParamDef().key("airway").name("气管插管/气切").unit("固定").group("安全评估")
                .source(SRC_MAN).inputType("chip").options("固定", "移位", "脱管", "渗血"));
        add(new ParamDef().key("cvc").name("中心静脉导管").unit("固定").group("安全评估")
                .source(SRC_MAN).inputType("chip").options("固定", "渗血", "脱出", "受压"));
        add(new ParamDef().key("art_line").name("动脉导管").unit("固定").group("安全评估")
                .source(SRC_MAN).inputType("chip").options("固定", "渗血", "脱出", "堵塞"));
        add(new ParamDef().key("tube").name("胃管/尿管").unit("固定").group("安全评估")
                .source(SRC_MAN).inputType("chip").options("固定", "脱出", "堵塞", "渗液"));
        add(new ParamDef().key("skin").name("皮肤完整性").unit("评估").group("安全评估")
                .source(SRC_MAN).inputType("chip").options("完好", "受压发红", "破损", "压力性损伤"));
        add(new ParamDef().key("eye").name("眼部护理").unit("评估").group("安全评估")
                .source(SRC_MAN).inputType("chip").options("已护理", "未护理", "结膜水肿", "角膜损伤"));
        add(new ParamDef().key("gastric").name("胃潴留").unit("有/无").group("安全评估")
                .source(SRC_MAN).inputType("chip").options("无", "有", "有 80ml"));
        add(new ParamDef().key("sputum").name("痰液性状/量").unit("—").group("安全评估")
                .source(SRC_MAN).inputType("txt"));
    }

    private static void add(ParamDef def) {
        PARAMS.add(def);
        INDEX.put(def.getKey(), def);
    }

    /** 全部参数（按分组顺序） */
    public static List<ParamDef> params() {
        return PARAMS;
    }

    /** 按编码取参数定义 */
    public static ParamDef get(String key) {
        return INDEX.get(key);
    }

    /** 需要系统计算的参数编码 */
    public static List<String> calcKeys() {
        List<String> keys = new ArrayList<>();
        for (ParamDef d : PARAMS) {
            if (d.isCalc()) {
                keys.add(d.getKey());
            }
        }
        return keys;
    }
}
