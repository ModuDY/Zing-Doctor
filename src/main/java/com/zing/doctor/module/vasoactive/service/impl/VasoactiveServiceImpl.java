package com.zing.doctor.module.vasoactive.service.impl;

import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.module.config.dataitem.service.DataItemConfigService;
import com.zing.doctor.module.vasoactive.service.VasoactiveService;
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
 * 血管活性药分析 Service 实现。
 *
 * <p>取数链路（与 SOFA 循环项同源，口径不另起炉灶）：
 * <ul>
 *   <li>执行时间轴：{@code patient_advice_execute} + {@code _drug}（药名/泵速/液量），
 *       时间重叠判定；调速步骤 {@code patient_advice_execute_step} 还原窗口内调速轨迹；</li>
 *   <li>当前在用：开立链路 {@code patient_advice}（status='1' 执行中），与交接班卡片同 SQL；</li>
 *   <li>MAP 趋势：监护观察项无创/有创平均压（oi_pjy / oi_ycpjy，docs/25 已核实编码）；</li>
 *   <li>乳酸：LIS 乳酸（排除乳酸脱氢酶，与脓毒症集束化同 SQL）。</li>
 * </ul>
 * 各块独立降级：单块查询失败只置 dataStatus=UNKNOWN，不拖垮整页。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VasoactiveServiceImpl implements VasoactiveService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter SEC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** MAP 观察项编码：无创平均压 / 有创平均压（selectVitalsByCodes 按 code 精确匹配） */
    private static final List<String> MAP_CODES = Arrays.asList("oi_pjy", "oi_ycpjy");

    private final IcuPatientMapper icuPatientMapper;
    private final IcuPatientService icuPatientService;

    @Override
    public Map<String, Object> analysis(String patientId, int hours) {
        int range = Math.max(1, Math.min(hours, 72));
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(range);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("patientId", patientId);
        out.put("rangeStart", start.format(ISO));
        out.put("rangeEnd", end.format(ISO));

        String inHospitalNo = null;
        try {
            WorkbenchPatient p = icuPatientService.getWorkbenchPatientAny(patientId);
            if (p != null) {
                inHospitalNo = p.getInHospitalNo();
                out.put("patientName", p.getName());
                out.put("bedNo", p.getBedNo());
                out.put("inHospitalNo", inHospitalNo);
            }
        } catch (Exception e) {
            log.warn("血管活性药分析：患者信息解析失败 patientId={}", patientId, e);
        }
        if (inHospitalNo == null || inHospitalNo.trim().isEmpty()) {
            out.put("dataStatus", "UNKNOWN");
            out.put("note", "未能解析患者住院号，无法取执行医嘱数据");
            return out;
        }
        out.put("dataStatus", "FOUND");

        String s = start.format(SEC);
        String e2 = end.format(SEC);
        out.put("executions", executions(inHospitalNo, s, e2));
        out.put("currentAdvice", currentAdvice(inHospitalNo));
        out.put("mapTrend", mapTrend(patientId, s, e2));
        out.put("lactateTrend", lactateTrend(inHospitalNo, start, end));
        return out;
    }

    /** 执行时间轴：执行单（含泵速）+ 调速步骤；单块失败降级为 UNKNOWN 块。 */
    private Map<String, Object> executions(String inHospitalNo, String start, String end) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("dataStatus", "FOUND");
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            List<Map<String, Object>> execs =
                    icuPatientMapper.selectVasopressorExecuteByRange(inHospitalNo, start, end, "",
                            dataItemConfigService.names(
                                    DataItemConfigService.DOMAIN_DRUG, DataItemConfigService.DRUG_VASOPRESSOR_EXEC));
            if (execs == null || execs.isEmpty()) {
                block.put("dataStatus", "EMPTY");
                block.put("note", "窗口内无血管活性药执行记录");
                block.put("rows", rows);
                return block;
            }
            // 汇总 execute_id 去重（一单多药会产生多行），调速步骤一次批量取
            List<String> executeIds = new ArrayList<>();
            for (Map<String, Object> r : execs) {
                String id = str(r.get("execute_id"));
                if (!id.isEmpty() && !executeIds.contains(id)) {
                    executeIds.add(id);
                }
            }
            Map<String, List<Map<String, Object>>> stepsByExec = new LinkedHashMap<>();
            if (!executeIds.isEmpty()) {
                try {
                    for (Map<String, Object> st : icuPatientMapper.selectVasopressorSpeedSteps(executeIds, end)) {
                        stepsByExec.computeIfAbsent(str(st.get("execute_id")), k -> new ArrayList<>()).add(st);
                    }
                } catch (Exception e) {
                    log.warn("血管活性药调速步骤查询失败（仅丢失调速轨迹，不影响主时间轴）", e);
                }
            }
            // 行按 execute_id + drug_name 去重聚合；泵速取主表当前速度，调速步骤挂子表
            Map<String, Map<String, Object>> byExecDrug = new LinkedHashMap<>();
            for (Map<String, Object> r : execs) {
                String key = str(r.get("execute_id")) + "|" + str(r.get("drug_name"));
                Map<String, Object> row = byExecDrug.get(key);
                if (row == null) {
                    row = new LinkedHashMap<>();
                    row.put("executeId", str(r.get("execute_id")));
                    row.put("drugName", str(r.get("drug_name")));
                    row.put("drugType", classifyVaso(str(r.get("drug_name"))));
                    row.put("adviceName", str(r.get("advice_name")));
                    row.put("speedNow", decimal(r.get("drug_now_speed")));
                    row.put("speedStart", decimal(r.get("drug_start_speed")));
                    row.put("speedUnit", str(r.get("drug_speed_unit")));
                    row.put("startTime", str(r.get("start_time")));
                    row.put("endTime", str(r.get("end_time")));
                    row.put("steps", stepsByExec.get(str(r.get("execute_id"))));
                    byExecDrug.put(key, row);
                }
            }
            rows.addAll(byExecDrug.values());
        } catch (Exception e) {
            log.warn("血管活性药执行记录查询失败 inHospitalNo={}", inHospitalNo, e);
            block.put("dataStatus", "UNKNOWN");
            block.put("note", "执行医嘱查询失败，数据暂不可用");
        }
        block.put("rows", rows);
        return block;
    }

    /** 当前在用（开立链路 status='1'），与交接班/集束化卡片同 SQL 同口径。 */
    private Map<String, Object> currentAdvice(String inHospitalNo) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("dataStatus", "FOUND");
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            List<Map<String, Object>> advices = icuPatientMapper.selectCurrentVasopressorAdvice(inHospitalNo,
                    dataItemConfigService.names(
                            DataItemConfigService.DOMAIN_DRUG, DataItemConfigService.DRUG_VASOPRESSOR));
            if (advices == null || advices.isEmpty()) {
                block.put("dataStatus", "EMPTY");
                block.put("note", "当前无执行中的血管活性药开立医嘱");
            } else {
                for (Map<String, Object> a : advices) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", str(a.get("name")));
                    row.put("drugType", classifyVaso(str(a.get("name"))));
                    row.put("method", str(a.get("method_name")));
                    row.put("freq", str(a.get("freq_name")));
                    row.put("startTime", str(a.get("start_time")));
                    row.put("dosage", str(a.get("drug_one_dosage")));
                    row.put("dosageUnit", str(a.get("drug_one_dosage_unit")));
                    list.add(row);
                }
            }
        } catch (Exception e) {
            log.warn("血管活性药在用医嘱查询失败 inHospitalNo={}", inHospitalNo, e);
            block.put("dataStatus", "UNKNOWN");
            block.put("note", "在用医嘱查询失败，数据暂不可用");
        }
        block.put("rows", list);
        return block;
    }

    /** MAP 趋势：监护观察项无创/有创平均压；来源标记保留，不合并两条通道。 */
    private Map<String, Object> mapTrend(String patientId, String start, String end) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("dataStatus", "FOUND");
        List<Map<String, Object>> points = new ArrayList<>();
        try {
            if (patientId == null || patientId.trim().isEmpty()) {
                block.put("dataStatus", "EMPTY");
            } else {
                List<Map<String, Object>> rows =
                        icuPatientMapper.selectVitalsByCodes(patientId, MAP_CODES, start, end);
                if (rows == null || rows.isEmpty()) {
                    block.put("dataStatus", "EMPTY");
                } else {
                    for (Map<String, Object> r : rows) {
                        BigDecimal v = decimal(r.get("item_value"));
                        if (v == null) {
                            continue;   // 带文字的值不入趋势点
                        }
                        Map<String, Object> p = new LinkedHashMap<>();
                        p.put("time", str(r.get("item_time")));
                        p.put("value", v);
                        p.put("itemName", str(r.get("item_name")));
                        points.add(p);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("MAP 趋势查询失败 patientId={}", patientId, e);
            block.put("dataStatus", "UNKNOWN");
            block.put("note", "监护平均压查询失败，数据暂不可用");
        }
        block.put("points", points);
        return block;
    }

    /** 乳酸趋势：LIS 全量查出后在 Java 侧截到回看窗口（SQL 无时间下界参数）。 */
    private Map<String, Object> lactateTrend(String inHospitalNo, LocalDateTime start, LocalDateTime end) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("dataStatus", "FOUND");
        List<Map<String, Object>> points = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = icuPatientMapper.selectLactateResults(inHospitalNo);
            if (rows != null) {
                for (Map<String, Object> r : rows) {
                    LocalDateTime t = parseTime(str(r.get("check_time")));
                    if (t == null || t.isBefore(start) || t.isAfter(end)) {
                        continue;
                    }
                    BigDecimal v = decimal(r.get("result"));
                    if (v == null) {
                        continue;
                    }
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("time", str(r.get("check_time")));
                    p.put("value", v);
                    p.put("unit", str(r.get("unit")));
                    points.add(p);
                }
            }
            if (points.isEmpty()) {
                block.put("dataStatus", "EMPTY");
            }
        } catch (Exception e) {
            log.warn("乳酸趋势查询失败 inHospitalNo={}", inHospitalNo, e);
            block.put("dataStatus", "UNKNOWN");
            block.put("note", "乳酸检验查询失败，数据暂不可用");
        }
        block.put("points", points);
        return block;
    }

    /**
     * 药名归类（展示用标签）。注意「去甲肾上腺素」包含「肾上腺素」，必须先判去甲
     * —— 与 SOFA 模块 classifyVaso 同序，间羟胺/加压素类为执行链路外常用药，补全归类。
     */
    static String classifyVaso(String drugName) {
        if (drugName == null) {
            return null;
        }
        String n = drugName.replace(" ", "").replace("　", "");
        if (n.contains("去甲肾上腺素")) return "去甲肾上腺素类";
        if (n.contains("多巴酚丁胺")) return "多巴酚丁胺类";
        if (n.contains("多巴胺")) return "多巴胺类";
        if (n.contains("肾上腺素")) return "肾上腺素类";
        if (n.contains("间羟胺")) return "间羟胺";
        if (n.contains("血管加压素") || n.contains("垂体后叶") || n.contains("特利加压素")) return "加压素类";
        return "其他";
    }

    private static BigDecimal decimal(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime parseTime(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.replace(' ', 'T').substring(0, 19));
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
