package com.zing.doctor.module.sedation.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zing.doctor.module.sedation.dto.CamPoint;
import com.zing.doctor.module.sedation.dto.SedationMonitorResponse.CamBlock;
import com.zing.doctor.module.sedation.dto.SedationMonitorResponse.ScaleBlock;
import com.zing.doctor.module.sedation.dto.SedationPoint;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 镇痛镇静 / 谵妄评分文书解析器（纯逻辑，无 IO，便于单元测试）。
 *
 * <p>输入 doc 三表 JOIN 后的原始行（一次批量查询），输出 NRS / CPOT / RASS / CAM 四个区块。
 * 所有医疗口径以 docs/20、docs/21 为准：
 * <ul>
 *   <li>NRS 0-10（能自述）、CPOT 0-8（无法自述 / 机械通气，四维度各 0-2）、RASS -5~+4。</li>
 *   <li>NRS 与 CPOT 独立，不合并、不求混合均值；RASS 保留负值。</li>
 *   <li>CAM 为分类：源值原样保留，RASS -4/-5 下录得的「无谵妄」派生 UTA_SUSPECT（-3 仍可评估）。</li>
 *   <li>阈值类硬预警（NRS≥4、CPOT&gt;2、过度镇静等）本期不启用，本类只做事实解析与状态分类。</li>
 * </ul>
 */
public final class SedationScoreParser {

    public static final String DOC_NRS = "Z_ICU_SCORE_NRS";
    public static final String DOC_CPOT = "Z_ICU_SCORE_CPOT";
    public static final String DOC_RASS = "Z_ICU_RASS";
    public static final String DOC_CAM = "Z_ICU_ZWPF_CAM";

    /** CAM 可评估性配对：评估时间之前、该小时数以内的最近一次 RASS 视为有效配对（待临床确认）。 */
    public static final int DEFAULT_RASS_PAIR_LOOKBACK_HOURS = 12;

    private static final ObjectMapper OM = new ObjectMapper();

    private SedationScoreParser() {}

    /** 内部携带时间的评估点，用于窗口过滤与 CAM-RASS 配对（不对外序列化）。 */
    private static final class TimedPoint {
        final SedationPoint point;
        final LocalDateTime time;
        TimedPoint(SedationPoint point, LocalDateTime time) { this.point = point; this.time = time; }
    }

    /**
     * 解析整批原始行。
     *
     * @param rows          doc 三表 JOIN 行，时间范围为 [displayStart - rassLookback, displayEnd)
     * @param displayStart  展示窗口开始（含）；此前的 RASS 仅用于 CAM 配对，不展示
     * @param displayEnd    展示窗口结束（不含）
     * @param rassLookback  RASS 为配对 CAM 前推的小时数
     */
    public static Result parse(List<Map<String, Object>> rows,
                               LocalDateTime displayStart, LocalDateTime displayEnd,
                               int rassLookback) {
        Result r = new Result();
        List<TimedPoint> rassAll = new ArrayList<>();   // 含前推扩展窗，配对用
        List<TimedPoint> nrsAll = new ArrayList<>();
        List<TimedPoint> cpotAll = new ArrayList<>();
        List<Map<String, Object>> camInWindow = new ArrayList<>();  // CAM 仅展示窗口内

        if (rows != null) {
            for (Map<String, Object> row : rows) {
                String docCode = str(row.get("doc_code"));
                LocalDateTime t = toTime(row.get("record_time"));
                if (docCode == null || t == null) continue;
                boolean inWindow = !t.isBefore(displayStart) && t.isBefore(displayEnd);
                switch (docCode) {
                    case DOC_RASS:
                        rassAll.add(new TimedPoint(parseRass(row), t));
                        break;
                    case DOC_NRS:
                        if (inWindow) nrsAll.add(new TimedPoint(parseNrs(row), t));
                        break;
                    case DOC_CPOT:
                        if (inWindow) cpotAll.add(new TimedPoint(parseCpot(row), t));
                        break;
                    case DOC_CAM:
                        if (inWindow) camInWindow.add(row);
                        break;
                    default:
                }
            }
        }

        // RASS 展示点只取展示窗口；配对用 rassAll（含前推）
        List<TimedPoint> rassDisplay = new ArrayList<>();
        for (TimedPoint tp : rassAll) {
            if (!tp.time.isBefore(displayStart) && tp.time.isBefore(displayEnd)) rassDisplay.add(tp);
        }

        r.nrs = buildNumericBlock(nrsAll, false);
        r.cpot = buildNumericBlock(cpotAll, true);
        r.rass = buildNumericBlock(rassDisplay, false);
        r.cam = buildCamBlock(camInWindow, rassAll, rassLookback);
        return r;
    }

