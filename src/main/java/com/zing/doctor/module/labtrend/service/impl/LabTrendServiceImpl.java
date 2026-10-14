package com.zing.doctor.module.labtrend.service.impl;

import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.module.labtrend.dto.LabTrendResponse;
import com.zing.doctor.module.labtrend.service.LabTrendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检验趋势实现（肝功能 / 凝血）。
 *
 * <p>项目口径全部来自 docs/25 生产库核查（近 90 天实测覆盖）：
 * <ul>
 *   <li>肝功能：ALT 丙氨酸氨基转移酶 / AST <b>天冬氨酸氨基转氨酶</b> /
 *       总胆红素 / 直接胆红素 / 间接胆红素 / 白蛋白 / 碱性磷酸酶 / γ-谷氨酰转肽酶；</li>
 *   <li>凝血：血小板计数 / PT / INR / APTT / 纤维蛋白原 / D-二聚体（仅 ICU 床边项目、低覆盖）。</li>
 * </ul>
 *
 * <p><b>按名称匹配而非只按 code</b>：核查证实 ALT 有第二套编码（约 5,200 患者），
 * 只按 code 100080 查会漏掉新系统数据；项目名普遍带 {@code *} 前缀（异常标记），用包含匹配。
 *
 * <p><b>不纳入「谷草/谷丙」组合项</b>：单字段含两个值（如"35/28"），
 * 拆分规则未经临床确认，强行拆分等于自行猜测数值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabTrendServiceImpl implements LabTrendService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final IcuPatientMapper icuPatientMapper;
    private final IcuPatientService icuPatientService;

    /** 肝功能分组：组名 → (项目名关键词, 展示名) */
    private static final LinkedHashMap<String, String[][]> LIVER = new LinkedHashMap<>();
    private static final String[][] COAG = {
            {"血小板计数", "PLT 血小板计数"},
            {"凝血酶原时间", "PT 凝血酶原时间"},
            {"凝血酶原国际比值", "INR"},
            {"活化部分凝血活酶", "APTT"},
            {"纤维蛋白原", "Fib 纤维蛋白原"},
            {"D-二聚体", "D-二聚体"},
    };

    static {
        LIVER.put("肝细胞损伤", new String[][]{
                {"丙氨酸氨基转移酶", "ALT 丙氨酸氨基转移酶"},
                {"天冬氨酸氨基转氨酶", "AST 天冬氨酸氨基转氨酶"},
        });
        LIVER.put("胆红素", new String[][]{
                {"总胆红素", "TBIL 总胆红素"},
                {"直接胆红素", "DBIL 直接胆红素"},
                {"间接胆红素", "IBIL 间接胆红素"},
        });
        LIVER.put("合成功能", new String[][]{
                {"白蛋白", "ALB 白蛋白"},
        });
        LIVER.put("胆道 / 酶学", new String[][]{
                {"碱性磷酸酶", "ALP 碱性磷酸酶"},
                {"谷氨酰转肽酶", "GGT γ-谷氨酰转肽酶"},
        });
    }

    @Override
    public LabTrendResponse trend(String patientId, String type, int hours) {
        int range = Math.max(1, Math.min(hours, 720));
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(range);
        LabTrendResponse resp = new LabTrendResponse();
        resp.setPatientId(patientId);
        resp.setRangeStart(start.format(ISO));
        resp.setRangeEnd(end.format(ISO));

        WorkbenchPatient p = icuPatientService.getWorkbenchPatientAny(patientId);
        if (p != null) {
            resp.setPatientName(p.getName());
            resp.setInHospitalNo(p.getInHospitalNo());
        }
        String inHospitalNo = p == null ? null : p.getInHospitalNo();

        LinkedHashMap<String, String[][]> groups = "coag".equals(type)
                ? singleGroup("凝血功能", COAG) : LIVER;

        // DB 端按目标项目关键词过滤取数，不再先拉全项目再 Java 筛
        List<String> keywords = new ArrayList<>();
        for (String[][] items : groups.values()) {
            for (String[] it : items) {
                keywords.add(it[0]);
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        boolean queryFailed = false;
        if (inHospitalNo == null || inHospitalNo.isEmpty()) {
            queryFailed = true;
            log.warn("检验趋势：未取到住院号 patientId={}", patientId);
        } else {
            try {
                rows = icuPatientMapper.selectLabsByKeywordsAndRange(
                        inHospitalNo, start.format(ISO), end.format(ISO), keywords);
            } catch (Exception e) {
                queryFailed = true;
                log.warn("检验明细查询失败 patientId={} inHospitalNo={}", patientId, inHospitalNo, e);
            }
        }

        for (Map.Entry<String, String[][]> e : groups.entrySet()) {
            LabTrendResponse.Group g = new LabTrendResponse.Group();
            g.setName(e.getKey());
            for (String[] item : e.getValue()) {
                g.getSeries().add(oneSeries(item[0], item[1], rows, queryFailed, resp));
            }
            resp.getGroups().add(g);
        }
        resp.setRecordCount(resp.getRecords().size());
        return resp;
    }

    private LinkedHashMap<String, String[][]> singleGroup(String name, String[][] items) {
        LinkedHashMap<String, String[][]> m = new LinkedHashMap<>();
        m.put(name, items);
        return m;
    }

    private LabTrendResponse.Series oneSeries(String keyword, String name,
                                              List<Map<String, Object>> rows,
                                              boolean queryFailed, LabTrendResponse resp) {
        LabTrendResponse.Series s = new LabTrendResponse.Series();
        s.setName(name);
        if (queryFailed) {
            s.setDataStatus("UNKNOWN");
            s.setNote("查询失败");
            return s;
        }
        for (Map<String, Object> r : rows) {
            String itemName = str(r.get("item_name"));
            if (itemName == null || !itemName.contains(keyword)) {
                continue;
            }
            // 「谷草/谷丙」组合项单字段双值，拆分规则未确认，跳过
            if (itemName.contains("谷草/谷丙") || itemName.contains("谷草／谷丙")) {
                continue;
            }
            Double v = parseDouble(r.get("item_value"));
            if (v == null) {
                continue;
            }
            LabTrendResponse.Point pt = new LabTrendResponse.Point();
            pt.setTime(toIso(r.get("check_time")));
            pt.setValue(v);
            pt.setRefLow(str(r.get("low_value")));
            pt.setRefHigh(str(r.get("high_value")));
            pt.setRefText(str(r.get("ref_text")));
            s.getPoints().add(pt);

            LabTrendResponse.Row row = new LabTrendResponse.Row();
            row.setTime(toIso(r.get("check_time")));
            row.setItemName(itemName);
            row.setValue(str(r.get("item_value")));
            row.setRefLow(str(r.get("low_value")));
            row.setRefHigh(str(r.get("high_value")));
            row.setRefText(str(r.get("ref_text")));
            row.setAlarmFlag(str(r.get("alarm_flag")));
            if (!resp.getRecords().contains(row)) {
                resp.getRecords().add(row);
            }
        }
        if (!s.getPoints().isEmpty()) {
            s.setDataStatus("AVAILABLE");
        } else {
            s.setDataStatus("NO_RECORD");
            s.setNote("窗口内无记录");
        }
        return s;
    }

    private static Double parseDouble(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(o).trim()).doubleValue();
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static String toIso(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) o).toLocalDateTime().format(ISO);
        }
        return String.valueOf(o);
    }
}
