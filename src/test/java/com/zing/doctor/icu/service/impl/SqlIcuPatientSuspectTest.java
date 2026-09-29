package com.zing.doctor.icu.service.impl;

import com.zing.doctor.icu.dto.IcuPatientBrief;
import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.module.antibiotic.service.AbxDrugRecognizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 疑似感染患者列表的入列与分级规则。
 *
 * <p>钉死的是四条容易被"顺手改回去"的约定：
 * <ol>
 *   <li>只靠 PCT 入列的患者，PCT 必须达到 0.5 —— 否则 0.05 这种也算疑似感染；</li>
 *   <li>靠休克标记 / 感染诊断入列的患者不受 PCT 阈值影响 —— 他们的依据本身就是感染证据；</li>
 *   <li>主表 is_sepsis_shock=1 但诊断没写"脓毒性休克"四字的患者，仍然是休克；</li>
 *   <li>列表查询次数与患者数无关 —— 逐患者查询（N+1）一旦回来，页面又会慢到要配 60 秒超时。</li>
 * </ol>
 */
class SqlIcuPatientSuspectTest {

    private IcuPatientMapper mapper;
    private SqlIcuPatientServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(IcuPatientMapper.class);
        service = new SqlIcuPatientServiceImpl(mapper, mock(AbxDrugRecognizer.class));
    }

    @Test
    @DisplayName("只靠 PCT 入列：0.12 不算疑似感染，1.2 才算")
    void pctThresholdFiltersPctOnlyPatients() {
        when(mapper.selectSuspectPatients("D001")).thenReturn(Arrays.asList(
                patientRow("P1", "N1", 0, 0, 1),
                patientRow("P2", "N2", 0, 0, 1)));
        when(mapper.selectInfectionLabsByNos(anyList())).thenReturn(Arrays.asList(
                labRow("N1", "降钙素原", "0.12"),
                labRow("N2", "降钙素原", "1.2")));

        List<IcuPatientBrief> list = service.listSuspectInfections("D001");

        assertEquals(1, list.size(), "PCT 0.12 的患者不应进入疑似感染列表");
        assertEquals("N2", list.get(0).getPatientNo());
        assertEquals("中风险", list.get(0).getRiskLevel());
    }

    @Test
    @DisplayName("感染诊断命中：即使 PCT 只有 0.05 也保留（入列依据是诊断，不是 PCT）")
    void diagnosisHitKeepsLowPctPatient() {
        when(mapper.selectSuspectPatients("D001")).thenReturn(Collections.singletonList(
                patientRow("P1", "N1", 0, 1, 1)));
        when(mapper.selectInfectionLabsByNos(anyList())).thenReturn(Collections.singletonList(
                labRow("N1", "降钙素原", "0.05")));
        when(mapper.selectDiagnosesByPatientIds(anyList())).thenReturn(Collections.singletonList(
                diagRow("P1", "重症肺炎")));

        List<IcuPatientBrief> list = service.listSuspectInfections("D001");

        assertEquals(1, list.size());
        assertEquals("社区获得性肺炎（CAP）", list.get(0).getInfectionType());
    }

    @Test
    @DisplayName("主表脓毒性休克标记命中：诊断没写「脓毒性休克」也算休克，风险为高")
    void shockFlagFromPatientInfoIsRespected() {
        when(mapper.selectSuspectPatients("D001")).thenReturn(Collections.singletonList(
                patientRow("P1", "N1", 1, 0, 0)));
        when(mapper.selectDiagnosesByPatientIds(anyList())).thenReturn(Collections.singletonList(
                diagRow("P1", "重症肺炎")));

        List<IcuPatientBrief> list = service.listSuspectInfections("D001");

        assertEquals(1, list.size());
        assertTrue(list.get(0).getSepticShock(), "主表打了休克标记就不能判成非休克");
        assertEquals("septic", list.get(0).getShockType());
        assertEquals("高风险", list.get(0).getRiskLevel());
    }

    @Test
    @DisplayName("MDR + 真菌 风险：即使没有 PCT 结果也不是低风险")
    void riskLevelConsidersResistanceFlags() {
        Map<String, Object> row = patientRow("P1", "N1", 0, 1, 0);
        row.put("mdr_bacteria", "3,5");
        row.put("resistant_bacteria", "念珠菌");
        when(mapper.selectSuspectPatients("D001")).thenReturn(Collections.singletonList(row));

        List<IcuPatientBrief> list = service.listSuspectInfections("D001");

        assertEquals(1, list.size());
        assertTrue(list.get(0).getMdrRisk());
        assertTrue(list.get(0).getFungalRisk());
        assertEquals("高风险", list.get(0).getRiskLevel(), "标签全亮却判低风险会让页面自相矛盾");
    }

    @Test
    @DisplayName("查询次数与患者数无关：批量方法各 1 次，逐患者方法 0 次")
    void noNPlusOneQueries() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            rows.add(patientRow("P" + i, "N" + i, 0, 1, 0));
        }
        when(mapper.selectSuspectPatients("D001")).thenReturn(rows);

        List<IcuPatientBrief> list = service.listSuspectInfections("D001");

        assertEquals(30, list.size());
        verify(mapper, times(1)).selectSuspectPatients("D001");
        verify(mapper, times(1)).selectDiagnosesByPatientIds(anyList());
        verify(mapper, times(1)).selectInfectionLabsByNos(anyList());
        verify(mapper, times(1)).selectLatestTemperatureByPatientIds(anyList());
        verify(mapper, times(1)).selectMicrobiologyByNos(anyList());
        verify(mapper, times(1)).selectCurrentAbxAdviceByNos(anyList());
        // 逐患者查询一条都不许出现
        verify(mapper, never()).selectDiagnoses(org.mockito.ArgumentMatchers.anyString());
        verify(mapper, never()).selectRecentLabs(org.mockito.ArgumentMatchers.anyString());
        verify(mapper, never()).selectLatestTemperature(org.mockito.ArgumentMatchers.anyString());
        verify(mapper, never()).selectMicrobiology(org.mockito.ArgumentMatchers.anyString());
        verify(mapper, never()).selectCurrentAbxAdvice(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("PCT 带单位 / 比较符也要解析出数值")
    void pctValueParsing() {
        when(mapper.selectSuspectPatients("D001")).thenReturn(Collections.singletonList(
                patientRow("P1", "N1", 0, 0, 1)));
        when(mapper.selectInfectionLabsByNos(anyList())).thenReturn(Collections.singletonList(
                labRow("N1", "降钙素原", "2.35 ng/mL")));

        List<IcuPatientBrief> list = service.listSuspectInfections("D001");

        assertEquals(1, list.size());
        assertEquals(0, new BigDecimal("2.35").compareTo(list.get(0).getPct()));
        assertEquals("高风险", list.get(0).getRiskLevel());
        assertFalse(list.get(0).getSepticShock());
    }

    // ------------------------------------------------------------------
    // 测试数据
    // ------------------------------------------------------------------

    /** patient_info 一行：三个命中标志按 [shock, diag, pct] 传入 */
    private Map<String, Object> patientRow(String patientId, String patientNo,
                                          int shockFlag, int diagFlag, int pctFlag) {
        Map<String, Object> row = new HashMap<>();
        row.put("patient_id", patientId);
        row.put("patient_no", patientNo);
        row.put("name", "患者" + patientNo);
        row.put("age", 60);
        row.put("gender", "男");
        row.put("department", "综合ICU");
        row.put("bed_no", "01");
        row.put("septic_shock", shockFlag);
        row.put("shock_flag", shockFlag);
        row.put("diag_flag", diagFlag);
        row.put("pct_flag", pctFlag);
        row.put("weight", new BigDecimal("65"));
        row.put("height", new BigDecimal("170"));
        return row;
    }

    private Map<String, Object> labRow(String inHospitalNo, String itemName, String result) {
        Map<String, Object> row = new HashMap<>();
        row.put("in_hospital_no", inHospitalNo);
        row.put("item_name", itemName);
        row.put("result", result);
        row.put("unit", "");
        return row;
    }

    private Map<String, Object> diagRow(String patientId, String diagName) {
        Map<String, Object> row = new HashMap<>();
        row.put("patient_id", patientId);
        row.put("diag_name", diagName);
        row.put("diag_time", "2026-09-01 10:00:00");
        return row;
    }
}
