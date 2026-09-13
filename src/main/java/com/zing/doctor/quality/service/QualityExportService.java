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
import java.util.List;

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
}
