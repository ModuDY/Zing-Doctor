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
 * <p>四类来源分别查询、分别汇总（复用 IcuPatientMapper 既有方法）：
 * <ol>
 *   <li>出量（尿/引流等）：{@code selectOutputByPatient}（config_io_item.io_type='o'）</li>
 *   <li>尿量：{@code selectUrineByPatient}（item_code='ii_nl'）</li>
 *   <li>医嘱入量：{@code selectAdviceIntakeByPatient}（patient_advice_execute.is_to_io='1'）</li>
 *   <li>其他入量：{@code selectOtherIntakeByPatient}（module_code='io_in'）</li>
 * </ol>
 *
 * <p><b>不做跨来源合计</b>：核查未能证明 I/O 记录与医嘱执行两条通道已去重，
 * 且 io_type='i'（252 个配置）从未被使用。方案明确"来源可解释且已去重后才计算净平衡"。
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
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        resp.getSources().add(source("output", "出量（尿/引流等）", icuPatientMapper.selectOutputByPatient(patientId, s, e), resp));
        resp.getSources().add(source("urine", "尿量", icuPatientMapper.selectUrineByPatient(patientId, s, e), resp));
        resp.getSources().add(source("advice_in", "医嘱入量", icuPatientMapper.selectAdviceIntakeByPatient(patientId, today, s, e), resp));
        resp.getSources().add(source("other_in", "其他入量", icuPatientMapper.selectOtherIntakeByPatient(patientId, s, e), resp));

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
