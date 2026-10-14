package com.zing.doctor.module.vitals;

import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.module.vitals.dto.VitalsResponse;
import com.zing.doctor.module.vitals.service.impl.VitalsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 生命体征趋势口径（docs/25 §1）。
 *
 * <p>钉住三件事：
 * <ol>
 *   <li><b>截断探测</b>：SQL 倒序取最新 2001 条，service 在 &gt;2000 条时置 truncated、
 *       去掉最早一条并反转回升序——只保留最新记录，避免 7 天高频患者丢最新数据；</li>
 *   <li><b>查询失败 → UNKNOWN，窗口内无记录 → NO_RECORD</b>，两者不得混同；</li>
 *   <li><b>按指标分组</b>：同一 item_code 归到对应指标序列，明细保留非数值记录。</li>
 * </ol>
 */
class VitalsServiceTest {

    private IcuPatientMapper icuPatientMapper;
    private IcuPatientService icuPatientService;
    private VitalsServiceImpl service;

    @BeforeEach
    void setUp() {
        icuPatientMapper = mock(IcuPatientMapper.class);
        icuPatientService = mock(IcuPatientService.class);
        service = new VitalsServiceImpl(icuPatientMapper, icuPatientService);
    }

    private void patientWith() {
        WorkbenchPatient p = new WorkbenchPatient();
        p.setName("测试患者");
        p.setInHospitalNo("H001");
        p.setBedNo("S1");
        when(icuPatientService.getWorkbenchPatientAny(anyString())).thenReturn(p);
    }

    private Map<String, Object> vit(String code, String value, String time) {
        Map<String, Object> r = new HashMap<>();
        r.put("item_code", code);
        r.put("item_value", value);
        r.put("item_time", Timestamp.valueOf(LocalDateTime.parse(time)));
        r.put("item_name", "心率");
        r.put("staff_name", "护士A");
        return r;
    }

    @Test
    @DisplayName("窗口内记录超过2000条：truncated=true、只保留最新2000条、回升序")
    void overLimitSetsTruncatedAndKeepsLatest() {
        patientWith();
        // SQL 倒序返回 2001 条（最早的在末尾）；全部是心率 oi_hr
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 2001; i >= 1; i--) {
            rows.add(vit("oi_hr", String.valueOf(60 + (i % 20)), String.format("2026-10-14T%02d:00:00", i % 24)));
        }
        when(icuPatientMapper.selectVitalsByCodes(anyString(), any(), anyString(), anyString())).thenReturn(rows);

        VitalsResponse resp = service.trend("P1", 168);

        assertTrue(Boolean.TRUE.equals(resp.getTruncated()), "超限必须置 truncated");
        assertNotNull(resp.getTruncatedNote());
        assertEquals(2000, resp.getRecordCount(), "应只保留最新 2000 条");
        VitalsResponse.Series hr = find(resp, "hr");
        assertEquals(2000, hr.getPoints().size());
        // 反转回升序：首点时间应早于末点时间
        String first = hr.getPoints().get(0).getTime();
        String last = hr.getPoints().get(hr.getPoints().size() - 1).getTime();
        assertTrue(first.compareTo(last) < 0, "点数应回升序，首点早于末点");
    }

    @Test
    @DisplayName("未超2000条：truncated=false，顺序保持升序")
    void underLimitNoTruncation() {
        patientWith();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 10; i >= 1; i--) { // 倒序传入
            rows.add(vit("oi_hr", "70", String.format("2026-10-14T%02d:00:00", i)));
        }
        when(icuPatientMapper.selectVitalsByCodes(anyString(), any(), anyString(), anyString())).thenReturn(rows);

        VitalsResponse resp = service.trend("P1", 24);

        assertFalse(Boolean.TRUE.equals(resp.getTruncated()), "未超限不置 truncated");
        assertEquals(10, resp.getRecordCount());
        assertEquals("AVAILABLE", find(resp, "hr").getDataStatus());
    }

    @Test
    @DisplayName("查询异常：全部序列 UNKNOWN/查询失败，不伪装成无记录")
    void queryFailureIsUnknown() {
        patientWith();
        when(icuPatientMapper.selectVitalsByCodes(anyString(), any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("连接超时"));

        VitalsResponse resp = service.trend("P1", 24);

        assertEquals(10, resp.getSeries().size(), "10 个指标序列齐全");
        for (VitalsResponse.Series s : resp.getSeries()) {
            assertEquals("UNKNOWN", s.getDataStatus());
            assertEquals("查询失败", s.getNote());
        }
    }

    @Test
    @DisplayName("窗口内无记录：NO_RECORD 而非 UNKNOWN，明细空")
    void noRecordIsNoRecord() {
        patientWith();
        when(icuPatientMapper.selectVitalsByCodes(anyString(), any(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        VitalsResponse resp = service.trend("P1", 24);

        assertEquals(0, resp.getRecordCount());
        for (VitalsResponse.Series s : resp.getSeries()) {
            assertEquals("NO_RECORD", s.getDataStatus());
        }
    }

    @Test
    @DisplayName("非数值记录（如\"测不出\"）不进曲线但保留在明细")
    void nonNumericKeptInRecordsNotInPoints() {
        patientWith();
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(vit("oi_hr", "88", "2026-10-14T08:00:00"));
        Map<String, Object> bad = vit("oi_hr", "测不出", "2026-10-14T09:00:00");
        rows.add(bad);
        when(icuPatientMapper.selectVitalsByCodes(anyString(), any(), anyString(), anyString())).thenReturn(rows);

        VitalsResponse resp = service.trend("P1", 24);

        assertEquals(1, find(resp, "hr").getPoints().size(), "非数值不计入曲线");
        assertEquals(2, resp.getRecords().size(), "明细保留全部记录");
    }

    @Test
    @DisplayName("无创/有创血压按编码分列，不合并到同一序列")
    void invasiveAndNoninvasiveBpSeparate() {
        patientWith();
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(vit("oi_wcssy", "120", "2026-10-14T08:00:00"));   // 无创收缩压
        rows.add(vit("oi_ycssy", "118", "2026-10-14T08:00:00"));   // 有创收缩压
        when(icuPatientMapper.selectVitalsByCodes(anyString(), any(), anyString(), anyString())).thenReturn(rows);

        VitalsResponse resp = service.trend("P1", 24);

        VitalsResponse.Series sbp = find(resp, "sbp");
        VitalsResponse.Series sbpInv = find(resp, "sbp_inv");
        assertEquals(1, sbp.getPoints().size());
        assertEquals(1, sbpInv.getPoints().size());
        assertEquals("无创", sbp.getSource());
        assertEquals("有创", sbpInv.getSource());
    }

    private VitalsResponse.Series find(VitalsResponse resp, String key) {
        return resp.getSeries().stream()
                .filter(s -> key.equals(s.getKey()))
                .findFirst()
                .orElse(null);
    }
}
