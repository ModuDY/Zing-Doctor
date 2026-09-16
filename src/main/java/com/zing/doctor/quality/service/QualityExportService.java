package com.zing.doctor.quality.service;

import com.zing.doctor.quality.entity.QualityCalcRun;
import com.zing.doctor.quality.entity.QualityIndex;
import com.zing.doctor.quality.entity.QualityMonthlyReport;
import com.zing.doctor.quality.mapper.QualityIndexMapper;
import com.zing.doctor.quality.support.XlsxStreamWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 质控 xlsx 导出。
 *
 * <p>用零依赖的流式写出器（见 {@link XlsxStreamWriter}）而非 POI/EasyExcel：
 * 不新增依赖、不改 pom，且逐行落盘不占内存。
 */
@Service
public class QualityExportService {

    private static final Logger log = LoggerFactory.getLogger(QualityExportService.class);

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String SHEET_MONTHLY = "月度汇总";
    private static final String SHEET_INDEX = "指标清单";
    private static final String SHEET_RUN = "计算批次";
    private static final String SHEET_PATIENT = "患者明细";

    private final QualityMonthlyService monthlyService;
    private final QualityQueryService queryService;
    private final QualityIndexMapper indexMapper;

    public QualityExportService(QualityMonthlyService monthlyService, QualityQueryService queryService,
                               QualityIndexMapper indexMapper) {
        this.monthlyService = monthlyService;
        this.queryService = queryService;
        this.indexMapper = indexMapper;
    }

    /**
     * 导出某年质控报表（3 个 sheet：月度汇总 / 指标清单 / 计算批次）。
     */
    public void exportYear(HttpServletResponse response, int year, String departCode) {
        String dept = departCode == null || departCode.trim().isEmpty() ? "ALL" : departCode.trim();
        String fileName = "质控指标_" + year + "_" + dept + ".xlsx";
        try {
            response.reset();
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\""
                    + URLEncoder.encode(fileName, "UTF-8").replace("+", "%20") + "\"");

            try (OutputStream out = response.getOutputStream();
                 XlsxStreamWriter w = new XlsxStreamWriter(out, SHEET_MONTHLY, SHEET_INDEX, SHEET_RUN)) {

                // Sheet 1：月度汇总（1-12 月横排）
                w.beginSheet(0, new String[]{"指标编号", "指标名称", "所属域", "单位", "值类型",
                        "1月", "2月", "3月", "4月", "5月", "6月",
                        "7月", "8月", "9月", "10月", "11月", "12月",
                        "Q1", "Q2", "Q3", "Q4", "全年合计", "全年平均", "最高月", "最低月"});
                for (QualityMonthlyReport r : monthlyService.list(year, dept)) {
                    w.writeRow(new Object[]{r.getIndexCode(), r.getIndexName(), r.getDomainCode(),
                            r.getUnit(), r.getValueType(),
                            r.getM01(), r.getM02(), r.getM03(), r.getM04(), r.getM05(), r.getM06(),
                            r.getM07(), r.getM08(), r.getM09(), r.getM10(), r.getM11(), r.getM12(),
                            r.getQ1(), r.getQ2(), r.getQ3(), r.getQ4(),
                            r.getYearTotal(), r.getYearAvg(), r.getMaxMonth(), r.getMinMonth()});
                }
                w.endSheet();

                // Sheet 2：指标清单（口径与实现状态）
                w.beginSheet(1, new String[]{"指标编号", "指标名称", "所属域", "单位", "值类型",
                        "实现状态", "事实层", "口径版本", "老系统脚本", "备注"});
                List<QualityIndex> indices = indexMapper.selectAllOrdered();
                for (QualityIndex i : indices) {
                    w.writeRow(new Object[]{i.getIndexCode(), i.getIndexName(), i.getDomainCode(),
                            i.getUnit(), i.getValueType(), i.getImplStatus(), i.getFactName(),
                            i.getExpressionVersion(), i.getLegacyScript(), i.getRemark()});
                }
                w.endSheet();

                // Sheet 3：计算批次（血缘第 1 层）
                w.beginSheet(2, new String[]{"批次号", "周期类型", "周期开始", "周期结束", "科室",
                        "触发方式", "引擎版本", "状态", "指标总数", "成功", "失败", "占位", "耗时(ms)", "开始时间"});
                List<QualityCalcRun> runs = queryService.recentRuns();
                List<Object[]> runRows = new ArrayList<>();
                for (QualityCalcRun r : runs) {
                    runRows.add(new Object[]{r.getRunId(), r.getPeriodType(),
                            r.getPeriodStart() == null ? null : DT.format(r.getPeriodStart()),
                            r.getPeriodEnd() == null ? null : DT.format(r.getPeriodEnd()),
                            r.getDepartCode(), r.getTriggerType(), r.getEngineVersion(), r.getStatus(),
                            r.getMetricTotal(), r.getMetricOk(), r.getMetricFail(), r.getMetricPlaceholder(),
                            r.getDurationMs(),
                            r.getStartTime() == null ? null : DT.format(r.getStartTime())});
                }
                for (Object[] row : runRows) {
                    w.writeRow(row);
                }
                w.endSheet();
            }
        } catch (Exception e) {
            log.error("[质控] 导出失败: year={}, dept={}", year, dept, e);
            if (!response.isCommitted()) {
                try {
                    response.reset();
                    response.setContentType("application/json;charset=UTF-8");
                    response.getOutputStream().write(
                            ("{\"code\":500,\"message\":\"导出失败: " + e.getMessage() + "\"}")
                                    .getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignore) {
                    // 已无法写入响应
                }
            }
        }
    }

