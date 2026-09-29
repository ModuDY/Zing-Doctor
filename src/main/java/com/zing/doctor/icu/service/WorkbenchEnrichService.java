package com.zing.doctor.icu.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.icu.support.InfectionRules;
import com.zing.doctor.module.apache2.entity.Apache2ScoreRecord;
import com.zing.doctor.module.apache2.mapper.Apache2ScoreRecordMapper;
import com.zing.doctor.module.antibiotic.entity.AntibioticReassessment;
import com.zing.doctor.module.antibiotic.mapper.AntibioticReassessmentMapper;
import com.zing.doctor.module.sofa.entity.SofaScoreRecord;
import com.zing.doctor.module.sofa.mapper.SofaScoreRecordMapper;
import com.zing.doctor.module.system.service.SysParamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
 * <p>评分待办口径由参数 {@code WORKBENCH_SCORE_TODO_RULE} 决定，两种口径对应两种排班习惯：
 * <ul>
 *   <li>{@code TODAY_NO_SCORE}（默认）—— 当天没有评分记录即计入（含从未评分）。
 *       适合每天晨间把全科过一遍的科室。</li>
 *   <li>{@code ADMIT_24H_NEVER} —— 入科满 24 小时且从未评分才计入。
 *       适合「新入科先观察、在科一天以上必须评估」的科室。</li>
 * </ul>
 * 两种口径的共同前置：<b>入科满 24 小时</b> —— 当天入科的患者一律不催。
 * 按小时判断而不是用「在科天数 ≥1」：后者对当天入科的患者也等于 1，条件恒真，
 * 等于没有前置（本项目踩过：23:00 入科的患者一小时后就被挂上待办）。
 *
 * <p>抗生素无血培养 / 导管超期等需要回 ICU 库逐患者比对，放第二期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkbenchEnrichService {

    private final SofaScoreRecordMapper sofaMapper;
    private final Apache2ScoreRecordMapper apacheMapper;
    private final IcuPatientMapper icuPatientMapper;
    private final AntibioticReassessmentMapper reassessmentMapper;
    private final SysParamService sysParamService;

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
        // 1b) 最近一次 APACHE II 总分（同上，患者诊疗摘要用）
        Map<String, Apache2ScoreRecord> latestApache = latestApacheByPatient(patientIds);
        // 2) 今天有评分的 patientId 集合
        Set<String> sofaToday = scoredToday("sofa", patientIds);
        Set<String> apacheToday = scoredToday("apache", patientIds);
        // 3) 「从未评过分」的集合只有口径二用得上：按需查，省掉一半查询
        String rule = scoreTodoRule();
        boolean admit24hRule = SysParamService.WORKBENCH_TODO_ADMIT_24H.equals(rule);
        Set<String> sofaEver = admit24hRule ? scoredEver("sofa", patientIds) : Collections.emptySet();
        Set<String> apacheEver = admit24hRule ? scoredEver("apache", patientIds) : Collections.emptySet();

        for (WorkbenchPatient p : patients) {
            SofaScoreRecord s = latestSofa.get(p.getPatientId());
            if (s != null) {
                p.setLastSofaScore(s.getTotalScore());
            }
            Apache2ScoreRecord a = latestApache.get(p.getPatientId());
            if (a != null) {
                p.setLastApacheScore(a.getTotalScore());
            }

            List<String> todos = new ArrayList<>(3);
            // 共同前置：入科满 24 小时 —— 当天入科的患者不催，留出评估时间窗。
            // 刻意不用 icuDays >= 1：它被钳到最小值 1，条件恒真，等于没有前置。
            if (over24Hours(p.getInDepartmentTime())) {
                String pid = p.getPatientId();
                // 口径一：当日无评分（含从未评分）；口径二：从未评分
                boolean sofaDone = admit24hRule ? sofaEver.contains(pid) : sofaToday.contains(pid);
                if (!sofaDone) {
                    todos.add("SOFA_NOT_TODAY");
                }
                boolean apacheDone = admit24hRule ? apacheEver.contains(pid) : apacheToday.contains(pid);
                if (!apacheDone) {
                    todos.add("APACHE_NOT_TODAY");
                }
            }
            p.setTodos(todos);
            p.setTodoCount(todos.size());
        }
    }

    /**
     * 评分待办口径。取值见 {@code WORKBENCH_SCORE_TODO_RULE}；
     * 未配置 / 已停用 / 留空时回退 {@code TODAY_NO_SCORE}（与参数上的 default_value 一致）。
     */
    private String scoreTodoRule() {
        String raw = sysParamService.value(SysParamService.KEY_WORKBENCH_SCORE_TODO_RULE);
        if (StrUtil.isBlank(raw)) {
            return SysParamService.WORKBENCH_TODO_TODAY;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 入科是否已满 24 小时。
     *
     * <p>按小时算而不是用「在科天数 ≥1」：后者把当天入科也算 1，条件恒成立，
     * 结果 23:00 入科的患者一小时后就被挂上待办。
     *
     * <p>入科时间缺失时按「不满 24 小时」处理 —— 宁可少催一次，
     * 也不要在数据不全的情况下凭猜测催医生。
     */
    private boolean over24Hours(LocalDateTime inDepartmentTime) {
        if (inDepartmentTime == null) {
            return false;
        }
        return ChronoUnit.HOURS.between(inDepartmentTime, LocalDateTime.now()) >= 24;
    }

    /**
     * 批量回填抗感染复评待办。只查一次本系统复评表，避免工作台逐患者 N+1。
     * 查询失败时标记 UNKNOWN，不能把「查不到」伪装成「没有待复评」。
     */
    public void enrichReassessmentTodos(List<WorkbenchPatient> patients) {
        if (patients == null || patients.isEmpty()) {
            return;
        }
        List<String> patientIds = patients.stream()
                .map(WorkbenchPatient::getPatientId)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        if (patientIds.isEmpty()) {
            return;
        }
        try {
            List<AntibioticReassessment> pending = reassessmentMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AntibioticReassessment>()
                            .in(AntibioticReassessment::getPatientId, patientIds)
                            .eq(AntibioticReassessment::getReviewStatus, "PENDING")
                            .eq(AntibioticReassessment::getVoidFlag, 0)
                            .orderByAsc(AntibioticReassessment::getReviewDueTime));
            Map<String, List<AntibioticReassessment>> byPatient = new HashMap<>();
            for (AntibioticReassessment task : pending) {
                byPatient.computeIfAbsent(task.getPatientId(), k -> new ArrayList<>()).add(task);
            }
            for (WorkbenchPatient patient : patients) {
                List<AntibioticReassessment> tasks = byPatient.getOrDefault(
                        patient.getPatientId(), Collections.emptyList());
                patient.setReassessmentCount(tasks.size());
                patient.setReassessmentDueTime(tasks.isEmpty() ? null : tasks.get(0).getReviewDueTime());
                patient.setReassessmentDataStatus("FOUND");
                if (!tasks.isEmpty()) {
                    List<String> todos = patient.getTodos() == null
                            ? new ArrayList<>() : new ArrayList<>(patient.getTodos());
                    if (!todos.contains("ABX_REASSESSMENT_PENDING")) {
                        todos.add("ABX_REASSESSMENT_PENDING");
                    }
                    patient.setTodos(todos);
                    patient.setTodoCount(todos.size());
                }
            }
        } catch (Exception e) {
            log.warn("工作台抗感染复评待办查询失败，标记为数据不可用", e);
            for (WorkbenchPatient patient : patients) {
                patient.setReassessmentDataStatus("UNKNOWN");
            }
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

    /** 每个 patientId 取 score_time 最大的一条 APACHE II 记录（不返回 pdf_data 大字段）。 */
    private Map<String, Apache2ScoreRecord> latestApacheByPatient(List<String> patientIds) {
        QueryWrapper<Apache2ScoreRecord> qw = new QueryWrapper<>();
        qw.select("patient_id", "total_score", "score_time")
                .in("patient_id", patientIds)
                .eq("status", 1)
                .orderByDesc("score_time");
        List<Apache2ScoreRecord> list = apacheMapper.selectList(qw);
        Map<String, Apache2ScoreRecord> out = new HashMap<>();
        for (Apache2ScoreRecord r : list) {
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
            // 主库不可用或表未建时降级为「不生成评分待办」：返回全集等价于「都当已评过」。
            // 注意不能返回空集 —— 空集会被上层理解成「谁都没评过」，于是一次数据库抖动
            // 就会凭空生成一整屏假待办。宁可这次不催，也不要制造噪音。
            log.warn("工作台待办查询失败({})，本次不生成评分待办", which, e);
            out.addAll(patientIds);
        }
        return out;
    }

    /**
     * 有<b>任何</b>评分记录（不限时间）的 patientId 集合 —— 口径二 {@code ADMIT_24H_NEVER} 用。
     *
     * <p>与 {@link #scoredToday} 的区别是「今天」与「全部历史」。这对 APACHE II 意义完全不同：
     * 它在本系统是「入科满 24h 自动初评、一人一条」，若按天去问「今天评了吗」，
     * 一个入科时就评过的患者会被天天挂上待办。
     */
    private Set<String> scoredEver(String which, List<String> patientIds) {
        Set<String> out = new HashSet<>();
        try {
            if ("sofa".equals(which)) {
                QueryWrapper<SofaScoreRecord> qw = new QueryWrapper<>();
                qw.select("patient_id")
                        .in("patient_id", patientIds)
                        .eq("status", 1);
                for (SofaScoreRecord r : sofaMapper.selectList(qw)) {
                    out.add(r.getPatientId());
                }
            } else {
                QueryWrapper<Apache2ScoreRecord> qw = new QueryWrapper<>();
                qw.select("patient_id")
                        .in("patient_id", patientIds)
                        .eq("status", 1);
                for (Apache2ScoreRecord r : apacheMapper.selectList(qw)) {
                    out.add(r.getPatientId());
                }
            }
        } catch (Exception e) {
            // 同 scoredToday：查不到时按「都评过」处理，不生成假待办
            log.warn("工作台「是否存在评分记录」查询失败({})，本次不生成评分待办", which, e);
            out.addAll(patientIds);
        }
        return out;
    }
}
