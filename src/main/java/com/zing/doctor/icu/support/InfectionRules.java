package com.zing.doctor.icu.support;

import cn.hutool.core.util.StrUtil;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 感染判定的规则内核（纯计算，不碰数据库）。
 *
 * <p><b>为什么要单独抽出来</b>：同一套规则（什么算疑似感染、什么算脓毒性休克、
 * 风险等级怎么分）现在有两个消费者——
 * <ul>
 *   <li>疑似感染患者列表（{@code SqlIcuPatientServiceImpl.listSuspectInfections}）</li>
 *   <li>患者工作台把感染维度回填到全量在科患者（{@code WorkbenchEnrichService}）</li>
 * </ul>
 * 复制一份实现出来的直接后果是：两个页面对同一个患者给出不同的感染类型或风险等级，
 * 而医生会同时看这两个页面。所以取数可以各查各的，判定规则只能有一份。
 *
 * <p>这里刻意做成静态方法：无状态、无 Spring 依赖，便于单测直接钉住规则。
 */
public final class InfectionRules {

    /** 疑似感染的 PCT 阈值（ng/mL）：低于此值不算感染证据 */
    public static final BigDecimal PCT_SUSPECT_THRESHOLD = new BigDecimal("0.5");

    /** PCT 高风险阈值（ng/mL） */
    public static final BigDecimal PCT_HIGH_THRESHOLD = new BigDecimal("2");

    /** 抗菌药物关键词（医嘱名称匹配，用于识别当前抗菌用药） */
    public static final List<String> ABX_KEYWORDS = Arrays.asList(
            "哌拉西林", "头孢", "美罗培南", "亚胺培南", "厄他培南", "比阿培南",
            "万古霉素", "利奈唑胺", "替考拉宁", "达托霉素",
            "左氧氟沙星", "莫西沙星", "环丙沙星", "奈诺沙星", "阿奇霉素", "克拉霉素",
            "阿莫西林", "氨苄西林", "阿米卡星", "庆大霉素", "妥布霉素",
            "替加环素", "多黏菌素", "多粘菌素", "磷霉素", "氨曲南",
            "卡泊芬净", "米卡芬净", "阿尼芬净", "氟康唑", "伏立康唑", "泊沙康唑",
            "两性霉素", "伊曲康唑", "甲硝唑", "奥硝唑", "替硝唑",
            "舒巴坦", "他唑巴坦", "克拉维酸", "复方新诺明", "磺胺",
            "四环素", "多西环素", "米诺环素", "利福平", "青霉素");

    /** 溶媒关键词（patient_advice 同一 group 下溶媒和溶质分开存储，排除溶媒只取溶质） */
    public static final List<String> SOLVENT_KEYWORDS = Arrays.asList(
            "氯化钠", "葡萄糖", "乳酸钠林格", "灭菌注射用水", "木糖醇",
            "转化糖", "果糖", "复方氯化钠", "甘油果糖");

    private static final Pattern FIRST_NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");

    private InfectionRules() {
    }

    // ------------------------------------------------------------------
    // 基础解析
    // ------------------------------------------------------------------

