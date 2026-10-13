package com.zing.doctor.icu.service;

import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.icu.service.impl.SqlIcuPatientServiceImpl;
import com.zing.doctor.module.antibiotic.service.AbxDrugRecognizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 历史患者（已出科）查阅的取数口径。
 *
 * <p>这里钉住三件事：
 * <ol>
 *   <li><b>只读能查、写不能写</b>：{@code getWorkbenchPatientAny} 走不限在科的查询，
 *       而 {@code getWorkbenchPatient}（在科口径，写入口用它）必须仍然只查在科 ——
 *       否则会往已出科的历史病历里写进新查房记录；</li>
 *   <li><b>出科信息要带出来</b>：{@code inDepart=false} + 出科时间，否则前端无法区分
 *       「在科」和「已出科」，医生会误以为看到的是当前状态；</li>
 *   <li><b>历史列表不查诊断</b>：一次可能返回几百行，逐行查诊断表就是 N+1，
 *       打开历史页会变成全院级大查询。</li>
 * </ol>
 */
class HistoryPatientLookupTest {

    private IcuPatientMapper mapper;
    private IcuPatientService service;

    @BeforeEach
    void setUp() {
        mapper = mock(IcuPatientMapper.class);
        service = new SqlIcuPatientServiceImpl(mapper, mock(AbxDrugRecognizer.class));
    }

    /** 一行 patient_info，已出科：is_in_depart=0 且有 out_depart_time。 */
    private Map<String, Object> dischargedRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("patient_id", "2109431063612854274");
        row.put("in_hospital_no", "1000176546004");
        row.put("name", "黄行龙");
        row.put("age", 73);
        row.put("gender", "男");
        row.put("depart_code", "20010231");
        row.put("depart_name", "心血管内科");
        row.put("ward_name", "24U区");
        row.put("bed_no", "24U01");
        row.put("in_depart_time", LocalDateTime.of(2026, 10, 12, 7, 27, 10));
        row.put("out_depart_time", LocalDateTime.of(2026, 10, 13, 10, 18, 45));
        row.put("is_in_depart", 0);
        row.put("charge_doctor_name", "余蔚菁");
        row.put("weight", 71);
        return row;
    }

    @Test
    @DisplayName("只读口径能查到已出科患者，并带出出科时间与在科标记")
    void anyLookupReturnsDischargedPatient() {
        when(mapper.selectInpatientByIdAny("2109431063612854274")).thenReturn(dischargedRow());
        when(mapper.selectDiagnoses("2109431063612854274")).thenReturn(Collections.emptyList());

        WorkbenchPatient p = service.getWorkbenchPatientAny("2109431063612854274");

        assertNotNull(p, "已出科患者应能被查到，否则出院后复盘、死亡病例讨论都做不了");
        assertFalse(p.getInDepart(), "is_in_depart=0 应映射为 inDepart=false");
        assertEquals(LocalDateTime.of(2026, 10, 13, 10, 18, 45), p.getOutDepartmentTime());
        assertEquals("黄行龙", p.getName());
        assertEquals("心血管内科", p.getDepartName());
        assertEquals("余蔚菁", p.getAttendingDoctor());
    }

    @Test
    @DisplayName("在科口径不查不限在科的 SQL：写入口（保存/删除查房记录）仍只认在科患者")
    void inpatientLookupStaysInpatientOnly() {
        // 在科查询返回 null（患者已出科），任何口径都不应把它当成可写
        when(mapper.selectInpatientById(anyString())).thenReturn(null);

        assertNull(service.getWorkbenchPatient("2109431063612854274"),
                "在科口径查不到就应返回 null，写入口据此拒绝");
        verify(mapper, never()).selectInpatientByIdAny(anyString());
    }

    @Test
    @DisplayName("历史列表不逐行查诊断表，避免 N+1")
    void historyListSkipsDiagnosisQuery() {
        Map<String, Object> a = dischargedRow();
        Map<String, Object> b = dischargedRow();
        b.put("patient_id", "2108249806484901890");
        b.put("in_hospital_no", "1000702161001");
        b.put("out_depart_time", LocalDateTime.of(2026, 10, 13, 9, 50, 8));
        when(mapper.selectDischargedPatients(eq("20010231"), eq("黄"), anyInt()))
                .thenReturn(Arrays.asList(a, b));

        List<WorkbenchPatient> list = service.listDischargedPatients("20010231", "黄", 300);

        assertEquals(2, list.size());
        assertFalse(list.get(0).getInDepart());
        assertNotNull(list.get(0).getOutDepartmentTime());
        verify(mapper, never()).selectDiagnoses(anyString());
    }

    @Test
    @DisplayName("历史列表把关键字透传给 mapper，不在 Java 端二次过滤")
    void historyListPassesKeywordToMapper() {
        when(mapper.selectDischargedPatients(any(), any(), anyInt())).thenReturn(Collections.emptyList());

        assertTrue(service.listDischargedPatients(null, "  ", 300).isEmpty());

        // 空关键字应被规范成 null，避免 SQL 里拼出 LIKE '%%' 把全院历史患者都捞出来
        verify(mapper).selectDischargedPatients(null, null, 300);
    }

    @Test
    @DisplayName("在科口径查不到这两列时，inDepart 默认 true（老数据不会被误标成已出科）")
    void inpatientRowWithoutInDepartColumnDefaultsToInpatient() {
        Map<String, Object> row = dischargedRow();
        row.remove("is_in_depart");
        row.remove("out_depart_time");
        when(mapper.selectInpatientById("p1")).thenReturn(row);
        when(mapper.selectDiagnoses("p1")).thenReturn(Collections.emptyList());

        WorkbenchPatient p = service.getWorkbenchPatient("p1");

        assertTrue(p.getInDepart(), "查不到 is_in_depart 说明这条查询本身限定在科，应视为在科");
        assertNull(p.getOutDepartmentTime());
    }
}