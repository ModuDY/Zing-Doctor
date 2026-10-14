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
    @DisplayName("肾功能：肾小球滤过 + 电解质两组，共 8 个序列（项目名取自生产库实测）")
    void renalHasTwoGroupsAndEightSeries() {
        patientWith("H001");
        when(icuPatientMapper.selectLabsByKeywordsAndRange(anyString(), anyString(), anyString(), any())).thenReturn(Arrays.asList(
                lab("*肌酐", "186", "2026-10-11T08:00:00"),
                lab("*尿素", "12.4", "2026-10-11T08:00:00"),
                lab("*钾", "5.3", "2026-10-11T08:00:00"),
                lab("钾离子", "5.1", "2026-10-11T14:00:00"),           // 血气侧第二套命名，与 *钾 同项目
                lab("*钠", "138", "2026-10-11T08:00:00"),
                lab("*氯", "102", "2026-10-11T08:00:00"),
                lab("*钙", "2.05", "2026-10-11T08:00:00"),
                lab("钙离子浓度(PH7.4)", "1.12", "2026-10-11T14:00:00"),
                lab("*镁", "0.86", "2026-10-11T08:00:00"),
                lab("*磷", "1.24", "2026-10-11T08:00:00")
        ));

        LabTrendResponse resp = service.trend("P1", "renal", 168);

        assertEquals(2, resp.getGroups().size(), "肾功能面板分「肾小球滤过功能」与「电解质」两组");
        assertEquals("肾小球滤过功能", resp.getGroups().get(0).getName());
        assertEquals(2, resp.getGroups().get(0).getSeries().size());
        assertEquals(6, resp.getGroups().get(1).getSeries().size(), "电解质含 钾 / 钠 / 氯 / 钙 / 镁 / 磷");
        assertEquals(1, findSeries(resp, "Cr").getPoints().size());
        assertEquals(1, findSeries(resp, "BUN").getPoints().size());
        assertEquals(2, findSeries(resp, "K⁺").getPoints().size(), "*钾 与 钾离子 是同一项目的两套命名，都要进钾序列");
        assertEquals(2, findSeries(resp, "Ca²⁺").getPoints().size(), "血气侧 钙离子浓度(PH7.4) 也是血钙，一并进钙序列");
        assertEquals("AVAILABLE", findSeries(resp, "Na⁺").getDataStatus());
        assertEquals("AVAILABLE", findSeries(resp, "Cl⁻").getDataStatus());
        assertEquals("AVAILABLE", findSeries(resp, "Mg²⁺").getDataStatus());
        assertEquals("AVAILABLE", findSeries(resp, "P ").getDataStatus());
    }

    @Test
    @DisplayName("肾功能：生产库实测到的同名异物必须全部挡掉（含药物的、含结晶的、别的标本的）")
    void renalExcludesConfusableItems() {
        patientWith("H001");
        when(icuPatientMapper.selectLabsByKeywordsAndRange(anyString(), anyString(), anyString(), any())).thenReturn(Arrays.asList(
                lab("*肌酐", "186", "2026-10-11T08:00:00"),
                lab("尿素/肌酐", "0.07", "2026-10-11T08:00:00"),         // 比值：单字段双值
                lab("内生肌酐清除率", "42", "2026-10-11T08:00:00"),        // 计算值（ml/min），不是检验结果
                lab("24小时腹透液肌酐", "1200", "2026-10-11T08:00:00"),    // 腹透液标本
                lab("降钙素原", "1.20", "2026-10-11T08:00:00"),           // PCT：含「钙」但不是血钙
                lab("床边肌钙蛋白(ICU专用)", "0.03", "2026-10-11T08:00:00"),// 含「肌钙」，与血钙无关
                lab("N端骨钙素", "18", "2026-10-11T08:00:00"),            // 骨代谢标志物
                lab("草酸钙结晶", "2", "2026-10-11T08:00:00"),            // 尿沉渣结晶
                lab("B型钠尿肽", "220", "2026-10-11T08:00:00"),           // BNP：含「钠」但不是血钠
                lab("血清药物浓度测定(苯妥英钠)", "8.2", "2026-10-11T08:00:00"), // 含「钠」的药物浓度
                lab("氯氮平", "120", "2026-10-11T08:00:00"),              // 含「氯」的药物浓度
                lab("磷酸铵镁结晶", "1", "2026-10-11T08:00:00"),           // 含「镁」「磷」的结晶
                lab("微量元素-钙", "1.8", "2026-10-11T08:00:00"),          // ICP-MS 独立面板
                lab("*钾", "4.1", "2026-10-11T08:00:00"),
                lab("钾离子", "4.0", "2026-10-11T08:00:00"),              // 血钾第二套命名，必须留下
                lab("尿钾", "38", "2026-10-11T08:00:00")                  // 尿液标本，与血钾不是一回事
        ));

        LabTrendResponse resp = service.trend("P1", "renal", 168);

        List<String> names = resp.getRecords().stream()
                .map(LabTrendResponse.Row::getItemName)
                .collect(java.util.stream.Collectors.toList());
        assertTrue(names.contains("*肌酐") && names.contains("*钾") && names.contains("钾离子"),
                "血标本项目要留下：" + names);
        for (String bad : Arrays.asList("尿素/肌酐", "清除率", "腹透液", "降钙素原", "肌钙蛋白", "骨钙素",
                "结晶", "钠尿肽", "苯妥英钠", "氯氮平", "微量元素")) {
            assertTrue(names.stream().noneMatch(n -> n.contains(bad)),
                    "「" + bad + "」不得进任何序列：" + names);
        }
        assertTrue(names.stream().noneMatch(n -> n.contains("尿钾")), "尿液标本不得纳入：" + names);
        assertEquals(1, findSeries(resp, "Cr").getPoints().size(), "只有真肌酐进 Cr 序列");
        assertEquals(2, findSeries(resp, "K⁺").getPoints().size(), "只有血钾（两套命名）进钾序列");
        assertEquals(0, findSeries(resp, "Ca²⁺").getPoints().size(), "钙序列不得混入 PCT / 肌钙蛋白 / 骨钙素 / 结晶");
        assertEquals(0, findSeries(resp, "Na⁺").getPoints().size(), "钠序列不得混入 BNP / 苯妥英钠");
        assertEquals(0, findSeries(resp, "Cl⁻").getPoints().size(), "氯序列不得混入氯氮平");
        assertEquals(0, findSeries(resp, "Mg²⁺").getPoints().size(), "镁序列不得混入磷酸铵镁结晶 / 微量元素");
        assertEquals(0, findSeries(resp, "P ").getPoints().size(), "磷序列不得混入结晶 / 微量元素");
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