    public static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    /**
     * 检验结果 → 数值。
     *
     * <p>检验结果常带单位（"0.85 ng/mL"）或比较符（"&lt;0.05"），
     * 直接 {@code new BigDecimal} 会整条失败、指标变 null（表现为列表里 PCT 一列全空）。
     * 这里失败后退回到"截取第一个数值"：带单位时取数字部分，带比较符时取阈值本身
     * （&lt;0.05 记作 0.05 —— 对 0.5 门槛的判断是保守的，不会把低值误判成感染证据）。
     */
    public static BigDecimal parseDecimalValue(Object o) {
        String s = str(o);
        if (StrUtil.isBlank(s)) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (Exception ignore) {
            // 落到下面的数值截取
        }
        Matcher m = FIRST_NUMBER.matcher(s);
        if (m.find()) {
            try {
                return new BigDecimal(m.group());
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
    }

    /** value 非空且 ≥ threshold */
    public static boolean geThreshold(BigDecimal value, BigDecimal threshold) {
        return value != null && value.compareTo(threshold) >= 0;
    }

    /** PCT 是否达到疑似感染证据的门槛 */
    public static boolean pctSuggestsInfection(BigDecimal pct) {
        return geThreshold(pct, PCT_SUSPECT_THRESHOLD);
    }

    /** 检验项目名 → 指标键（PCT/WBC/CRP/乳酸/肌酐），识别不出返回 null */
    public static String matchLabKey(String itemName) {
        String s = itemName == null ? "" : itemName.trim();
        // 先排除易混淆项目：乳酸脱氢酶（非血乳酸）、尿素/肌酐（比值）、尿常规/白细胞分类计数（非血常规白细胞总数）
        if (s.contains("脱氢酶") || s.contains("尿素/肌酐")) {
            return null;
        }
        if (s.contains("降钙素原")) return "PCT";
        if (s.contains("白细胞") && !s.contains("尿") && !s.contains("分类")) return "WBC";
        if (s.contains("C反应蛋白") || s.contains("超敏C")) return "CRP";
        if (s.contains("乳酸")) return "乳酸";
        if (s.contains("肌酐")) return "肌酐";
        String upper = s.toUpperCase();
        if (upper.equals("PCT")) return "PCT";
        if (upper.equals("WBC")) return "WBC";
        if (upper.equals("CRP")) return "CRP";
        if (upper.contains("CREA") || upper.contains("CR") && upper.length() <= 4) return "肌酐";
        return null;
    }

    /** 医嘱名是否为抗菌药 */
    public static boolean matchesAbx(String name) {
        if (StrUtil.isBlank(name)) {
            return false;
        }
        for (String kw : ABX_KEYWORDS) {
            if (name.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /** 医嘱名是否为溶媒（同一 group 下溶媒与溶质分开存储，只取溶质） */
    public static boolean isSolvent(String name) {
        if (StrUtil.isBlank(name)) {
            return false;
        }
        for (String kw : SOLVENT_KEYWORDS) {
            if (name.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsAny(String s, String... keys) {
        if (StrUtil.isBlank(s)) {
            return false;
        }
        for (String k : keys) {
            if (s.contains(k)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsIgnoreCase(String s, String... keys) {
        if (StrUtil.isBlank(s)) {
            return false;
        }
        String lower = s.toLowerCase();
        for (String k : keys) {
            if (lower.contains(k.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 判定规则
    // ------------------------------------------------------------------

    /**
     * 从诊断推断感染类型（优先级：脓毒 &gt; HAP/VAP &gt; CAP &gt; 腹腔 &gt; 血流 &gt; 尿路 &gt; 真菌 &gt; 其他感染）。
     *
     * <p>诊断需按时间倒序传入，命中第一条匹配即返回，同时带回该条诊断原文与时间作为判定依据
     * ——列表上感染类型只是个标签，医生看到第一反应是"凭什么这么判"。
     *
     * @param shockFlag patient_info.is_sepsis_shock 标记，用于无感染诊断时的兜底归类
     */
    public static InfectionResult inferInfection(List<Map<String, Object>> diagnoses, boolean shockFlag) {
        if (diagnoses != null) {
            for (Map<String, Object> d : diagnoses) {
                if (d == null) {
                    continue;
                }
                String name = str(d.get("diag_name"));
                if (StrUtil.isBlank(name)) {
                    continue;
                }
                if (containsAny(name, "脓毒", "感染性休克")) {
                    return infection("脓毒症/感染性休克", name, d.get("diag_time"));
                }
                if (name.contains("肺炎")) {
                    if (containsAny(name, "医院获得", "呼吸机", "医院", "院内")) {
                        return infection("医院获得性肺炎（HAP/VAP）", name, d.get("diag_time"));
                    }
                    return infection("社区获得性肺炎（CAP）", name, d.get("diag_time"));
                }
                if (containsAny(name, "腹腔", "腹膜炎", "胆道")) {
                    return infection("腹腔感染", name, d.get("diag_time"));
                }
                if (containsAny(name, "血流", "菌血症", "败血症")) {
                    return infection("血流感染", name, d.get("diag_time"));
                }
                if (containsAny(name, "尿路", "泌尿", "肾盂")) {
                    return infection("尿路感染", name, d.get("diag_time"));
                }
                if (containsAny(name, "真菌", "念珠菌", "曲霉")) {
                    return infection("侵袭性真菌感染", name, d.get("diag_time"));
                }
                if (name.contains("感染")) {
                    return infection(name.length() > 30 ? "感染（部位待明确）" : name, name, d.get("diag_time"));
                }
            }
        }
        if (shockFlag) {
            return new InfectionResult("脓毒症/感染性休克",
                    "无感染相关诊断，依据患者主表脓毒性休克标记归类");
        }
        return new InfectionResult("感染（部位待明确）",
                "无明确感染部位诊断；入列依据为 PCT 升高等感染相关检验");
    }

    /**
     * 判定休克类型。
     *
     * <p>诊断文字优先（脓毒性休克 / 感染性休克），都不匹配时看 patient_info.is_sepsis_shock 标记。
     * 标记位本身就是"脓毒性休克"的临床结论，不能因为诊断措辞不同就丢掉——
     * 丢了会让风险等级掉到中/低，决策页也走不到脓毒性休克分层推荐。
     */
    public static ShockResult shock(List<Map<String, Object>> diagnoses, boolean shockFlag) {
        if (diagnoses != null) {
            for (Map<String, Object> d : diagnoses) {
                if (d == null) {
                    continue;
                }
                String name = str(d.get("diag_name"));
                if (StrUtil.isBlank(name)) {
                    continue;
                }
                if (name.contains("脓毒性休克")) {
                    return new ShockResult(true, "septic");
                }
                if (name.contains("感染性休克")) {
                    return new ShockResult(true, "infectious");
                }
            }
        }
        if (shockFlag) {
            return new ShockResult(true, "septic");
        }
        return new ShockResult(false, "none");
    }

    /** 培养/药敏结果增强风险判断：菌名或药敏中含耐药关键词 → MRSA / MDR / 真菌 */
    public static CultureRisk cultureRisk(List<Map<String, Object>> microRows) {
        boolean mrsa = false;
        boolean mdr = false;
        boolean fungal = false;
        if (microRows == null) {
            return new CultureRisk(false, false, false);
        }
        for (Map<String, Object> r : microRows) {
            if (r == null) {
                continue;
            }
            String combined = str(r.get("item_name")) + " " + str(r.get("result"));
            if (containsIgnoreCase(combined, "MRSA", "耐甲氧西林", "甲氧西林耐药")) {
                mrsa = true;
            }
            if (containsIgnoreCase(combined, "ESBL", "CRE", "CRKP", "CRAB", "耐碳青霉烯",
                    "碳青霉烯耐药", "鲍曼", "铜绿假单胞", "泛耐药", "MDR")) {
                mdr = true;
            }
            if (containsIgnoreCase(combined, "念珠菌", "曲霉", "隐球菌", "真菌")) {
                fungal = true;
            }
        }
        return new CultureRisk(mrsa, mdr, fungal);
    }

    /**
     * 风险分层（多因素）。
     *
     * <p>只看"休克 / PCT"会出现「标签栏三个高危（MDR+MRSA+真菌）全亮、风险等级却是低风险」
     * 这种自相矛盾的显示——医生看列表首先扫的就是风险等级，标签与等级打架会直接削弱信任。
     *
     * <p>现行口径：
     * <ul>
     *   <li>高风险：脓毒性休克 / PCT ≥ 2 / MDR 合并 MRSA / MDR 合并真菌风险</li>
     *   <li>中风险：PCT 0.5~2 / MDR、MRSA、真菌任一风险 / HAP-VAP / 血流感染</li>
     *   <li>低风险：单纯疑似感染，暂无上述高危指标</li>
     * </ul>
     */
    public static String evaluateRisk(BigDecimal pct, boolean septicShock,
                                      boolean mdr, boolean mrsa, boolean fungal, String infectionType) {
        if (septicShock) {
            return "高风险";
        }
        if (geThreshold(pct, PCT_HIGH_THRESHOLD)) {
            return "高风险";
        }
        if (mdr && (mrsa || fungal)) {
            return "高风险";
        }
        if (geThreshold(pct, PCT_SUSPECT_THRESHOLD) || mdr || mrsa || fungal) {
            return "中风险";
        }
        String type = infectionType == null ? "" : infectionType;
        if (type.contains("HAP") || type.contains("VAP") || type.contains("血流")) {
            return "中风险";
        }
        return "低风险";
    }

    private static InfectionResult infection(String type, String diagName, Object diagTime) {
        String when = str(diagTime);
        if (when.length() > 16) {
            when = when.substring(0, 16);
        }
        String evidence = StrUtil.isBlank(when)
                ? "诊断：" + diagName
                : "诊断：" + diagName + "（" + when + "）";
        return new InfectionResult(type, evidence);
    }

    /** 感染类型判定结果 */
    public static final class InfectionResult {
        private final String type;
        private final String evidence;

        public InfectionResult(String type, String evidence) {
            this.type = type;
            this.evidence = evidence;
        }

        public String getType() {
            return type;
        }

        public String getEvidence() {
            return evidence;
        }
    }

    /** 休克判定结果 */
    public static final class ShockResult {
        private final boolean septic;
        private final String type;

        public ShockResult(boolean septic, String type) {
            this.septic = septic;
            this.type = type;
        }

        public boolean isSeptic() {
            return septic;
        }

        public String getType() {
            return type;
        }
    }

    /** 培养耐药风险 */
    public static final class CultureRisk {
        private final boolean mrsa;
        private final boolean mdr;
        private final boolean fungal;

        public CultureRisk(boolean mrsa, boolean mdr, boolean fungal) {
            this.mrsa = mrsa;
            this.mdr = mdr;
            this.fungal = fungal;
        }

        public boolean isMrsa() {
            return mrsa;
        }

        public boolean isMdr() {
            return mdr;
        }

        public boolean isFungal() {
            return fungal;
        }
    }

    /** 空诊断列表的便捷常量（避免调用方到处判 null） */
    public static List<Map<String, Object>> emptyDiagnoses() {
        return Collections.emptyList();
    }
}
