package com.zing.doctor.module.sofa.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * SOFA 评分规则规格测试（对照 Vincent 1996 / Sepsis-3 标准阈值）。
 *
 * <p>不加载 Spring、不连数据库：评分方法为纯计算，直接 new 实例即可验证。
 */
class SofaScoringTest {

    private final SofaServiceImpl svc = new SofaServiceImpl();

    // ---------------- 呼吸：PaO2/FiO2 ----------------

    @Test
    @DisplayName("呼吸：≥400=0 / <400=1 / <300=2；3、4 分要求有呼吸支持")
    void respiration() {
        assertEquals(0, svc.scoreResp(400.0, false));
        assertEquals(0, svc.scoreResp(500.0, false));
        assertEquals(1, svc.scoreResp(399.9, false));
        assertEquals(1, svc.scoreResp(300.0, false));
        assertEquals(2, svc.scoreResp(299.9, false));
        assertEquals(2, svc.scoreResp(200.0, false));
        // <200 且有呼吸支持 = 3；无呼吸支持只能按 <300 记 2
        assertEquals(3, svc.scoreResp(199.9, true));
        assertEquals(2, svc.scoreResp(199.9, false));
        assertEquals(3, svc.scoreResp(100.0, true));
        assertEquals(4, svc.scoreResp(99.9, true));
        assertEquals(2, svc.scoreResp(99.9, false));
        // 缺数据
        assertEquals(0, svc.scoreResp(null, true));
    }

    // ---------------- 凝血：血小板 ----------------

    @Test
    @DisplayName("凝血 血小板(×10³/µL)：≥150=0 / <150=1 / <100=2 / <50=3 / <20=4")
    void coagulation() {
        assertEquals(0, svc.scoreCoag(150.0));
        assertEquals(1, svc.scoreCoag(149.9));
        assertEquals(1, svc.scoreCoag(100.0));
        assertEquals(2, svc.scoreCoag(99.9));
        assertEquals(2, svc.scoreCoag(50.0));
        assertEquals(3, svc.scoreCoag(49.9));
        assertEquals(3, svc.scoreCoag(20.0));
        assertEquals(4, svc.scoreCoag(19.9));
        assertEquals(0, svc.scoreCoag(null));
    }

    // ---------------- 肝：总胆红素 mg/dL ----------------

    @Test
    @DisplayName("肝 总胆红素(mg/dL)：<1.2=0 / 1.2-1.9=1 / 2.0-5.9=2 / 6.0-11.9=3 / >12=4")
    void liver() {
        assertEquals(0, svc.scoreLiver(1.1));
        assertEquals(1, svc.scoreLiver(1.2));
        assertEquals(1, svc.scoreLiver(1.9));
        assertEquals(2, svc.scoreLiver(2.0));
        assertEquals(2, svc.scoreLiver(5.9));
        assertEquals(3, svc.scoreLiver(6.0));
        assertEquals(3, svc.scoreLiver(11.9));
        assertEquals(4, svc.scoreLiver(12.0));
        assertEquals(4, svc.scoreLiver(20.0));
        assertEquals(0, svc.scoreLiver(null));
    }

    // ---------------- 循环：MAP ----------------

    @Test
    @DisplayName("循环 MAP：≥70=0 / <70=1")
    void cardiovascularMap() {
        assertEquals(0, svc.scoreCardioMap(70.0));
        assertEquals(1, svc.scoreCardioMap(69.9));
        assertEquals(1, svc.scoreCardioMap(40.0));
        assertEquals(0, svc.scoreCardioMap(null));
    }

    // ---------------- 循环：血管活性药剂量档 ----------------

    @Test
    @DisplayName("多巴胺：≤5=2 / >5~15=3 / >15=4（5.0 与 15.0 边界含等号）")
    void dopamineTiers() {
        Map<String, String> cfg = new HashMap<>();
        assertEquals(2, svc.scoreVaso(vaso("dopamine", 0.0), cfg));
        assertEquals(2, svc.scoreVaso(vaso("dopamine", 5.0), cfg));
        assertEquals(3, svc.scoreVaso(vaso("dopamine", 5.1), cfg));
        assertEquals(3, svc.scoreVaso(vaso("dopamine", 15.0), cfg));
        assertEquals(4, svc.scoreVaso(vaso("dopamine", 15.1), cfg));
    }

    @Test
    @DisplayName("去甲肾上腺素 / 肾上腺素：≤0.1=3 / >0.1=4")
    void catecholamineTiers() {
        Map<String, String> cfg = new HashMap<>();
        for (String drug : new String[]{"norepinephrine", "epinephrine"}) {
            assertEquals(3, svc.scoreVaso(vaso(drug, 0.05), cfg), drug);
            assertEquals(3, svc.scoreVaso(vaso(drug, 0.1), cfg), drug);
            assertEquals(4, svc.scoreVaso(vaso(drug, 0.11), cfg), drug);
        }
    }

    @Test
    @DisplayName("多巴酚丁胺：任意剂量均 2 分")
    void dobutamineTier() {
        Map<String, String> cfg = new HashMap<>();
        assertEquals(2, svc.scoreVaso(vaso("dobutamine", 0.0), cfg));
        assertEquals(2, svc.scoreVaso(vaso("dobutamine", 50.0), cfg));
    }

