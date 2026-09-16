package com.zing.doctor.icu.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.zing.doctor.common.BizException;
import com.zing.doctor.icu.dto.IcuPatientAssessment;
import com.zing.doctor.icu.dto.IcuPatientBrief;
import com.zing.doctor.icu.dto.LabTrend;
import com.zing.doctor.icu.dto.TrendPoint;
import com.zing.doctor.icu.service.IcuPatientService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ICU 数据 Mock 实现（P0 默认）。
 *
 * <p>用于在 ICU 表结构/数据源就绪前打通「外链打开 → 列表 → 决策详情 → 方案推荐」全流程。
 * 生产切换：实现 {@link IcuPatientService} 的 SQL 版本并将
 * application.yml 中 icu-data-provider 改为 sql（届时删除本类或加 @ConditionalOnProperty 排除）。
 */
@Service
@ConditionalOnProperty(name = "zing.doctor.icu-data-provider", havingValue = "mock", matchIfMissing = true)
public class MockIcuPatientServiceImpl implements IcuPatientService {

    private final List<IcuPatientBrief> mockPatients = buildMock();

    @Override
    public List<IcuPatientBrief> listSuspectInfections() {
        return mockPatients;
    }

    @Override
    public String resolvePatientIdByInHospitalNo(String inHospitalNo) {
        if (StrUtil.isBlank(inHospitalNo)) {
            return null;
        }
        return mockPatients.stream()
                .filter(p -> StrUtil.equals(p.getPatientNo(), inHospitalNo)
                        || StrUtil.equals(p.getPatientId(), inHospitalNo))
                .map(IcuPatientBrief::getPatientId)
                .findFirst()
                .orElse(null);
    }

