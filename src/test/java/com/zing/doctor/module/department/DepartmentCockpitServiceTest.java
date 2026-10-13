package com.zing.doctor.module.department;

import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.module.apache2.mapper.Apache2ScoreRecordMapper;
import com.zing.doctor.module.department.dto.CockpitSnapshot;
import com.zing.doctor.module.department.mapper.DepartmentCockpitMapper;
import com.zing.doctor.module.department.service.impl.DepartmentCockpitServiceImpl;
import com.zing.doctor.module.sofa.mapper.SofaScoreRecordMapper;
import com.zing.doctor.quality.service.QualityQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 科室驾驶舱快照口径（阶段1）。
 *
 * <p>这里钉住三件事：
 * <ol>
 *   <li><b>不虚构数据</b>：床位使用率恒为 PENDING_SOURCE（fact_bed 待接数据源）、
 *       在科为 0 时危重比例是 null 而不是 0% —— 「0% 危重」会被医生读成"很安全"；</li>
 *   <li><b>危重取并集不相加</b>：同一患者同时上 ECMO 和 CRRT，只算 1 个危重患者，
 *       四类各自相加会让卡片数字虚高；</li>
 *   <li><b>流转曲线连续</b>：SQL 只返回有数据的日期，缺失日期必须补 0，
 *       否则前端折线出现断点、被误读成"那天没人入科是数据丢了"。</li>
 * </ol>
 */
class DepartmentCockpitServiceTest {

    private DepartmentCockpitMapper cockpitMapper;
    private IcuPatientMapper icuPatientMapper;
    private IcuPatientService icuPatientService;
    private SofaScoreRecordMapper sofaMapper;
    private Apache2ScoreRecordMapper apache2Mapper;
    private QualityQueryService qualityQueryService;
    private DepartmentCockpitServiceImpl service;

    private static final String DAY_FMT = "yyyy-MM-dd";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern(DAY_FMT);

    @BeforeEach
    void setUp() {
        cockpitMapper = mock(DepartmentCockpitMapper.class);
        icuPatientMapper = mock(IcuPatientMapper.class);
        icuPatientService = mock(IcuPatientService.class);
        sofaMapper = mock(SofaScoreRecordMapper.class);
        apache2Mapper = mock(Apache2ScoreRecordMapper.class);
        qualityQueryService = mock(QualityQueryService.class);
        service = new DepartmentCockpitServiceImpl(cockpitMapper, icuPatientMapper, icuPatientService,
                sofaMapper, apache2Mapper, qualityQueryService);
    }

    private Map<String, Object> snapshotRow(int in, int todayIn, int todayOut) {
        Map<String, Object> row = new HashMap<>();
        row.put("in_count", in);
        row.put("today_in_count", todayIn);
        row.put("today_out_count", todayOut);
        return row;
    }

    @Test
    @DisplayName("床位使用率恒为 PENDING_SOURCE，不返回假百分比")
    void bedUsageIsPendingNotZero() {
        when(cockpitMapper.selectTodaySnapshot(anyString(), anyString(), anyString())).thenReturn(snapshotRow(89, 5, 7));
        when(icuPatientService.countOrganSupport(anyString())).thenReturn(Collections.emptyMap());
        when(icuPatientMapper.selectAllDepartments()).thenReturn(Collections.emptyList());

        CockpitSnapshot s = service.snapshot("20010231", 30);

        assertEquals("PENDING_SOURCE", s.getBedUsage().getStatus());
        assertNull(s.getBedUsage().getValue(), "没有可靠分母前不得给出床位比例");
        assertNotNull(s.getBedUsage().getNote());
    }

    @Test
    @DisplayName("在科为 0 时危重比例返回 UNKNOWN，不造 0%")
    void criticalRatioNullWhenNoInpatients() {
        when(cockpitMapper.selectTodaySnapshot(anyString(), anyString(), anyString())).thenReturn(snapshotRow(0, 0, 0));
        Map<String, Integer> support = new HashMap<>();
        support.put("ventilated", 0);
        support.put("vasopressor", 0);
        support.put("crrt", 0);
        support.put("ecmo", 0);
        when(icuPatientService.countOrganSupport(anyString())).thenReturn(support);
        when(icuPatientMapper.selectAllDepartments()).thenReturn(Collections.emptyList());

        CockpitSnapshot s = service.snapshot("", 30);

        assertEquals(0, s.getInCount().getValue());
        assertEquals("UNKNOWN", s.getCriticalRatio().getStatus());
        assertNull(s.getCriticalRatio().getValue(), "0 在科时不能算出 0% 危重，那会被读成很安全");
    }

