package com.zing.doctor.module.fluid;

import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.module.fluid.dto.FluidBalanceResponse;
import com.zing.doctor.module.fluid.service.impl.FluidBalanceServiceImpl;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 体液平衡口径（docs/25 §2，评估修复后的语义）。
 *
 * <p>钉住三件事：
 * <ol>
 *   <li><b>医嘱入量走任意时间窗</b>：调用 selectAdviceIntakeByWindow（不再固定
 *       07:00~次日07:00），与出量/尿量/其他入量同一窗口，四栏可比；</li>
 *   <li><b>四栏互斥</b>：出量用 selectFluidOutputExclUrine（排除 ii_nl），尿量单列，
 *       两者不重复计；</li>
 *   <li><b>查询成功但无记录 → NO_RECORD，不入 0</b>，且不跨来源合计净平衡。</li>
 * </ol>
 */
class FluidBalanceServiceTest {

    private IcuPatientMapper icuPatientMapper;
    private IcuPatientService icuPatientService;
    private FluidBalanceServiceImpl service;

    @BeforeEach
    void setUp() {
        icuPatientMapper = mock(IcuPatientMapper.class);
        icuPatientService = mock(IcuPatientService.class);
        service = new FluidBalanceServiceImpl(icuPatientMapper, icuPatientService);
    }

    private void patientWith() {
        WorkbenchPatient p = new WorkbenchPatient();
        p.setName("测试患者");
        p.setInHospitalNo("H001");
        when(icuPatientService.getWorkbenchPatientAny(anyString())).thenReturn(p);
    }

    private Map<String, Object> row(String value, String time, String name, String unit) {
        Map<String, Object> r = new HashMap<>();
        r.put("item_value", value);
        r.put("item_time", Timestamp.valueOf(LocalDateTime.parse(time)));
        r.put("item_name", name);
        r.put("item_unit", unit);
        r.put("staff_name", "护士A");
        return r;
    }

    @Test
    @DisplayName("医嘱入量走任意时间窗方法（selectAdviceIntakeByWindow），四栏同窗口")
    void adviceIntakeUsesWindowMethod() {
        patientWith();
        when(icuPatientMapper.selectFluidOutputExclUrine(anyString(), anyString(), anyString()))
                .thenReturn(java.util.Collections.singletonList(row("100", "2026-10-14T10:00:00", "引流液", "ml")));
        when(icuPatientMapper.selectUrineByPatient(anyString(), anyString(), anyString()))
                .thenReturn(java.util.Collections.singletonList(row("1200", "2026-10-14T10:00:00", "尿量", "ml")));
        when(icuPatientMapper.selectAdviceIntakeByWindow(anyString(), anyString(), anyString()))
                .thenReturn(java.util.Collections.singletonList(row("500", "2026-10-14T10:00:00", "0.9%氯化钠注射液", "ml")));
        when(icuPatientMapper.selectOtherIntakeByPatient(anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        FluidBalanceResponse resp = service.balance("P1", 24);

        verify(icuPatientMapper).selectAdviceIntakeByWindow(anyString(), anyString(), anyString());
        FluidBalanceResponse.Source advice = src(resp, "advice_in");
        assertEquals("AVAILABLE", advice.getDataStatus());
        assertEquals(500.0, advice.getTotal());
        // 其他入量无记录 → NO_RECORD，不是 0
        assertEquals("NO_RECORD", src(resp, "other_in").getDataStatus());
    }

    @Test
    @DisplayName("出量排除尿量：非尿出量与尿量互斥，各自独立合计")
    void outputExcludesUrine() {
        patientWith();
        when(icuPatientMapper.selectFluidOutputExclUrine(anyString(), anyString(), anyString()))
                .thenReturn(java.util.Collections.singletonList(row("215", "2026-10-14T10:00:00", "引流液", "ml")));
        when(icuPatientMapper.selectUrineByPatient(anyString(), anyString(), anyString()))
                .thenReturn(java.util.Collections.singletonList(row("3020", "2026-10-14T10:00:00", "尿量", "ml")));
        when(icuPatientMapper.selectAdviceIntakeByWindow(anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());
        when(icuPatientMapper.selectOtherIntakeByPatient(anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        FluidBalanceResponse resp = service.balance("P1", 24);

        assertEquals(215.0, src(resp, "output").getTotal(), "出量应排除尿量（ii_nl）");
        assertEquals(3020.0, src(resp, "urine").getTotal(), "尿量单列");
    }

    @Test
    @DisplayName("同来源内按小时分桶汇总；单位不强制转换，原样展示")
    void bucketsByHourKeepUnit() {
        patientWith();
        when(icuPatientMapper.selectFluidOutputExclUrine(anyString(), anyString(), anyString()))
                .thenReturn(java.util.Arrays.asList(
                        row("100", "2026-10-14T10:00:00", "引流液", "ml"),
                        row("50", "2026-10-14T11:00:00", "引流液", "ml")));
        when(icuPatientMapper.selectUrineByPatient(anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());
        when(icuPatientMapper.selectAdviceIntakeByWindow(anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());
        when(icuPatientMapper.selectOtherIntakeByPatient(anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        FluidBalanceResponse resp = service.balance("P1", 24);

        FluidBalanceResponse.Source out = src(resp, "output");
        assertEquals(2, out.getBuckets().size(), "10点、11点两个小时桶");
        assertEquals(150.0, out.getTotal());
        assertEquals("ml", out.getRows().get(0).getUnit());
        assertTrue(out.getBuckets().stream().anyMatch(b -> b.getValue() == 100.0));
        assertTrue(out.getBuckets().stream().anyMatch(b -> b.getValue() == 50.0));
    }

    private FluidBalanceResponse.Source src(FluidBalanceResponse resp, String key) {
        return resp.getSources().stream()
                .filter(s -> key.equals(s.getKey()))
                .findFirst()
                .orElse(null);
    }
}
