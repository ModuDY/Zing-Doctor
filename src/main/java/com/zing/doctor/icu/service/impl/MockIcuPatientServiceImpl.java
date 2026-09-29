package com.zing.doctor.icu.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.zing.doctor.common.BizException;
import com.zing.doctor.icu.dto.IcuPatientAssessment;
import com.zing.doctor.icu.dto.IcuPatientBrief;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.dto.LabTrend;
import com.zing.doctor.icu.dto.TrendPoint;
import com.zing.doctor.icu.service.IcuPatientService;
import lombok.extern.slf4j.Slf4j;
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
 * ICU 数据 Mock 实现（联调用，非默认）。
 *
 * <p>用于在 ICU 表结构/数据源就绪前打通「外链打开 → 列表 → 决策详情 → 方案推荐」全流程，
 * 仅用于联调。默认已切换为 SQL 版本，本类只有在显式配置
 * {@code zing.doctor.icu-data-provider=mock} 时才会生效，且启动时会打 WARN。
 */
@Slf4j
@Service
// 不再 matchIfMissing：Mock 必须被显式指定才会生效。
// 否则一旦配置源读不到该键（或被人删掉），系统会静默退回假数据，页面照常渲染且无任何报错。
@ConditionalOnProperty(name = "zing.doctor.icu-data-provider", havingValue = "mock")
public class MockIcuPatientServiceImpl implements IcuPatientService {

    private final List<IcuPatientBrief> mockPatients = buildMock();

    public MockIcuPatientServiceImpl() {
        log.warn("[ICU数据源] 当前使用 Mock 示例数据（zing.doctor.icu-data-provider=mock）："
                + "疑似感染列表 / 决策详情 / PKPD / 职工检索返回的都是内置假数据，不是真实 ICU 数据。"
                + "生产请设为 sql（默认已是 sql，显式写 mock 才会走到这里）。");
    }

    @Override
    public List<WorkbenchPatient> listInpatients(String departCode) {
        List<WorkbenchPatient> rows = new ArrayList<>();
        for (IcuPatientBrief source : mockPatients) {
            WorkbenchPatient patient = new WorkbenchPatient();
            patient.setPatientId(source.getPatientId());
            String no = source.getPatientNo();
            patient.setInHospitalNo(no);
            patient.setPatientNo(no == null || no.length() <= 4 ? "****" : no.substring(0, 3) + "***" + no.substring(no.length() - 2));
            String name = source.getName();
            patient.setName(name == null || name.isEmpty() ? "未知" : (name.contains("*") ? name : name.substring(0, 1) + "*"));
            patient.setAge(source.getAge());
            patient.setGender(source.getGender());
            // mock 数据里只有病区名、没有 sys_depart.org_code，故科室过滤在此退化为不过滤。
            // 真实环境走 SqlIcuPatientServiceImpl，由 pi.depart_code 提供。
            patient.setDepartCode(null);
            patient.setWardName(source.getDepartment());
            patient.setBedNo(source.getBedNo());
            patient.setInDepartmentTime(null);
            patient.setIcuDays(null);
            rows.add(patient);
        }
        return rows;
    }
    /**
     * Mock 数据不区分科室（假数据里没有 depart_code），科室过滤已在调用方校验过，
     * 这里保持原样返回全量，避免演示环境因科室为空而列表空白。
     */
    @Override
    public List<IcuPatientBrief> listSuspectInfections(String departCode) {
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
