package com.zing.doctor.module.antibiotic.service.impl;

import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.module.antibiotic.dto.*;
import com.zing.doctor.module.antibiotic.entity.DddConfig;
import com.zing.doctor.module.antibiotic.service.DddConfigService;
import com.zing.doctor.module.antibiotic.service.DddStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 第三维度：抗菌药物使用强度（DDD）统计 Service 实现。
 *
 * <p>核心计算：
 * <ul>
 *   <li>从 patient_advice 表查询抗菌药物医嘱</li>
 *   <li>正则解析医嘱名称中的单次剂量</li>
 *   <li>计算每条医嘱 DDDs = 单次剂量 × 每日次数 × 使用天数 / DDD值</li>
 *   <li>使用率 = 使用抗菌药物患者数 / 总患者数 × 100%</li>
 *   <li>使用强度 = 总 DDDs / 总床日数 × 100</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DddStatsServiceImpl implements DddStatsService {

    private final IcuPatientMapper icuPatientMapper;
    private final DddConfigService dddConfigService;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    // 剂量解析正则：匹配 "数字g"、"数字mg"、"数字g/瓶 数字g" 等
    private static final Pattern DOSE_PATTERN = Pattern.compile("(\\d+\\.?\\d*)\\s*(g|mg|MU|万单位)", Pattern.CASE_INSENSITIVE);

    // 溶媒关键词（排除）
    private static final List<String> SOLVENT_KEYWORDS = Arrays.asList(
            "氯化钠", "葡萄糖", "乳酸钠林格", "灭菌注射用水", "木糖醇",
            "转化糖", "果糖", "复方氯化钠", "甘油果糖", "生理盐水"
    );

    // 频次转换（每日次数）
    private static final Map<String, Double> FREQ_MAP = new HashMap<>();
    static {
        FREQ_MAP.put("qd", 1.0);
        FREQ_MAP.put("bid", 2.0);
        FREQ_MAP.put("tid", 3.0);
        FREQ_MAP.put("qid", 4.0);
        FREQ_MAP.put("q4h", 6.0);
        FREQ_MAP.put("q6h", 4.0);
        FREQ_MAP.put("q8h", 3.0);
        FREQ_MAP.put("q12h", 2.0);
        FREQ_MAP.put("q24h", 1.0);
        FREQ_MAP.put("q48h", 0.5);
        FREQ_MAP.put("q72h", 0.33);
        FREQ_MAP.put("st", 1.0);
        FREQ_MAP.put("prn", 1.0);
        FREQ_MAP.put("必要时", 1.0);
        FREQ_MAP.put("立即", 1.0);
    }

    @Override
    public List<Map<String, Object>> getAllDepartments() {
        return icuPatientMapper.selectAllDepartments();
    }

    @Override
    public DddOverviewView getOverview(String startTime, String endTime, String departCode) {
        DddOverviewView view = new DddOverviewView();
        view.setStartTime(startTime);
        view.setEndTime(endTime);

        // 1. 查询患者列表，计算总床日数和总患者数
        List<Map<String, Object>> patients = icuPatientMapper.selectPatientsForStats(startTime, endTime, departCode);
        BigDecimal totalBedDays = calcTotalBedDays(patients, startTime, endTime);
        int totalPatientCount = patients.size();

        view.setTotalBedDays(totalBedDays);
        view.setTotalPatientCount(totalPatientCount);

        // 2. 查询抗菌药物医嘱
        List<Map<String, Object>> advices = icuPatientMapper.selectAbxAdviceForStats(startTime, endTime, departCode);

        // 3. 计算每条医嘱的 DDDs
        List<AdviceDddCalc> calcList = calcAdviceDdds(advices, startTime, endTime, departCode);

        // 4. 汇总统计
        BigDecimal totalDdds = calcList.stream()
                .map(AdviceDddCalc::getDdds)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal specialDdds = calcList.stream()
                .filter(a -> "特殊".equals(a.getManageLevel()))
                .map(AdviceDddCalc::getDdds)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Set<String> usedPatientIds = calcList.stream()
                .map(AdviceDddCalc::getInHospitalNo)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> drugKinds = calcList.stream()
                .map(AdviceDddCalc::getDrugName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        view.setTotalDdds(totalDdds);
        view.setSpecialDdds(specialDdds);
        view.setUsedPatientCount(usedPatientIds.size());
        view.setDrugKindCount(drugKinds.size());

        // 5. 计算使用率和使用强度
        if (totalPatientCount > 0) {
            BigDecimal usageRate = new BigDecimal(usedPatientIds.size())
                    .multiply(new BigDecimal("100"))
                    .divide(new BigDecimal(totalPatientCount), 2, RoundingMode.HALF_UP);
            view.setUsageRate(usageRate);
            view.setUsageRate达标(usageRate.compareTo(new BigDecimal("60")) <= 0);
        }

        if (totalBedDays.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal useDensity = totalDdds
                    .multiply(new BigDecimal("100"))
                    .divide(totalBedDays, 2, RoundingMode.HALF_UP);
            view.setUseDensity(useDensity);
            view.setUseDensity达标(useDensity.compareTo(new BigDecimal("40")) <= 0);

            if (totalDdds.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal specialUseDensity = specialDdds
                        .multiply(new BigDecimal("100"))
                        .divide(totalBedDays, 2, RoundingMode.HALF_UP);
                view.setSpecialUseDensity(specialUseDensity);

                BigDecimal specialRatio = specialDdds
                        .multiply(new BigDecimal("100"))
                        .divide(totalDdds, 2, RoundingMode.HALF_UP);
                view.setSpecialRatio(specialRatio);
            }
        }

        // 6. 药品分类占比
        view.setClassRatios(calcClassRatios(calcList, totalDdds));

        // 7. 管理级别占比
        view.setLevelRatios(calcLevelRatios(calcList, totalDdds));

        // 8. 月度趋势（近12个月）
        view.setMonthlyTrend(getMonthlyTrend(startTime, endTime, departCode));

        return view;
    }

    @Override
    public List<DddDrugRank> getDrugRankTop20(String startTime, String endTime, String departCode) {
        List<Map<String, Object>> advices = icuPatientMapper.selectAbxAdviceForStats(startTime, endTime, departCode);
        List<AdviceDddCalc> calcList = calcAdviceDdds(advices, startTime, endTime, departCode);

        // 按药品名称分组汇总
        Map<String, List<AdviceDddCalc>> grouped = calcList.stream()
                .collect(Collectors.groupingBy(AdviceDddCalc::getDrugName));

        List<DddDrugRank> ranks = new ArrayList<>();
        BigDecimal totalDdds = calcList.stream()
                .map(AdviceDddCalc::getDdds)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (Map.Entry<String, List<AdviceDddCalc>> entry : grouped.entrySet()) {
            List<AdviceDddCalc> list = entry.getValue();
            if (list.isEmpty()) continue;

            DddDrugRank rank = new DddDrugRank();
            rank.setDrugName(entry.getKey());
            rank.setDrugClass(list.get(0).getDrugClass());
            rank.setManageLevel(list.get(0).getManageLevel());
            rank.setDddValue(list.get(0).getDddValue());
            rank.setDddUnit(list.get(0).getDddUnit());

            BigDecimal drugTotalDose = list.stream()
                    .map(AdviceDddCalc::getTotalDose)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal drugTotalDdds = list.stream()
                    .map(AdviceDddCalc::getDdds)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            rank.setTotalDose(drugTotalDose);
            rank.setTotalDdds(drugTotalDdds);
            if (totalDdds.compareTo(BigDecimal.ZERO) > 0) {
                rank.setRatio(drugTotalDdds.multiply(new BigDecimal("100"))
                        .divide(totalDdds, 2, RoundingMode.HALF_UP));
            }
            rank.setPatientCount((int) list.stream().map(AdviceDddCalc::getInHospitalNo).distinct().count());
            rank.setAdviceCount(list.size());

            ranks.add(rank);
        }

        // 按 DDDs 降序排序，取 TOP20
        ranks.sort((a, b) -> b.getTotalDdds().compareTo(a.getTotalDdds()));
        for (int i = 0; i < ranks.size() && i < 20; i++) {
            ranks.get(i).setRank(i + 1);
        }
        return ranks.size() > 20 ? ranks.subList(0, 20) : ranks;
    }

    @Override
    public List<DddOverviewView.DddTrendPoint> getMonthlyTrend(String startTime, String endTime, String departCode) {
        List<DddOverviewView.DddTrendPoint> trend = new ArrayList<>();

        try {
            LocalDateTime start = LocalDateTime.parse(startTime, DT_FMT);
            LocalDateTime end = LocalDateTime.parse(endTime, DT_FMT);

            // 按月遍历
            LocalDate monthStart = start.toLocalDate().withDayOfMonth(1);
            LocalDate monthEnd = end.toLocalDate().withDayOfMonth(1);

            while (monthStart.isBefore(monthEnd)) {
                LocalDateTime ms = monthStart.atStartOfDay();
                LocalDateTime me = monthStart.plusMonths(1).atStartOfDay();
                if (me.isAfter(end)) me = end;

                String monthStr = monthStart.format(MONTH_FMT);

                // 查询该月的患者和医嘱
                List<Map<String, Object>> patients = icuPatientMapper.selectPatientsForStats(
                        ms.format(DT_FMT), me.format(DT_FMT), departCode);
                BigDecimal bedDays = calcTotalBedDays(patients, ms.format(DT_FMT), me.format(DT_FMT));

                List<Map<String, Object>> advices = icuPatientMapper.selectAbxAdviceForStats(
                        ms.format(DT_FMT), me.format(DT_FMT), departCode);
                List<AdviceDddCalc> calcList = calcAdviceDdds(advices, ms.format(DT_FMT), me.format(DT_FMT), departCode);

                BigDecimal monthDdds = calcList.stream()
                        .map(AdviceDddCalc::getDdds)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                int usedCount = (int) calcList.stream().map(AdviceDddCalc::getInHospitalNo).distinct().count();

                DddOverviewView.DddTrendPoint point = new DddOverviewView.DddTrendPoint();
                point.setMonth(monthStr);
                point.setTotalDdds(monthDdds);

                if (patients.size() > 0) {
                    point.setUsageRate(new BigDecimal(usedCount).multiply(new BigDecimal("100"))
                            .divide(new BigDecimal(patients.size()), 2, RoundingMode.HALF_UP));
                }
                if (bedDays.compareTo(BigDecimal.ZERO) > 0) {
                    point.setUseDensity(monthDdds.multiply(new BigDecimal("100"))
                            .divide(bedDays, 2, RoundingMode.HALF_UP));
                }

                trend.add(point);
                monthStart = monthStart.plusMonths(1);
            }
        } catch (Exception e) {
            log.warn("计算月度趋势失败: {}", e.getMessage());
        }

        return trend;
    }

    @Override
    public List<DddPatientDetail> getPatientDetails(String startTime, String endTime, String departCode) {
        List<Map<String, Object>> advices = icuPatientMapper.selectAbxAdviceForStats(startTime, endTime, departCode);
        List<AdviceDddCalc> calcList = calcAdviceDdds(advices, startTime, endTime, departCode);

        // 查询患者信息
        List<Map<String, Object>> patients = icuPatientMapper.selectPatientsForStats(startTime, endTime, departCode);
        Map<String, Map<String, Object>> patientMap = patients.stream()
                .collect(Collectors.toMap(
                        p -> str(p.get("in_hospital_no")),
                        p -> p,
                        (a, b) -> a
                ));

        // 按患者分组
        Map<String, List<AdviceDddCalc>> grouped = calcList.stream()
                .filter(a -> a.getInHospitalNo() != null)
                .collect(Collectors.groupingBy(AdviceDddCalc::getInHospitalNo));

        List<DddPatientDetail> details = new ArrayList<>();
        for (Map.Entry<String, List<AdviceDddCalc>> entry : grouped.entrySet()) {
            String inHospitalNo = entry.getKey();
            List<AdviceDddCalc> list = entry.getValue();

            DddPatientDetail detail = new DddPatientDetail();
            detail.setInHospitalNo(inHospitalNo);

            Map<String, Object> p = patientMap.get(inHospitalNo);
            if (p == null) {
                // 患者信息不存在（已出科且出科时间早于统计周期开始，或数据同步问题），跳过
                continue;
            }
            detail.setName(str(p.get("name")));
            detail.setGender(str(p.get("gender")));
            detail.setAge(str(p.get("age")));
            detail.setDepartment(str(p.get("department")));
            detail.setBedNo(str(p.get("bed_no")));
            detail.setInDepartTime(str(p.get("in_depart_time")));
            detail.setOutDepartTime(str(p.get("out_depart_time")));
            detail.setInDepart("1".equals(str(p.get("is_in_depart"))));
            detail.setStayDays(calcStayDaysForDisplay(p, startTime, endTime));

            detail.setDrugKindCount((int) list.stream().map(AdviceDddCalc::getDrugName).distinct().count());
            detail.setTotalDdds(list.stream().map(AdviceDddCalc::getDdds)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));

            // 主要用药（按 DDDs 降序取前3）
            List<String> mainDrugs = list.stream()
                    .sorted((a, b) -> b.getDdds().compareTo(a.getDdds()))
                    .map(AdviceDddCalc::getDrugName)
                    .distinct()
                    .limit(3)
                    .collect(Collectors.toList());
            detail.setMainDrugs(String.join("、", mainDrugs));

            // 药品使用明细
            List<DddPatientDetail.DrugUsageItem> usages = new ArrayList<>();
            for (AdviceDddCalc a : list) {
                DddPatientDetail.DrugUsageItem item = new DddPatientDetail.DrugUsageItem();
                item.setDrugName(a.getDrugName());
                item.setDrugClass(a.getDrugClass());
                item.setManageLevel(a.getManageLevel());
                item.setFreq(a.getFreq());
                item.setMethod(a.getMethod());
                item.setStartTime(a.getStartTime());
                item.setEndTime(a.getEndTime());
                item.setUseDays(a.getUseDays());
                item.setSingleDose(a.getSingleDose());
                item.setTotalDose(a.getTotalDose());
                item.setDddValue(a.getDddValue());
                item.setDdds(a.getDdds());
                item.setOpenStaffName(a.getOpenStaffName());
                usages.add(item);
            }
            usages.sort((a, b) -> b.getDdds().compareTo(a.getDdds()));
            detail.setDrugUsages(usages);

            details.add(detail);
        }

        // 按总 DDDs 降序
        details.sort((a, b) -> b.getTotalDdds().compareTo(a.getTotalDdds()));
        return details;
    }

    @Override
    public DddPatientDetail getPatientDetail(String inHospitalNo, String startTime, String endTime) {
        List<DddPatientDetail> all = getPatientDetails(startTime, endTime, "");
        return all.stream()
                .filter(d -> inHospitalNo.equals(d.getInHospitalNo()))
                .findFirst()
                .orElse(null);
    }

    // ==================================================================
    // 核心计算方法
    // ==================================================================

    /**
     * 计算每条医嘱的 DDDs。
     */
    private List<AdviceDddCalc> calcAdviceDdds(List<Map<String, Object>> advices, String startTime, String endTime,
                                                   String departCode) {
        List<AdviceDddCalc> result = new ArrayList<>();
        List<DddConfig> dddConfigs = dddConfigService.listAllActive();

        // 查询患者信息（用于限制使用时长在在科时间范围内）
        List<Map<String, Object>> patients = icuPatientMapper.selectPatientsForStats(startTime, endTime, departCode);
        Map<String, Map<String, Object>> patientMap = patients.stream()
                .collect(Collectors.toMap(
                        p -> str(p.get("in_hospital_no")),
                        p -> p,
                        (a, b) -> a
                ));

        for (Map<String, Object> advice : advices) {
            String name = str(advice.get("name"));
            if (name == null || name.isEmpty()) continue;

            // 排除溶媒
            if (isSolvent(name)) continue;

            // 匹配 DDD 知识库
            DddConfig config = matchDddConfig(name, dddConfigs);
            if (config == null) continue;

            // 解析单次剂量（优先从 drug_one_dosage 字段取，兼容从 name 正则解析）
            BigDecimal singleDose = parseDoseFromFields(advice, config.getDddUnit());
            if (singleDose == null) {
                singleDose = parseDose(name, config.getDddUnit());
            }
            if (singleDose == null || singleDose.compareTo(BigDecimal.ZERO) <= 0) continue;

            // 计算使用天数（限制在患者在科时间范围内）
            BigDecimal useDays = calcUseDays(advice, startTime, endTime, patientMap);

            // 计算每日次数
            double dailyTimes = parseFreq(str(advice.get("freq_name")));

            // 总剂量 = 单次剂量 × 每日次数 × 使用天数
            BigDecimal totalDose = singleDose
                    .multiply(BigDecimal.valueOf(dailyTimes))
                    .multiply(useDays)
                    .setScale(4, RoundingMode.HALF_UP);

            // DDDs = 总剂量 / DDD值
            BigDecimal ddds = totalDose.divide(config.getDddValue(), 4, RoundingMode.HALF_UP);

            AdviceDddCalc calc = new AdviceDddCalc();
            calc.setInHospitalNo(str(advice.get("in_hospital_no")));
            calc.setDrugName(config.getDrugName());
            calc.setDrugClass(config.getDrugClass());
            calc.setManageLevel(config.getManageLevel());
            calc.setDddValue(config.getDddValue());
            calc.setDddUnit(config.getDddUnit());
            calc.setFreq(str(advice.get("freq_name")));
            calc.setMethod(str(advice.get("method_name")));
            calc.setStartTime(str(advice.get("start_time")));
            calc.setUseDays(useDays);
            calc.setSingleDose(singleDose);
            calc.setTotalDose(totalDose);
            calc.setDdds(ddds);

            result.add(calc);
        }

        return result;
    }

    /**
     * 匹配 DDD 配置（先匹配通用名，再匹配关键词）。
     */
    private DddConfig matchDddConfig(String adviceName, List<DddConfig> configs) {
        for (DddConfig config : configs) {
            if (adviceName.contains(config.getDrugName())) {
                return config;
            }
        }
        for (DddConfig config : configs) {
            String keywords = config.getKeywords();
            if (keywords != null && !keywords.isEmpty()) {
                for (String kw : keywords.split(",")) {
                    if (kw != null && !kw.trim().isEmpty() && adviceName.contains(kw.trim())) {
                        return config;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 从 drug_one_dosage 和 drug_one_dosage_unit 字段解析单次剂量，并转换为目标单位。
     */
    private BigDecimal parseDoseFromFields(Map<String, Object> advice, String targetUnit) {
        Object dosageObj = advice.get("drug_one_dosage");
        Object unitObj = advice.get("drug_one_dosage_unit");
        if (dosageObj == null) return null;

        try {
            double value = Double.parseDouble(str(dosageObj));
            String unit = str(unitObj);
            if (unit == null) unit = "";
            unit = unit.toLowerCase().trim();

            // 统一转换为目标单位
            String target = targetUnit == null ? "g" : targetUnit.toLowerCase().trim();

            if ("g".equals(target)) {
                if ("mg".equals(unit) || "毫克".equals(unit)) {
                    value = value / 1000;
                } else if ("μg".equals(unit) || "ug".equals(unit) || "mcg".equals(unit) || "微克".equals(unit)) {
                    value = value / 1000000;
                }
                // g、MU、万单位不转换
            } else if ("mg".equals(target)) {
                if ("g".equals(unit) || "克".equals(unit)) {
                    value = value * 1000;
                } else if ("μg".equals(unit) || "ug".equals(unit) || "mcg".equals(unit) || "微克".equals(unit)) {
                    value = value / 1000;
                }
            }
            // MU 目标单位不做换算

            return BigDecimal.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从医嘱名称中解析单次剂量（转换为 g）。
     */
    private BigDecimal parseDose(String adviceName, String targetUnit) {
        if (adviceName == null) return null;

        Matcher matcher = DOSE_PATTERN.matcher(adviceName);
        List<BigDecimal> doses = new ArrayList<>();

        while (matcher.find()) {
            try {
                double value = Double.parseDouble(matcher.group(1));
                String unit = matcher.group(2).toLowerCase();
                // 转换为 g
                if ("mg".equals(unit)) {
                    value = value / 1000;
                } else if ("mu".equals(unit) || "万单位".equals(unit)) {
                    // MU 不转换，保持原值（多粘菌素等）
                }
                doses.add(BigDecimal.valueOf(value));
            } catch (Exception e) {
                // 忽略解析失败的
            }
        }

        if (doses.isEmpty()) return null;

        // 取最大剂量值（如"0.5g/瓶 1g"取1g）
        return doses.stream().max(BigDecimal::compareTo).orElse(null);
    }

    /**
     * 计算医嘱使用天数（与统计周期、患者在科时间取交集）。
     */
    private BigDecimal calcUseDays(Map<String, Object> advice, String startTime, String endTime,
                                    Map<String, Map<String, Object>> patientMap) {
        try {
            LocalDateTime start = parseDateTime(str(advice.get("start_time")));
            LocalDateTime end = parseDateTime(str(advice.get("end_time")));
            LocalDateTime planEnd = parseDateTime(str(advice.get("plan_end_time")));

            if (start == null) return BigDecimal.ONE;

            // 结束时间优先取实际结束时间，没有则取计划结束时间，再没有则取统计周期结束
            if (end == null) end = planEnd;
            if (end == null) end = LocalDateTime.parse(endTime, DT_FMT);

            // 与统计周期取交集
            LocalDateTime periodStart = LocalDateTime.parse(startTime, DT_FMT);
            LocalDateTime periodEnd = LocalDateTime.parse(endTime, DT_FMT);

            LocalDateTime effectiveStart = start.isBefore(periodStart) ? periodStart : start;
            LocalDateTime effectiveEnd = end.isAfter(periodEnd) ? periodEnd : end;

            // 与患者在科时间取交集（关键修复：避免使用时长超过在科天数）
            String inHospitalNo = str(advice.get("in_hospital_no"));
            if (patientMap != null && inHospitalNo != null) {
                Map<String, Object> patient = patientMap.get(inHospitalNo);
                if (patient != null) {
                    LocalDateTime patientIn = parseDateTime(str(patient.get("in_depart_time")));
                    LocalDateTime patientOut = parseDateTime(str(patient.get("out_depart_time")));
                    if (patientOut == null) patientOut = periodEnd; // 还在科，用统计周期结束

                    if (patientIn != null && effectiveStart.isBefore(patientIn)) effectiveStart = patientIn;
                    if (effectiveEnd.isAfter(patientOut)) effectiveEnd = patientOut;
                }
            }

            if (effectiveEnd.isBefore(effectiveStart)) return BigDecimal.ONE;

            long hours = ChronoUnit.HOURS.between(effectiveStart, effectiveEnd);
            double days = hours / 24.0;
            if (days < 1) days = 1; // 不足1天按1天算

            return BigDecimal.valueOf(days).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ONE;
        }
    }

    /**
     * 解析频次为每日次数。
     */
    private double parseFreq(String freq) {
        if (freq == null || freq.isEmpty()) return 1.0;
        String lower = freq.toLowerCase().trim();
        return FREQ_MAP.getOrDefault(lower, 1.0);
    }

    /**
     * 判断是否为溶媒。
     */
    private boolean isSolvent(String name) {
        for (String keyword : SOLVENT_KEYWORDS) {
            if (name.contains(keyword)) {
                // 但如果同时包含抗菌药关键词，则不是纯溶媒（如"氯化钠+美罗培南"）
                return false; // 暂时不过滤，交给 DDD 匹配
            }
        }
        return false;
    }

    /**
     * 计算总床日数。
     */
    private BigDecimal calcTotalBedDays(List<Map<String, Object>> patients, String startTime, String endTime) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> p : patients) {
            total = total.add(calcPatientBedDays(p, startTime, endTime));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算患者在统计周期内的在科天数（患者明细展示用，整天 +1 口径）。
     * 有效开始 = max(入科时间, 周期开始)；有效结束 = min(出科时间, 周期结束, 当前时间)。
     * 在科天数 = 结束日期 - 开始日期 + 1（入科当天计 1 天，如 9-01 至 9-30 = 30 天）。
     * 与第四维度 MDRO 在科天数口径保持一致；床日数分母（calcPatientBedDays）不随此调整。
     */
    private BigDecimal calcStayDaysForDisplay(Map<String, Object> patient, String startTime, String endTime) {
        try {
            LocalDateTime inDepart = parseDateTime(str(patient.get("in_depart_time")));
            if (inDepart == null) return BigDecimal.ZERO;

            LocalDateTime outDepart = parseDateTime(str(patient.get("out_depart_time")));
            if (outDepart == null) {
                // 还在科：用当前时间作为截止（与第四维度一致）
                outDepart = LocalDateTime.now();
            }
            LocalDateTime periodStart = LocalDateTime.parse(startTime, DT_FMT);
            LocalDateTime periodEnd = LocalDateTime.parse(endTime, DT_FMT);

            LocalDateTime effectiveStart = inDepart.isAfter(periodStart) ? inDepart : periodStart;
            LocalDateTime effectiveEnd = outDepart.isBefore(periodEnd) ? outDepart : periodEnd;

            if (!effectiveStart.isBefore(effectiveEnd)) {
                return BigDecimal.ZERO; // 周期内无在科交集
            }

            long days = ChronoUnit.DAYS.between(effectiveStart.toLocalDate(), effectiveEnd.toLocalDate());
            return BigDecimal.valueOf(Math.max(days + 1, 1));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 计算单个患者在统计周期内的床日数。
     */
    private BigDecimal calcPatientBedDays(Map<String, Object> patient, String startTime, String endTime) {
        try {
            LocalDateTime inDepart = parseDateTime(str(patient.get("in_depart_time")));
            LocalDateTime outDepart = parseDateTime(str(patient.get("out_depart_time")));
            LocalDateTime periodStart = LocalDateTime.parse(startTime, DT_FMT);
            LocalDateTime periodEnd = LocalDateTime.parse(endTime, DT_FMT);

            if (inDepart == null) return BigDecimal.ONE;
            if (outDepart == null) outDepart = periodEnd;

            LocalDateTime effectiveStart = inDepart.isBefore(periodStart) ? periodStart : inDepart;
            LocalDateTime effectiveEnd = outDepart.isAfter(periodEnd) ? periodEnd : outDepart;

            if (effectiveEnd.isBefore(effectiveStart)) return BigDecimal.ONE;

            long hours = ChronoUnit.HOURS.between(effectiveStart, effectiveEnd);
            double days = hours / 24.0;
            if (days < 1) days = 1;

            return BigDecimal.valueOf(days).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ONE;
        }
    }

    /**
     * 计算药品分类占比。
     */
    private List<DddOverviewView.DddClassRatio> calcClassRatios(List<AdviceDddCalc> calcList, BigDecimal totalDdds) {
        Map<String, BigDecimal> classMap = calcList.stream()
                .collect(Collectors.groupingBy(
                        AdviceDddCalc::getDrugClass,
                        Collectors.reducing(BigDecimal.ZERO, AdviceDddCalc::getDdds, BigDecimal::add)
                ));

        List<DddOverviewView.DddClassRatio> ratios = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : classMap.entrySet()) {
            DddOverviewView.DddClassRatio r = new DddOverviewView.DddClassRatio();
            r.setDrugClass(entry.getKey() != null ? entry.getKey() : "其他");
            r.setDdds(entry.getValue());
            if (totalDdds.compareTo(BigDecimal.ZERO) > 0) {
                r.setRatio(entry.getValue().multiply(new BigDecimal("100"))
                        .divide(totalDdds, 2, RoundingMode.HALF_UP));
            }
            ratios.add(r);
        }
        ratios.sort((a, b) -> b.getDdds().compareTo(a.getDdds()));
        return ratios;
    }

    /**
     * 计算管理级别占比。
     */
    private List<DddOverviewView.DddLevelRatio> calcLevelRatios(List<AdviceDddCalc> calcList, BigDecimal totalDdds) {
        Map<String, BigDecimal> levelMap = calcList.stream()
                .collect(Collectors.groupingBy(
                        AdviceDddCalc::getManageLevel,
                        Collectors.reducing(BigDecimal.ZERO, AdviceDddCalc::getDdds, BigDecimal::add)
                ));

        List<DddOverviewView.DddLevelRatio> ratios = new ArrayList<>();
        for (String level : Arrays.asList("非限制", "限制", "特殊")) {
            DddOverviewView.DddLevelRatio r = new DddOverviewView.DddLevelRatio();
            r.setManageLevel(level);
            BigDecimal ddds = levelMap.getOrDefault(level, BigDecimal.ZERO);
            r.setDdds(ddds);
            if (totalDdds.compareTo(BigDecimal.ZERO) > 0) {
                r.setRatio(ddds.multiply(new BigDecimal("100"))
                        .divide(totalDdds, 2, RoundingMode.HALF_UP));
            }
            ratios.add(r);
        }
        return ratios;
    }

    // ==================================================================
    // 工具方法
    // ==================================================================

    private String str(Object o) {
        if (o == null) return "";
        // 达梦 CLOB/NClob 类型必须主动读取内容，否则 toString() 只返回对象地址
        // （如 dm.jdbc.driver.DmdbNClob@6300cfa5），NClob 继承自 Clob，统一处理
        if (o instanceof java.sql.Clob) {
            try {
                java.sql.Clob clob = (java.sql.Clob) o;
                long len = clob.length();
                if (len <= 0) return "";
                return clob.getSubString(1, (int) Math.min(len, 65535)).trim();
            } catch (Exception e) {
                return "";
            }
        }
        return String.valueOf(o).trim();
    }

    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            if (s.length() >= 19) {
                return LocalDateTime.parse(s.substring(0, 19), DT_FMT);
            }
            return LocalDateTime.parse(s, DT_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 内部计算结果类。
     */
    @lombok.Data
    private static class AdviceDddCalc {
        private String inHospitalNo;
        private String drugName;
        private String drugClass;
        private String manageLevel;
        private BigDecimal dddValue;
        private String dddUnit;
        private String freq;
        private String method;
        private String startTime;
        private String endTime;
        private BigDecimal useDays;
        private BigDecimal singleDose;
        private BigDecimal totalDose;
        private BigDecimal ddds;
        private String openStaffName;
    }
}
