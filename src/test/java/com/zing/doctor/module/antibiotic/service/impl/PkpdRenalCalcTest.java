package com.zing.doctor.module.antibiotic.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PK/PD 肾功能计算规格测试（第二维度剂量优化的基础）。
 *
 * <p>这三项计算直接决定「肾功能不全时减量多少」，算错会指导临床用药，
 * 因此按公式逐个核对边界值。不加载 Spring、不连数据库：直接 new 实例验证纯计算。
 *
 * <p>公式口径：
 * <ul>
 *   <li>Cockcroft-Gault：CrCl = (140 - 年龄) × 体重(kg) / (72 × Scr(mg/dL))，女性 × 0.85，保留 1 位小数</li>
 *   <li>CKD-EPI（简化，不含种族）：141/144 × (Scr/κ)^α × 0.993^年龄，保留 1 位小数</li>
 *   <li>KDIGO 分级：G1 ≥90、G2 60-89、G3a 45-59、G3b 30-44、G4 15-29、G5 &lt;15</li>
 * </ul>
 */
class PkpdRenalCalcTest {

    private final PkpdServiceImpl svc = new PkpdServiceImpl(null, null);

    // ---------------- Cockcroft-Gault ----------------

    @Test
    @DisplayName("Cockcroft-Gault：男性按 (140-年龄)×体重/(72×Scr) 计算，保留 1 位小数")
    void crclMale() {
        // (140-60)×60 / (72×1.0) = 4800/72 = 66.67 → 66.7
        BigDecimal crcl = svc.calcCrclCockcroftGault(60, new BigDecimal("60"), new BigDecimal("1.0"), "男");
        assertEquals(0, new BigDecimal("66.7").compareTo(crcl), "实际=" + crcl);
    }

    @Test
    @DisplayName("Cockcroft-Gault：女性在男性结果基础上 ×0.85")
    void crclFemale() {
        // 66.7 × 0.85 = 56.695 → 56.7
        BigDecimal crcl = svc.calcCrclCockcroftGault(60, new BigDecimal("60"), new BigDecimal("1.0"), "女");
        assertEquals(0, new BigDecimal("56.7").compareTo(crcl), "实际=" + crcl);

        // 性别为 null 时不折算，等同男性口径
        BigDecimal noGender = svc.calcCrclCockcroftGault(60, new BigDecimal("60"), new BigDecimal("1.0"), null);
        assertEquals(0, new BigDecimal("66.7").compareTo(noGender));
    }

    @Test
    @DisplayName("Cockcroft-Gault：Scr 缺失或非正数时返回 null（不给出误导性剂量建议）")
    void crclInvalidCreatinine() {
        assertNull(svc.calcCrclCockcroftGault(60, new BigDecimal("60"), null, "男"));
        assertNull(svc.calcCrclCockcroftGault(60, new BigDecimal("60"), BigDecimal.ZERO, "男"));
        assertNull(svc.calcCrclCockcroftGault(60, new BigDecimal("60"), new BigDecimal("-0.5"), "男"));
    }

    // ---------------- CKD-EPI ----------------

    @Test
    @DisplayName("CKD-EPI：60 岁男性 Scr=1.0 落在 80~83 区间（临床参考值）")
    void egfrMale() {
        BigDecimal egfr = svc.calcEgfrCkdEpi(60, new BigDecimal("1.0"), "男");
        assertNotNull(egfr);
        assertTrue(egfr.doubleValue() > 80 && egfr.doubleValue() < 83,
                "60 岁男性 Scr=1.0 的 eGFR 应在 80~83，实际=" + egfr);
        // 保留 1 位小数
        assertEquals(1, egfr.scale());
    }

    @Test
    @DisplayName("CKD-EPI：同年龄同 Scr 下女性 eGFR 低于男性（性别系数 144<141 且 κ 更小）")
    void egfrFemaleLowerThanMale() {
        BigDecimal male = svc.calcEgfrCkdEpi(60, new BigDecimal("1.0"), "男");
        BigDecimal female = svc.calcEgfrCkdEpi(60, new BigDecimal("1.0"), "女");
        assertTrue(female.compareTo(male) < 0, "女性=" + female + " 男性=" + male);
    }

    @Test
    @DisplayName("CKD-EPI：Scr 缺失或非正数时返回 null")
    void egfrInvalidCreatinine() {
        assertNull(svc.calcEgfrCkdEpi(60, null, "男"));
        assertNull(svc.calcEgfrCkdEpi(60, BigDecimal.ZERO, "女"));
    }

    // ---------------- KDIGO 分级 ----------------

    @Test
    @DisplayName("KDIGO 分级：90/60/45/30/15 为各档下边界，跨档即变级")
    void renalStageBoundaries() {
        assertEquals("G1", svc.classifyRenalStage(new BigDecimal("90"))[0]);
        assertEquals("G1", svc.classifyRenalStage(new BigDecimal("120"))[0]);
        assertEquals("G2", svc.classifyRenalStage(new BigDecimal("89.9"))[0]);
        assertEquals("G2", svc.classifyRenalStage(new BigDecimal("60"))[0]);
        assertEquals("G3a", svc.classifyRenalStage(new BigDecimal("59.9"))[0]);
        assertEquals("G3a", svc.classifyRenalStage(new BigDecimal("45"))[0]);
        assertEquals("G3b", svc.classifyRenalStage(new BigDecimal("44.9"))[0]);
        assertEquals("G3b", svc.classifyRenalStage(new BigDecimal("30"))[0]);
        assertEquals("G4", svc.classifyRenalStage(new BigDecimal("29.9"))[0]);
        assertEquals("G4", svc.classifyRenalStage(new BigDecimal("15"))[0]);
        assertEquals("G5", svc.classifyRenalStage(new BigDecimal("14.9"))[0]);
    }

    @Test
    @DisplayName("KDIGO 分级：中文描述与英文代码同时返回，eGFR 为 null 时给 unknown")
    void renalStageChineseDesc() {
        String[] g3b = svc.classifyRenalStage(new BigDecimal("35"));
        assertEquals("G3b", g3b[0]);
        assertEquals("中重度下降", g3b[1]);

        String[] unknown = svc.classifyRenalStage(null);
        assertEquals("unknown", unknown[0]);
        assertEquals("未知", unknown[1]);
    }
}