    @Test
    @DisplayName("剂量未归一（降级）按最低档：多巴胺=2 / 去甲、肾上腺素=3")
    void degradedTiers() {
        Map<String, String> cfg = new HashMap<>();
        assertEquals(2, svc.scoreVaso(vaso("dopamine", null), cfg));
        assertEquals(3, svc.scoreVaso(vaso("norepinephrine", null), cfg));
        assertEquals(3, svc.scoreVaso(vaso("epinephrine", null), cfg));
    }

    @Test
    @DisplayName("阈值可被配置覆盖：dopamine=多巴胺,3,20")
    void thresholdsFromConfig() {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("dopamine", "多巴胺,3,20");
        assertEquals(2, svc.scoreVaso(vaso("dopamine", 3.0), cfg));
        assertEquals(3, svc.scoreVaso(vaso("dopamine", 3.1), cfg));
        assertEquals(3, svc.scoreVaso(vaso("dopamine", 20.0), cfg));
        assertEquals(4, svc.scoreVaso(vaso("dopamine", 20.1), cfg));
    }

    // ---------------- 神经：GCS ----------------

    @Test
    @DisplayName("神经 GCS：15=0 / 13-14=1 / 10-12=2 / 6-9=3 / <6=4")
    void neuro() {
        assertEquals(0, svc.scoreNeuro(15));
        assertEquals(1, svc.scoreNeuro(14));
        assertEquals(1, svc.scoreNeuro(13));
        assertEquals(2, svc.scoreNeuro(12));
        assertEquals(2, svc.scoreNeuro(10));
        assertEquals(3, svc.scoreNeuro(9));
        assertEquals(3, svc.scoreNeuro(6));
        assertEquals(4, svc.scoreNeuro(5));
        assertEquals(4, svc.scoreNeuro(3));
        assertEquals(0, svc.scoreNeuro(null));
    }

    // ---------------- 肾：肌酐 与 尿量 ----------------

    @Test
    @DisplayName("肾 肌酐(mg/dL)：<1.2=0 / 1.2-1.9=1 / 2.0-3.4=2 / 3.5-4.9=3 / >5.0=4")
    void renalCreatinine() {
        assertEquals(0, svc.scoreRenalCreatinine(1.1));
        assertEquals(1, svc.scoreRenalCreatinine(1.2));
        assertEquals(1, svc.scoreRenalCreatinine(1.9));
        assertEquals(2, svc.scoreRenalCreatinine(2.0));
        assertEquals(2, svc.scoreRenalCreatinine(3.4));
        assertEquals(3, svc.scoreRenalCreatinine(3.5));
        assertEquals(3, svc.scoreRenalCreatinine(4.9));
        assertEquals(4, svc.scoreRenalCreatinine(5.0));
        assertEquals(0, svc.scoreRenalCreatinine(null));
    }

    @Test
    @DisplayName("肾 尿量(mL/d)：≥500=0 / <500=3 / <200=4")
    void renalUrine() {
        assertEquals(0, svc.scoreRenalUrine(600.0));
        assertEquals(0, svc.scoreRenalUrine(500.0));
        assertEquals(3, svc.scoreRenalUrine(499.0));
        assertEquals(3, svc.scoreRenalUrine(200.0));
        assertEquals(4, svc.scoreRenalUrine(199.0));
        assertEquals(0, svc.scoreRenalUrine(null));
    }

    // ---------------- 血管活性药药名归类（子串陷阱） ----------------

    @Test
    @DisplayName("药名归类：「去甲肾上腺素」不得被判为「肾上腺素」")
    void vasoClassify() {
        // 真实库中带前导空格与商品名后缀
        assertEquals("norepinephrine", svc.classifyVaso(" 重酒石酸去甲肾上腺素注射液 (津药)"));
        assertEquals("norepinephrine", svc.classifyVaso("去甲肾上腺素"));
        assertEquals("epinephrine", svc.classifyVaso("肾上腺素注射液"));
        assertEquals("epinephrine", svc.classifyVaso("盐酸肾上腺素"));
        assertEquals("dopamine", svc.classifyVaso("多巴胺注射液"));
        assertEquals("dobutamine", svc.classifyVaso("多巴酚丁胺"));
        // 核心断言：含“去甲”的串不能被归为肾上腺素
        assertNotEquals("epinephrine", svc.classifyVaso("重酒石酸去甲肾上腺素注射液"));
        assertEquals(null, svc.classifyVaso("0.9%氯化钠注射液"));
    }

    // ---------------- 命中区间文本 ----------------

    @Test
    @DisplayName("命中区间文本与分值一致（抽查）")
    void rangeText() {
        assertEquals("≥400", svc.rangeResp(450.0, false));
        assertEquals("<300", svc.rangeResp(250.0, false));
        assertEquals("<200", svc.rangeResp(150.0, true));
        assertEquals("<20", svc.rangeCoag(10.0));
        assertEquals(">12", svc.rangeLiver(15.0));
        assertEquals("<6", svc.rangeNeuro(4));
        // 尿量分更高时展示尿量区间（同分时按设计优先展示肌酐区间）
        assertEquals("尿量<200 mL/d", svc.rangeRenal(1.0, 150.0, 0, 4));
        assertEquals(">5.0", svc.rangeRenal(6.0, 150.0, 4, 4));
    }

    // ---------------- 测试夹具 ----------------

    private static SofaServiceImpl.VasoDose vaso(String drugType, Double doseUgKgMin) {
        SofaServiceImpl.VasoDose d = new SofaServiceImpl.VasoDose();
        d.drugType = drugType;
        d.doseUgKgMin = doseUgKgMin;
        return d;
    }
}
