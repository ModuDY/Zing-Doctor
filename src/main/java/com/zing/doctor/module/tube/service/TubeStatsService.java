package com.zing.doctor.module.tube.service;

import com.zing.doctor.module.tube.mapper.TubeStatsMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导管统计聚合 Service。
 *
 * <p>把 Mapper 的原始计数按临床口径归类（CVC / 导尿管 / 气道导管 / 其他），
 * 并基于留置起止时间算平均留置天数与数据覆盖率。只读。
 */
@Service
public class TubeStatsService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TubeStatsMapper mapper;

    public TubeStatsService(TubeStatsMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * @param yearMonth 形如 2026-09
     * @param departCode 已收敛科室；null/ALL 全院
     */
    public Map<String, Object> monthly(String yearMonth, String departCode) {
        LocalDateTime start = LocalDateTime.parse(yearMonth + "-01 00:00:00", FMT);
        LocalDateTime end = start.plusMonths(1);

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
            String group = groupOf(code);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tubeCode", code);
            row.put("tubeName", str(r.get("tube_name")));
            row.put("group", group);
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
        // 院感确诊（is_infect 全为0，尚未录入）明确标注，避免被当成已实现
        out.put("infectionNote", "VAP/CRBSI/CAUTI 确诊标记（is_infect）当前无录入数据，院感发生率暂不计算");
        return out;
    }

    /** 三管归类：CVC / 导尿管 / 气道导管 / 其他。 */
    private String groupOf(String code) {
        if ("tube_CVC管".equals(code)) return "CVC";
        if ("tube_导尿管".equals(code)) return "导尿管";
        if ("tube_气管插管".equals(code) || "tube_气管切开套管".equals(code)
                || "tube_金属气管套管".equals(code) || "默认".equals(code) && code != null) return "气道导管";
        return "其他";
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
