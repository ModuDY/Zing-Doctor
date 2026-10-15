package com.zing.doctor.module.tube.service;

import com.zing.doctor.module.tube.mapper.TubeStatsMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 导管统计聚合 Service。
 *
 * <p>把 Mapper 的原始计数按临床口径归类（CVC / 导尿管 / 气道导管 / 其他），
 * 并基于留置起止时间算平均留置天数与数据覆盖率。只读。
 */
@Service
public class TubeStatsService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 气道导管白名单：气管插管 / 气管切开套管 / 金属气管套管 */
    private static final Set<String> AIRWAY_TUBE_CODES = new HashSet<>(Arrays.asList(
            "tube_气管插管", "tube_气管切开套管", "tube_金属气管套管"));

    /**
     * 字典缺失时的兜底 code，历史上归入气道导管。
     * {@code sql/} 全目录没有 {@code tube_code = '默认'} 的种子数据，来源待确认；
     * 此处保留原行为，只把条件写明确，避免顺带改动既有统计口径。
     */
    private static final String FALLBACK_TUBE_CODE = "默认";

    private final TubeStatsMapper mapper;
    private final TubeGroupConfigService groupConfigService;

    public TubeStatsService(TubeStatsMapper mapper, TubeGroupConfigService groupConfigService) {
        this.mapper = mapper;
        this.groupConfigService = groupConfigService;
    }

    /**
     * @param yearMonth 形如 2026-09
     * @param departCode 已收敛科室；null/ALL 全院
     */
    public Map<String, Object> monthly(String yearMonth, String departCode) {
        LocalDateTime start = LocalDateTime.parse(yearMonth + "-01 00:00:00", FMT);
        LocalDateTime end = start.plusMonths(1);
        Map<String, com.zing.doctor.module.tube.entity.TubeGroupConfig> cfgByCode = tubeConfig();

        List<Map<String, Object>> counts = mapper.monthlyCounts(fmt(start), fmt(end), departCode);
        List<Map<String, Object>> durations = mapper.placementDurations(fmt(start), fmt(end), departCode);

        // 按 tube_code 汇总留置时长，供算平均留置日
        Map<String, double[]> daysByCode = new LinkedHashMap<>(); // [总天数, 有效条数(有上界)]
        for (Map<String, Object> row : durations) {
            String code = str(row.get("tube_code"));
            LocalDateTime s = parse(row.get("start_time"));
            if (s == null) continue;
            LocalDateTime bound = parse(row.get("end_time"));          // 已拔
            if (bound == null) bound = parse(row.get("out_time"));    // 未拔但已出科
            if (bound == null) bound = end;                           // 仍在科：算到月截止
            double days = java.time.Duration.between(s, bound).toMillis() / 86400000.0;
            if (days < 0) days = 0;
            boolean hasUpper = row.get("end_time") != null || row.get("out_time") != null;
            double[] agg = daysByCode.computeIfAbsent(code, k -> new double[3]);
            agg[0] += days;                       // 总留置天
            agg[1] += 1;                          // 新置管条数
            if (hasUpper) agg[2] += 1;             // 有明确上界（已拔或已出科）
        }

        // 组装按类型明细
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, double[]> total = new LinkedHashMap<>(); // 三管汇总
        for (Map<String, Object> r : counts) {
            String code = str(r.get("tube_code"));
            String group = groupOf(code, cfgByCode);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tubeCode", code);
            row.put("tubeName", str(r.get("tube_name")));
            row.put("group", group);
            row.put("riskLevel", riskOf(code, group, cfgByCode));
            row.put("placed", num(r.get("placed")));
            row.put("broughtIn", num(r.get("brought_in")));
            row.put("selfPlaced", num(r.get("self_placed")));
            row.put("removed", num(r.get("removed")));
            row.put("takenAway", num(r.get("taken_away")));
            row.put("unplannedRemoval", num(r.get("unplanned_removal")));
            row.put("reintubation24h", num(r.get("reintubation_24h")));

            double[] d = daysByCode.get(code);
            if (d != null && d[1] > 0) {
                row.put("avgDays", round1(d[0] / d[1]));
                row.put("totalDays", round1(d[0]));
                // 覆盖率：有明确拔管/出科上界的比例（未拔在科的不算完整留置）
                row.put("daysCoverage", round1(d[2] / d[1] * 100.0));
            } else {
                row.put("avgDays", null);
                row.put("totalDays", 0);
                row.put("daysCoverage", 0);
            }
            rows.add(row);

            // 三管汇总累加
            double[] t = total.computeIfAbsent(group, k -> new double[8]);
            t[0] += num(r.get("placed"));
            t[1] += num(r.get("brought_in"));
            t[2] += num(r.get("self_placed"));
            t[3] += num(r.get("removed"));
            t[4] += num(r.get("taken_away"));
            t[5] += num(r.get("unplanned_removal"));
            t[6] += num(r.get("reintubation_24h"));
            if (d != null) t[7] += d[0];
        }

        List<Map<String, Object>> groups = new ArrayList<>();
        for (Map.Entry<String, double[]> e : total.entrySet()) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("group", e.getKey());
            double[] t = e.getValue();
            g.put("placed", (int) t[0]);
            g.put("broughtIn", (int) t[1]);
            g.put("selfPlaced", (int) t[2]);
            g.put("removed", (int) t[3]);
            g.put("takenAway", (int) t[4]);
            g.put("unplannedRemoval", (int) t[5]);
            g.put("reintubation24h", (int) t[6]);
            g.put("totalDays", round1(t[7]));
            groups.add(g);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("periodStart", start.format(DateTimeFormatter.ISO_LOCAL_DATE));
        out.put("periodEnd", end.format(DateTimeFormatter.ISO_LOCAL_DATE));
        out.put("departCode", departCode == null || departCode.isEmpty() ? "ALL" : departCode);
        out.put("groups", groups);
        out.put("rows", rows);
        // 院感确诊口径：以「导管相关感染确诊」页（patient_doc_device_infection）医生录入为准，
        // patient_tube.is_infect 源标记全 0 不再使用；确诊后质控 quality_24/26/425 与
        // 规则层千导管日发病率在重算后更新。
        out.put("infectionNote", "VAP/CRBSI/CAUTI 以医生确诊录入为准（临床分析 → 导管相关感染确诊）；未录入时院感发生率为空而非 0");
        return out;
    }

    /**
     * 三管归类：CVC / 导尿管 / 气道导管 / 其他。
     *
     * <p>白名单为精确匹配的 code 值（对应 {@code patient_tube.tube_code} 字典）。
     * 原先写作
     * {@code ... || "tube_金属气管套管".equals(code) || "默认".equals(code) && code != null}：
     * {@code &&} 优先级高于 {@code ||}，所以实际语义 = 白名单三者 <b>或</b> {@code "默认"}；
     * 其中 {@code code != null} 是死条件（{@link #str} 对 null 返回空串），
     * 但缺括号使得读起来像「任意 code 都归气道导管」。这里按原语义改用集合匹配 +
     * 显式的 {@code FALLBACK_TUBE_CODE} 分支，既不改变统计口径，也消除歧义。</p>
     */
    private String groupOf(String code, Map<String, ?> cfgByCode) {
        Object cfg = cfgByCode.get(code);
        if (cfg instanceof com.zing.doctor.module.tube.entity.TubeGroupConfig) {
            com.zing.doctor.module.tube.entity.TubeGroupConfig c =
                    (com.zing.doctor.module.tube.entity.TubeGroupConfig) cfg;
            return c.getGroupName() == null ? "其他" : c.getGroupName();
        }
        if ("tube_CVC管".equals(code)) return "CVC";
        if ("tube_导尿管".equals(code)) return "导尿管";
        if (AIRWAY_TUBE_CODES.contains(code) || FALLBACK_TUBE_CODE.equals(code)) return "气道导管";
        return "其他";
    }

    /** 危重等级：配置行优先，未配置的三管给默认档，其余「低」。 */
    private String riskOf(String code, String group, Map<String, ?> cfgByCode) {
        Object cfg = cfgByCode.get(code);
        if (cfg instanceof com.zing.doctor.module.tube.entity.TubeGroupConfig) {
            com.zing.doctor.module.tube.entity.TubeGroupConfig c =
                    (com.zing.doctor.module.tube.entity.TubeGroupConfig) cfg;
            if (c.getRiskLevel() != null) return c.getRiskLevel();
        }
        if ("气道导管".equals(group) || "CVC".equals(group)) return "高";
        if ("导尿管".equals(group)) return "中";
        return "低";
    }

    /**
     * 分组展示顺序：默认三管 + 「其他」在前，自定义分组名按配置序追加在后。
     * 供年度/按日视图补齐空桶，保证前端列齐整。
     */
    private List<String> groupOrder(Map<String, ?> cfgByCode) {
        List<String> order = new ArrayList<>(Arrays.asList("CVC", "导尿管", "气道导管", "其他"));
        for (Object c : cfgByCode.values()) {
            if (c instanceof com.zing.doctor.module.tube.entity.TubeGroupConfig) {
                String name = ((com.zing.doctor.module.tube.entity.TubeGroupConfig) c).getGroupName();
                if (name != null && !name.isEmpty() && !order.contains(name)) order.add(name);
            }
        }
        return order;
    }

    private Map<String, com.zing.doctor.module.tube.entity.TubeGroupConfig> tubeConfig() {
        return groupConfigService.activeMap();
    }

    /**
     * 年度视图：12 个月 × 导管类型组的置管/拔管/非计划拔管等计数趋势。
     * 口径与 {@link #monthly} 完全一致（同一套 Mapper 谓词，仅按月份再分桶），
     * 避免「月度对得上、年度对不上」。
     *
     * @param year       形如 2026
     * @param departCode 已收敛科室；null/ALL 全院
     */
    public Map<String, Object> yearly(String year, String departCode) {
        LocalDateTime start = LocalDateTime.parse(year + "-01-01 00:00:00", FMT);
        LocalDateTime end = start.plusYears(1);
        Map<String, com.zing.doctor.module.tube.entity.TubeGroupConfig> cfgByCode = tubeConfig();

        List<Map<String, Object>> raw = mapper.yearlyCounts(fmt(start), fmt(end), departCode);

        // ym -> group -> 计数
        Map<String, Map<String, double[]>> byMonth = new LinkedHashMap<>();
        for (Map<String, Object> r : raw) {
            String ym = str(r.get("ym"));
            if (ym == null || ym.isEmpty()) continue;
            String group = groupOf(str(r.get("tube_code")), cfgByCode);
            Map<String, double[]> gm = byMonth.computeIfAbsent(ym, k -> new LinkedHashMap<>());
            double[] t = gm.computeIfAbsent(group, k -> new double[7]);
            t[0] += num(r.get("placed"));
            t[1] += num(r.get("brought_in"));
            t[2] += num(r.get("self_placed"));
            t[3] += num(r.get("removed"));
            t[4] += num(r.get("taken_away"));
            t[5] += num(r.get("unplanned_removal"));
            t[6] += num(r.get("reintubation_24h"));
        }

        // 补齐 12 个月空桶，保证前端 12 列齐整
        List<Map<String, Object>> months = new ArrayList<>();
        double[] yearTotal = new double[7];
        for (int m = 1; m <= 12; m++) {
            String ym = String.format("%s-%02d", year, m);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ym", ym);
            Map<String, double[]> gm = byMonth.get(ym);
            List<Map<String, Object>> groups = new ArrayList<>();
            for (String g : groupOrder(cfgByCode)) {
                Map<String, Object> g0 = new LinkedHashMap<>();
                g0.put("group", g);
                double[] t = gm != null ? gm.get(g) : null;
                g0.put("placed", t == null ? 0 : (int) t[0]);
                g0.put("broughtIn", t == null ? 0 : (int) t[1]);
                g0.put("selfPlaced", t == null ? 0 : (int) t[2]);
                g0.put("removed", t == null ? 0 : (int) t[3]);
                g0.put("takenAway", t == null ? 0 : (int) t[4]);
                g0.put("unplannedRemoval", t == null ? 0 : (int) t[5]);
                g0.put("reintubation24h", t == null ? 0 : (int) t[6]);
                if (t != null) {
                    for (int i = 0; i < 7; i++) yearTotal[i] += t[i];
                }
                groups.add(g0);
            }
            row.put("groups", groups);
            months.add(row);
        }

        Map<String, Object> yearSum = new LinkedHashMap<>();
        yearSum.put("placed", (int) yearTotal[0]);
        yearSum.put("broughtIn", (int) yearTotal[1]);
        yearSum.put("selfPlaced", (int) yearTotal[2]);
        yearSum.put("removed", (int) yearTotal[3]);
        yearSum.put("takenAway", (int) yearTotal[4]);
        yearSum.put("unplannedRemoval", (int) yearTotal[5]);
        yearSum.put("reintubation24h", (int) yearTotal[6]);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year);
        out.put("periodStart", start.format(DateTimeFormatter.ISO_LOCAL_DATE));
        out.put("periodEnd", end.format(DateTimeFormatter.ISO_LOCAL_DATE));
        out.put("departCode", departCode == null || departCode.isEmpty() ? "ALL" : departCode);
        out.put("months", months);
        out.put("yearSum", yearSum);
        return out;
    }

    /**
     * 按日视图：月内每天 × 导管类型组的 {newPlaced, removed, takenAway, unplanned, reintubation24h, indwelling}。
     *
     * <p>口径与月度一致，只是把事件按「日」切分：
     * <ul>
     *   <li>newPlaced：{@code tube_set_start_time} 当日；</li>
     *   <li>removed / takenAway / unplanned：事件时间当日，非计划拔管看 {@code is_not_plan_extubation}；</li>
     *   <li>reintubation24h：当日新置管且 {@code is_reintubation24}=1；</li>
     *   <li>indwelling（在管）：当日 0 点处于留置区间内，区间上界取
     *       {@code COALESCE(end_time, take_away_time, 统计截止)}，与 Mapper 的 overlap 谓词一致。</li>
     * </ul>
     */
    /** 兼容入口：按月视图。 */
    public Map<String, Object> daily(String yearMonth, String departCode) {
        return daily(yearMonth, null, null, departCode);
    }

    /**
     * 按日视图（支持自定义起止区间）： startDate/endDate 同时提供时按区间（日期级，含首尾，
     * 上限 92 天），否则按 yearMonth 整月。
     */
    public Map<String, Object> daily(String yearMonth, String startDate, String endDate, String departCode) {
        final LocalDate s0;
        final LocalDate e0;
        if (startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty()) {
            s0 = LocalDate.parse(startDate.trim().substring(0, 10));
            e0 = LocalDate.parse(endDate.trim().substring(0, 10));
            if (e0.isBefore(s0)) {
                throw new IllegalArgumentException("结束日期必须不早于开始日期");
            }
            if (ChronoUnit.DAYS.between(s0, e0) > 92) {
                throw new IllegalArgumentException("自定义区间不能超过 92 天");
            }
        } else {
            LocalDate first = LocalDate.parse(yearMonth + "-01");
            s0 = first;
            e0 = first.plusMonths(1).minusDays(1);
        }
        LocalDateTime start = s0.atStartOfDay();
        LocalDateTime end = e0.plusDays(1).atStartOfDay(); // 统计截止（不含）
        Map<String, com.zing.doctor.module.tube.entity.TubeGroupConfig> cfgByCode = tubeConfig();
        List<String> order = groupOrder(cfgByCode);

        List<Map<String, Object>> raw = mapper.dailyRows(fmt(start), fmt(end), departCode);

        // day -> group -> 计数数组 [newPlaced, removed, takenAway, unplanned, reintubation24h]
        Map<LocalDate, Map<String, double[]>> byDay = new LinkedHashMap<>();
        // 每组当日 0 点在管数：先按日累计留置覆盖
        Map<LocalDate, Map<String, Integer>> indwelling = new LinkedHashMap<>();

        for (Map<String, Object> r : raw) {
            String code = str(r.get("tube_code"));
            String group = groupOf(code, cfgByCode);
            LocalDateTime s = parse(r.get("start_time"));
            if (s == null) continue;
            // 区间上界：已拔 > 已带出 > 统计截止
            LocalDateTime bound = parse(r.get("end_time"));
            boolean removedFlag = bound != null;
            if (bound == null) bound = parse(r.get("take_away_time"));
            if (bound == null || bound.isAfter(end)) bound = end;
            if (bound.isBefore(start)) bound = start;

            boolean unplanned = num(r.get("is_not_plan_extubation")) == 1;
            boolean reintube = num(r.get("is_reintubation24")) == 1;

            // 遍历该导管覆盖到的每一天（区间上限 92 天，行数有限，可接受）
            LocalDateTime cur = s.isBefore(start) ? start : s;
            while (cur.isBefore(bound) && cur.isBefore(end)) {
                LocalDate day = cur.toLocalDate();
                boolean isNewDay = cur.equals(s) || (cur.toLocalDate().equals(s.toLocalDate()));
                Map<String, double[]> gm = byDay.computeIfAbsent(day, k -> new LinkedHashMap<>());
                double[] t = gm.computeIfAbsent(group, k -> new double[5]);
                if (isNewDay) {
                    t[0] += 1;                                   // 当日新置
                    if (reintube) t[4] += 1;                     // 当日新置且 48h 再插管
                }
                indwelling.computeIfAbsent(day, k -> new LinkedHashMap<>())
                        .merge(group, 1, Integer::sum);          // 覆盖到即算当日 0 点在管
                LocalDateTime next = cur.plusDays(1).toLocalDate().atStartOfDay();
                // 拔管/带出事件日：只记一次，且该日不再算在管（事件发生在当日 0 点之后，本行简化：拔管日仍计入在管后跳出）
                cur = next;
            }

            if (removedFlag) {
                LocalDateTime endL = parse(r.get("end_time"));
                if (endL != null && !endL.isBefore(start) && endL.isBefore(end)) {
                    LocalDate day = endL.toLocalDate();
                    double[] t = byDay.computeIfAbsent(day, k -> new LinkedHashMap<>())
                            .computeIfAbsent(group, k -> new double[5]);
                    t[1] += 1;
                    if (unplanned) t[3] += 1;
                }
            }
            LocalDateTime awayL = parse(r.get("take_away_time"));
            if (awayL != null && !awayL.isBefore(start) && awayL.isBefore(end)) {
                LocalDate day = awayL.toLocalDate();
                byDay.computeIfAbsent(day, k -> new LinkedHashMap<>())
                        .computeIfAbsent(group, k -> new double[5])[2] += 1;
            }
        }

        // 组装输出：区间内每天固定列，空桶补 0
        List<Map<String, Object>> days = new ArrayList<>();
        for (LocalDate d = s0; !d.isAfter(e0); d = d.plusDays(1)) {
            String date = d.toString();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", date);
            List<Map<String, Object>> groups = new ArrayList<>();
            Map<String, double[]> gm = byDay.get(d);
            Map<String, Integer> ind = indwelling.get(d);
            for (String g : order) {
                double[] t = gm != null ? gm.get(g) : null;
                Map<String, Object> g0 = new LinkedHashMap<>();
                g0.put("group", g);
                g0.put("newPlaced", t == null ? 0 : (int) t[0]);
                g0.put("removed", t == null ? 0 : (int) t[1]);
                g0.put("takenAway", t == null ? 0 : (int) t[2]);
                g0.put("unplanned", t == null ? 0 : (int) t[3]);
                g0.put("reintubation24h", t == null ? 0 : (int) t[4]);
                g0.put("indwelling", ind == null ? 0 : ind.getOrDefault(g, 0));
                groups.add(g0);
            }
            row.put("groups", groups);
            days.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("yearMonth", s0.toString().substring(0, 7));
        out.put("periodStart", s0.toString());
        out.put("periodEnd", e0.toString());
        out.put("departCode", departCode == null || departCode.isEmpty() ? "ALL" : departCode);
        out.put("groups", order);
        out.put("days", days);
        return out;
    }

    private static String fmt(LocalDateTime t) { return t.format(FMT); }
    private static LocalDateTime parse(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        try { return LocalDateTime.parse(s.replace("T", " ").substring(0, 19), FMT); }
        catch (Exception e) { return null; }
    }
    private static int num(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return new BigDecimal(String.valueOf(v)).intValue(); } catch (Exception e) { return 0; }
    }
    private static double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
    private static String str(Object v) { return v == null ? "" : String.valueOf(v); }
}
