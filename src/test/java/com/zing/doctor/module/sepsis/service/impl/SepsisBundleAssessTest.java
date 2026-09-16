package com.zing.doctor.module.sepsis.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 脓毒症集束化治疗（1H/3H/6H）自动判定的基础口径测试。
 *
 * <p>集束化达标率是质控上报项，判定错会直接影响科室考核数据，因此覆盖：
 * <ul>
 *   <li>休克类型：感染性休克优先于脓毒症，且只看入科后、出科前的诊断</li>
 *   <li>液体复苏量：只累计 ml 单位的晶体液，跳过 g 单位药物与时间窗外医嘱</li>
 *   <li>菌名有效性：把「未检出/阴性/杂菌生长」等描述挡在菌株统计之外</li>
 * </ul>
 */
class SepsisBundleAssessTest {

    private final SepsisBundleServiceImpl svc = new SepsisBundleServiceImpl();

    private static final LocalDateTime IN = LocalDateTime.of(2026, 9, 1, 8, 0, 0);
    private static final LocalDateTime OUT = LocalDateTime.of(2026, 9, 3, 20, 0, 0);

    // ---------------- 休克类型判定 ----------------

    @Test
    @DisplayName("休克类型：感染性休克优先（同时存在脓毒症诊断时仍返回感染性休克）")
    void septicShockPriority() {
        List<Map<String, Object>> diags = new ArrayList<>();
        diags.add(diag("脓毒症", "2026-09-01 09:00:00"));
        diags.add(diag("感染性休克", "2026-09-01 10:00:00"));

        assertEquals("感染性休克", svc.determineShockType(diags, IN, OUT));
    }

    @Test
    @DisplayName("休克类型：仅脓毒症/败血症诊断时返回脓毒症")
    void sepsisOnly() {
        List<Map<String, Object>> diags = new ArrayList<>();
        diags.add(diag("脓毒血症", "2026-09-01 09:00:00"));
        assertEquals("脓毒症", svc.determineShockType(diags, IN, OUT));
    }

    @Test
    @DisplayName("休克类型：非休克诊断返回空串")
    void noShock() {
        List<Map<String, Object>> diags = new ArrayList<>();
        diags.add(diag("肺部感染", "2026-09-01 09:00:00"));
        assertEquals("", svc.determineShockType(diags, IN, OUT));
    }

    @Test
    @DisplayName("休克类型：入科前的感染性休克诊断不计入（避免把院外史算作本次）")
    void shockBeforeAdmissionIgnored() {
        List<Map<String, Object>> diags = new ArrayList<>();
        diags.add(diag("感染性休克", "2026-08-30 10:00:00"));
        assertEquals("", svc.determineShockType(diags, IN, OUT));
    }

    @Test
    @DisplayName("休克类型：出科后的诊断不计入")
    void shockAfterDischargeIgnored() {
        List<Map<String, Object>> diags = new ArrayList<>();
        diags.add(diag("感染性休克", "2026-09-10 10:00:00"));
        assertEquals("", svc.determineShockType(diags, IN, OUT));
    }

    @Test
    @DisplayName("休克类型：诊断时间缺失时不过滤（按现有实现保留该诊断）")
    void shockWithoutDiagTimeKept() {
        List<Map<String, Object>> diags = new ArrayList<>();
        Map<String, Object> d = new HashMap<>();
        d.put("diag_name", "感染性休克");
        diags.add(d);
        assertEquals("感染性休克", svc.determineShockType(diags, IN, OUT));
    }

    // ---------------- 液体复苏量 ----------------

    @Test
    @DisplayName("液体量：只累计 ml/mL 单位，g 单位的药物不计入")
    void fluidAmountUnitFiltered() {
        List<Map<String, Object>> fluids = new ArrayList<>();
        fluids.add(fluid("2026-09-01 10:00:00", "500", "ml"));
        fluids.add(fluid("2026-09-01 12:00:00", "500", "mL"));
        // 药液量按 g 记录的（如万古霉素 1g）不应算作复苏液体
        fluids.add(fluid("2026-09-01 13:00:00", "1", "g"));

        assertEquals(1000.0, svc.calculateFluidAmount(fluids, IN, OUT), 0.0001);
    }

    @Test
    @DisplayName("液体量：时间窗外的医嘱不计入（闭区间两端计入）")
    void fluidAmountTimeWindow() {
        List<Map<String, Object>> fluids = new ArrayList<>();
        fluids.add(fluid("2026-09-01 07:00:00", "500", "ml")); // 入科前，剔除
        fluids.add(fluid("2026-09-01 08:00:00", "300", "ml")); // 恰在起点，计入
        fluids.add(fluid("2026-09-03 20:00:00", "200", "ml")); // 恰在终点，计入
        fluids.add(fluid("2026-09-04 10:00:00", "500", "ml")); // 出科后，剔除

        assertEquals(500.0, svc.calculateFluidAmount(fluids, IN, OUT), 0.0001);
    }

    @Test
    @DisplayName("液体量：剂量非数字时跳过该条，不影响其它记录累计")
    void fluidAmountInvalidDosage() {
        List<Map<String, Object>> fluids = new ArrayList<>();
        fluids.add(fluid("2026-09-01 10:00:00", "abc", "ml"));
        fluids.add(fluid("2026-09-01 11:00:00", "250", "ml"));

        assertEquals(250.0, svc.calculateFluidAmount(fluids, IN, OUT), 0.0001);
    }

    // ---------------- 菌名有效性 ----------------

    @Test
    @DisplayName("菌名有效性：阴性/未检出/培养描述一律判为无效")
    void invalidBacteriaNames() {
        assertFalse(svc.isValidBacteriaName(null));
        assertFalse(svc.isValidBacteriaName(""));
        assertFalse(svc.isValidBacteriaName("未检出"));
        assertFalse(svc.isValidBacteriaName("培养阴性"));
        assertFalse(svc.isValidBacteriaName("无细菌"));
        assertFalse(svc.isValidBacteriaName("正常菌群"));
        assertFalse(svc.isValidBacteriaName("厌氧培养5天未检出细菌"));
        assertFalse(svc.isValidBacteriaName("杂菌生长"));
        assertFalse(svc.isValidBacteriaName("正常菌群生长"));
    }

    @Test
    @DisplayName("菌名有效性：具体菌株名判为有效")
    void validBacteriaNames() {
        assertTrue(svc.isValidBacteriaName("金黄色葡萄球菌"));
        assertTrue(svc.isValidBacteriaName("肺炎克雷伯菌"));
        assertTrue(svc.isValidBacteriaName("白色念珠菌"));
    }

    // ---------------- 溶媒判定 ----------------

    @Test
    @DisplayName("溶媒判定：常见溶媒命中，抗菌药物不误判")
    void solventDetection() {
        assertTrue(svc.isSolvent("0.9%氯化钠注射液"));
        assertTrue(svc.isSolvent("5%葡萄糖注射液"));
        assertTrue(svc.isSolvent("乳酸钠林格注射液"));
        assertFalse(svc.isSolvent("注射用美罗培南"));
        assertFalse(svc.isSolvent(null));
    }

    // ---------------- 辅助 ----------------

    private Map<String, Object> diag(String name, String time) {
        Map<String, Object> map = new HashMap<>();
        map.put("diag_name", name);
        map.put("diag_time", time);
        return map;
    }

    private Map<String, Object> fluid(String startTime, String dosage, String unit) {
        Map<String, Object> map = new HashMap<>();
        map.put("start_time", startTime);
        map.put("drug_one_dosage", dosage);
        map.put("drug_one_dosage_unit", unit);
        return map;
    }
}
