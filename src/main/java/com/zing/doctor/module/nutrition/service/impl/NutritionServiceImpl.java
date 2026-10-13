package com.zing.doctor.module.nutrition.service.impl;

import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.module.nutrition.dto.NutritionResponse;
import com.zing.doctor.module.nutrition.dto.NutritionResponse.NutritionOrder;
import com.zing.doctor.module.nutrition.dto.NutritionResponse.PatientHeader;
import com.zing.doctor.module.nutrition.dto.NutritionResponse.Totals;
import com.zing.doctor.module.nutrition.dto.NutritionResponse.Window;
import com.zing.doctor.module.nutrition.mapper.NutritionFormulaMapper;
import com.zing.doctor.module.nutrition.mapper.NutritionIcuMapper;
import com.zing.doctor.module.nutrition.service.NutritionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 营养支持分析实现。
 *
 * <p>算法（已连库核实）：实际给入 ml = Σ 每小时 item.amount；能量/蛋白 = 该值 × 配方密度。
 * 用医嘱名称匹配 config_nutrition_formula.match_keywords 识别制剂；未匹配的普通输液/溶媒不计入营养。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NutritionServiceImpl implements NutritionService {

    private final NutritionIcuMapper nutritionIcuMapper;
    private final NutritionFormulaMapper nutritionFormulaMapper;

    private static final int DEFAULT_HOURS = 24;
    private static final int MIN_HOURS = 1;
    private static final int MAX_HOURS = 720;

    @Override
    public NutritionResponse analyze(WorkbenchPatient patient, Integer hours, String startIso, String endIso) {
        boolean hasStart = startIso != null && !startIso.trim().isEmpty();
        boolean hasEnd = endIso != null && !endIso.trim().isEmpty();
        if (hasStart != hasEnd) {
            throw new IllegalArgumentException("自定义起止时间需同时提供开始与结束");
        }

        int windowHours;
        LocalDateTime end;
        LocalDateTime start;
        if (hasStart) {
            LocalDateTime s = parseIso(startIso.trim(), "开始");
            LocalDateTime e = parseIso(endIso.trim(), "结束");
            if (!e.isAfter(s)) throw new IllegalArgumentException("结束时间必须晚于开始时间");
            long minutes = Duration.between(s, e).toMinutes();
            if (minutes > (long) MAX_HOURS * 60) throw new IllegalArgumentException("自定义时间范围不能超过 30 天");
            start = s; end = e;
            windowHours = (int) Math.max(MIN_HOURS, Math.round(minutes / 60.0));
        } else {
            windowHours = normalize(hours);
            end = LocalDateTime.now();
            start = end.minusHours(windowHours);
        }

        NutritionResponse resp = new NutritionResponse();
        resp.setHeader(toHeader(patient));
        Window w = new Window();
        w.setHours(windowHours);
        w.setStartTime(start.toString());
        w.setEndTime(end.toString());
        resp.setWindow(w);
        resp.setNote("实际给入量按执行明细逐小时累加（ml），能量/蛋白＝给入量×制剂密度；"
                + "普通输液、溶媒/冲管用水不计入营养；肠外为部分能量，未含葡萄糖热卡，仅供参考。");

        try {
            List<Map<String, Object>> orders = nutritionIcuMapper.selectOrders(
                    patient.getPatientId(), start.toString(), end.toString());
            List<Map<String, Object>> items = nutritionIcuMapper.selectHourItems(
                    patient.getPatientId(), start.toString(), end.toString());
            List<Map<String, Object>> formulas = nutritionFormulaMapper.selectActiveFormulas();

            // 每执行单已给入 ml
            Map<String, BigDecimal> givenByExec = new HashMap<>();
            for (Map<String, Object> it : items) {
                BigDecimal amt = toBd(it.get("amount"));
                if (amt == null) continue;
                givenByExec.merge(String.valueOf(it.get("execute_id")), amt, BigDecimal::add);
            }

            List<NutritionOrder> list = new ArrayList<>();
            Totals totals = new Totals();
            int unmapped = 0;
            for (Map<String, Object> o : orders) {
                String name = String.valueOf(o.get("name") == null ? "" : o.get("name"));
                String execId = String.valueOf(o.get("execute_id"));
                BigDecimal given = givenByExec.getOrDefault(execId, BigDecimal.ZERO);
                Map<String, Object> f = matchFormula(name, formulas);
                if (f == null) {
                    if (!name.isEmpty()) unmapped++;
                    continue;
                }
                BigDecimal energy = toBd(f.get("energy_kcal_per_ml"));
                BigDecimal protein = toBd(f.get("protein_g_per_ml"));
                NutritionOrder no = new NutritionOrder();
                no.setFormulaCode(String.valueOf(f.get("formula_code")));
                no.setFormulaName(String.valueOf(f.get("formula_name")));
                no.setRouteType(String.valueOf(f.get("route_type")));
                no.setMethodName(String.valueOf(o.get("method_name")));
                no.setBagVolume(toBd(o.get("bag_volume")));
                no.setStartTime(o.get("start_time") == null ? null : String.valueOf(o.get("start_time")));
                no.setEndTime(o.get("end_time") == null ? null : String.valueOf(o.get("end_time")));
                no.setGivenMl(given.setScale(1, RoundingMode.HALF_UP));
                BigDecimal kcal = energy == null ? BigDecimal.ZERO : given.multiply(energy);
                BigDecimal p = protein == null ? BigDecimal.ZERO : given.multiply(protein);
                no.setKcal(kcal.setScale(1, RoundingMode.HALF_UP));
                no.setProteinG(p.setScale(2, RoundingMode.HALF_UP));
                no.setMapped(true);
                list.add(no);

                if ("ENTERAL".equals(no.getRouteType())) {
                    totals.setEnteralMl(totals.getEnteralMl().add(given));
                    totals.setEnteralKcal(totals.getEnteralKcal().add(kcal));
                    totals.setEnteralProteinG(totals.getEnteralProteinG().add(p));
                } else {
                    totals.setParenteralMl(totals.getParenteralMl().add(given));
                    totals.setParenteralKcal(totals.getParenteralKcal().add(kcal));
                    totals.setParenteralProteinG(totals.getParenteralProteinG().add(p));
                }
            }
            totals.setTotalKcal(totals.getEnteralKcal().add(totals.getParenteralKcal()).setScale(1, RoundingMode.HALF_UP));
            totals.setTotalProteinG(totals.getEnteralProteinG().add(totals.getParenteralProteinG()).setScale(2, RoundingMode.HALF_UP));
            totals.setEnteralKcal(totals.getEnteralKcal().setScale(1, RoundingMode.HALF_UP));
            totals.setEnteralProteinG(totals.getEnteralProteinG().setScale(2, RoundingMode.HALF_UP));
            totals.setParenteralKcal(totals.getParenteralKcal().setScale(1, RoundingMode.HALF_UP));
            totals.setParenteralProteinG(totals.getParenteralProteinG().setScale(2, RoundingMode.HALF_UP));

            resp.setOrders(list);
            resp.setTotals(totals);
            resp.setUnmappedCount(unmapped);
            resp.setDataStatus(list.isEmpty() ? "NOT_RECORDED" : "FOUND");
        } catch (Exception e) {
            log.warn("营养支持分析查询失败(patientId={})", patient.getPatientId(), e);
            resp.setDataStatus("UNKNOWN");
            resp.setNote("数据暂不可用");
            resp.setOrders(new ArrayList<>());
            resp.setTotals(new Totals());
        }
        return resp;
    }

    /** 名称命中配方任一关键词即匹配（长关键词优先，避免“瑞代”误中“瑞代短肽”重复）。 */
    private Map<String, Object> matchFormula(String name, List<Map<String, Object>> formulas) {
        if (name == null || name.isEmpty()) return null;
        Map<String, Object> hit = null;
        int bestLen = 0;
        for (Map<String, Object> f : formulas) {
            String kw = String.valueOf(f.get("match_keywords") == null ? "" : f.get("match_keywords"));
            for (String k : kw.split(",")) {
                String key = k.trim();
                if (!key.isEmpty() && name.contains(key) && key.length() > bestLen) {
                    hit = f; bestLen = key.length();
                }
            }
        }
        return hit;
    }

    private static BigDecimal toBd(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        try { return new BigDecimal(String.valueOf(v)); }
        catch (Exception e) { return null; }
    }

    private static LocalDateTime parseIso(String value, String label) {
        try { return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException(label + "时间格式错误"); }
    }

    private int normalize(Integer hours) {
        if (hours == null || hours <= 0) return DEFAULT_HOURS;
        if (hours < MIN_HOURS) return MIN_HOURS;
        return Math.min(hours, MAX_HOURS);
    }

    private PatientHeader toHeader(WorkbenchPatient p) {
        PatientHeader h = new PatientHeader();
        h.setPatientId(p.getPatientId());
        h.setInHospitalNo(p.getInHospitalNo());
        h.setName(p.getName());
        h.setBedNo(p.getBedNo());
        h.setDepartName(p.getDepartName());
        h.setInDepartmentTime(p.getInDepartmentTime() == null ? null : p.getInDepartmentTime().toString());
        return h;
    }
}