    @Override
    public IcuPatientBrief getPatientBrief(String patientId) {
        return mockPatients.stream()
                .filter(p -> StrUtil.equals(p.getPatientId(), patientId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public IcuPatientAssessment getAssessment(String patientId) {
        IcuPatientBrief patient = mockPatients.stream()
                .filter(p -> StrUtil.equals(p.getPatientId(), patientId))
                .findFirst()
                .orElseThrow(() -> new BizException(404, "未找到患者：" + patientId));

        IcuPatientAssessment assessment = new IcuPatientAssessment();
        assessment.setPatient(patient);
        assessment.setAllergies(Arrays.asList("无已知药物过敏"));
        assessment.setCreatinine("102");
        assessment.setWeight("68");
        assessment.setLabs(new HashMap<String, String>() {{
            put("PCT", patient.getPct() == null ? "0.5 ng/mL" : patient.getPct().toPlainString() + " ng/mL");
            put("WBC", patient.getWbc() == null ? "12.4 ×10⁹/L" : patient.getWbc().toPlainString() + " ×10⁹/L");
            put("CRP", "86 mg/L");
            put("乳酸", "2.1 mmol/L");
        }});
        assessment.setLabTrends(buildMockLabTrends(patient));
        assessment.setPastCultures(CollUtil.newArrayList(
                "90 天内血培养：大肠埃希菌 ESBL（对碳青霉烯敏感）",
                "痰培养：鲍曼不动杆菌（碳青霉烯中介）"));
        com.zing.doctor.icu.dto.AbxCurrentItem mockAbx = new com.zing.doctor.icu.dto.AbxCurrentItem();
        mockAbx.setName("哌拉西林/他唑巴坦");
        mockAbx.setMethod("静脉输液");
        mockAbx.setFreq("q8h");
        mockAbx.setStartTime("09-02 08:00");
        mockAbx.setStatusCode("running");
        mockAbx.setStatusText("执行中");
        assessment.setCurrentAntibiotics(CollUtil.newArrayList(mockAbx));
        assessment.setLabTrends(buildMockTrends());
        assessment.setRemark("示例数据（Mock），用于 P0 流程验证；ICU 表结构接入后替换为真实数据。");
        return assessment;
    }

    /** 示例检验趋势（近 7 天，每个指标 5 个点，便于前端趋势图联调） */
    private java.util.List<com.zing.doctor.icu.dto.LabTrend> buildMockLabTrends(IcuPatientBrief patient) {
        java.util.List<com.zing.doctor.icu.dto.LabTrend> out = new ArrayList<>();
        java.util.Map<String, java.math.BigDecimal> latest = new HashMap<>();
        latest.put("PCT", patient.getPct() == null ? new java.math.BigDecimal("0.5") : patient.getPct());
        latest.put("WBC", patient.getWbc() == null ? new java.math.BigDecimal("12.4") : patient.getWbc());
        latest.put("CRP", new java.math.BigDecimal("86"));
        latest.put("乳酸", new java.math.BigDecimal("2.1"));
        latest.put("肌酐", new java.math.BigDecimal("102"));
        for (java.util.Map.Entry<String, java.math.BigDecimal> e : latest.entrySet()) {
            com.zing.doctor.icu.dto.LabTrend trend = new com.zing.doctor.icu.dto.LabTrend();
            trend.setItemName(e.getKey());
            java.math.BigDecimal cur = e.getValue();
            for (int i = 4; i >= 0; i--) {
                com.zing.doctor.icu.dto.TrendPoint p = new com.zing.doctor.icu.dto.TrendPoint();
                p.setTime(java.time.LocalDateTime.now().minusDays(i)
                        .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")));
                p.setValue(cur.subtract(new java.math.BigDecimal(i)));
                trend.getSeries().add(p);
            }
            out.add(trend);
        }
        return out;
    }

    private List<LabTrend> buildMockTrends() {
        List<LabTrend> out = new ArrayList<>();
        out.add(trend("PCT", "08-28 08:00", "0.32", "08-30 08:00", "0.18", "09-01 08:00", "0.13"));
        out.add(trend("WBC", "08-28 08:00", "13.2", "08-30 08:00", "10.1", "09-01 08:00", "7.4"));
        out.add(trend("CRP", "08-28 08:00", "128", "08-30 08:00", "52", "09-01 08:00", "9.4"));
        out.add(trend("乳酸", "08-28 08:00", "2.6", "09-01 08:00", "1.0"));
        out.add(trend("肌酐", "08-28 08:00", "76", "08-30 08:00", "62", "09-01 08:00", "54"));
        return out;
    }

    private LabTrend trend(String name, String... pairs) {
        LabTrend t = new LabTrend();
        t.setItemName(name);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            TrendPoint p = new TrendPoint();
            p.setTime(pairs[i]);
            p.setValue(new BigDecimal(pairs[i + 1]));
            t.getSeries().add(p);
        }
        return t;
    }

    private List<IcuPatientBrief> buildMock() {
        List<IcuPatientBrief> list = new ArrayList<>();

        IcuPatientBrief p1 = base("P1001", "NO20260901001", "张**", 68, "男", "ICU-1", "03 床",
                "脓毒性休克", true, true, true, true,
                new BigDecimal("8.6"), new BigDecimal("18.2"), new BigDecimal("39.2"),
                LocalDateTime.now().minusHours(1), "高风险");

        IcuPatientBrief p2 = base("P1002", "NO20260901002", "李**", 54, "女", "ICU-1", "05 床",
                "医院获得性肺炎（HAP）", false, false, true, false,
                new BigDecimal("2.3"), new BigDecimal("14.6"), new BigDecimal("38.5"),
                LocalDateTime.now().minusHours(6), "中风险");

        IcuPatientBrief p3 = base("P1003", "NO20260901003", "王**", 76, "男", "ICU-2", "01 床",
                "腹腔感染", false, true, true, true,
                new BigDecimal("5.1"), new BigDecimal("16.8"), new BigDecimal("38.9"),
                LocalDateTime.now().minusDays(1), "高风险");

        list.add(p1);
        list.add(p2);
        list.add(p3);
        return list;
    }

    private IcuPatientBrief base(String patientId, String patientNo, String name, Integer age,
                                 String gender, String department, String bedNo,
                                 String infectionType, boolean shock, boolean mrsa, boolean mdr,
                                 boolean fungal, BigDecimal pct, BigDecimal wbc,
                                 BigDecimal temperature, LocalDateTime abxStart, String riskLevel) {
        IcuPatientBrief p = new IcuPatientBrief();
        p.setPatientId(patientId);
        p.setPatientNo(patientNo);
        p.setName(name);
        p.setAge(age);
        p.setGender(gender);
        p.setDepartment(department);
        p.setBedNo(bedNo);
        p.setInfectionType(infectionType);
        p.setSepticShock(shock);
        p.setMrsaRisk(mrsa);
        p.setMdrRisk(mdr);
        p.setFungalRisk(fungal);
        p.setPct(pct);
        p.setWbc(wbc);
        p.setTemperature(temperature);
        p.setAbxStartTime(abxStart);
        p.setRiskLevel(riskLevel);
        return p;
    }
}
