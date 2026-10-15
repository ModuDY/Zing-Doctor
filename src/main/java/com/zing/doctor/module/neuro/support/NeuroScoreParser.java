package com.zing.doctor.module.neuro.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zing.doctor.module.neuro.dto.NeuroMonitorResponse.ConsciousPoint;
import com.zing.doctor.module.neuro.dto.NeuroMonitorResponse.ConsciousnessBlock;
import com.zing.doctor.module.neuro.dto.NeuroMonitorResponse.Diagnosis;
import com.zing.doctor.module.neuro.dto.NeuroMonitorResponse.DiagnosisBlock;
import com.zing.doctor.module.neuro.dto.NeuroMonitorResponse.FourBlock;
import com.zing.doctor.module.neuro.dto.NeuroMonitorResponse.FourPoint;
import com.zing.doctor.module.neuro.dto.NeuroMonitorResponse.GcsBlock;
import com.zing.doctor.module.neuro.dto.NeuroMonitorResponse.GcsPoint;
import com.zing.doctor.module.neuro.dto.NeuroMonitorResponse.IcpBlock;
import com.zing.doctor.module.neuro.dto.NeuroMonitorResponse.IcpPoint;
import com.zing.doctor.module.neuro.dto.NeuroMonitorResponse.PupilBlock;
import com.zing.doctor.module.neuro.dto.NeuroMonitorResponse.PupilPoint;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 神经功能监测数据解析（纯静态、无副作用）。
 *
 * <p>口径（实施方案 docs/20、阶段0复核 docs/21）：
 * <ul>
 *   <li>急性脑损伤按 ICD-10 编码前缀识别：I60 蛛网膜下腔出血 / I61 脑出血 / I62 其他非创伤性颅内出血
 *       （含硬膜下、硬膜外）/ I63 脑梗死 / S06 创伤性；排除 I69 后遗症、Z86.7 个人史、G45 短暂性脑缺血发作，
 *       以及「鉴别 / 待排 / 否认 / 既往 / 排除」类诊断。</li>
 *   <li>神志 1-10 为类别编码，仅 1神清→2嗜睡→3浅昏迷→4中昏迷→5深昏迷 主线用于昏迷加深提示，
 *       6谵妄 / 7镇静 / 8昏睡 / 9朦胧 / 10麻醉未清醒 单独展示，不按编码差判恶化。</li>
 *   <li>瞳孔值 {@code mm/反射}，直径含 0.5 步长；不规则 / 无法观察为不可测，不参与左右差计算。</li>
 *   <li>GCS item1=E、item2/item21=V、item3=M；item21="VT" 表示插管 / 气切、言语不可评，
 *       总分按 E+M 并标 T（如 3T），不把占位 item2=1 当言语分，不反哺 SOFA。</li>
 * </ul>
 */
public final class NeuroScoreParser {

    public static final String ITEM_SHENZHI = "oi_shenzhi";
    public static final String ITEM_PUPIL_LEFT = "oi_tkz";
    public static final String ITEM_PUPIL_RIGHT = "oi_tky";
    public static final String ITEM_ICP = "oi_lny";

    /** FOUR 评分文书编码（按 Z_ICU_GCS 命名惯例，待护理部 / 源系统确认后生效）。 */
    public static final String DOC_FOUR = "Z_ICU_FOUR";

    public static final String CAT_SAH = "SAH";
    public static final String CAT_ICH = "ICH";
    public static final String CAT_OTHER_NONTRAUMATIC = "OTHER_NONTRAUMATIC";
    public static final String CAT_ISCHEMIC = "ISCHEMIC";
    public static final String CAT_TRAUMATIC = "TRAUMATIC";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** 神志类别码 → 文本（与重症系统监测评估弹窗一致）。 */
    private static final Map<Integer, String> SHENZHI_TEXT = new LinkedHashMap<>();
    static {
        SHENZHI_TEXT.put(1, "神清");
        SHENZHI_TEXT.put(2, "嗜睡");
        SHENZHI_TEXT.put(3, "浅昏迷");
        SHENZHI_TEXT.put(4, "中昏迷");
        SHENZHI_TEXT.put(5, "深昏迷");
        SHENZHI_TEXT.put(6, "谵妄");
        SHENZHI_TEXT.put(7, "镇静");
        SHENZHI_TEXT.put(8, "昏睡");
        SHENZHI_TEXT.put(9, "朦胧");
        SHENZHI_TEXT.put(10, "麻醉未清醒");
    }

