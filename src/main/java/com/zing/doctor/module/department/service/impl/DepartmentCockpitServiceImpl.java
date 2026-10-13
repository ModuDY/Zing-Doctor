package com.zing.doctor.module.department.service.impl;

import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.module.apache2.mapper.Apache2ScoreRecordMapper;
import com.zing.doctor.module.department.dto.CockpitSnapshot;
import com.zing.doctor.module.department.mapper.DepartmentCockpitMapper;
import com.zing.doctor.module.department.service.DepartmentCockpitService;
import com.zing.doctor.module.sofa.mapper.SofaScoreRecordMapper;
import com.zing.doctor.quality.entity.QualityMetricResult;
import com.zing.doctor.quality.service.QualityQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 科室驾驶舱实现：今日快照 + 近 N 天流转。
 *
 * <p>设计原则（对应 docs/23）：
 * <ul>
 *   <li><b>区块级容错</b>：每个区块单独 try-catch，失败只把该块标 UNKNOWN，其它块照常返回。
 *       不让一个数据源的故障把整页拖成空白 —— 与患者摘要卡 / 神经监测的处理一致。</li>
 *   <li><b>不虚构数据</b>：床位使用率因 fact_bed 为 PENDING_SOURCE，恒返回 PENDING_SOURCE
 *       状态而不是 0；危重比例在科数为 0 时返回 null 而不是 0%。</li>
 *   <li><b>复用既有口径</b>：危重计数走 {@link IcuPatientService#countOrganSupport}（与工作台同一批查询）；
 *       出科时间、左闭右开区间、住院号去重均与出科统计 / 交班览表一致。</li>
 *   <li><b>聚合在后端</b>：SQL 一次 GROUP BY 聚合，不把患者明细传到前端再统计。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentCockpitServiceImpl implements DepartmentCockpitService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    private final DepartmentCockpitMapper cockpitMapper;
    private final IcuPatientMapper icuPatientMapper;
    private final IcuPatientService icuPatientService;
    private final SofaScoreRecordMapper sofaMapper;
    private final Apache2ScoreRecordMapper apache2Mapper;
    private final QualityQueryService qualityQueryService;

    @Override
    public CockpitSnapshot snapshot(String departCode, int days) {
        CockpitSnapshot s = new CockpitSnapshot();
        s.setDepartCode(departCode == null ? "" : departCode);
        int range = Math.max(1, Math.min(days, 90));
        LocalDate today = LocalDate.now();
        s.setDate(today.format(DAY));
        s.setDepartName(resolveDepartName(departCode));

        String todayStart = today.atTime(0, 0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String todayEnd = today.plusDays(1).atTime(0, 0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String flowStart = today.minusDays(range - 1L).atTime(0, 0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        fillTodaySnapshot(s, departCode, todayStart, todayEnd);
        fillFlow(s, departCode, flowStart, todayEnd, range, today);
        // 本月首日：质控按月周期（MONTH periodStart），与质控中台同一周期边界
        String monthStart = today.withDayOfMonth(1).atTime(0, 0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        // 评分分布与流转图使用同一趋势窗口；当前快照和本月质控仍保持各自明确的周期。
        fillScores(s, departCode, flowStart, todayEnd);
        fillQualityCards(s, departCode, monthStart);
        fillNotes(s);
        return s;
    }

    // ---------------- C/E. 评分分布 + 质控指标卡 ----------------

    /** SOFA 分桶（与临床惯例及参考系统一致）：0-4 / 5-8 / 9-12 / 13-16 / 17-20 / 21-24 */
    private static final int[][] SOFA_BUCKETS = {{0, 5}, {5, 9}, {9, 13}, {13, 17}, {17, 21}, {21, 25}};

    /** APACHE II 分桶：0-14 / 15-19 / 20-24 / 25-30（总分范围 0~30） */
    private static final int[][] APACHE2_BUCKETS = {{0, 15}, {15, 20}, {20, 25}, {25, 31}};

    private void fillScores(CockpitSnapshot s, String departCode, String scoreStart, String scoreEnd) {
        s.setSofaDist(scoreDist(departCode,
                () -> sofaMapper.selectLatestPerEpisode(departCode, scoreStart, scoreEnd),
                "total_score", SOFA_BUCKETS, "SOFA"));
        s.setApache2Dist(scoreDist(departCode,
                () -> apache2Mapper.selectLatestPerEpisode(departCode, scoreStart, scoreEnd),
                "total_score", APACHE2_BUCKETS, "APACHE II"));
    }

    private CockpitSnapshot.SnapshotBlock<List<CockpitSnapshot.ScoreBucket>> scoreDist(
            String departCode,
            java.util.function.Supplier<List<Map<String, Object>>> query,
            String scoreKey, int[][] buckets, String label) {
        try {
            List<Map<String, Object>> rows = query.get();
            int[] counts = new int[buckets.length];
            int total = 0;
            if (rows != null) {
                for (Map<String, Object> r : rows) {
                    Integer score = intOrNull(r.get(scoreKey));
                    if (score == null) {
                        continue;
                    }
                    for (int i = 0; i < buckets.length; i++) {
                        if (score >= buckets[i][0] && score < buckets[i][1]) {
                            counts[i]++;
                            total++;
                            break;
                        }
                    }
                }
            }
            if (total == 0) {
                return CockpitSnapshot.SnapshotBlock.of(null, "EMPTY", "周期内无" + label + "评分记录");
            }
            List<CockpitSnapshot.ScoreBucket> list = new ArrayList<>();
            for (int i = 0; i < buckets.length; i++) {
                String tag = buckets[i][1] > buckets[i][0] + 1
                        ? buckets[i][0] + "-" + (buckets[i][1] - 1)
                        : String.valueOf(buckets[i][0]);
                list.add(new CockpitSnapshot.ScoreBucket(tag, counts[i]));
            }
            return CockpitSnapshot.SnapshotBlock.of(list);
        } catch (Exception e) {
            log.warn("驾驶舱-{}分布查询失败 departCode={}", label, departCode, e);
            return CockpitSnapshot.SnapshotBlock.unknown(label + "分布查询失败");
        }
    }

    /**
     * 质控指标卡：直接读质控中台的计算结果（{@code quality_metric_result} 表），
     * 驾驶舱不重算 —— 这是 docs/23 §4.2 的复用原则。取不到 / 不可算时原样标状态。
     *
     * <p>集束化只展示「完成人数」绝对值：完成率的分母（感染性休克病例识别口径、
     * 无记录是否算未完成）尚未与院方确认，方案 F 节明确口径定清前不出"率"。
     */
    private void fillQualityCards(CockpitSnapshot s, String departCode, String monthStart) {
        try {
            List<CockpitSnapshot.QualityCard> cards = new ArrayList<>();
            cards.add(qualityCard("quality_30", monthStart, departCode));
            cards.add(qualityCard("quality_22", monthStart, departCode));
            cards.add(qualityCard("quality_34", monthStart, departCode));
            cards.add(qualityCard("quality_9", monthStart, departCode));
            cards.add(qualityCard("quality_419", monthStart, departCode));
            s.setQualityCards(CockpitSnapshot.SnapshotBlock.of(cards));
        } catch (Exception e) {
            log.warn("驾驶舱-质控指标查询失败 departCode={}", departCode, e);
            s.setQualityCards(CockpitSnapshot.SnapshotBlock.unknown("质控指标查询失败（引擎未启用或连接异常）"));
        }
    }

    private CockpitSnapshot.QualityCard qualityCard(String code, String monthStart, String departCode) {
        QualityMetricResult r = qualityQueryService.findResult(code, LocalDateTime.parse(monthStart), departCode);
        if (r == null) {
            return CockpitSnapshot.QualityCard.pending(code, code, "本月暂无质控计算结果（引擎未跑或未配置）");
        }
        CockpitSnapshot.QualityCard c = new CockpitSnapshot.QualityCard();
        c.setCode(code);
        c.setName(r.getMetricName() != null ? r.getMetricName() : code);
        c.setUnit(r.getUnit());
        c.setCalcStatus(r.getCalcStatus() != null ? r.getCalcStatus() : "NO_DATA");
        c.setNote(r.getErrorMsg());
        if (r.getMetricValue() != null && "OK".equals(c.getCalcStatus())) {
            c.setValue(r.getMetricValue().doubleValue());
        }
        c.setNumerator(r.getNumerator() == null ? null : r.getNumerator().doubleValue());
        c.setDenominator(r.getDenominator() == null ? null : r.getDenominator().doubleValue());
        return c;
    }

    // ---------------- B. 今日快照 ----------------

    private void fillTodaySnapshot(CockpitSnapshot s, String departCode, String todayStart, String todayEnd) {
        // 在科 / 今日入科 / 今日出科：一条 SQL 三个子查询
        try {
            Map<String, Object> row = cockpitMapper.selectTodaySnapshot(departCode, todayStart, todayEnd);
            int inCount = intOf(row.get("in_count"));
            int todayIn = intOf(row.get("today_in_count"));
            int todayOut = intOf(row.get("today_out_count"));
            s.setInCount(CockpitSnapshot.SnapshotBlock.of(inCount));
            s.setTodayInCount(CockpitSnapshot.SnapshotBlock.of(todayIn));
            s.setTodayOutCount(CockpitSnapshot.SnapshotBlock.of(todayOut));
        } catch (Exception e) {
            log.warn("驾驶舱-今日快照查询失败 departCode={}", departCode, e);
            s.setInCount(CockpitSnapshot.SnapshotBlock.unknown("在科人数查询失败"));
            s.setTodayInCount(CockpitSnapshot.SnapshotBlock.unknown("今日入科查询失败"));
            s.setTodayOutCount(CockpitSnapshot.SnapshotBlock.unknown("今日出科查询失败"));
        }

        // 器官支持（危重窄口径）：复用工作台同一批查询
        try {
            Map<String, Integer> support = icuPatientService.countOrganSupport(departCode);
            int vent = support.getOrDefault("ventilated", 0);
            int vaso = support.getOrDefault("vasopressor", 0);
            int crrt = support.getOrDefault("crrt", 0);
            int ecmo = support.getOrDefault("ecmo", 0);
            s.setVentilatedCount(CockpitSnapshot.SnapshotBlock.of(vent));
            s.setVasopressorCount(CockpitSnapshot.SnapshotBlock.of(vaso));
            s.setCrrtCount(CockpitSnapshot.SnapshotBlock.of(crrt));
            s.setEcmoCount(CockpitSnapshot.SnapshotBlock.of(ecmo));

            // 危重 = 四类器官支持任一（窄口径，与工作台「危重」一致）；
            // 四类可能重叠（同一患者上 ECMO 又用 CRRT），按患者取并集而不是相加。
            int critical = unionCritical(departCode);
            s.setCriticalCount(CockpitSnapshot.SnapshotBlock.of(critical));

            Integer inCount = s.getInCount() != null ? s.getInCount().getValue() : null;
            if (inCount != null && inCount > 0) {
                double ratio = BigDecimal.valueOf(critical)
                        .divide(BigDecimal.valueOf(inCount), 3, RoundingMode.HALF_UP)
                        .doubleValue();
                s.setCriticalRatio(CockpitSnapshot.SnapshotBlock.of(ratio));
            } else {
                // 在科数为 0（或没查到）时不造 0%，否则「0% 危重」会被误读成"很安全"
                s.setCriticalRatio(CockpitSnapshot.SnapshotBlock.unknown("当前无在科患者，比例不适用"));
            }
        } catch (Exception e) {
            log.warn("驾驶舱-器官支持统计失败 departCode={}", departCode, e);
            s.setCriticalCount(CockpitSnapshot.SnapshotBlock.unknown("危重统计失败"));
            s.setCriticalRatio(CockpitSnapshot.SnapshotBlock.unknown("危重统计失败"));
            s.setVentilatedCount(CockpitSnapshot.SnapshotBlock.unknown("机械通气统计失败"));
            s.setVasopressorCount(CockpitSnapshot.SnapshotBlock.unknown("血管活性药统计失败"));
            s.setCrrtCount(CockpitSnapshot.SnapshotBlock.unknown("CRRT 统计失败"));
            s.setEcmoCount(CockpitSnapshot.SnapshotBlock.unknown("ECMO 统计失败"));
        }

        // 床位使用率：质控 fact_bed = PENDING_SOURCE，没有可靠分母前只报状态
        s.setBedUsage(CockpitSnapshot.SnapshotBlock.pending(
                "待接床位数据源：需 ICU 床位配置（开放/暂停/临时床位）后方可计算，暂不显示比例"));
    }

    /**
     * 危重患者并集：四类器官支持各自按 patient_id 去重后取并集。
     *
     * <p>单独算而不复用 {@code countOrganSupport} 的计数值，是因为那里每类各自计数，
     * 同一患者同时上 ECMO + CRRT 会被算成 2 个危重患者 —— 卡片上会虚高。
     */
    private int unionCritical(String departCode) {
        java.util.Set<String> all = new java.util.HashSet<>();
        String dept = departCode == null ? "" : departCode;
        try {
            List<Map<String, Object>> vaso = icuPatientMapper.selectHandoverVasopressor(dept);
            if (vaso != null) {
                for (Map<String, Object> r : vaso) {
                    String pid = String.valueOf(r.get("patient_id"));
                    if (pid != null && !"null".equals(pid) && !pid.isEmpty()) all.add(pid);
                }
            }
        } catch (Exception e) {
            log.warn("驾驶舱-危重并集(血管活性药)失败 dept={}", dept, e);
        }
        try {
            List<String> crrt = icuPatientMapper.selectHandoverCrrtPatients(dept);
            if (crrt != null) all.addAll(crrt);
        } catch (Exception e) {
            log.warn("驾驶舱-危重并集(CRRT)失败 dept={}", dept, e);
        }
        try {
            List<String> ecmo = icuPatientMapper.selectHandoverEcmoPatients(dept);
            if (ecmo != null) all.addAll(ecmo);
        } catch (Exception e) {
            log.warn("驾驶舱-危重并集(ECMO)失败 dept={}", dept, e);
        }
        try {
            List<Map<String, Object>> inpatients = icuPatientMapper.selectInpatients(dept);
            List<String> pids = new ArrayList<>();
            if (inpatients != null) {
                for (Map<String, Object> r : inpatients) {
                    String pid = String.valueOf(r.get("patient_id"));
                    if (pid != null && !"null".equals(pid) && !pid.isEmpty()) pids.add(pid);
                }
            }
            if (!pids.isEmpty()) {
                List<Map<String, Object>> vent = icuPatientMapper.selectLatestVentilatorParams(pids);
                if (vent != null) {
                    for (Map<String, Object> r : vent) {
                        String pid = String.valueOf(r.get("patient_id"));
                        if (pid != null && !"null".equals(pid) && !pid.isEmpty()) all.add(pid);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("驾驶舱-危重并集(呼吸机)失败 dept={}", dept, e);
        }
        return all.size();
    }

    // ---------------- D. 患者流转 ----------------

    private void fillFlow(CockpitSnapshot s, String departCode, String flowStart, String flowEnd,
                          int range, LocalDate today) {
        try {
            List<Map<String, Object>> inRows = cockpitMapper.selectDailyInCount(departCode, flowStart, flowEnd);
            List<Map<String, Object>> outRows = cockpitMapper.selectDailyOutCount(departCode, flowStart, flowEnd);

            // 日期 → 计数；SQL 只返回有数据的日期，缺失日期补 0，保证曲线连续
            Map<String, int[]> byDay = new TreeMap<>();
            for (int i = 0; i < range; i++) {
                byDay.put(today.minusDays(range - 1L - i).format(DAY), new int[]{0, 0});
            }
            for (Map<String, Object> r : inRows) {
                int[] c = byDay.computeIfAbsent(dayOf(r.get("day")), k -> new int[]{0, 0});
                c[0] = intOf(r.get("cnt"));
            }
            for (Map<String, Object> r : outRows) {
                int[] c = byDay.computeIfAbsent(dayOf(r.get("day")), k -> new int[]{0, 0});
                c[1] = intOf(r.get("cnt"));
            }
            List<CockpitSnapshot.DayFlow> flow = new ArrayList<>();
            for (Map.Entry<String, int[]> e : byDay.entrySet()) {
                CockpitSnapshot.DayFlow d = new CockpitSnapshot.DayFlow();
                d.setDay(e.getKey());
                d.setInCount(e.getValue()[0]);
                d.setOutCount(e.getValue()[1]);
                flow.add(d);
            }
            s.setDailyFlow(CockpitSnapshot.SnapshotBlock.of(flow));
        } catch (Exception e) {
            log.warn("驾驶舱-流转趋势查询失败 departCode={}", departCode, e);
            s.setDailyFlow(CockpitSnapshot.SnapshotBlock.unknown("患者流转查询失败"));
        }

        // 平均 ICU 住院日：只统计已出科患者（在科者的住院日还在增长，混入会低估）
        try {
            Map<String, Object> row = cockpitMapper.selectAvgStayDays(departCode, flowStart, flowEnd);
            Object avg = row.get("avg_stay_days");
            int count = intOf(row.get("patient_count"));
            s.setAvgStayPatientCount(count);
            if (avg == null || count == 0) {
                s.setAvgStayDays(CockpitSnapshot.SnapshotBlock.of(null, "EMPTY", "区间内无已出科患者"));
            } else {
                // 达梦 AVG 经 JDBC 可能是 BigDecimal 也可能是 Double，统一转 BigDecimal 再取 1 位，
                // 否则前端会显示 7.37037037037037 这种尾巴
                BigDecimal bd = avg instanceof BigDecimal
                        ? (BigDecimal) avg
                        : BigDecimal.valueOf(Double.parseDouble(String.valueOf(avg)));
                s.setAvgStayDays(CockpitSnapshot.SnapshotBlock.of(
                        bd.setScale(1, RoundingMode.HALF_UP).doubleValue()));
            }
        } catch (Exception e) {
            log.warn("驾驶舱-平均住院日查询失败 departCode={}", departCode, e);
            s.setAvgStayDays(CockpitSnapshot.SnapshotBlock.unknown("平均住院日查询失败"));
            s.setAvgStayPatientCount(null);
        }
    }

    private void fillNotes(CockpitSnapshot s) {
        s.getNotes().add("在科患者：is_in_depart=1，按住院号去重，与患者工作台同口径");
        s.getNotes().add("危重：机械通气 / 血管活性药 / CRRT / ECMO 任一（窄口径，取并集不去重相加）");
        s.getNotes().add("入科 / 出科：按入出科时间自然日聚合，左闭右开区间，与出科统计一致");
        s.getNotes().add("平均 ICU 住院日：仅统计区间内已出科患者，在科者住院日仍在增长不计入");
        s.getNotes().add("床位使用率：待接床位数据源，暂不计算");
        s.getNotes().add("评分分布：统计单位是「患者」（每患者取周期内最新一条有效评分），非评分次数");
        s.getNotes().add("APACHE II 预测病死率单独标注为预测值，不与实际病死率（quality_30）混用");
        s.getNotes().add("集束化展示完成人数绝对值：完成率分母口径（病例识别/无记录规则）待院方确认后开放");
    }

    // ---------------- 工具 ----------------

    private String resolveDepartName(String departCode) {
        if (departCode == null || departCode.isEmpty()) {
            return "全院";
        }
        try {
            // 复用科室下拉的字典（sys_depart，status=1 且未删），在内存里匹配 org_code。
            // 科室数量是几十行量级，不必为单次查询新增 SQL。
            List<Map<String, Object>> departs = icuPatientMapper.selectAllDepartments();
            if (departs != null) {
                for (Map<String, Object> d : departs) {
                    if (departCode.equals(String.valueOf(d.get("org_code")))) {
                        Object name = d.get("depart_name");
                        return name == null || String.valueOf(name).isEmpty() ? departCode : String.valueOf(name);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("驾驶舱-科室名称查询失败 departCode={}", departCode, e);
        }
        return departCode;
    }

    private static String dayOf(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime().toLocalDate().format(DAY);
        }
        if (value instanceof LocalDate) {
            return ((LocalDate) value).format(DAY);
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).toLocalDate().format(DAY);
        }
        return String.valueOf(value).substring(0, Math.min(10, String.valueOf(value).length()));
    }

    private static int intOf(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return new BigDecimal(String.valueOf(value)).intValue();
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Integer intOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return new BigDecimal(String.valueOf(value)).intValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
