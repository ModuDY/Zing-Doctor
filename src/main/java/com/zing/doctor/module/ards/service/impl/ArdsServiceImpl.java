package com.zing.doctor.module.ards.service.impl;

import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.module.ards.service.ArdsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ARDS 监测 Service 实现
 */
@Slf4j
@Service
public class ArdsServiceImpl implements ArdsService {

    @Autowired
    private IcuPatientMapper icuPatientMapper;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Map<String, Object> getArdsOverview(String departCode, String startTime, String endTime, String grade) {
        // 1. 查询ARDS患者列表（按出科时间范围）
        List<Map<String, Object>> patients = icuPatientMapper.selectArdsPatients(departCode, startTime, endTime);
        if (patients == null || patients.isEmpty()) {
            return buildEmptyResult();
        }

        List<String> patientIds = patients.stream()
                .map(p -> str(p.get("patient_id")))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        // 2. 批量查询最新呼吸机参数
        Map<String, Map<String, Object>> ventMap = new HashMap<>();
        if (!patientIds.isEmpty()) {
            List<Map<String, Object>> ventList = icuPatientMapper.selectLatestVentilatorParams(patientIds);
            if (ventList != null) {
                for (Map<String, Object> v : ventList) {
                    String pid = str(v.get("patient_id"));
                    String code = str(v.get("item_code"));
                    ventMap.computeIfAbsent(pid, k -> new HashMap<>()).put(code, v);
                }
            }
        }

        // 3. 组装患者数据
        List<Map<String, Object>> resultList = new ArrayList<>();
        int cntMild = 0, cntModerate = 0, cntSevere = 0, cntUnknown = 0;
        int cntVtOk = 0, cntPeepOk = 0, cntFio2Ok = 0, cntRrOk = 0, cntVentData = 0;
        BigDecimal sumOi = BigDecimal.ZERO;
        int cntOi = 0;

        for (Map<String, Object> p : patients) {
            String pid = str(p.get("patient_id"));
            Map<String, Object> patientData = new LinkedHashMap<>();
            patientData.put("patient_id", pid);
            patientData.put("in_hospital_no", p.get("in_hospital_no"));
            patientData.put("patient_name", p.get("patient_name"));
            patientData.put("bed_code", p.get("bed_code"));
            patientData.put("gender", p.get("gender"));
            patientData.put("height", p.get("height"));
            patientData.put("in_depart_time", p.get("in_depart_time"));
            patientData.put("out_depart_time", p.get("out_depart_time"));

            // 呼吸机参数
            Map<String, Object> vent = ventMap.get(pid);
            Double vt = getVentValue(vent, "oi_呼末潮气量");
            Double peep = getVentValue(vent, "oi_peep");
            Double fio2 = getVentValue(vent, "oi_FiO2(设置值)");
            Double rr = getVentValue(vent, "oi_呼吸频率(设置值)");
            Double mv = getVentValue(vent, "oi_呼末分钟通气量");

            patientData.put("vt_ml", vt);
            patientData.put("peep", peep);
            patientData.put("fio2", fio2);
            patientData.put("rr", rr);
            patientData.put("mv", mv);

            // 理想体重和潮气量/kg
            Double idealWeight = calcIdealWeight(p.get("gender"), p.get("height"), p.get("weight"));
            patientData.put("ideal_weight_estimated", p.get("height") == null);
            patientData.put("ideal_weight", idealWeight);
            Double vtPerKg = null;
            if (vt != null && idealWeight != null && idealWeight > 0) {
                vtPerKg = round1(vt / idealWeight);
            }
            patientData.put("vt_per_kg", vtPerKg);

            // 氧合指数（取最新血气）
            BigDecimal latestOi = null;
            try {
                List<Map<String, Object>> oiList = icuPatientMapper.selectOxygenationHistory(pid);
                if (oiList != null && !oiList.isEmpty()) {
                    Map<String, Object> last = oiList.get(oiList.size() - 1);
                    Object oiVal = last.get("oxygenation_index");
                    if (oiVal != null) {
                        latestOi = new BigDecimal(oiVal.toString());
                    }
                }
            } catch (Exception e) {
                log.warn("查询氧合指数失败: patientId={}", pid, e);
            }
            patientData.put("oxygenation_index", latestOi);

            // ARDS分级
            String ardsGrade = "未知";
            if (latestOi != null) {
                double oi = latestOi.doubleValue();
                if (oi > 200 && oi <= 300) {
                    ardsGrade = "轻度";
                    cntMild++;
                } else if (oi > 100 && oi <= 200) {
                    ardsGrade = "中度";
                    cntModerate++;
                } else if (oi > 0 && oi <= 100) {
                    ardsGrade = "重度";
                    cntSevere++;
                } else {
                    cntUnknown++;
                }
                sumOi = sumOi.add(latestOi);
                cntOi++;
            } else {
                cntUnknown++;
            }
            patientData.put("ards_grade", ardsGrade);

            // 肺保护性通气达标情况
            Map<String, Object> compliance = new LinkedHashMap<>();
            boolean hasVentData = vt != null || peep != null || fio2 != null || rr != null;
            if (hasVentData) cntVentData++;

            boolean vtOk = vtPerKg != null && vtPerKg <= 6.0;
            boolean peepOk = peep != null && peep >= 5.0;
            // FiO2可能是百分比(60)或小数(0.6)，统一判断
            boolean fio2Ok = fio2 != null && (fio2 <= 60.0 || (fio2 <= 1.0 && fio2 > 0));
            boolean rrOk = rr != null && rr <= 35.0;

            compliance.put("vt_ok", vtOk);
            compliance.put("peep_ok", peepOk);
            compliance.put("fio2_ok", fio2Ok);
            compliance.put("rr_ok", rrOk);
            compliance.put("all_ok", vtOk && peepOk && fio2Ok && rrOk);
            patientData.put("compliance", compliance);

            if (hasVentData) {
                if (vtOk) cntVtOk++;
                if (peepOk) cntPeepOk++;
                if (fio2Ok) cntFio2Ok++;
                if (rrOk) cntRrOk++;
            }

            // 机械通气天数（入科到现在或出科）
            int ventDays = calcVentDays(p.get("in_depart_time"), p.get("out_depart_time"));
            patientData.put("vent_days", ventDays);

            // 按分级筛选
            if (grade != null && !grade.isEmpty() && !"全部".equals(grade)) {
                if (!grade.equals(ardsGrade)) {
                    continue;
                }
            }

            resultList.add(patientData);
        }

        // 4. 汇总统计
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", resultList.size());
        summary.put("mild", cntMild);
        summary.put("moderate", cntModerate);
        summary.put("severe", cntSevere);
        summary.put("unknown_grade", cntUnknown);
        summary.put("avg_oxygenation_index", cntOi > 0 ? round1(sumOi.doubleValue() / cntOi) : null);
        summary.put("vent_data_count", cntVentData);
        // 依从率
        summary.put("vt_compliance_rate", cntVentData > 0 ? round1(cntVtOk * 100.0 / cntVentData) : 0);
        summary.put("peep_compliance_rate", cntVentData > 0 ? round1(cntPeepOk * 100.0 / cntVentData) : 0);
        summary.put("fio2_compliance_rate", cntVentData > 0 ? round1(cntFio2Ok * 100.0 / cntVentData) : 0);
        summary.put("rr_compliance_rate", cntVentData > 0 ? round1(cntRrOk * 100.0 / cntVentData) : 0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("patients", resultList);
        return result;
    }

    @Override
    public List<Map<String, Object>> getVentilatorTrend(String patientId, String startTime, String endTime) {
        List<Map<String, Object>> list = icuPatientMapper.selectVentilatorTrend(patientId, startTime, endTime);
        return list != null ? list : new ArrayList<>();
    }

    @Override
    public List<Map<String, Object>> getOxygenationHistory(String patientId) {
        List<Map<String, Object>> list = icuPatientMapper.selectOxygenationHistory(patientId);
        return list != null ? list : new ArrayList<>();
    }

    // ==================== 工具方法 ====================

    private Map<String, Object> buildEmptyResult() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", 0);
        summary.put("mild", 0);
        summary.put("moderate", 0);
        summary.put("severe", 0);
        summary.put("unknown_grade", 0);
        summary.put("avg_oxygenation_index", null);
        summary.put("vent_data_count", 0);
        summary.put("vt_compliance_rate", 0);
        summary.put("peep_compliance_rate", 0);
        summary.put("fio2_compliance_rate", 0);
        summary.put("rr_compliance_rate", 0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("patients", new ArrayList<>());
        return result;
    }

    private Double getVentValue(Map<String, Object> vent, String code) {
        if (vent == null) return null;
        Map<String, Object> item = (Map<String, Object>) vent.get(code);
        if (item == null) return null;
        Object val = item.get("item_value");
        if (val == null) return null;
        try {
            return Double.parseDouble(val.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 理想体重计算：男 50 + 0.91×(身高cm-152.4)，女 45.5 + 0.91×(身高cm-152.4)
     */
    private Double calcIdealWeight(Object gender, Object height, Object weight) {
        String g = str(gender);
        // 优先：有身高 → 按公式计算理想体重
        if (height != null) {
            try {
                double h = Double.parseDouble(height.toString());
                if (h > 0) {
                    double base = "女".equals(g) ? 45.5 : 50.0;
                    return round1(base + 0.91 * (h - 152.4));
                }
            } catch (Exception ignored) {}
        }
        // 降级1：身高为空但有实际体重 → 用实际体重代替
        if (weight != null) {
            try {
                double w = Double.parseDouble(weight.toString());
                if (w > 0) return round1(w);
            } catch (Exception ignored) {}
        }
        // 降级2：都为空 → 用默认理想体重（男70kg / 女60kg）
        return "女".equals(g) ? 60.0 : 70.0;
    }

    private int calcVentDays(Object inDepartTime, Object outDepartTime) {
        if (inDepartTime == null) return 0;
        try {
            LocalDateTime in = parseTime(inDepartTime.toString());
            LocalDateTime out = outDepartTime != null ? parseTime(outDepartTime.toString()) : LocalDateTime.now();
            if (in == null || out == null) return 0;
            long hours = java.time.Duration.between(in, out).toHours();
            return (int) Math.ceil(hours / 24.0);
        } catch (Exception e) {
            return 0;
        }
    }

    private LocalDateTime parseTime(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            // 处理 ISO 格式 2026-09-08T06:15:00.000+00:00
            if (s.contains("T")) {
                s = s.replace("T", " ").substring(0, Math.min(19, s.length()));
            }
            return LocalDateTime.parse(s, DTF);
        } catch (Exception e) {
            return null;
        }
    }

    private String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private Double round1(Double v) {
        if (v == null) return null;
        return Math.round(v * 10.0) / 10.0;
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