    /** 主类别优先级（数字小者优先作为患者主类别）：创伤 > 脑出血 > SAH > 其他非创伤 > 缺血。 */
    private static final Map<String, Integer> CATEGORY_PRIORITY = new LinkedHashMap<>();
    static {
        CATEGORY_PRIORITY.put(CAT_TRAUMATIC, 1);
        CATEGORY_PRIORITY.put(CAT_ICH, 2);
        CATEGORY_PRIORITY.put(CAT_SAH, 3);
        CATEGORY_PRIORITY.put(CAT_OTHER_NONTRAUMATIC, 4);
        CATEGORY_PRIORITY.put(CAT_ISCHEMIC, 5);
    }

    private NeuroScoreParser() {
    }

    // ============================ 诊断识别 ============================

    public static DiagnosisBlock parseDiagnoses(List<Map<String, Object>> rows) {
        DiagnosisBlock block = new DiagnosisBlock();
        List<Diagnosis> hits = new ArrayList<>();
        Map<String, Diagnosis> dedup = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String code = asStr(row.get("diag_code"));
            String name = asStr(row.get("diag_name"));
            String typeName = asStr(row.get("diag_type_name"));
            String category = classify(code, name, typeName);
            if (category == null) {
                continue;
            }
            Diagnosis d = new Diagnosis();
            d.setDiagCode(code);
            d.setDiagName(name);
            d.setDiagTypeName(typeName);
            d.setDiagKindName(asStr(row.get("diag_kind_name")));
            d.setDiagTime(toIso(row.get("diag_time")));
            d.setCategory(category);
            d.setCategoryName(categoryName(category));
            String key = (code == null ? "" : code) + "|" + (name == null ? "" : name);
            // 同一诊断多条（入院/初步/修正）去重，保留首次遇到（查询已按时间倒序，即最新一条）
            dedup.putIfAbsent(key, d);
        }
        hits.addAll(dedup.values());
        block.setItems(hits);
        block.setBrainInjury(!hits.isEmpty());
        if (!hits.isEmpty()) {
            String primary = hits.stream()
                    .map(Diagnosis::getCategory)
                    .min(Comparator.comparingInt(c -> CATEGORY_PRIORITY.getOrDefault(c, 99)))
                    .orElse(null);
            block.setCategory(primary);
            block.setCategoryName(categoryName(primary));
        }
        block.setNote("按 ICD-10 编码识别急性脑损伤（I60-I63 脑血管病、S06 创伤性颅脑损伤）；"
                + "已排除脑梗死后遗症(I69)、脑梗死个人史(Z86.7)、短暂性脑缺血发作(G45) 及鉴别/待排/否认/既往诊断。");
        return block;
    }

    /** 返回急性脑损伤类别码；不属于或应排除返回 null。 */
    private static String classify(String code, String name, String typeName) {
        if (typeName != null && containsAny(typeName, "鉴别")) {
            return null;
        }
        if (name != null && containsAny(name, "待排", "待除外", "排除", "否认", "既往", "个人史", "后遗症")) {
            return null;
        }
        String c = code == null ? "" : code.trim().toUpperCase();
        if (c.isEmpty()) {
            return nameKeywordFallback(name);
        }
        // 排除项优先
        if (c.startsWith("I69") || c.startsWith("Z86.7") || c.startsWith("G45")) {
            return null;
        }
        if (c.startsWith("I60")) return CAT_SAH;
        if (c.startsWith("I61")) return CAT_ICH;
        if (c.startsWith("I62")) return CAT_OTHER_NONTRAUMATIC;
        if (c.startsWith("I63")) return CAT_ISCHEMIC;
        if (c.startsWith("S06")) return CAT_TRAUMATIC;
        // 编码未命中前缀时，用名称关键词兜底（仅在确有急性脑损伤词时）
        return nameKeywordFallback(name);
    }

    private static String nameKeywordFallback(String name) {
        if (name == null) {
            return null;
        }
        if (containsAny(name, "创伤性", "脑挫伤", "弥漫性轴索", "脑疝", "颅内积气", "创伤性脑水肿")) {
            return CAT_TRAUMATIC;
        }
        if (containsAny(name, "蛛网膜下腔出血")) {
            return CAT_SAH;
        }
        if (containsAny(name, "脑干出血", "脑室出血", "脑出血", "基底节出血", "小脑出血", "丘脑出血")) {
            return CAT_ICH;
        }
        if (containsAny(name, "硬膜下血肿", "硬膜外血肿", "硬膜下出血", "硬膜外出血", "颅内血肿", "颅内出血")) {
            return CAT_OTHER_NONTRAUMATIC;
        }
        if (containsAny(name, "脑梗死", "脑梗塞", "脑栓塞", "脑血栓形成", "腔隙性脑梗死", "大面积脑梗死")) {
            return CAT_ISCHEMIC;
        }
        return null;
    }

    public static String categoryName(String category) {
        if (category == null) {
            return null;
        }
        switch (category) {
            case CAT_SAH: return "蛛网膜下腔出血";
            case CAT_ICH: return "脑出血";
            case CAT_OTHER_NONTRAUMATIC: return "非创伤性颅内出血";
            case CAT_ISCHEMIC: return "缺血性脑卒中（脑梗死）";
            case CAT_TRAUMATIC: return "创伤性颅脑损伤";
            default: return category;
        }
    }

    // ============================ observe 分流 ============================

    public static final class ObserveResult {
        public final ConsciousnessBlock consciousness = new ConsciousnessBlock();
        public final PupilBlock pupils = new PupilBlock();
        public final IcpBlock icp = new IcpBlock();
    }

    public static ObserveResult parseObserve(List<Map<String, Object>> rows) {
        ObserveResult r = new ObserveResult();
        List<Map<String, Object>> shenzhi = new ArrayList<>();
        List<Map<String, Object>> pupilL = new ArrayList<>();
        List<Map<String, Object>> pupilR = new ArrayList<>();
        List<Map<String, Object>> icp = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String code = asStr(row.get("item_code"));
            if (code == null) {
                continue;
            }
            switch (code) {
                case ITEM_SHENZHI: shenzhi.add(row); break;
                case ITEM_PUPIL_LEFT: pupilL.add(row); break;
                case ITEM_PUPIL_RIGHT: pupilR.add(row); break;
                case ITEM_ICP: icp.add(row); break;
                default:
            }
        }
        fillConsciousness(r.consciousness, shenzhi);
        fillPupils(r.pupils, pupilL, pupilR);
        fillIcp(r.icp, icp);
        return r;
    }

    private static void fillConsciousness(ConsciousnessBlock block, List<Map<String, Object>> rows) {
        List<ConsciousPoint> points = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String raw = asStr(row.get("item_value"));
            Integer code = parseIntOrNull(raw);
            if (code == null || !SHENZHI_TEXT.containsKey(code)) {
                continue;
            }
            ConsciousPoint p = new ConsciousPoint();
            p.setTime(toIso(row.get("item_time")));
            p.setStaffName(asStr(row.get("staff_name")));
            p.setCode(code);
            p.setText(SHENZHI_TEXT.get(code));
            p.setMainline(code >= 1 && code <= 5);
            p.setRaw(raw);
            points.add(p);
        }
        block.setPoints(points);
        block.setCount(points.size());
        block.setLatest(points.isEmpty() ? null : points.get(points.size() - 1));
        if (!points.isEmpty()) {
            block.setDataStatus("FOUND");
        }
        block.setNote("神志为类别评估（非连续数值），仅 1神清→2嗜睡→3浅昏迷→4中昏迷→5深昏迷 主线用于昏迷加深提示；"
                + "镇静、麻醉未清醒等为治疗性状态，不按编码差判恶化。");
    }

    private static void fillPupils(PupilBlock block, List<Map<String, Object>> leftRows,
                                   List<Map<String, Object>> rightRows) {
        // 按评估时间配对左右
        TreeMap<String, PupilPoint> byTime = new TreeMap<>();
        for (Map<String, Object> row : leftRows) {
            String t = toIso(row.get("item_time"));
            if (t == null) {
                continue;
            }
            PupilPoint p = byTime.computeIfAbsent(t, k -> {
                PupilPoint np = new PupilPoint();
                np.setTime(k);
                return np;
            });
            p.setStaffName(firstNonBlank(p.getStaffName(), asStr(row.get("staff_name"))));
            String raw = asStr(row.get("item_value"));
            p.setLeftRaw(raw);
            applyPupilSide(raw, true, p);
        }
        for (Map<String, Object> row : rightRows) {
            String t = toIso(row.get("item_time"));
            if (t == null) {
                continue;
            }
            PupilPoint p = byTime.computeIfAbsent(t, k -> {
                PupilPoint np = new PupilPoint();
                np.setTime(k);
                return np;
            });
            p.setStaffName(firstNonBlank(p.getStaffName(), asStr(row.get("staff_name"))));
            String raw = asStr(row.get("item_value"));
            p.setRightRaw(raw);
            applyPupilSide(raw, false, p);
        }
        List<PupilPoint> points = new ArrayList<>(byTime.values());
        for (PupilPoint p : points) {
            if (p.getLeftMm() != null && p.getRightMm() != null) {
                p.setDiffMm(Math.abs(p.getLeftMm() - p.getRightMm()));
            }
        }
        block.setPoints(points);
        block.setCount(points.size());
        block.setLatest(points.isEmpty() ? null : points.get(points.size() - 1));
        if (!points.isEmpty()) {
            boolean anyLeft = points.stream().anyMatch(p -> p.getLeftRaw() != null);
            boolean anyRight = points.stream().anyMatch(p -> p.getRightRaw() != null);
            if (anyLeft && anyRight) {
                block.setDataStatus("FOUND");
            } else {
                block.setDataStatus("PARTIAL");
            }
        }
        block.setNote("瞳孔格式为「直径mm/对光反射」；左右差、对光反射变化仅作事实展示，"
                + "不规则 / 无法观察为不可测，不参与差值计算；预警阈值需科室审批后启用。");
    }

    private static void applyPupilSide(String raw, boolean left, PupilPoint p) {
        if (raw == null || raw.trim().isEmpty()) {
            return;
        }
        String[] parts = raw.split("/", 2);
        String sizePart = parts[0].trim();
        String reflex = parts.length > 1 ? parts[1].trim() : null;
        Double mm = parseDoubleOrNull(sizePart);
        if (mm == null) {
            // 不规则 / 无法观察等非数值直径
            p.setUnobservable(true);
        }
        if (reflex != null && (reflex.contains("无法") || reflex.contains("不规则"))) {
            p.setUnobservable(true);
        }
        if (left) {
            p.setLeftMm(mm);
            p.setLeftReflex(reflex);
        } else {
            p.setRightMm(mm);
            p.setRightReflex(reflex);
        }
    }

    private static void fillIcp(IcpBlock block, List<Map<String, Object>> rows) {
        List<IcpPoint> points = new ArrayList<>();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Map<String, Object> row : rows) {
            String raw = asStr(row.get("item_value"));
            Integer v = parseIntOrNull(raw);
            if (v == null) {
                continue;
            }
            IcpPoint p = new IcpPoint();
            p.setTime(toIso(row.get("item_time")));
            p.setStaffName(asStr(row.get("staff_name")));
            p.setValue(v);
            p.setRaw(raw);
            points.add(p);
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        block.setPoints(points);
        block.setCount(points.size());
        block.setLatest(points.isEmpty() ? null : points.get(points.size() - 1));
        if (!points.isEmpty()) {
            block.setDataStatus("FOUND");
            block.setMin(min);
            block.setMax(max);
        }
        block.setNote("颅内压（mmHg）来自重症系统监测记录，仅颅内监测患者有数据；无记录不等于未监测。"
                + "ICP>22mmHg 的治疗阈值仅适用于重型创伤性脑损伤（BTF 指南），不向所有脑损伤泛化，本期仅展示事实与趋势。");
    }

    // ============================ GCS doc ============================

    public static GcsBlock parseGcs(List<Map<String, Object>> rows) {
        GcsBlock block = new GcsBlock();
        List<GcsPoint> points = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String json = asStr(row.get("score_json"));
            String scoreValue = asStr(row.get("score_value"));
            GcsPoint p = new GcsPoint();
            p.setTime(toIso(row.get("record_time")));
            p.setStaffName(asStr(row.get("staff_name")));
            p.setRawScore(scoreValue);
            p.setRawJson(json);
            Object audited = row.get("is_audit");
            if (audited != null) {
                p.setAudited("1".equals(String.valueOf(audited)));
            }
            boolean parsed = false;
            if (json != null && !json.isEmpty()) {
                try {
                    JsonNode root = JSON.readTree(json);
                    Integer e = intNode(root, "item1");
                    Integer m = intNode(root, "item3");
                    String vRaw = textNode(root, "item21");
                    boolean intubated = "VT".equalsIgnoreCase(vRaw)
                            || (scoreValue != null && scoreValue.replaceAll("\\s", "").toUpperCase().contains("VT"));
                    Integer v = intubated ? null : (vRaw != null ? parseIntOrNull(vRaw) : intNode(root, "item2"));
                    p.setE(e);
                    p.setM(m);
                    p.setV(v);
                    p.setIntubated(intubated);
                    Integer total = null;
                    if (e != null && m != null) {
                        total = intubated ? (e + m) : (v != null ? e + v + m : null);
                    }
                    p.setTotal(total);
                    p.setDisplay(intubated ? (total == null ? "T" : total + "T")
                            : (total == null ? null : String.valueOf(total)));
                    parsed = e != null && m != null;
                } catch (Exception ignore) {
                    parsed = false;
                }
            }
            if (!parsed) {
                // JSON 不可用时尝试从 score_value（如 4+5+6 / 1+VT+2）解析
                parseScoreValueFallback(scoreValue, p);
            }
            if (p.getE() == null || p.getM() == null) {
                continue;
            }
            points.add(p);
        }
        block.setPoints(points);
        block.setCount(points.size());
        block.setLatest(points.isEmpty() ? null : points.get(points.size() - 1));
        if (!points.isEmpty()) {
            boolean allComplete = points.stream().allMatch(p -> !p.isIntubated() && p.getV() != null && p.getTotal() != null)
                    || points.stream().allMatch(p -> p.getTotal() != null);
            block.setDataStatus(allComplete ? "FOUND" : "PARTIAL");
        }
        block.setNote("GCS 来自重症系统正式评分文书（E 睁眼 / V 言语 / M 运动）；插管或气切时言语不可评，"
                + "总分按 E+M 并标 T（如 3T），不把占位言语分计入，也不反哺 SOFA 评分。");
        return block;
    }

    // ============================ FOUR doc ============================

    /**
     * FOUR 评分（Full Outline of UnResponsiveness）文书解析：E 睁眼 0-4 / V 言语 0-5
     * （0=插管气切）/ M 运动 0-6 / B 脑干反射 0-4，总分 0-16。2024 版国家质控指标
     * ICU-18（意识评估率）认可 GCS 或 FOUR；FOUR 对插管患者可评（V 记 0），
     * 弥补 GCS 插管标 T 的缺陷。
     *
     * <p>源文书编码按 Z_ICU_GCS 命名惯例取 {@code Z_ICU_FOUR}，待与护理部确认后
     * 即可出数（未接入时该块 NOT_RECORDED，不出假 0）。JSON 字段沿用 item1-E /
     * item2-V / item3-M / item4-B / totalScore 约定。</p>
     */
    public static FourBlock parseFour(List<Map<String, Object>> rows) {
        FourBlock block = new FourBlock();
        List<FourPoint> points = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                String json = asStr(row.get("score_json"));
                String scoreValue = asStr(row.get("score_value"));
                FourPoint p = new FourPoint();
                p.setTime(toIso(row.get("record_time")));
                p.setStaffName(asStr(row.get("staff_name")));
                p.setRawScore(scoreValue);
                p.setRawJson(json);
                Object audited = row.get("is_audit");
                if (audited != null) {
                    p.setAudited("1".equals(String.valueOf(audited)));
                }
                JsonNode node = null;
                if (json != null && !json.isEmpty()) {
                    try {
                        node = JSON.readTree(json);
                    } catch (Exception ignore) {
                        node = null;
                    }
                }
                Integer e = itemInt(node, "item1", 0, 4);
                Integer v = itemInt(node, "item2", 0, 5);
                Integer m = itemInt(node, "item3", 0, 6);
                Integer b = itemInt(node, "item4", 0, 4);
                if (node == null && scoreValue != null && scoreValue.matches("-?\\d{1,2}")) {
                    try {
                        int t = Integer.parseInt(scoreValue.trim());
                        if (t >= 0 && t <= 16) p.setTotal(t);
                    } catch (NumberFormatException ignored) {
                    }
                } else {
                    Integer total = itemInt(node, "totalScore", 0, 16);
                    if (total == null && e != null && v != null && m != null && b != null) {
                        total = e + v + m + b;
                    }
                    p.setTotal(total);
                }
                p.setE(e);
                p.setV(v);
                p.setM(m);
                p.setB(b);
                if (e == null && v == null && m == null && b == null && p.getTotal() == null) {
                    continue;
                }
                points.add(p);
            }
        }
        block.setPoints(points);
        block.setCount(points.size());
        block.setLatest(points.isEmpty() ? null : points.get(points.size() - 1));
        if (!points.isEmpty()) {
            boolean allComplete = points.stream().allMatch(p -> p.getE() != null && p.getV() != null
                    && p.getM() != null && p.getB() != null && p.getTotal() != null);
            block.setDataStatus(allComplete ? "FOUND" : "PARTIAL");
        }
        block.setNote("FOUR 全面无反应性量表（E 睁眼 / V 言语 / M 运动 / B 脑干反射，0-16）；"
                + "插管 / 气切患者言语记 0，仍可评估 —— 与 GCS 标 T 相比对昏迷患者分级更细"
                + "（2024版 ICU-18 意识评估口径认可 GCS 或 FOUR）。源文书编码待护理部确认。");
        return block;
    }

    private static Integer itemInt(JsonNode node, String key, int lo, int hi) {
        if (node == null || !node.hasNonNull(key)) return null;
        String s = node.get(key).asText("").trim();
        if (!s.matches("-?\\d+")) return null;
        try {
            int v = Integer.parseInt(s);
            return (v >= lo && v <= hi) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void parseScoreValueFallback(String scoreValue, GcsPoint p) {
        if (scoreValue == null) {
            return;
        }
        String s = scoreValue.replaceAll("\\s", "").toUpperCase();
        String[] seg = s.split("\\+");
        try {
            if (seg.length == 3) {
                Integer e = parseIntOrNull(seg[0]);
                Integer m = parseIntOrNull(seg[2]);
                boolean intubated = "VT".equals(seg[1]);
                Integer v = intubated ? null : parseIntOrNull(seg[1]);
                p.setE(e);
                p.setM(m);
                p.setV(v);
                p.setIntubated(intubated);
                if (e != null && m != null) {
                    Integer total = intubated ? e + m : (v != null ? e + v + m : null);
                    p.setTotal(total);
                    p.setDisplay(intubated ? (total == null ? "T" : total + "T")
                            : (total == null ? null : String.valueOf(total)));
                }
            }
        } catch (Exception ignore) {
            // 留待 PARTIAL / NOT_RECORDED
        }
    }

    // ============================ 工具 ============================

    private static boolean containsAny(String text, String... keys) {
        if (text == null) {
            return false;
        }
        for (String k : keys) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a;
        }
        return b;
    }

    private static Integer intNode(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isNumber()) {
            return n.asInt();
        }
        return parseIntOrNull(n.asText());
    }

    private static String textNode(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        String t = n.asText();
        return t == null || t.trim().isEmpty() ? null : t.trim();
    }

    private static Integer parseIntOrNull(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s).intValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseDoubleOrNull(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s).doubleValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String asStr(Object value) {
        if (value == null) {
            return null;
        }
        String s = value instanceof BigDecimal ? ((BigDecimal) value).toPlainString() : String.valueOf(value);
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static String toIso(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime().format(ISO);
        }
        if (value instanceof Date) {
            return new Timestamp(((Date) value).getTime()).toLocalDateTime().format(ISO);
        }
        return String.valueOf(value);
    }
}
