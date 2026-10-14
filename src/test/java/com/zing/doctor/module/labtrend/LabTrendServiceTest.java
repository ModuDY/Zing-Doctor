package com.zing.doctor.module.labtrend;

import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.module.labtrend.dto.LabTrendResponse;
import com.zing.doctor.module.labtrend.service.impl.LabTrendServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 检验趋势口径（docs/25 §4/§5）。
 *
 * <p>钉住三件事：
 * <ol>
 *   <li><b>按名称匹配而非只按 code</b>：生产库 ALT 有两套编码，只按 code 会漏数。
 *       这里用"项目名包含关键词"的匹配方式验证；</li>
 *   <li><b>「谷草/谷丙」组合项不纳入</b>：单字段双值（"35/28"），拆分规则未经临床确认；</li>
 *   <li><b>缺失不造 0 / 查询失败不伪装</b>：无记录是 NO_RECORD，查询异常是 UNKNOWN，
 *       两者都不能显示成 0 或"正常"。</li>
 * </ol>
 */
class LabTrendServiceTest {

    private IcuPatientMapper icuPatientMapper;
    private IcuPatientService icuPatientService;
    private LabTrendServiceImpl service;

    @BeforeEach
    void setUp() {
        icuPatientMapper = mock(IcuPatientMapper.class);
        icuPatientService = mock(IcuPatientService.class);
        service = new LabTrendServiceImpl(icuPatientMapper, icuPatientService);
    }

    private void patientWith(String inHospitalNo) {
        WorkbenchPatient p = new WorkbenchPatient();
        p.setInHospitalNo(inHospitalNo);
        p.setName("测试患者");
        when(icuPatientService.getWorkbenchPatientAny(anyString())).thenReturn(p);
    }

    private Map<String, Object> lab(String itemName, String value, String checkTime) {
        Map<String, Object> r = new HashMap<>();
        r.put("item_name", itemName);
        r.put("item_value", value);
        r.put("check_time", Timestamp.valueOf(LocalDateTime.parse(checkTime)));
        return r;
    }

    @Test
    @DisplayName("肝功能：ALT 两套编码都能按名称匹配到，合并为一条序列")
    void liverMatchesByNameNotOnlyByCode() {
        patientWith("H001");
        when(icuPatientMapper.selectLabsByKeywordsAndRange(anyString(), anyString(), anyString(), any())).thenReturn(Arrays.asList(
                lab("丙氨酸氨基转移酶", "35", "2026-10-11T08:00:00"),
                lab("*丙氨酸氨基转移酶", "42", "2026-10-12T08:00:00"),   // 带 * 前缀 + 另一套编码的数据
                lab("天冬氨酸氨基转氨酶", "28", "2026-10-12T08:00:00"),
                lab("谷草/谷丙", "35/28", "2026-10-12T08:00:00"),       // 组合项：必须跳过
                lab("白细胞计数", "11.2", "2026-10-12T08:00:00")        // 不相关项目
        ));

        LabTrendResponse resp = service.trend("P1", "liver", 168);

        LabTrendResponse.Series alt = findSeries(resp, "ALT");
        assertNotNull(alt, "肝功能必须含 ALT 序列");
        assertEquals(2, alt.getPoints().size(), "两套编码的 ALT 都应被名称匹配到");
        assertEquals("AVAILABLE", alt.getDataStatus());
        // 组合项不得进入任何序列
        assertTrue(resp.getRecords().stream().noneMatch(r -> r.getItemName().contains("谷草/谷丙")),
                "「谷草/谷丙」单字段双值，拆分规则未确认，不得纳入");
    }

    @Test
    @DisplayName("无记录是 NO_RECORD，不是 0，也不是 UNKNOWN")
    void noRecordIsNoRecord() {
        patientWith("H001");
        when(icuPatientMapper.selectLabsByKeywordsAndRange(anyString(), anyString(), anyString(), any()))
                .thenReturn(Collections.emptyList());

        LabTrendResponse resp = service.trend("P1", "liver", 168);

        LabTrendResponse.Series alt = findSeries(resp, "ALT");
        assertEquals("NO_RECORD", alt.getDataStatus(),
                "查询成功但窗口内无记录 → NO_RECORD（不得伪装成 AVAILABLE，也不该说成查询失败）");
    }

    @Test
    @DisplayName("查询异常是 UNKNOWN 而非空列表（前端据此提示失败，不当成没数据）")
    void queryFailureIsUnknown() {
        patientWith("H001");
        when(icuPatientMapper.selectLabsByKeywordsAndRange(anyString(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("连接超时"));

        LabTrendResponse resp = service.trend("P1", "liver", 168);

        LabTrendResponse.Series alt = findSeries(resp, "ALT");
        assertEquals("UNKNOWN", alt.getDataStatus());
        assertEquals("查询失败", alt.getNote());
    }

    @Test
    @DisplayName("凝血：D-二聚体纳入但标注低覆盖属性由前端说明；序列齐全")
    void coagHasAllSeries() {
        patientWith("H001");
        when(icuPatientMapper.selectLabsByKeywordsAndRange(anyString(), anyString(), anyString(), any())).thenReturn(Arrays.asList(
                lab("*血小板计数", "180", "2026-10-11T08:00:00"),
                lab("*血浆凝血酶原时间(PT)", "13.5", "2026-10-11T08:00:00"),
                lab("床边血浆D-二聚体测定(ICU)", "2.1", "2026-10-11T08:00:00")
        ));

        LabTrendResponse resp = service.trend("P1", "coag", 168);

        assertEquals(1, resp.getGroups().size());
        assertEquals(6, resp.getGroups().get(0).getSeries().size(), "凝血应有 6 个序列");
        LabTrendResponse.Series plt = findSeries(resp, "PLT");
        assertEquals(1, plt.getPoints().size());
        assertEquals("AVAILABLE", plt.getDataStatus());
        LabTrendResponse.Series inr = findSeries(resp, "INR");
        assertEquals("NO_RECORD", inr.getDataStatus(), "窗口内无 INR 记录时应为 NO_RECORD");
    }

    @Test
    @DisplayName("住院号缺失时全部序列 UNKNOWN，不静默返回空")
    void missingInHospitalNoIsUnknown() {
        WorkbenchPatient p = new WorkbenchPatient();
        when(icuPatientService.getWorkbenchPatientAny(anyString())).thenReturn(p);

        LabTrendResponse resp = service.trend("P1", "liver", 168);

        assertEquals("UNKNOWN", findSeries(resp, "ALT").getDataStatus());
        assertNull(findSeries(resp, "ALT").getPoints().isEmpty() ? null : findSeries(resp, "ALT").getPoints());
    }

    private LabTrendResponse.Series findSeries(LabTrendResponse resp, String prefix) {
        return resp.getGroups().stream()
                .flatMap(g -> g.getSeries().stream())
                .filter(s -> s.getName().startsWith(prefix))
                .findFirst()
                .orElse(null);
    }

    @Test
    @DisplayName("type 非法时按肝功能兜底而非报错（前端只传 liver/coag）")
    void unknownTypeFallsBackToLiver() {
        patientWith("H001");
        when(icuPatientMapper.selectLabsByKeywordsAndRange(anyString(), anyString(), anyString(), any()))
                .thenReturn(Collections.emptyList());
        LabTrendResponse resp = service.trend("P1", "whatever", 168);
        assertNotNull(findSeries(resp, "ALT"), "未知 type 应按肝功能处理");
    }
}
