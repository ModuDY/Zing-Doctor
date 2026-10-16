package com.zing.doctor.icu.service;

import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.module.antibiotic.mapper.AntibioticReassessmentMapper;
import com.zing.doctor.module.apache2.mapper.Apache2ScoreRecordMapper;
import com.zing.doctor.module.ards.prone.entity.ArdsProneRecord;
import com.zing.doctor.module.ards.prone.mapper.ArdsProneRecordMapper;
import com.zing.doctor.module.sofa.mapper.SofaScoreRecordMapper;
import com.zing.doctor.module.system.service.SysParamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
 * 预警短信的数值来源（{@code WorkbenchPatient.alertVars}）。
 *
 * <p>这些数值库里没有第二份来源，短信里写错没人能靠页面核对出来，
 * 所以在这里钉死：
 * <ol>
 *   <li>GCS 变量的含义是「首查 → 最新」，降幅取两者之差；降 1 分不发待办（也不该发短信）；</li>
 *   <li>俯卧位时长按小时取整、呼吸频率去掉多余的 0（{@code 7.0 → 7}）——短信是给人看的；</li>
 *   <li>脑损伤只带命中的关键字，不带整条诊断原文。</li>
 * </ol>
 */
class WorkbenchAlertVarsTest {

    private IcuPatientMapper mapper;
    private WorkbenchEnrichService service;

    @BeforeEach
    void setUp() {
        mapper = mock(IcuPatientMapper.class);
        SofaScoreRecordMapper sofaMapper = mock(SofaScoreRecordMapper.class);
        Apache2ScoreRecordMapper apacheMapper = mock(Apache2ScoreRecordMapper.class);
        service = new WorkbenchEnrichService(sofaMapper, apacheMapper, mapper,
                mock(AntibioticReassessmentMapper.class),
                mock(SysParamService.class));
        // 评分表 mock 默认返回 null，会让 latestSofa 之类的遍历 NPE；显式给空结果
        when(sofaMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(apacheMapper.selectList(any())).thenReturn(Collections.emptyList());
        // 俯卧位记录是字段注入（不进构造器），这里补上
        ArdsProneRecord prone = new ArdsProneRecord();
        prone.setPatientId("P1");
        // 18 小时零 5 秒：ChronoUnit.HOURS 截断后是 18，避免跨整点边界时的 ±1 抖动
        prone.setStartTime(LocalDateTime.now().minusHours(18).minusSeconds(5));
        ArdsProneRecordMapper proneMapper = mock(ArdsProneRecordMapper.class);
        when(proneMapper.selectList(any())).thenReturn(Collections.singletonList(prone));
        ReflectionTestUtils.setField(service, "proneRecordMapper", proneMapper);
    }

    @Test
    @DisplayName("GCS 降 3 分：待办与文案变量同时带出首查、最新与降幅")
    void gcsVarsCarryFirstLatestAndDrop() {
        when(mapper.selectArdsFlagByPatientIds(anyList())).thenReturn(Collections.singletonList(
                row("patient_id", "P1", "is_ards", 1)));
        when(mapper.selectGcsRecordsByPatientIds(anyList(), anyString(), anyString())).thenReturn(Arrays.asList(
                gcsRow("P1", "12"), gcsRow("P1", "9")));
        when(mapper.selectRrByPatientIds(anyList(), anyString(), anyString())).thenReturn(Collections.singletonList(
                row("patient_id", "P1", "item_value", "7")));
        when(mapper.selectDiagnosesByPatientIds(anyList())).thenReturn(Collections.singletonList(
                row("patient_id", "P1", "diag_name", "脑出血伴脑疝形成，既往高血压3级")));

        WorkbenchPatient p = patient();
        service.enrich(Collections.singletonList(p));

        Map<String, String> v = p.getAlertVars();
        assertEquals("12", v.get("gcsFrom"), "gcsFrom 是今日首查");
        assertEquals("9", v.get("gcsTo"), "gcsTo 是最新一次");
        assertEquals("3", v.get("gcsDrop"));
        assertEquals("18", v.get("proneHours"));
        assertEquals("7", v.get("rrValue"), "7.0 要收敛成 7");
        assertEquals("脑出血", v.get("brainDiag"), "只带命中的关键字，不带整条诊断原文");
        assertEquals("123456", v.get("inHospitalNo"));
        assertEquals("张三", v.get("patientName"));

        assertTrue(p.getTodos().contains("ARDS_VENTILATED"));
        assertTrue(p.getTodos().contains("GCS_DROP_2"));
        assertTrue(p.getTodos().contains("PRONE_OVERTIME"));
        assertTrue(p.getTodos().contains("RR_DEPRESSION"));
        assertTrue(p.getTodos().contains("BRAIN_INJURY"));
    }

    @Test
    @DisplayName("GCS 只降 1 分：不发待办（也就不会发短信）")
    void gcsDropOfOneIsNotAnAlert() {
        when(mapper.selectGcsRecordsByPatientIds(anyList(), anyString(), anyString())).thenReturn(Arrays.asList(
                gcsRow("P1", "12"), gcsRow("P1", "11")));

        WorkbenchPatient p = patient();
        service.enrich(Collections.singletonList(p));

        assertFalse(p.getTodos().contains("GCS_DROP_2"),
                "阈值是下降 ≥2 分，降 1 分不该惊动值班医生");
        // 变量仍然给全（页面/文案两处口径一致），但因为没有待办，推送侧不会取用它
        assertEquals("1", p.getAlertVars().get("gcsDrop"));
    }

    private WorkbenchPatient patient() {
        WorkbenchPatient p = new WorkbenchPatient();
        p.setPatientId("P1");
        p.setName("张三");
        p.setBedNo("3床");
        p.setInHospitalNo("123456");
        p.setDepartCode("D001");
        p.setAge(68);
        p.setGender("男");
        p.setVentilated(true);
        p.setInDepartmentTime(LocalDateTime.now().minusHours(30));
        return p;
    }

    private Map<String, Object> row(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new HashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private Map<String, Object> gcsRow(String pid, String totalScore) {
        return row("patient_id", pid, "score_json", "{\"totalScore\":\"" + totalScore + "\"}");
    }
}