    // ------------------------------------------------------------------
    // 各量表解析
    // ------------------------------------------------------------------

    private static SedationPoint parseNrs(Map<String, Object> row) {
        SedationPoint p = basePoint(DOC_NRS, row);
        JsonNode node = readJson(p.getRawJson());
        Integer total = firstIntRange(node, "totalScore", 0, 10);
        if (total == null) total = intRange(str(row.get("score_value")), 0, 10);
        p.setScore(total);
        return p;
    }

    private static SedationPoint parseRass(Map<String, Object> row) {
        SedationPoint p = basePoint(DOC_RASS, row);
        JsonNode node = readJson(p.getRawJson());
        Integer total = firstIntRange(node, "totalScore", -5, 4);
        if (total == null) total = intRange(str(row.get("score_value")), -5, 4);
        p.setScore(total);
        p.setLevelText(total == null ? null : rassLevelText(total));
        return p;
    }

    private static SedationPoint parseCpot(Map<String, Object> row) {
        SedationPoint p = basePoint(DOC_CPOT, row);
        JsonNode node = readJson(p.getRawJson());
        Integer face = firstIntRange(node, "item1", 0, 2);
        Integer movement = firstIntRange(node, "item2", 0, 2);
        Integer muscle = firstIntRange(node, "item3", 0, 2);
        // 第 4 维度：插管 / 气切走 item4（通气顺应性），否则 item5（发声 / 发声抵抗）
        Integer item4 = firstIntRange(node, "item4", 0, 2);
        Integer item5 = firstIntRange(node, "item5", 0, 2);
        Integer ventOrVocal = item4 != null ? item4 : item5;
        Boolean ventilated = item4 != null ? Boolean.TRUE : (item5 != null ? Boolean.FALSE : null);

        Map<String, Object> dims = new LinkedHashMap<>();
        if (face != null) dims.put("face", face);
        if (movement != null) dims.put("movement", movement);
        if (muscle != null) dims.put("muscle", muscle);
        if (ventOrVocal != null) dims.put("ventOrVocal", ventOrVocal);
        // item60 的 a/d 选项码权威中文待护理部 / 厂商确认，原样保留不参与分值
        if (node != null && node.hasNonNull("item60")) dims.put("item60", node.get("item60").asText(""));
        if (!dims.isEmpty()) p.setDimensions(dims);
        p.setVentilated(ventilated);

        Integer total = firstIntRange(node, "totalScore", 0, 8);
        if (total == null) {
            // 无总分但四维度齐全则求和
            if (face != null && movement != null && muscle != null && ventOrVocal != null) {
                total = face + movement + muscle + ventOrVocal;
            }
        }
        p.setScore(total);
        return p;
    }