    @Test
    @DisplayName("危重按患者取并集：ECMO+CRRT 同一患者只算 1 个")
    void criticalCountIsUnionNotSum() {
        when(cockpitMapper.selectTodaySnapshot(anyString(), anyString(), anyString())).thenReturn(snapshotRow(50, 5, 7));
        // countOrganSupport 与四个 mapper 必须是同一口径（真实环境同源），mock 值保持一致：
        // 患者 A 上 ECMO+CRRT+血管活性药、患者 B 上呼吸机
        Map<String, Integer> support = new HashMap<>();
        support.put("ventilated", 1);
        support.put("vasopressor", 1);
        support.put("crrt", 1);
        support.put("ecmo", 1);
        when(icuPatientService.countOrganSupport(anyString())).thenReturn(support);
        when(icuPatientMapper.selectAllDepartments()).thenReturn(Collections.emptyList());
        // 患者 A 同时上 ECMO、CRRT 和血管活性药；患者 B 上呼吸机
        when(icuPatientMapper.selectHandoverVasopressor(anyString())).thenReturn(
                Collections.singletonList(row("A")));
        when(icuPatientMapper.selectHandoverCrrtPatients(anyString())).thenReturn(Collections.singletonList("A"));
        when(icuPatientMapper.selectHandoverEcmoPatients(anyString())).thenReturn(Collections.singletonList("A"));
        when(icuPatientMapper.selectInpatients(anyString())).thenReturn(
                Arrays.asList(row("A"), row("B")));
        when(icuPatientMapper.selectLatestVentilatorParams(any())).thenReturn(
                Collections.singletonList(row("B")));

        CockpitSnapshot s = service.snapshot("20010231", 30);

        // 并集 = {A, B} = 2；若按四类相加会得到 3（CRRT 1 + ECMO 1 + 呼吸机 1）
        assertEquals(2, s.getCriticalCount().getValue(), "同一患者多器官支持只算一个危重患者");
        // 器官支持分项仍按各类自己的计数（工作台同口径）
        assertEquals(1, s.getCrrtCount().getValue());
        assertEquals(1, s.getEcmoCount().getValue());
        assertEquals(1, s.getVentilatedCount().getValue());
        assertEquals(1, s.getVasopressorCount().getValue());
        // 比例 = 2/50
        assertEquals(0.04, s.getCriticalRatio().getValue(), 0.0001);
    }

