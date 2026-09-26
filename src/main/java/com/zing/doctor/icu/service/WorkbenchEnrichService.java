package com.zing.doctor.icu.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.icu.support.InfectionRules;
import com.zing.doctor.module.apache2.entity.Apache2ScoreRecord;
import com.zing.doctor.module.apache2.mapper.Apache2ScoreRecordMapper;
import com.zing.doctor.module.sofa.entity.SofaScoreRecord;
import com.zing.doctor.module.sofa.mapper.SofaScoreRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 患者工作台待办与评分 enrichment（本系统主库，非 ICU 库）。
 *
 * <p>与 {@link com.zing.doctor.icu.service.impl.SqlIcuPatientServiceImpl}（@DS("icu")）
 * 分开：后者只读 ICU 信息库；本服务查本系统自己的评分文书表
 * （patient_doc_sofa_score_record / patient_doc_apache2_score_record），
 * 用 MyBatis-Plus QueryWrapper 批量取，避免逐患者 N+1。
 *
 * <p>待办口径（第一期，克制）：
 * <ul>
 *   <li>{@code SOFA_NOT_TODAY} —— 入科 ≥24h 但今天还没有 SOFA 评分记录。
 *       新入科（icuDays ≤1）不催，给医生留当天入科评估的时间窗。</li>
 *   <li>{@code APACHE_NOT_TODAY} —— 入科当天应评 admission，24h 内应有记录；
 *       超过 24h 仍没有任何 APACHE 记录才提醒。</li>
 * </ul>
 * 抗生素无血培养 / 导管超期等需要回 ICU 库逐患者比对，放第二期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkbenchEnrichService {

    private final SofaScoreRecordMapper sofaMapper;
    private final Apache2ScoreRecordMapper apacheMapper;
    private final IcuPatientMapper icuPatientMapper;

    /**
     * 给在科患者列表回填：最近 SOFA 总分、当日待办。
     * 直接修改入参列表，不返回新对象。
     */
    public void enrich(List<WorkbenchPatient> patients) {
        if (patients == null || patients.isEmpty()) {
            return;
        }
        List<String> patientIds = new ArrayList<>(patients.size());
        for (WorkbenchPatient p : patients) {
            if (p.getPatientId() != null && !p.getPatientId().isEmpty()) {
                patientIds.add(p.getPatientId());
            }
        }
        if (patientIds.isEmpty()) {
            return;
        }

        // 1) 最近一次 SOFA 总分（每个患者取 score_time 最新的一条）
        Map<String, SofaScoreRecord> latestSofa = latestSofaByPatient(patientIds);
        // 2) 今天有 SOFA 评分的 patientId 集合
        Set<String> sofaToday = scoredToday("sofa", patientIds);
        // 3) 今天/近 24h 有 APACHE 评分的 patientId 集合
        Set<String> apacheToday = scoredToday("apache", patientIds);

        LocalDate today = LocalDate.now();
        for (WorkbenchPatient p : patients) {
            SofaScoreRecord s = latestSofa.get(p.getPatientId());
            if (s != null) {
                p.setLastSofaScore(s.getTotalScore());
            }

            List<String> todos = new ArrayList<>(2);
            long icuDays = p.getIcuDays() == null ? 0 : p.getIcuDays();
            // 入科 ≥24h 还没评 SOFA
            if (icuDays >= 1 && !sofaToday.contains(p.getPatientId())) {
                todos.add("SOFA_NOT_TODAY");
            }
            // 入科 ≥24h 还没有任何 APACHE 记录（admission 应在入科 24h 内完成）
            if (icuDays >= 1 && !apacheToday.contains(p.getPatientId())) {
                todos.add("APACHE_NOT_TODAY");
            }
            p.setTodos(todos);
            p.setTodoCount(todos.size());
        }
    }

    /**
     * 给在科患者列表回填感染维度（左连接语义：全量在科患者是主表，感染信息是补充）。
     *
     * <p><b>为什么不让前端把两个接口的结果按 patientId 合并</b>：两个接口的科室授权、
     * 外链放行、数据源各不相同，前端合并时只要有一边失败，页面上「没有感染信息」
     * 和「感染信息没拉到」就分不清了——而这恰好是医生最不能看错的一件事。
     *
     * <p>查询次数与患者数无关：疑似集合 1 次 + 诊断/检验/体温/培养/抗菌药各 1 次。
     * 并且只对<b>疑似感染</b>的患者取这些明细：全量患者里大量非感染患者不必拉检验，
     * 他们直接标记"已查过、没有"。
     *
     * @param departCode 科室编码，必须与「感染风险」视图同一个值：疑似集合按科室取，
     *                   两边口径不一致会出现"工作台说这人有感染风险、感染视图里却查不到他"
     */
    public void enrichInfection(List<WorkbenchPatient> patients, String departCode) {
        if (patients == null || patients.isEmpty()) {
            return;
        }
        try {
            // 1) 疑似集合与命中标志：复用「感染风险」视图同一个 SQL，口径不会漂移
            Map<String, boolean[]> suspectFlags = new HashMap<>();
            List<Map<String, Object>> suspects = icuPatientMapper.selectSuspectPatients(departCode);
            if (suspects != null) {
                for (Map<String, Object> r : suspects) {
                    if (r == null) {
                        continue;
                    }
                    String pid = InfectionRules.str(r.get("patient_id"));
                    if (StrUtil.isBlank(pid)) {
                        continue;
                    }
                    suspectFlags.put(pid, new boolean[]{
                            truthy(r.get("shock_flag")) || truthy(r.get("septic_shock")),
                            truthy(r.get("diag_flag")),
                            truthy(r.get("pct_flag"))});
                }
            }

            // 2) 只对疑似患者批量取明细
            List<String> suspectIds = new ArrayList<>();
            List<String> suspectNos = new ArrayList<>();
            for (WorkbenchPatient p : patients) {
                if (p.getPatientId() != null && suspectFlags.containsKey(p.getPatientId())) {
                    suspectIds.add(p.getPatientId());
                    if (StrUtil.isNotBlank(p.getInHospitalNo())) {
                        suspectNos.add(p.getInHospitalNo());
                    }
                }
            }
            if (suspectIds.isEmpty()) {
                // 一个疑似都没有：同样是"已查过"，明确标记为非疑似，而不是留空让人猜
                for (WorkbenchPatient p : patients) {
                    p.setSuspectedInfection(false);
                    p.setInfectionDataStatus("FOUND");
                }
                return;
            }

            Map<String, List<Map<String, Object>>> diagMap =
                    groupRows(icuPatientMapper.selectDiagnosesByPatientIds(suspectIds), "patient_id");
            Map<String, List<Map<String, Object>>> labMap = suspectNos.isEmpty()
                    ? Collections.emptyMap()
                    : groupRows(icuPatientMapper.selectInfectionLabsByNos(suspectNos), "in_hospital_no");
            Map<String, String> tempMap = toValueMap(
                    icuPatientMapper.selectLatestTemperatureByPatientIds(suspectIds), "patient_id", "item_value");
            Map<String, List<Map<String, Object>>> microMap = suspectNos.isEmpty()
                    ? Collections.emptyMap()
                    : groupRows(icuPatientMapper.selectMicrobiologyByNos(suspectNos), "in_hospital_no");
            Map<String, List<Map<String, Object>>> abxMap = suspectNos.isEmpty()
                    ? Collections.emptyMap()
                    : groupRows(icuPatientMapper.selectCurrentAbxAdviceByNos(suspectNos), "in_hospital_no");

            // 3) 逐患者组装（非疑似患者保持空字段 + 明确状态）
            for (WorkbenchPatient p : patients) {
                boolean[] flags = suspectFlags.get(p.getPatientId());
                if (flags == null) {
                    p.setSuspectedInfection(false);
                    p.setInfectionDataStatus("FOUND");
                    continue;
                }
                boolean byShock = flags[0];
                boolean byDiagnosis = flags[1];
                List<Map<String, Object>> diagnoses =
                        diagMap.getOrDefault(p.getPatientId(), Collections.emptyList());

                BigDecimal pct = null;
                BigDecimal wbc = null;
                Set<String> filled = new HashSet<>();
                for (Map<String, Object> r : labMap.getOrDefault(p.getInHospitalNo(), Collections.emptyList())) {
                    String itemName = InfectionRules.str(r.get("item_name"));
                    String result = InfectionRules.str(r.get("result"));
                    if (StrUtil.isBlank(itemName) || StrUtil.isBlank(result)) {
                        continue;
                    }
                    String key = InfectionRules.matchLabKey(itemName);
                    if (key == null || !filled.add(key)) {
                        continue;
                    }
                    if ("PCT".equals(key)) {
                        pct = InfectionRules.parseDecimalValue(result);
                    } else if ("WBC".equals(key)) {
                        wbc = InfectionRules.parseDecimalValue(result);
                    }
                }

                // 仅靠"有 PCT 结果"命中的患者，数值必须达到 0.5 —— 与感染视图同一门槛，
                // 否则工作台会全出现一批 PCT 0.05 的"感染风险"患者
                if (!byShock && !byDiagnosis && !InfectionRules.pctSuggestsInfection(pct)) {
                    p.setSuspectedInfection(false);
                    p.setInfectionDataStatus("FOUND");
                    continue;
                }

                InfectionRules.InfectionResult infection = InfectionRules.inferInfection(diagnoses, byShock);
                InfectionRules.ShockResult shock = InfectionRules.shock(diagnoses, byShock);
                InfectionRules.CultureRisk culture =
                        InfectionRules.cultureRisk(microMap.getOrDefault(p.getInHospitalNo(), Collections.emptyList()));

                List<String> abxNames = new ArrayList<>();
                LocalDateTime abxStart = null;
                for (Map<String, Object> r : abxMap.getOrDefault(p.getInHospitalNo(), Collections.emptyList())) {
                    String name = InfectionRules.str(r.get("name"));
                    if (!InfectionRules.matchesAbx(name) || InfectionRules.isSolvent(name)) {
                        continue;
                    }
                    if (!abxNames.contains(name)) {
                        abxNames.add(name);
                    }
                    LocalDateTime t = toLocalDateTime(r.get("start_time"));
                    if (t != null && (abxStart == null || t.isBefore(abxStart))) {
                        abxStart = t;
                    }
                }

                p.setSuspectedInfection(true);
                p.setInfectionDataStatus("FOUND");
                p.setInfectionType(infection.getType());
                p.setInfectionEvidence(infection.getEvidence());
                p.setSepticShock(shock.isSeptic());
                p.setShockType(shock.getType());
                p.setMrsaRisk(culture.isMrsa());
                p.setMdrRisk(culture.isMdr());
                p.setFungalRisk(culture.isFungal());
                p.setPct(pct);
                p.setWbc(wbc);
                p.setTemperature(InfectionRules.parseDecimalValue(tempMap.get(p.getPatientId())));
                p.setAbxStartTime(abxStart);
                p.setCurrentAbx(abxNames);
                p.setInfectionRiskLevel(InfectionRules.evaluateRisk(pct, shock.isSeptic(),
                        culture.isMdr(), culture.isMrsa(), culture.isFungal(), infection.getType()));
            }
        } catch (Exception e) {
            // 注意：这里是 UNKNOWN，不是"无感染"——字段同样为空，但语义完全不同，
            // 前端必须显示「感染信息暂不可用」而不是「未发现疑似感染证据」
            log.warn("工作台感染维度回填失败，标记为数据不可用", e);
            for (WorkbenchPatient p : patients) {
                p.setInfectionDataStatus("UNKNOWN");
            }
        }
    }

    /** 按指定列把查询结果分组 */
    private Map<String, List<Map<String, Object>>> groupRows(List<Map<String, Object>> rows, String keyColumn) {
        Map<String, List<Map<String, Object>>> out = new HashMap<>();
        if (rows == null) {
            return out;
        }
        for (Map<String, Object> r : rows) {
            if (r == null) {
                continue;
            }
            String key = InfectionRules.str(r.get(keyColumn));
            if (StrUtil.isBlank(key)) {
                continue;
            }
            out.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        return out;
    }

    /** 两列结果转 Map（键列 → 值列） */
    private Map<String, String> toValueMap(List<Map<String, Object>> rows, String keyColumn, String valueColumn) {
        Map<String, String> out = new HashMap<>();
        if (rows == null) {
            return out;
        }
        for (Map<String, Object> r : rows) {
            if (r == null) {
                continue;
            }
            String key = InfectionRules.str(r.get(keyColumn));
            if (StrUtil.isNotBlank(key)) {
                out.put(key, InfectionRules.str(r.get(valueColumn)));
            }
        }
        return out;
    }

    /** ICU 库里布尔标记的写法不统一（1/0、true/false、是/否），统一判断 */
    private static boolean truthy(Object o) {
        String s = InfectionRules.str(o);
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "是".equals(s);
    }

    private static LocalDateTime toLocalDateTime(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof LocalDateTime) {
            return (LocalDateTime) o;
        }
        if (o instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) o).toLocalDateTime();
        }
        if (o instanceof java.util.Date) {
            return new java.sql.Timestamp(((java.util.Date) o).getTime()).toLocalDateTime();
        }
        try {
            return LocalDateTime.parse(InfectionRules.str(o).replace(' ', 'T'));
        } catch (Exception e) {
            return null;
        }
    }

    /** 每个 patientId 取 score_time 最大的一条 SOFA 记录（不返回 pdf_data 大字段）。 */
    private Map<String, SofaScoreRecord> latestSofaByPatient(List<String> patientIds) {
        QueryWrapper<SofaScoreRecord> qw = new QueryWrapper<>();
        qw.select("patient_id", "total_score", "score_time")
                .in("patient_id", patientIds)
                .eq("status", 1)
                .orderByDesc("score_time");
        List<SofaScoreRecord> list = sofaMapper.selectList(qw);
        Map<String, SofaScoreRecord> out = new HashMap<>();
        for (SofaScoreRecord r : list) {
            // 已按 score_time 倒序，putIfAbsent 保留每个 patient 的第一条（最新）
            out.putIfAbsent(r.getPatientId(), r);
        }
        return out;
    }

    /** 今天 [今天 00:00, 明天 00:00) 有评分记录的 patientId 集合。 */
    private Set<String> scoredToday(String which, List<String> patientIds) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        Set<String> out = new HashSet<>();
        try {
            if ("sofa".equals(which)) {
                QueryWrapper<SofaScoreRecord> qw = new QueryWrapper<>();
                qw.select("patient_id")
                        .in("patient_id", patientIds)
                        .eq("status", 1)
                        .ge("score_time", start)
                        .lt("score_time", end);
                for (SofaScoreRecord r : sofaMapper.selectList(qw)) {
                    out.add(r.getPatientId());
                }
            } else {
                QueryWrapper<Apache2ScoreRecord> qw = new QueryWrapper<>();
                qw.select("patient_id")
                        .in("patient_id", patientIds)
                        .eq("status", 1)
                        .ge("score_time", start)
                        .lt("score_time", end);
                for (Apache2ScoreRecord r : apacheMapper.selectList(qw)) {
                    out.add(r.getPatientId());
                }
            }
        } catch (Exception e) {
            // 主库不可用或表未建时，待办降级为空，不拖垮工作台主流程
            log.warn("工作台待办查询失败({})，降级为无待办", which, e);
        }
        return out;
    }
}