    private static CamPoint parseCam(Map<String, Object> row, List<TimedPoint> rassAll,
                              LocalDateTime camTime, int lookbackHours) {
        CamPoint c = new CamPoint();
        c.setTime(toIso(camTime));
        c.setStaffName(str(row.get("staff_name")));
        c.setAudited(toBool(row.get("is_audit")));
        String rawResult = str(row.get("score_value"));
        String json = str(row.get("score_json"));
        c.setRawJson(json);

        // 源值缺 score_value 时回退 json.item1：1=谵妄，0=无谵妄
        boolean positiveRaw;
        boolean recognized;
        if (rawResult != null && (rawResult.contains("谵妄"))) {
            positiveRaw = !rawResult.contains("无");
            recognized = true;
        } else {
            JsonNode node = readJson(json);
            Integer item1 = node != null && node.hasNonNull("item1") ? intRange(node.get("item1").asText(""), 0, 1) : null;
            if (item1 != null) {
                positiveRaw = item1 == 1;
                recognized = true;
                if (rawResult == null) rawResult = positiveRaw ? "谵妄" : "无谵妄";
            } else {
                recognized = false;
                positiveRaw = false;
            }
        }
        c.setRawResult(rawResult);
        if (!recognized) {
            c.setDerivedStatus("UNPARSED");
            c.setHint("源结果无法识别为「谵妄 / 无谵妄」，请核对原始文书");
            return c;
        }

        // 配对评估时间之前最近、lookback 小时内的 RASS
        TimedPoint pair = latestRassBefore(rassAll, camTime, lookbackHours);
        if (pair != null && pair.point.getScore() != null) {
            c.setPairedRass(pair.point.getScore());
            c.setPairedRassTime(pair.point.getTime());
        }

        if (positiveRaw) {
            // 以源为准：阳性即 POSITIVE，不被镇静深度覆盖
            c.setDerivedStatus("POSITIVE");
            return c;
        }
        // 源为「无谵妄」：依据配对 RASS 判断是否有效阴性
        if (pair == null || pair.point.getScore() == null) {
            c.setDerivedStatus("PAIR_UNKNOWN");
            c.setHint("未找到评估前 " + lookbackHours + " 小时内的 RASS 记录，"
                    + "无法确认本次评估是否处于可评估镇静深度（RASS -3～+4）");
            return c;
        }
        int rass = pair.point.getScore();
        if (rass <= -4) {
            c.setDerivedStatus("UTA_SUSPECT");
            c.setHint("评估时 RASS=" + rass + "（" + rassLevelText(rass)
                    + "），按 CAM-ICU 流程该镇静深度无法评估谵妄；源系统录为「无谵妄」，建议核查");
        } else {
            c.setDerivedStatus("NEGATIVE");
        }
        return c;
    }

    // ------------------------------------------------------------------
    // 区块组装
    // ------------------------------------------------------------------

    private static ScaleBlock buildNumericBlock(List<TimedPoint> all, boolean cpot) {
        ScaleBlock block = new ScaleBlock();
        List<SedationPoint> points = new ArrayList<>();
        boolean partial = false;
        Integer min = null, max = null;
        SedationPoint latest = null;
        LocalDateTime latestTime = null;
        for (TimedPoint tp : all) {
            SedationPoint p = tp.point;
            if (p == null) continue;
            points.add(p);
            if (cpot && p.getScore() != null && (p.getDimensions() == null || p.getDimensions().size() < 4)) {
                // CPOT 有总分但四维度不全（item60 不计入四维度）
                long dimCount = p.getDimensions() == null ? 0
                        : p.getDimensions().keySet().stream().filter(k -> !"item60".equals(k)).count();
                if (dimCount < 4) partial = true;
            }
            if (p.getScore() != null) {
                if (min == null || p.getScore() < min) min = p.getScore();
                if (max == null || p.getScore() > max) max = p.getScore();
            }
            if (latestTime == null || tp.time.isAfter(latestTime)) { latestTime = tp.time; latest = p; }
        }
        block.setPoints(points);
        block.setCount(points.size());
        block.setMin(min);
        block.setMax(max);
        block.setLatest(latest);
        if (points.isEmpty()) {
            block.setDataStatus("NOT_RECORDED");
        } else {
            block.setDataStatus(partial ? "PARTIAL" : "FOUND");
        }
        return block;
    }

