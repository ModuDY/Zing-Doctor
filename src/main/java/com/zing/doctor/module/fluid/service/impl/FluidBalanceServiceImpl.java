package com.zing.doctor.module.fluid.service.impl;

import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.module.fluid.dto.FluidBalanceResponse;
import com.zing.doctor.module.fluid.service.FluidBalanceService;
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
 * 体液平衡实现（docs/25 §2）。
 *
 * <p>四类来源分别查询、分别汇总（体液平衡专用方法，与交接班口径区分）：
 * <ol>
 *   <li>非尿出量：{@code selectFluidOutputExclUrine}（io_type='o' 且排除 ii_nl）</li>
 *   <li>尿量：{@code selectUrineByPatient}（item_code='ii_nl'）</li>
 *   <li>医嘱入量：{@code selectAdviceIntakeByWindow}（任意时间窗，不做07:00班次换算）</li>
 *   <li>其他入量：{@code selectOtherIntakeByPatient}（module_code='io_in'）</li>
 * </ol>
 *
 * <p><b>四栏互斥</b>：非尿出量已排除 ii_nl，与尿量不重复；入量侧医嘱入量与其他入量
 * （非药品 io_in）按来源分通道，不跨通道合计净平衡（两条通道未证明已去重）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FluidBalanceServiceImpl implements FluidBalanceService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("MM-dd HH");

    private final IcuPatientMapper icuPatientMapper;
    private final IcuPatientService icuPatientService;

    @Override
    public FluidBalanceResponse balance(String patientId, int hours) {
        int range = Math.max(1, Math.min(hours, 72));
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(range);
        FluidBalanceResponse resp = new FluidBalanceResponse();
        resp.setPatientId(patientId);
        resp.setRangeStart(start.format(ISO));
        resp.setRangeEnd(end.format(ISO));

        WorkbenchPatient p = icuPatientService.getWorkbenchPatientAny(patientId);
        if (p != null) {
            resp.setPatientName(p.getName());
            resp.setInHospitalNo(p.getInHospitalNo());
        }

        String s = start.format(ISO);
        String e = end.format(ISO);

        resp.getSources().add(source("output", "出量（引流/呕吐等，不含尿）", icuPatientMapper.selectFluidOutputExclUrine(patientId, s, e), resp));
        resp.getSources().add(source("urine", "尿量", icuPatientMapper.selectUrineByPatient(patientId, s, e), resp));
        resp.getSources().add(source("advice_in", "医嘱入量", icuPatientMapper.selectAdviceIntakeByWindow(patientId, s, e), resp));
        resp.getSources().add(source("other_in", "其他入量（非药品）", icuPatientMapper.selectOtherIntakeByPatient(patientId, s, e), resp));

        resp.setRecordCount(resp.getSources().stream()
                .mapToInt(x -> x.getRows().size()).sum());
        return resp;
    }

    private FluidBalanceResponse.Source source(String key, String name,
                                              List<Map<String, Object>> rows,
                                              FluidBalanceResponse resp) {
        FluidBalanceResponse.Source src = new FluidBalanceResponse.Source();
        src.setKey(key);
        src.setName(name);
        if (rows == null || rows.isEmpty()) {
            src.setDataStatus("NO_RECORD");
            src.setNote("窗口内无记录");
            return src;
        }
        Map<String, Double> byHour = new LinkedHashMap<>();
        double total = 0;
        boolean hasValue = false;
        for (Map<String, Object> r : rows) {
            Double v = parseDouble(firstOf(r, "item_value", "value", "amount", "total_value"));
            String time = toIso(firstOf(r, "item_time", "time", "start_time"));
            String itemName = str(firstOf(r, "item_name", "name", "drug_name"));
            String unit = str(firstOf(r, "item_unit", "unit", "dosage_unit"));
            String staff = str(firstOf(r, "staff_name", "record_staff_name", "execute_staff_name"));
            if (v != null) {
                total += v;
                hasValue = true;
                String hour = time != null && time.length() >= 16 ? time.substring(5, 16).replace('T', ' ') : time;
                byHour.merge(hour == null ? "?" : hour, v, Double::sum);
            }
            FluidBalanceResponse.Row row = new FluidBalanceResponse.Row();
            row.setTime(time);
            row.setItemName(itemName);
            row.setValue(str(firstOf(r, "item_value", "value", "amount", "total_value")));
            row.setUnit(unit);
            row.setStaffName(staff);
            row.setSource(key);
            src.getRows().add(row);
        }
        byHour.forEach((h, v) -> {
            FluidBalanceResponse.Bucket b = new FluidBalanceResponse.Bucket();
            b.setHour(h);
            b.setValue(v);
            src.getBuckets().add(b);
        });
        src.setTotal(hasValue ? total : null);
        src.setDataStatus("AVAILABLE");
        src.setNote("同一来源内合计；不同来源未去重，不相加");
        return src;
    }

    private static Object firstOf(Map<String, Object> r, String... keys) {
        for (String k : keys) {
            if (r.get(k) != null) {
                return r.get(k);
            }
        }
        return null;
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