    /**
     * 导出某条指标的对象级（患者）明细。
     *
     * <p>为什么需要它：看板能下钻到人，但导出只有汇总 —— 临床要「不达标患者名单」时
     * 只能对着屏幕截图，而质控核查、病案复核都要逐人核对原始记录。
     *
     * <p>列定义直接取该指标的 {@code patientFields}（与页面同源），不另设一套出列逻辑：
     * 否则会出现「屏幕上第 3 列是科室、导出后跑到第 5 列」这类差异，被当成数据问题来查。
     *
     * @param includeExcluded true = 连同「既没进分子也没进分母」的人也导出，
     *        用于核对「这个人为什么不算进分母」—— 那正是质控争议的焦点
     * @param view 纳入视角；传具体值时导出该视角的名单，与屏幕上当前那一档逐行一致。
     *        文件名带上视角：同一条指标常要按「未达标」「未纳入」各导一份交不同的人，
     *        都叫「质控患者明细」在下载目录里分不出来。
     */
    public void exportPatients(HttpServletResponse response, String code, String periodStart,
                               String departCode, boolean includeExcluded, String view) {
        String dept = departCode == null || departCode.trim().isEmpty() ? "ALL" : departCode.trim();
        String viewLabel = viewLabel(view);
        String fileName = "质控患者明细_" + code + "_" + periodStart + "_" + dept
                + (viewLabel.isEmpty() ? "" : "_" + viewLabel) + ".xlsx";
        try {
            Map<String, Object> data = queryService.patients(code, periodStart, dept, includeExcluded, view);
            Object rawRows = data.get("patients");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = rawRows instanceof List
                    ? (List<Map<String, Object>>) rawRows : new ArrayList<>();
            List<Map<String, Object>> fields = queryService.patientColumnsOf(code);
            if (fields.isEmpty()) {
                // 指标未定义时退回最小列集：宁可导出一份精简文件，也不要让使用者拿到空文件
                fields = defaultPatientColumns();
            }

            response.reset();
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\""
                    + URLEncoder.encode(fileName, "UTF-8").replace("+", "%20") + "\"");

            String[] header = new String[fields.size()];
            for (int i = 0; i < fields.size(); i++) {
                header[i] = text(fields.get(i).get("label"), String.valueOf(fields.get(i).get("key")));
            }

            try (OutputStream out = response.getOutputStream();
                 XlsxStreamWriter w = new XlsxStreamWriter(out, SHEET_PATIENT)) {
                w.beginSheet(0, header);
                for (Map<String, Object> row : rows) {
                    Object[] line = new Object[fields.size()];
                    for (int i = 0; i < fields.size(); i++) {
                        Map<String, Object> f = fields.get(i);
                        String key = String.valueOf(f.get("key"));
                        Object v = row.get(key);
                        // 布尔列导出成「是 / 否」，与页面一致：导出文件常被直接贴进汇报材料，
                        // 原样导 0/1 会让看图的人重新猜一遍含义
                        if (v instanceof Number && "bool".equals(String.valueOf(f.get("type")))) {
                            v = ((Number) v).intValue() == 1 ? "是" : "否";
                        }
                        line[i] = v;
                    }
                    w.writeRow(line);
                }
                w.endSheet();
            }
        } catch (Exception e) {
            log.error("[质控] 患者明细导出失败: code={}, period={}, dept={}", code, periodStart, dept, e);
            if (!response.isCommitted()) {
                try {
                    response.reset();
                    response.setContentType("application/json;charset=UTF-8");
                    response.getOutputStream().write(
                            ("{\"code\":500,\"message\":\"导出失败: " + e.getMessage() + "\"}")
                                    .getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignore) {
                    // 已无法写入响应
                }
            }
        }
    }

    /** 兜底列：指标未定义时也能给出一份可读文件。 */
    private List<Map<String, Object>> defaultPatientColumns() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(column("patientName", "姓名", "text"));
        list.add(column("inHospitalNo", "住院号", "text"));
        list.add(column("patientId", "患者ID", "text"));
        list.add(column("departCode", "科室", "text"));
        list.add(column("inNumerator", "入分子", "bool"));
        list.add(column("inDenominator", "入分母", "bool"));
        return list;
    }

    private Map<String, Object> column(String key, String label, String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("type", type);
        return m;
    }

    /** 视角的中文短名，用于文件名；"all" 或未知值不追加后缀。 */
    private String viewLabel(String view) {
        if (view == null) {
            return "";
        }
        String v = view.trim();
        if ("inNumerator".equals(v)) {
            return "进分子";
        }
        if ("inDenominator".equals(v)) {
            return "进分母";
        }
        if ("achieved".equals(v)) {
            return "已达标";
        }
        if ("missed".equals(v)) {
            return "未达标";
        }
        if ("excluded".equals(v)) {
            return "未纳入";
        }
        if ("abnormal".equals(v)) {
            return "口径异常";
        }
        return "";
    }

    private String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? fallback : s;
    }
}
