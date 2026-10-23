package com.zing.doctor.module.vitals.service.impl;

import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.icu.service.impl.SqlIcuPatientServiceImpl;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.module.config.dataitem.entity.DataItemConfig;
import com.zing.doctor.module.config.dataitem.service.DataItemConfigService;
import com.zing.doctor.module.vitals.dto.VitalsResponse;
import com.zing.doctor.module.vitals.service.VitalsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
 *
 * <p><b>编码来源已改为可配置</b>：上面这些 {@code oi_*} 编码只是内置默认值，实际取数优先读
 * 配置表 {@code config_data_item}（domain=VITAL，页面 /page/data-item-config）。
 * 换医院时对方观察项编码不同，改配置即可，不必改代码重打包；配置为空或表不存在时
 * 自动回退下面这份内置定义，行为与改造前完全一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VitalsServiceImpl implements VitalsService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final IcuPatientMapper icuPatientMapper;
    private final IcuPatientService icuPatientService;
    private final DataItemConfigService dataItemConfigService;

    /** 指标定义：key / 展示名 / 单位 / 来源 / 编码组 */
    private static final class Def {
        private final String key;
        private final String name;
        private final String unit;
        private final String source;
        private final List<String> codes = new ArrayList<>();

        private Def(String key, String name, String unit, String source, String code) {
            this.key = key;
            this.name = name;
            this.unit = unit;
            this.source = source;
            if (code != null) {
                this.codes.add(code);
            }
        }
    }

    /** 内置默认定义（配置表为空时的回退，也是初始化数据 config_data_item 的来源） */
    private static final List<Def> BUILTIN = new ArrayList<>();

    static {
        BUILTIN.add(new Def("hr", "心率", "次/分", "无创", "oi_hr"));
        BUILTIN.add(new Def("rr", "呼吸频率", "次/分", "无创", "oi_hxpl"));
        BUILTIN.add(new Def("spo2", "SpO₂", "%", "无创", "oi_spo2"));
        BUILTIN.add(new Def("sbp", "收缩压", "mmHg", "无创", "oi_wcssy"));
        BUILTIN.add(new Def("dbp", "舒张压", "mmHg", "无创", "oi_wcszy"));
        BUILTIN.add(new Def("map", "平均压", "mmHg", "无创", "oi_pjy"));
        BUILTIN.add(new Def("temp", "体温", "℃", "未标明", "oi_tiwen"));
        BUILTIN.add(new Def("sbp_inv", "收缩压（有创）", "mmHg", "有创", "oi_ycssy"));
        BUILTIN.add(new Def("dbp_inv", "舒张压（有创）", "mmHg", "有创", "oi_ycszy"));
        BUILTIN.add(new Def("map_inv", "平均压（有创）", "mmHg", "有创", "oi_ycpjy"));
    }

    /**
     * 取本次使用的指标定义：优先配置表，未配置则用内置。
     *
     * <p>同一 item_key 配了多行（例如心率在监护仪与呼吸机各一个编码）时合并成一条曲线，
     * 否则图上会出现两条同名曲线，读数反而更乱。
     */
    private List<Def> defs() {
        List<DataItemConfig> rows = dataItemConfigService.enabled(DataItemConfigService.DOMAIN_VITAL);
        if (rows == null || rows.isEmpty()) {
            return BUILTIN;
        }
        Map<String, Def> byKey = new LinkedHashMap<>();
        for (DataItemConfig row : rows) {
            if (row == null || row.getItemKey() == null) {
                continue;
            }
            List<String> codes = DataItemConfigService.split(row.getMatchValue());
            if (codes.isEmpty()) {
                continue;
            }
            Def def = byKey.get(row.getItemKey());
            if (def == null) {
                def = new Def(row.getItemKey(),
                        row.getItemName() == null ? row.getItemKey() : row.getItemName(),
                        row.getUnit(), row.getSource(), null);
                byKey.put(row.getItemKey(), def);
            }
            def.codes.addAll(codes);
        }
        return byKey.isEmpty() ? BUILTIN : new ArrayList<>(byKey.values());
    }

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
        List<Def> defs = defs();
        List<String> codes = new ArrayList<>();
        for (Def def : defs) {
            codes.addAll(def.codes);
        }
        List<Map<String, Object>> rows;
        try {
            rows = icuPatientMapper.selectVitalsByCodes(patientId, codes,
                    start.format(ISO), end.format(ISO));
        } catch (Exception e) {
            log.warn("生命体征查询失败 patientId={}", patientId, e);
            for (Def def : defs) {
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
        for (Def def : defs) {
            VitalsResponse.Series s = newSeries(def);
            byKey.put(def.key, s);
        }
        for (Map<String, Object> r : rows) {
            String code = str(r.get("item_code"));
            for (Def def : defs) {
                if (!def.codes.contains(code)) {
                    continue;
                }
                VitalsResponse.Series s = byKey.get(def.key);
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

    private VitalsResponse.Series newSeries(Def def) {
        VitalsResponse.Series s = new VitalsResponse.Series();
        s.setKey(def.key);
        s.setName(def.name);
        s.setUnit(def.unit);
        s.setSource(def.source);
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