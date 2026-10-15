package com.zing.doctor.module.tube.service;

import com.zing.doctor.quality.support.XlsxStreamWriter;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

/**
 * 导管统计导出（月度明细 + 年度趋势，两个 sheet）。
 *
 * <p>复用质控导出的零依赖流式写出器 {@link XlsxStreamWriter}：不新增依赖，
 * 逐行落盘不占内存。数据与页面同一份组装结果（{@link TubeStatsService}），
 * 不另写一套口径 —— 避免「屏幕上有、导出里没有」。</p>
 */
@Service
public class TubeStatsExportService {

    private static final String[] MONTH_HEADERS = {
            "月份", "导管类型", "新置管", "带入", "本科置管", "拔管", "带出", "非计划拔管", "48h再插管"};

    private final TubeStatsService tubeStatsService;

    public TubeStatsExportService(TubeStatsService tubeStatsService) {
        this.tubeStatsService = tubeStatsService;
    }

    /** 导出某月明细：sheet1 按导管类型明细，sheet2 三管汇总。 */
    public void exportMonthly(HttpServletResponse response, String yearMonth, String departCode) {
        String dept = departCode == null || departCode.isEmpty() ? "ALL" : departCode;
        Map<String, Object> data = tubeStatsService.monthly(yearMonth, departCode);
        write(response, "导管统计_" + yearMonth + "_" + dept + ".xlsx", w -> {
            w.beginSheet(0, new String[]{"导管类型", "导管编码", "导管名称", "新置管", "带入", "本科置管",
                    "拔管", "带出", "非计划拔管", "48h再插管", "平均留置天", "留置总天", "数据覆盖率%"});
            for (Map<String, Object> r : rows(data.get("rows"))) {
                w.writeRow(new Object[]{str(r.get("group")), str(r.get("tubeCode")), str(r.get("tubeName")),
                        n(r.get("placed")), n(r.get("broughtIn")), n(r.get("selfPlaced")),
                        n(r.get("removed")), n(r.get("takenAway")), n(r.get("unplannedRemoval")),
                        n(r.get("reintubation24h")), r.get("avgDays"), r.get("totalDays"), r.get("daysCoverage")});
            }
            w.endSheet();

            w.beginSheet(1, new String[]{"导管类型", "新置管", "带入", "本科置管", "拔管", "带出", "非计划拔管", "48h再插管", "留置总天"});
            for (Map<String, Object> g : rows(data.get("groups"))) {
                w.writeRow(new Object[]{str(g.get("group")), n(g.get("placed")), n(g.get("broughtIn")),
                        n(g.get("selfPlaced")), n(g.get("removed")), n(g.get("takenAway")),
                        n(g.get("unplannedRemoval")), n(g.get("reintubation24h")), g.get("totalDays")});
            }
            w.endSheet();
        });
    }

    /** 导出年度趋势：sheet1 月×类型矩阵，sheet2 全年汇总。 */
    public void exportYearly(HttpServletResponse response, String year, String departCode) {
        String dept = departCode == null || departCode.isEmpty() ? "ALL" : departCode;
        Map<String, Object> data = tubeStatsService.yearly(year, departCode);
        write(response, "导管统计_" + year + "年度_" + dept + ".xlsx", w -> {
            w.beginSheet(0, MONTH_HEADERS);
            for (Map<String, Object> m : rows(data.get("months"))) {
                for (Map<String, Object> g : rows(m.get("groups"))) {
                    w.writeRow(new Object[]{str(m.get("ym")), str(g.get("group")),
                            n(g.get("placed")), n(g.get("broughtIn")), n(g.get("selfPlaced")),
                            n(g.get("removed")), n(g.get("takenAway")), n(g.get("unplannedRemoval")),
                            n(g.get("reintubation24h"))});
                }
            }
            w.endSheet();

            Map<String, Object> sum = castMap(data.get("yearSum"));
            w.beginSheet(1, new String[]{"导管类型", "新置管", "带入", "本科置管", "拔管", "带出", "非计划拔管", "48h再插管"});
            w.writeRow(new Object[]{"全年合计", n(sum.get("placed")), n(sum.get("broughtIn")), n(sum.get("selfPlaced")),
                    n(sum.get("removed")), n(sum.get("takenAway")), n(sum.get("unplannedRemoval")),
                    n(sum.get("reintubation24h"))});
            w.endSheet();
        });
    }

    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface SheetWriter {
        void write(XlsxStreamWriter w) throws Exception;
    }

    private void write(HttpServletResponse response, String fileName, SheetWriter body) {
        try {
            response.reset();
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\""
                    + URLEncoder.encode(fileName, "UTF-8").replace("+", "%20") + "\"");
            OutputStream out = response.getOutputStream();
            try (XlsxStreamWriter w = new XlsxStreamWriter(out, "明细", "汇总")) {
                body.write(w);
            }
        } catch (Exception e) {
            throw new RuntimeException("导管统计导出失败: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Object v) {
        return v instanceof List ? (List<Map<String, Object>>) v : java.util.Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : new java.util.LinkedHashMap<>();
    }

    private static long n(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
