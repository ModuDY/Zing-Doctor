package com.zing.doctor.icu.service;

import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.module.apache2.mapper.Apache2ScoreRecordMapper;
import com.zing.doctor.module.sofa.mapper.SofaScoreRecordMapper;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 工作台的感染维度回填（左连接语义）。
 *
 * <p>这里钉住三件事：
 * <ol>
 *   <li>感染维度是"补到全量在科患者上"，不是用疑似患者去替换全量患者；</li>
 *   <li>仅有 PCT 结果但数值不达标的患者，在工作台上也不能算疑似感染
 *       （与感染视图同一门槛，否则两个页面人数对不上）；</li>
 *   <li>ICU 库查询失败时是 {@code UNKNOWN} 而不是"无感染"——
 *       把"没查到"显示成"未发现感染证据"，医生会据此认为患者安全。</li>
 * </ol>
 */
class WorkbenchInfectionEnrichTest {

    private IcuPatientMapper mapper;
    private WorkbenchEnrichService service;

    @BeforeEach
    void setUp() {
        mapper = mock(IcuPatientMapper.class);
        service = new WorkbenchEnrichService(mock(SofaScoreRecordMapper.class),
                mock(Apache2ScoreRecordMapper.class), mapper);
    }

    @Test
    @DisplayName("疑似患者回填感染字段，非疑似患者标记为「已查过、没有」")
    void enrichSuspectAndNonSuspect() {
        when(mapper.selectSuspectPatients("D001")).thenReturn(Collections.singletonList(
                suspectRow("P1", 0, 0, 1)));
        when(mapper.selectInfectionLabsByNos(anyList())).thenReturn(Collections.singletonList(
                labRow("N1", "降钙素原", "1.6")));
        when(mapper.selectDiagnosesByPatientIds(anyList())).thenReturn(Collections.singletonList(
                diagRow("P1", "医院获得性肺炎")));

        List<WorkbenchPatient> patients = Arrays.asList(
                patient("P1", "N1"), patient("P2", "N2"));
        service.enrichInfection(patients, "D001");

        WorkbenchPatient suspect = patients.get(0);
        assertTrue(suspect.getSuspectedInfection());
        assertEquals("FOUND", suspect.getInfectionDataStatus());
        assertEquals("医院获得性肺炎（HAP/VAP）", suspect.getInfectionType());
        assertEquals("中风险", suspect.getInfectionRiskLevel());

        WorkbenchPatient normal = patients.get(1);
        assertFalse(normal.getSuspectedInfection());
        assertEquals("FOUND", normal.getInfectionDataStatus());
        assertEquals(null, normal.getInfectionType(), "非疑似患者不该带感染类型");
    }

    @Test
    @DisplayName("PCT 只有 0.1 的患者不算疑似感染（与感染视图同一门槛）")
    void lowPctIsNotSuspect() {
        when(mapper.selectSuspectPatients("D001")).thenReturn(Collections.singletonList(
                suspectRow("P1", 0, 0, 1)));
        when(mapper.selectInfectionLabsByNos(anyList())).thenReturn(Collections.singletonList(
                labRow("N1", "降钙素原", "0.1")));

        List<WorkbenchPatient> patients = Collections.singletonList(patient("P1", "N1"));
        service.enrichInfection(patients, "D001");

        assertFalse(patients.get(0).getSuspectedInfection());
        assertEquals("FOUND", patients.get(0).getInfectionDataStatus());
    }

    @Test
    @DisplayName("ICU 库查询失败 → UNKNOWN，不能显示成「未发现感染证据」")
    void queryFailureIsUnknownNotNegative() {
        when(mapper.selectSuspectPatients(anyString())).thenThrow(new RuntimeException("ICU 库不可用"));

        List<WorkbenchPatient> patients = Collections.singletonList(patient("P1", "N1"));
        service.enrichInfection(patients, "D001");

        assertEquals("UNKNOWN", patients.get(0).getInfectionDataStatus());
    }

    @Test
    @DisplayName("科室里一个疑似都没有：也标记为已查过，而不是留空")
    void noSuspectAtAll() {
        when(mapper.selectSuspectPatients("D001")).thenReturn(Collections.emptyList());

        List<WorkbenchPatient> patients = Collections.singletonList(patient("P1", "N1"));
        service.enrichInfection(patients, "D001");

        assertFalse(patients.get(0).getSuspectedInfection());
        assertEquals("FOUND", patients.get(0).getInfectionDataStatus());
    }

    private WorkbenchPatient patient(String patientId, String inHospitalNo) {
        WorkbenchPatient p = new WorkbenchPatient();
        p.setPatientId(patientId);
        p.setInHospitalNo(inHospitalNo);
        p.setPatientNo(inHospitalNo);
        p.setName("患者" + patientId);
        p.setInDepartmentTime(LocalDateTime.now().minusDays(2));
        p.setIcuDays(2L);
        return p;
    }

    private Map<String, Object> suspectRow(String patientId, int shockFlag, int diagFlag, int pctFlag) {
        Map<String, Object> row = new HashMap<>();
        row.put("patient_id", patientId);
        row.put("septic_shock", shockFlag);
        row.put("shock_flag", shockFlag);
        row.put("diag_flag", diagFlag);
        row.put("pct_flag", pctFlag);
        return row;
    }

    private Map<String, Object> labRow(String inHospitalNo, String itemName, String result) {
        Map<String, Object> row = new HashMap<>();
        row.put("in_hospital_no", inHospitalNo);
        row.put("item_name", itemName);
        row.put("result", result);
        return row;
    }

    private Map<String, Object> diagRow(String patientId, String diagName) {
        Map<String, Object> row = new HashMap<>();
        row.put("patient_id", patientId);
        row.put("diag_name", diagName);
        row.put("diag_time", "2026-09-20 08:00:00");
        return row;
    }
}
