package com.zing.doctor.module.antibiotic.service.impl;

import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.module.antibiotic.dto.MdroBacteriaRank;
import com.zing.doctor.module.antibiotic.dto.MdroOverviewView;
import com.zing.doctor.module.antibiotic.dto.MdroPatientDetail;
import com.zing.doctor.module.antibiotic.service.MdroConfigService;
import com.zing.doctor.module.antibiotic.service.MdroStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 第四维度：细菌培养检出监测统计 Service 实现。
 *
 * <p>核心逻辑：
 * <ul>
 *   <li>从 patient_info_lis_item 表查询细菌培养记录（lis_item_name = '细菌培养+药敏'）</li>
 *   <li>阳性结果：lis_item_result 非空且不是"未检出"、"无细菌生长"、"阴性"</li>
 *   <li>细菌分类：通过 MdroConfigService.matchBacteriaClass() 匹配（革兰阳性/阴性/真菌）</li>
 *   <li>高风险细菌：通过 MdroConfigService.isHighRiskBacteria() 判断</li>
 *   <li>患者关联：通过 in_hospital_no 关联 patient_info 表</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MdroStatsServiceImpl implements MdroStatsService {

    private final IcuPatientMapper icuPatientMapper;
    private final MdroConfigService mdroConfigService;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // 阴性结果关键词（排除）
    private static final List<String> NEGATIVE_KEYWORDS = Arrays.asList(
            "未检出", "无细菌生长", "阴性", "无致病菌", "正常菌群", "未生长", "无菌生长"
    );

    // 非细菌/真菌结果关键词（明确不是真正的细菌或真菌，排除）
    private static final List<String> NON_BACTERIA_FUNGI_KEYWORDS = Arrays.asList(
            "杂菌", "无沙门菌", "无志贺菌", "菌群失调", "正常菌群", "未见细菌", "无细菌",
            "未检出", "培养", "需氧", "厌氧", "无菌生长", "无细菌生长", "无致病菌",
            "阴性", "未生长", "培养阳性", "培养阴性"
    );

    /**
     * 判断是否为非细菌/真菌结果（明确不是真正的细菌或真菌）
     * 规则：包含数字和"天"，或者包含"生长"
     */
    boolean isNonBacteriaFungi(String bacteriaName) {
        if (bacteriaName == null || bacteriaName.isEmpty()) {
            return true;
        }
        // 同时包含数字和"天"（如"厌氧培养5天未检出细菌"、"需氧培养5天未检出细菌"）
        if (bacteriaName.matches(".*\\d+.*天.*")) {
            return true;
        }
        // 包含"生长"（如"无细菌生长"、"杂菌生长"）
        if (bacteriaName.contains("生长")) {
            return true;
        }
        return false;
    }

    @Override
    public List<Map<String, Object>> getAllDepartments() {
        return icuPatientMapper.selectAllDepartments();
    }

    @Override
    public MdroOverviewView getOverview(String startTime, String endTime, String departCode) {
        log.info("[MDRO] 总览统计: {} ~ {}, departCode={}", startTime, endTime, departCode);

        MdroOverviewView view = new MdroOverviewView();
        view.setStartTime(startTime);
        view.setEndTime(endTime);
        view.setDepartCode(departCode);

        // 1. 查询在科患者列表（计算总患者数）
        List<Map<String, Object>> allPatients = icuPatientMapper.selectPatientsForStats(startTime, endTime, departCode);
        Set<String> totalPatientSet = allPatients.stream()
                .map(m -> String.valueOf(m.get("in_hospital_no")))
                .collect(Collectors.toSet());
        view.setTotalPatients(totalPatientSet.size());

        // 2. 查询所有细菌培养送检记录（含阴性）
        List<Map<String, Object>> allCultures = icuPatientMapper.selectAllBacteriaCultureForStats(startTime, endTime, departCode);
        Set<String> culturePatientSet = allCultures.stream()
                .map(m -> String.valueOf(m.get("in_hospital_no")))
                .collect(Collectors.toSet());
        view.setCulturePatients(culturePatientSet.size());
        view.setTotalCultureCount(allCultures.size());

        // 3. 查询阳性细菌培养记录
        List<Map<String, Object>> positiveCultures = icuPatientMapper.selectBacteriaCultureForStats(startTime, endTime, departCode);
        Set<String> positivePatientSet = positiveCultures.stream()
                .map(m -> String.valueOf(m.get("in_hospital_no")))
                .collect(Collectors.toSet());
        view.setPositivePatients(positivePatientSet.size());
        view.setPositiveCultureCount(positiveCultures.size());

        // 4. 计算阳性率
        if (view.getCulturePatients() > 0) {
            view.setPositiveRate(round2(view.getPositivePatients() * 100.0 / view.getCulturePatients()));
        } else {
            view.setPositiveRate(0.0);
        }

        // 5. 统计高风险细菌检出患者
        Set<String> highRiskPatientSet = new HashSet<>();
        int gramPositive = 0, gramNegative = 0, fungi = 0, other = 0;
        int totalBacteriaFungi = 0; // 统计所有真正的细菌/真菌（含无法分类的其他）

        // 标本类型统计
        Map<String, Integer> specimenMap = new LinkedHashMap<>();

        for (Map<String, Object> record : positiveCultures) {
            String bacteriaName = String.valueOf(record.get("bacteria_name"));
            String inHospitalNo = String.valueOf(record.get("in_hospital_no"));
            String specimenType = record.get("specimen_type") != null ? String.valueOf(record.get("specimen_type")) : "未知";

            // 剔除非细菌/真菌结果（如杂菌生长、无沙门菌志贺菌生长等）
            if (isNonBacteriaFungi(bacteriaName)) {
                continue;
            }

            // 细菌分类：无法匹配配置表的归为"其他"，但仍统计
            String bacteriaClass = mdroConfigService.matchBacteriaClass(bacteriaName);
            if ("gram_positive".equals(bacteriaClass)) {
                gramPositive++;
            } else if ("gram_negative".equals(bacteriaClass)) {
                gramNegative++;
            } else if ("fungi".equals(bacteriaClass)) {
                fungi++;
            } else {
                other++;
            }
            totalBacteriaFungi++;

            // 高风险细菌
            if (mdroConfigService.isHighRiskBacteria(bacteriaName)) {
                highRiskPatientSet.add(inHospitalNo);
            }

            // 标本类型统计
            specimenMap.merge(specimenType, 1, Integer::sum);
        }

        view.setGramPositiveCount(gramPositive);
        view.setGramNegativeCount(gramNegative);
        view.setFungiCount(fungi);
        view.setOtherCount(other);

        view.setHighRiskPatients(highRiskPatientSet.size());
        if (view.getPositivePatients() > 0) {
            view.setHighRiskRate(round2(highRiskPatientSet.size() * 100.0 / view.getPositivePatients()));
        } else {
            view.setHighRiskRate(0.0);
        }

        // 6. 细菌分类占比（含其他，已剔除非细菌/真菌）
        List<Map<String, Object>> classDistribution = new ArrayList<>();
        classDistribution.add(buildDistItem("革兰阳性菌", gramPositive, totalBacteriaFungi, "#67c23a"));
        classDistribution.add(buildDistItem("革兰阴性菌", gramNegative, totalBacteriaFungi, "#409eff"));
        classDistribution.add(buildDistItem("真菌", fungi, totalBacteriaFungi, "#e6a23c"));
        classDistribution.add(buildDistItem("其他", other, totalBacteriaFungi, "#909399"));
        view.setClassDistribution(classDistribution);

        // 7. 标本类型分布（TOP10）
        int totalSpecimen = positiveCultures.size();
        List<Map<String, Object>> specimenDistribution = specimenMap.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(10)
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", e.getKey());
                    item.put("value", e.getValue());
                    item.put("percentage", totalSpecimen > 0 ? round2(e.getValue() * 100.0 / totalSpecimen) : 0.0);
                    return item;
                })
                .collect(Collectors.toList());
        view.setSpecimenDistribution(specimenDistribution);

        // 8. 科室名称
        if (departCode != null && !departCode.isEmpty()) {
            List<Map<String, Object>> depts = getAllDepartments();
            for (Map<String, Object> dept : depts) {
                if (departCode.equals(String.valueOf(dept.get("org_code")))) {
                    view.setDepartName(String.valueOf(dept.get("depart_name")));
                    break;
                }
            }
        }

        return view;
    }

    @Override
    public List<MdroBacteriaRank> getBacteriaRankTop20(String startTime, String endTime, String departCode) {
        log.info("[MDRO] 菌株排名: {} ~ {}, departCode={}", startTime, endTime, departCode);

        List<Map<String, Object>> positiveCultures = icuPatientMapper.selectBacteriaCultureForStats(startTime, endTime, departCode);

        // 按细菌名称分组统计
        Map<String, List<Map<String, Object>>> bacteriaGroup = positiveCultures.stream()
                .collect(Collectors.groupingBy(m -> String.valueOf(m.get("bacteria_name"))));

        List<MdroBacteriaRank> ranks = new ArrayList<>();
        int totalBacteriaFungi = 0; // 只统计细菌和真菌，剔除其他

        for (Map.Entry<String, List<Map<String, Object>>> entry : bacteriaGroup.entrySet()) {
            String bacteriaName = entry.getKey();
            List<Map<String, Object>> records = entry.getValue();

            // 剔除非细菌/真菌结果（如杂菌生长、无沙门菌志贺菌生长等）
            if (isNonBacteriaFungi(bacteriaName)) {
                log.info("[MDRO] 菌株排名剔除非细菌/真菌: {}", bacteriaName);
                continue;
            }
            totalBacteriaFungi += records.size();

            // 细菌分类：无法匹配配置表的归为"其他"，但仍统计
            String bacteriaClass = mdroConfigService.matchBacteriaClass(bacteriaName);

            MdroBacteriaRank rank = new MdroBacteriaRank();
            rank.setBacteriaName(bacteriaName);
            rank.setDetectCount(records.size());
            rank.setPatientCount((int) records.stream().map(m -> String.valueOf(m.get("in_hospital_no"))).distinct().count());
            rank.setPercentage(0.0); // 最后统一计算百分比

            rank.setBacteriaClass(bacteriaClass);
            rank.setBacteriaClassName(getClassName(bacteriaClass));
            rank.setIsHighRisk(mdroConfigService.isHighRiskBacteria(bacteriaName) ? 1 : 0);

            // 最常见标本类型
            Map<String, Long> specimenCount = records.stream()
                    .collect(Collectors.groupingBy(
                            m -> m.get("specimen_type") != null ? String.valueOf(m.get("specimen_type")) : "未知",
                            Collectors.counting()));
            rank.setTopSpecimenType(specimenCount.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("未知"));

            // 首次/最近检出时间
            rank.setFirstDetectTime(records.stream()
                    .map(m -> String.valueOf(m.get("detect_time")))
                    .min(String::compareTo)
                    .orElse(""));
            rank.setLastDetectTime(records.stream()
                    .map(m -> String.valueOf(m.get("detect_time")))
                    .max(String::compareTo)
                    .orElse(""));

            ranks.add(rank);
        }

        // 按检出次数降序排序，取 TOP20
        ranks.sort((a, b) -> b.getDetectCount() - a.getDetectCount());
        List<MdroBacteriaRank> top20 = ranks.stream().limit(20).collect(Collectors.toList());

        // 统一计算百分比（基于细菌/真菌总数）
        for (MdroBacteriaRank rank : top20) {
            rank.setPercentage(totalBacteriaFungi > 0 ? round2(rank.getDetectCount() * 100.0 / totalBacteriaFungi) : 0.0);
        }

        // 设置排名
        for (int i = 0; i < top20.size(); i++) {
            top20.get(i).setRank(i + 1);
        }

        return top20;
    }

    @Override
    public List<MdroOverviewView.MdroTrendPoint> getMonthlyTrend(String startTime, String endTime, String departCode) {
        log.info("[MDRO] 月度趋势: {} ~ {}, departCode={}", startTime, endTime, departCode);

        List<MdroOverviewView.MdroTrendPoint> trend = new ArrayList<>();

        LocalDate start = LocalDate.parse(startTime.substring(0, 10), DATE_FMT);
        LocalDate end = LocalDate.parse(endTime.substring(0, 10), DATE_FMT);

        LocalDate monthStart = start.withDayOfMonth(1);
        LocalDate monthEnd = end.withDayOfMonth(1);

        while (monthStart.isBefore(monthEnd)) {
            LocalDate nextMonth = monthStart.plusMonths(1);

            String mStart = monthStart.format(DATE_FMT) + " 00:00:00";
            String mEnd = nextMonth.format(DATE_FMT) + " 00:00:00";

            MdroOverviewView.MdroTrendPoint point = new MdroOverviewView.MdroTrendPoint();
            point.setMonth(monthStart.format(MONTH_FMT));

            try {
                // 查询当月送检记录
                List<Map<String, Object>> allCultures = icuPatientMapper.selectAllBacteriaCultureForStats(mStart, mEnd, departCode);
                point.setCulturePatients((int) allCultures.stream()
                        .map(m -> String.valueOf(m.get("in_hospital_no")))
                        .distinct().count());

                // 查询当月阳性记录
                List<Map<String, Object>> positiveCultures = icuPatientMapper.selectBacteriaCultureForStats(mStart, mEnd, departCode);
                point.setPositivePatients((int) positiveCultures.stream()
                        .map(m -> String.valueOf(m.get("in_hospital_no")))
                        .distinct().count());

                if (point.getCulturePatients() > 0) {
                    point.setPositiveRate(round2(point.getPositivePatients() * 100.0 / point.getCulturePatients()));
                } else {
                    point.setPositiveRate(0.0);
                }

                // 高风险细菌和分类统计
                int highRisk = 0, gp = 0, gn = 0, fg = 0;
                for (Map<String, Object> record : positiveCultures) {
                    String bacteriaName = String.valueOf(record.get("bacteria_name"));
                    if (mdroConfigService.isHighRiskBacteria(bacteriaName)) {
                        highRisk++;
                    }
                    String cls = mdroConfigService.matchBacteriaClass(bacteriaName);
                    if ("gram_positive".equals(cls)) gp++;
                    else if ("gram_negative".equals(cls)) gn++;
                    else if ("fungi".equals(cls)) fg++;
                }
                point.setHighRiskCount(highRisk);
                point.setGramPositiveCount(gp);
                point.setGramNegativeCount(gn);
                point.setFungiCount(fg);

            } catch (Exception e) {
                log.warn("[MDRO] 月度趋势查询失败: month={}, error={}", monthStart, e.getMessage());
                point.setCulturePatients(0);
                point.setPositivePatients(0);
                point.setPositiveRate(0.0);
                point.setHighRiskCount(0);
                point.setGramPositiveCount(0);
                point.setGramNegativeCount(0);
                point.setFungiCount(0);
            }

            trend.add(point);
            monthStart = nextMonth;
        }

        return trend;
    }

    @Override
    public List<MdroPatientDetail> getPatientDetails(String startTime, String endTime, String departCode) {
        log.info("[MDRO] 患者明细: {} ~ {}, departCode={}", startTime, endTime, departCode);

        // 查询阳性细菌培养记录
        List<Map<String, Object>> positiveCultures = icuPatientMapper.selectBacteriaCultureForStats(startTime, endTime, departCode);

        // 查询所有送检记录（计算送检次数）
        List<Map<String, Object>> allCultures = icuPatientMapper.selectAllBacteriaCultureForStats(startTime, endTime, departCode);
        Map<String, Integer> cultureCountMap = allCultures.stream()
                .collect(Collectors.groupingBy(
                        m -> String.valueOf(m.get("in_hospital_no")),
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        // 按患者分组
        Map<String, List<Map<String, Object>>> patientGroup = positiveCultures.stream()
                .collect(Collectors.groupingBy(m -> String.valueOf(m.get("in_hospital_no"))));

        List<MdroPatientDetail> details = new ArrayList<>();

        for (Map.Entry<String, List<Map<String, Object>>> entry : patientGroup.entrySet()) {
            String inHospitalNo = entry.getKey();
            List<Map<String, Object>> records = entry.getValue();

            // 取第一条记录的患者信息
            Map<String, Object> first = records.get(0);

            // 过滤：患者在科时间与统计周期必须有交集（排除已出科且出科时间早于统计周期开始的患者）
            String inDt = first.get("in_depart_time") != null ? String.valueOf(first.get("in_depart_time")) : "";
            String outDt = first.get("out_depart_time") != null ? String.valueOf(first.get("out_depart_time")) : "";
            if (!isInDepartOverlap(inDt, outDt, startTime, endTime)) {
                log.info("[MDRO] 跳过已出科患者: inHospitalNo={}, inDepartTime={}, outDepartTime={}", inHospitalNo, inDt, outDt);
                continue;
            }

            MdroPatientDetail detail = new MdroPatientDetail();
            detail.setInHospitalNo(inHospitalNo);
            detail.setName(first.get("patient_name") != null ? String.valueOf(first.get("patient_name")) : "");
            detail.setGender(first.get("gender") != null ? String.valueOf(first.get("gender")) : "");
            detail.setAge(first.get("age") != null ? String.valueOf(first.get("age")) : "");
            detail.setDepartment(first.get("department") != null ? String.valueOf(first.get("department")) : "");
            detail.setBedNo(first.get("bed_no") != null ? String.valueOf(first.get("bed_no")) : "");
            detail.setInDepartTime(first.get("in_depart_time") != null ? String.valueOf(first.get("in_depart_time")) : "");
            detail.setOutDepartTime(first.get("out_depart_time") != null ? String.valueOf(first.get("out_depart_time")) : "");
            detail.setIsInDepart(first.get("is_in_depart") != null ? Integer.valueOf(String.valueOf(first.get("is_in_depart"))) : 0);

            // 计算在科天数（统计周期内的在科天数）
            detail.setInDepartDays(calcInDepartDays(detail.getInDepartTime(), detail.getOutDepartTime(), startTime, endTime));

            // 送检次数和阳性次数
            detail.setCultureCount(cultureCountMap.getOrDefault(inHospitalNo, 0));
            detail.setPositiveCount(records.size());
            if (detail.getCultureCount() > 0) {
                detail.setPositiveRate(round2(detail.getPositiveCount() * 100.0 / detail.getCultureCount()));
            } else {
                detail.setPositiveRate(0.0);
            }

            // 按细菌名称分组
            Map<String, List<Map<String, Object>>> bacteriaGroup = records.stream()
                    .collect(Collectors.groupingBy(m -> String.valueOf(m.get("bacteria_name"))));

            detail.setBacteriaSpeciesCount(bacteriaGroup.size());

            // 构建细菌明细列表
            List<MdroPatientDetail.BacteriaDetectItem> bacteriaList = new ArrayList<>();
            Set<String> highRiskNames = new LinkedHashSet<>();
            boolean hasHighRisk = false;

            for (Map.Entry<String, List<Map<String, Object>>> bEntry : bacteriaGroup.entrySet()) {
                String bacteriaName = bEntry.getKey();
                List<Map<String, Object>> bRecords = bEntry.getValue();

                MdroPatientDetail.BacteriaDetectItem item = new MdroPatientDetail.BacteriaDetectItem();
                item.setBacteriaName(bacteriaName);
                String cls = mdroConfigService.matchBacteriaClass(bacteriaName);
                item.setBacteriaClass(cls);
                item.setBacteriaClassName(getClassName(cls));
                item.setIsHighRisk(mdroConfigService.isHighRiskBacteria(bacteriaName) ? 1 : 0);
                item.setDetectCount(bRecords.size());

                // 标本类型
                String specimenTypes = bRecords.stream()
                        .map(m -> m.get("specimen_type") != null ? String.valueOf(m.get("specimen_type")) : "未知")
                        .distinct()
                        .collect(Collectors.joining(", "));
                item.setSpecimenTypes(specimenTypes);

                // 首次/最近检出时间
                item.setFirstDetectTime(bRecords.stream()
                        .map(m -> String.valueOf(m.get("detect_time")))
                        .min(String::compareTo).orElse(""));
                item.setLastDetectTime(bRecords.stream()
                        .map(m -> String.valueOf(m.get("detect_time")))
                        .max(String::compareTo).orElse(""));

                bacteriaList.add(item);

                if (item.getIsHighRisk() == 1) {
                    hasHighRisk = true;
                    highRiskNames.add(bacteriaName);
                }
            }

            // 按检出次数降序
            bacteriaList.sort((a, b) -> b.getDetectCount() - a.getDetectCount());
            detail.setBacteriaList(bacteriaList);

            detail.setHasHighRiskBacteria(hasHighRisk ? 1 : 0);
            detail.setHighRiskBacteriaNames(String.join(", ", highRiskNames));

            // 首次/最近阳性时间
            detail.setFirstPositiveTime(records.stream()
                    .map(m -> String.valueOf(m.get("detect_time")))
                    .min(String::compareTo).orElse(""));
            detail.setLastPositiveTime(records.stream()
                    .map(m -> String.valueOf(m.get("detect_time")))
                    .max(String::compareTo).orElse(""));

            details.add(detail);
        }

        // 按最近阳性时间降序
        details.sort((a, b) -> {
            String aTime = a.getLastPositiveTime() != null ? a.getLastPositiveTime() : "";
            String bTime = b.getLastPositiveTime() != null ? b.getLastPositiveTime() : "";
            return bTime.compareTo(aTime);
        });

        return details;
    }

    @Override
    public MdroPatientDetail getPatientDetail(String inHospitalNo, String startTime, String endTime) {
        log.info("[MDRO] 单患者明细: inHospitalNo={}, {} ~ {}", inHospitalNo, startTime, endTime);

        List<MdroPatientDetail> all = getPatientDetails(startTime, endTime, "");
        return all.stream()
                .filter(d -> inHospitalNo.equals(d.getInHospitalNo()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Map<String, Object>> getSpecimenDistribution(String startTime, String endTime, String departCode) {
        List<Map<String, Object>> positiveCultures = icuPatientMapper.selectBacteriaCultureForStats(startTime, endTime, departCode);

        Map<String, Integer> specimenMap = new LinkedHashMap<>();
        for (Map<String, Object> record : positiveCultures) {
            String specimenType = record.get("specimen_type") != null ? String.valueOf(record.get("specimen_type")) : "未知";
            specimenMap.merge(specimenType, 1, Integer::sum);
        }

        int total = positiveCultures.size();
        return specimenMap.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", e.getKey());
                    item.put("value", e.getValue());
                    item.put("percentage", total > 0 ? round2(e.getValue() * 100.0 / total) : 0.0);
                    return item;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<MdroPatientDetail> getHighRiskAlerts(String startTime, String endTime, String departCode) {
        List<MdroPatientDetail> all = getPatientDetails(startTime, endTime, departCode);
        return all.stream()
                .filter(d -> d.getHasHighRiskBacteria() != null && d.getHasHighRiskBacteria() == 1)
                .collect(Collectors.toList());
    }

    // ==================================================================
    // 辅助方法
    // ==================================================================

    private Map<String, Object> buildDistItem(String name, int count, int total, String color) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("value", count);
        item.put("percentage", total > 0 ? round2(count * 100.0 / total) : 0.0);
        item.put("color", color);
        return item;
    }

    String getClassName(String bacteriaClass) {
        if ("gram_positive".equals(bacteriaClass)) return "革兰阳性菌";
        if ("gram_negative".equals(bacteriaClass)) return "革兰阴性菌";
        if ("fungi".equals(bacteriaClass)) return "真菌";
        return "其他";
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * 通用时间解析，支持多种格式：
     * yyyy-MM-dd HH:mm:ss / 带毫秒 / 带T分隔符 / ISO格式
     */
    private LocalDateTime parseDateTime(String text) {
        if (text == null || text.isEmpty() || "null".equals(text)) {
            return null;
        }
        try {
            String[] patterns = {
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy-MM-dd HH:mm:ss.S",
                    "yyyy-MM-dd HH:mm:ss.SS",
                    "yyyy-MM-dd HH:mm:ss.SSS",
                    "yyyy-MM-dd'T'HH:mm:ss",
                    "yyyy-MM-dd'T'HH:mm:ss.S",
                    "yyyy-MM-dd'T'HH:mm:ss.SS",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS"
            };
            for (String pattern : patterns) {
                try {
                    return LocalDateTime.parse(text, DateTimeFormatter.ofPattern(pattern));
                } catch (Exception ignored) {
                }
            }
            return LocalDateTime.parse(text);
        } catch (Exception e) {
            log.warn("[MDRO] 时间解析失败: {}", text);
            return null;
        }
    }

    /**
     * 计算统计周期内的在科天数。
     * 有效开始时间 = max(入科时间, 统计周期开始)
     * 有效结束时间 = min(出科时间, 统计周期结束, 当前时间)
     * 在科天数 = 有效结束时间 - 有效开始时间
     */
    int calcInDepartDays(String inDepartTime, String outDepartTime, String statStart, String statEnd) {
        try {
            LocalDateTime in = parseDateTime(inDepartTime);
            if (in == null) return 0;

            LocalDateTime out;
            if (outDepartTime != null && !outDepartTime.isEmpty() && !"null".equals(outDepartTime)) {
                out = parseDateTime(outDepartTime);
                if (out == null) out = LocalDateTime.now();
            } else {
                // 还在科，用当前时间
                out = LocalDateTime.now();
            }

            LocalDateTime sStart = parseDateTime(statStart);
            LocalDateTime sEnd = parseDateTime(statEnd);
            if (sStart == null || sEnd == null) return 0;

            // 统计周期内的在科时间
            LocalDateTime effectiveStart = in.isAfter(sStart) ? in : sStart;
            LocalDateTime effectiveEnd = out.isBefore(sEnd) ? out : sEnd;

            if (!effectiveStart.isBefore(effectiveEnd)) {
                return 0; // 无交集
            }

            long days = ChronoUnit.DAYS.between(effectiveStart.toLocalDate(), effectiveEnd.toLocalDate());
            // 在科天数按整天 +1：入科当天计 1 天（如 9-01 至 9-30 在科 = 30 天）
            return (int) Math.max(days + 1, 1);
        } catch (Exception e) {
            log.warn("[MDRO] 计算在科天数失败: in={}, out={}, error={}", inDepartTime, outDepartTime, e.getMessage());
            return 0;
        }
    }

    /**
     * 判断患者在科时间与统计周期是否有交集。
     * 交集条件：入科时间 < 统计周期结束 且 (出科时间 > 统计周期开始 或 出科时间为空/还在科)
     */
    boolean isInDepartOverlap(String inDepartTime, String outDepartTime, String statStart, String statEnd) {
        LocalDateTime in = parseDateTime(inDepartTime);
        if (in == null) {
            return true; // 入科时间为空，不过滤
        }
        LocalDateTime sEnd = parseDateTime(statEnd);
        if (sEnd == null) {
            return true; // 统计周期结束时间解析失败，不过滤
        }
        if (!in.isBefore(sEnd)) {
            return false; // 入科时间 >= 统计周期结束，无交集
        }
        LocalDateTime out = parseDateTime(outDepartTime);
        if (out == null) {
            return true; // 还在科或出科时间解析失败，有交集
        }
        LocalDateTime sStart = parseDateTime(statStart);
        if (sStart == null) {
            return true; // 统计周期开始时间解析失败，不过滤
        }
        return out.isAfter(sStart); // 出科时间 > 统计周期开始，有交集
    }
}