    private static CamBlock buildCamBlock(List<Map<String, Object>> camInWindow,
                                         List<TimedPoint> rassAll, int lookbackHours) {
        CamBlock block = new CamBlock();
        List<CamPoint> points = new ArrayList<>();
        for (Map<String, Object> row : camInWindow) {
            LocalDateTime t = toTime(row.get("record_time"));
            if (t == null) continue;
            points.add(parseCam(row, rassAll, t, lookbackHours));
        }
        points.sort((a, b) -> a.getTime().compareTo(b.getTime()));
        int total = 0, positive = 0, negative = 0, uta = 0, pairUnknown = 0, unparsed = 0;
        for (CamPoint c : points) {
            total++;
            switch (c.getDerivedStatus()) {
                case "POSITIVE": positive++; break;
                case "NEGATIVE": negative++; break;
                case "UTA_SUSPECT": uta++; break;
                case "PAIR_UNKNOWN": pairUnknown++; break;
                default: unparsed++; break;
            }
        }
        block.setPoints(points);
        block.setTotal(total);
        block.setPositive(positive);
        block.setNegative(negative);
        block.setUtaSuspect(uta);
        block.setPairUnknown(pairUnknown);
        block.setUnparsed(unparsed);
        block.setDataStatus(points.isEmpty() ? "NOT_RECORDED"
                : (unparsed > 0 || pairUnknown > 0 ? "PARTIAL" : "FOUND"));
        return block;
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private static SedationPoint basePoint(String docCode, Map<String, Object> row) {
        SedationPoint p = new SedationPoint();
        p.setDocCode(docCode);
        LocalDateTime t = toTime(row.get("record_time"));
        p.setTime(toIso(t));
        p.setStaffName(str(row.get("staff_name")));
        p.setAudited(toBool(row.get("is_audit")));
        p.setRawScore(str(row.get("score_value")));
        p.setRawJson(str(row.get("score_json")));
        return p;
    }

    private static TimedPoint latestRassBefore(List<TimedPoint> rassAll, LocalDateTime camTime, int lookbackHours) {
        TimedPoint best = null;
        for (TimedPoint tp : rassAll) {
            if (tp.point.getScore() == null) continue;
            if (tp.time.isAfter(camTime)) continue;
            if (tp.time.isBefore(camTime.minusHours(lookbackHours))) continue;
            if (best == null || tp.time.isAfter(best.time)) best = tp;
        }
        return best;
    }

    public static String rassLevelText(int v) {
        switch (v) {
            case 4: return "有攻击性";
            case 3: return "非常躁动";
            case 2: return "躁动";
            case 1: return "不安";
            case 0: return "清醒平静";
            case -1: return "昏昏欲睡";
            case -2: return "轻度镇静";
            case -3: return "中度镇静";
            case -4: return "重度镇静";
            case -5: return "不能唤醒";
            default: return String.valueOf(v);
        }
    }

    private static JsonNode readJson(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return OM.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer firstIntRange(JsonNode node, String key, int lo, int hi) {
        if (node == null || !node.hasNonNull(key)) return null;
        return intRange(node.get(key).asText(""), lo, hi);
    }

    private static Integer intRange(String s, int lo, int hi) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty() || !t.matches("-?\\d+")) return null;
        try {
            int v = Integer.parseInt(t);
            return (v >= lo && v <= hi) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private static Boolean toBool(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof Number) return ((Number) o).intValue() != 0;
        String s = String.valueOf(o).trim();
        if ("1".equals(s) || "true".equalsIgnoreCase(s)) return Boolean.TRUE;
        if ("0".equals(s) || "false".equalsIgnoreCase(s)) return Boolean.FALSE;
        return null;
    }

    private static LocalDateTime toTime(Object o) {
        if (o == null) return null;
        if (o instanceof Timestamp) return ((Timestamp) o).toLocalDateTime();
        if (o instanceof LocalDateTime) return (LocalDateTime) o;
        if (o instanceof java.util.Date) return new Timestamp(((java.util.Date) o).getTime()).toLocalDateTime();
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) return null;
        try {
            // ISO（LocalDateTime.toString）或 "yyyy-MM-dd HH:mm:ss[.0]"
            return LocalDateTime.parse(s.replace(' ', 'T').replaceFirst("\\.\\d+$", ""));
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(s.replace(' ', 'T').replaceFirst("\\.\\d+$", ""),
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private static String toIso(LocalDateTime t) {
        return t == null ? null : t.toString();
    }

    /** 解析结果持有者。 */
    public static final class Result {
        public ScaleBlock nrs;
        public ScaleBlock cpot;
        public ScaleBlock rass;
        public CamBlock cam;
    }
}
