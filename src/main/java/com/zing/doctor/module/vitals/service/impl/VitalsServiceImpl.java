package com.zing.doctor.module.vitals.service.impl;

import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.icu.service.impl.SqlIcuPatientServiceImpl;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.module.vitals.dto.VitalsResponse;
import com.zing.doctor.module.vitals.service.VitalsService;
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
 * 生命体征趋势实现。
 *
 * <p>编码来自 docs/25 核查结果（近 90 天实测覆盖 1190 患者）：
 * <table>
 *   <tr><td>心率</td><td>oi_hr</td></tr>
 *   <tr><td>呼吸频率</td><td>oi_hxpl</td></tr>
 *   <tr><td>SpO₂</td><td>oi_spo2</td></tr>
 *   <tr><td>无创收缩压/舒张压/平均压</td><td>oi_wcssy / oi_wcszy / oi_pjy</td></tr>
 *   <tr><td>有创收缩压/舒张压/平均压</td><td>oi_ycssy / oi_ycszy / oi_ycpjy</td></tr>
 *   <tr><td>体温</td><td>oi_tiwen</td></tr>
 * </table>
 * 无创与有创血压是两套编码，按来源分列展示、不合并 —— 合并会让"无创测得的低血压"
 * 与"有创测得的正常值"相互抵消。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VitalsServiceImpl implements VitalsService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final IcuPatientMapper icuPatientMapper;
    private final IcuPatientService icuPatientService;

    /** 指标定义：key / 名称 / 单位 / 来源 / 编码组 */
    private static final Object[][] DEFS = {
            {"hr", "心率", "次/分", "无创", new String[]{"oi_hr"}},
            {"rr", "呼吸频率", "次/分", "无创", new String[]{"oi_hxpl"}},
            {"spo2", "SpO₂", "%", "无创", new String[]{"oi_spo2"}},
            {"sbp", "收缩压", "mmHg", "无创", new String[]{"oi_wcssy"}},
            {"dbp", "舒张压", "mmHg", "无创", new String[]{"oi_wcszy"}},
            {"map", "平均压", "mmHg", "无创", new String[]{"oi_pjy"}},
            {"temp", "体温", "℃", "未标明", new String[]{"oi_tiwen"}},
            {"sbp_inv", "收缩压（有创）", "mmHg", "有创", new String[]{"oi_ycssy"}},
            {"dbp_inv", "舒张压（有创）", "mmHg", "有创", new String[]{"oi_ycszy"}},
            {"map_inv", "平均压（有创）", "mmHg", "有创", new String[]{"oi_ycpjy"}},
    };

    @Override
    public VitalsResponse trend(String patientId, int hours) {
        int range = Math.max(1, Math.min(hours, 168));
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(range);
        VitalsResponse resp = new VitalsResponse();
        resp.setPatientId(patientId);
        resp.setRangeStart(start.format(ISO));
        resp.setRangeEnd(end.format(ISO));

        WorkbenchPatient p = icuPatientService.getWorkbenchPatientAny(patientId);
        if (p != null) {
            resp.setPatientName(p.getName());
            resp.setInHospitalNo(p.getInHospitalNo());
            resp.setBedNo(p.getBedNo());
        }

        // 一次批量查全部编码组，Java 端分组（避免按指标 N+1）
        List<String> codes = new ArrayList<>();
        for (Object[] def : DEFS) {
            codes.addAll(Arrays.asList((String[]) def[4]));
        }
        List<Map<String, Object>> rows;
        try {
            rows = icuPatientMapper.selectVitalsByCodes(patientId, codes,
                    start.format(ISO), end.format(ISO));
        } catch (Exception e) {
            log.warn("生命体征查询失败 patientId={}", patientId, e);
            for (Object[] def : DEFS) {
                VitalsResponse.Series s = newSeries(def);
                s.setDataStatus("UNKNOWN");
                s.setNote("查询失败");
                resp.getSeries().add(s);
            }
            return resp;
        }

        // 截断探测：SQL 倒序取最新 2001 条，>2000 说明窗口内记录超限。
        // 去掉最早一条（DESC 顺序下在末尾），再反转回升序供图表按时间轴绘制。
        if (rows.size() > 2000) {
            resp.setTruncated(true);
            resp.setTruncatedNote("窗口内记录超过 2000 条，仅展示最近 2000 条，更早记录已省略");
            rows.remove(rows.size() - 1);
        }
        java.util.Collections.reverse(rows);

        // 按指标分组
        Map<String, VitalsResponse.Series> byKey = new LinkedHashMap<>();
        for (Object[] def : DEFS) {
            VitalsResponse.Series s = newSeries(def);
            byKey.put((String) def[0], s);
        }
        for (Map<String, Object> r : rows) {
            String code = str(r.get("item_code"));
            for (Object[] def : DEFS) {
                if (!Arrays.asList((String[]) def[4]).contains(code)) {
                    continue;
                }
                VitalsResponse.Series s = byKey.get(def[0]);
                Double v = parseDouble(r.get("item_value"));
                if (v == null) {
                    continue; // 非数值（如"测不出"）不计入曲线，明细里仍保留
                }
                VitalsResponse.Point pt = new VitalsResponse.Point();
                pt.setTime(toIso(r.get("item_time")));
                pt.setValue(v);
                pt.setStaffName(str(r.get("staff_name")));
                s.getPoints().add(pt);
            }
            VitalsResponse.Row row = new VitalsResponse.Row();
            row.setTime(toIso(r.get("item_time")));
            row.setItemName(str(r.get("item_name")));
            row.setValue(str(r.get("item_value")));
            row.setItemCode(code);
            row.setStaffName(str(r.get("staff_name")));
            resp.getRecords().add(row);
        }

        resp.setRecordCount(resp.getRecords().size());
        // 状态设置必须遍历 byKey（而不是 resp.getSeries()）：
        // Series 对象此时还在 byKey 里，漏掉 addAll 会让接口返回空 series
        // —— 表现就是页面只有原始记录表、所有趋势图消失（浏览器实测踩过）。
        for (VitalsResponse.Series s : byKey.values()) {
            if (!s.getPoints().isEmpty()) {
                s.setDataStatus("AVAILABLE");
            } else {
                s.setDataStatus("NO_RECORD");
                s.setNote("窗口内无记录");
            }
        }
        resp.getSeries().addAll(byKey.values());
        return resp;
    }

    private VitalsResponse.Series newSeries(Object[] def) {
        VitalsResponse.Series s = new VitalsResponse.Series();
        s.setKey((String) def[0]);
        s.setName((String) def[1]);
        s.setUnit((String) def[2]);
        s.setSource((String) def[3]);
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