    @Test
    @DisplayName("流转趋势补零：无数据日期也要出现，曲线不断")
    void dailyFlowFillsMissingDays() {
        when(cockpitMapper.selectTodaySnapshot(anyString(), anyString(), anyString())).thenReturn(snapshotRow(10, 1, 1));
        when(icuPatientService.countOrganSupport(anyString())).thenReturn(Collections.emptyMap());
        when(icuPatientMapper.selectAllDepartments()).thenReturn(Collections.emptyList());

        // 只返回今天一天有数据，其余天缺失
        String today = LocalDate.now().format(DAY);
        Map<String, Object> todayRow = new HashMap<>();
        todayRow.put("day", Timestamp.valueOf(LocalDate.now().atTime(0, 0)));
        todayRow.put("cnt", 5);
        when(cockpitMapper.selectDailyInCount(anyString(), anyString(), anyString()))
                .thenReturn(Collections.singletonList(todayRow));
        when(cockpitMapper.selectDailyOutCount(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        CockpitSnapshot s = service.snapshot("20010231", 30);

        List<CockpitSnapshot.DayFlow> flow = s.getDailyFlow().getValue();
        assertEquals(30, flow.size(), "30 天窗口必须有 30 个点，缺日期要补零");
        assertEquals(today, flow.get(flow.size() - 1).getDay());
        assertEquals(5, flow.get(flow.size() - 1).getInCount());
        assertEquals(0, flow.get(0).getInCount(), "无数据日期补 0 而不是从结果里消失");
        assertTrue(flow.stream().allMatch(d -> d.getDay().matches("\\d{4}-\\d{2}-\\d{2}")));
    }

    @Test
    @DisplayName("平均住院日只统计已出科患者，且无数据时是 EMPTY 而非 0")
    void avgStayDaysEmptyWhenNoDischarge() {
        when(cockpitMapper.selectTodaySnapshot(anyString(), anyString(), anyString())).thenReturn(snapshotRow(10, 1, 1));
        when(icuPatientService.countOrganSupport(anyString())).thenReturn(Collections.emptyMap());
        when(icuPatientMapper.selectAllDepartments()).thenReturn(Collections.emptyList());
        when(cockpitMapper.selectDailyInCount(anyString(), anyString(), anyString())).thenReturn(Collections.emptyList());
        when(cockpitMapper.selectDailyOutCount(anyString(), anyString(), anyString())).thenReturn(Collections.emptyList());
        Map<String, Object> empty = new HashMap<>();
        empty.put("avg_stay_days", null);
        empty.put("patient_count", 0);
        when(cockpitMapper.selectAvgStayDays(anyString(), anyString(), anyString())).thenReturn(empty);

        CockpitSnapshot s = service.snapshot("", 30);

        assertEquals("EMPTY", s.getAvgStayDays().getStatus());
        assertNull(s.getAvgStayDays().getValue());
        assertEquals(0, s.getAvgStayPatientCount());
    }

    @Test
    @DisplayName("平均住院日有值时保留 1 位小数")
    void avgStayDaysRounded() {
        when(cockpitMapper.selectTodaySnapshot(anyString(), anyString(), anyString())).thenReturn(snapshotRow(10, 1, 1));
        when(icuPatientService.countOrganSupport(anyString())).thenReturn(Collections.emptyMap());
        when(icuPatientMapper.selectAllDepartments()).thenReturn(Collections.emptyList());
        when(cockpitMapper.selectDailyInCount(anyString(), anyString(), anyString())).thenReturn(Collections.emptyList());
        when(cockpitMapper.selectDailyOutCount(anyString(), anyString(), anyString())).thenReturn(Collections.emptyList());
        Map<String, Object> row = new HashMap<>();
        row.put("avg_stay_days", new BigDecimal("4.8642"));
        row.put("patient_count", 50);
        when(cockpitMapper.selectAvgStayDays(anyString(), anyString(), anyString())).thenReturn(row);

        CockpitSnapshot s = service.snapshot("", 30);

        assertEquals(4.9, s.getAvgStayDays().getValue(), 0.0001);
        assertEquals(50, s.getAvgStayPatientCount());
    }

    @Test
    @DisplayName("区块级容错：快照查询失败只让该块 UNKNOWN，不影响流转与器官支持")
    void snapshotFailureIsolated() {
        when(cockpitMapper.selectTodaySnapshot(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("连接超时"));
        Map<String, Integer> support = new HashMap<>();
        support.put("ventilated", 3);
        support.put("vasopressor", 2);
        support.put("crrt", 1);
        support.put("ecmo", 0);
        when(icuPatientService.countOrganSupport(anyString())).thenReturn(support);
        when(icuPatientMapper.selectAllDepartments()).thenReturn(Collections.emptyList());
        when(cockpitMapper.selectDailyInCount(anyString(), anyString(), anyString())).thenReturn(Collections.emptyList());
        when(cockpitMapper.selectDailyOutCount(anyString(), anyString(), anyString())).thenReturn(Collections.emptyList());

        CockpitSnapshot s = service.snapshot("20010231", 7);

        assertEquals("UNKNOWN", s.getInCount().getStatus(), "快照失败应标 UNKNOWN 而不是伪装成 0");
        assertNull(s.getInCount().getValue());
        // 流转与器官支持不受影响
        assertEquals("OK", s.getDailyFlow().getStatus());
        assertEquals(7, s.getDailyFlow().getValue().size());
        assertEquals("OK", s.getVentilatedCount().getStatus());
        assertEquals(3, s.getVentilatedCount().getValue());
        assertEquals("OK", s.getVasopressorCount().getStatus());
        assertEquals(2, s.getVasopressorCount().getValue());
        assertFalse(s.getNotes().isEmpty(), "口径说明必须随快照返回，前端要在页面上展示");
    }

    @Test
    @DisplayName("评分分布与用户选择的趋势窗口保持一致")
    void scoreDistributionUsesSelectedRange() {
        when(cockpitMapper.selectTodaySnapshot(anyString(), anyString(), anyString()))
                .thenReturn(snapshotRow(10, 1, 1));
        when(icuPatientService.countOrganSupport(anyString())).thenReturn(Collections.emptyMap());
        when(icuPatientMapper.selectAllDepartments()).thenReturn(Collections.emptyList());
        when(cockpitMapper.selectDailyInCount(anyString(), anyString(), anyString())).thenReturn(Collections.emptyList());
        when(cockpitMapper.selectDailyOutCount(anyString(), anyString(), anyString())).thenReturn(Collections.emptyList());
        when(cockpitMapper.selectAvgStayDays(anyString(), anyString(), anyString()))
                .thenReturn(Collections.singletonMap("patient_count", 0));

        service.snapshot("20010231", 7);

        String expectedStart = LocalDate.now().minusDays(6).atTime(0, 0)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        verify(sofaMapper).selectLatestPerEpisode(eq("20010231"), eq(expectedStart), anyString());
        verify(apache2Mapper).selectLatestPerEpisode(eq("20010231"), eq(expectedStart), anyString());
    }

    @Test
    @DisplayName("days 参数夹紧到 1~90，防止误传超大区间把库拖垮")
    void daysClamped() {
        when(cockpitMapper.selectTodaySnapshot(anyString(), anyString(), anyString())).thenReturn(snapshotRow(1, 0, 0));
        when(icuPatientService.countOrganSupport(anyString())).thenReturn(Collections.emptyMap());
        when(icuPatientMapper.selectAllDepartments()).thenReturn(Collections.emptyList());
        when(cockpitMapper.selectDailyInCount(anyString(), anyString(), anyString())).thenReturn(Collections.emptyList());
        when(cockpitMapper.selectDailyOutCount(anyString(), anyString(), anyString())).thenReturn(Collections.emptyList());

        assertEquals(90, service.snapshot("", 999).getDailyFlow().getValue().size());
        assertEquals(1, service.snapshot("", 0).getDailyFlow().getValue().size());
    }

    private static Map<String, Object> row(String patientId) {
        Map<String, Object> r = new HashMap<>();
        r.put("patient_id", patientId);
        return r;
    }
}
