package com.zing.doctor.module.ards.prone.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ARDS 俯卧位核心计算规格测试（ΔP / P/F / P/F变化 / OI）。
 *
 * <p>这四项计算直接驱动「肺保护性通气依从性」「氧合改善评估」「俯卧位指征判断」，
 * 算错会误导临床决策，因此按公式逐个核对正常值、缺项、除零与边界值。
 *
 * <p>不加载 Spring、不连数据库：计算方法为纯函数，直接 new 实例验证。
 *
 * <p>公式口径：
 * <ul>
 *   <li>ΔP = Pplat − PEEP（驱动压）</li>
 *   <li>P/F = PaO₂ × 100 ÷ FiO₂(%)，FiO₂ ≤ 0 时返回 null</li>
 *   <li>P/F变化 = 当前 P/F − T0 P/F</li>
 *   <li>OI = FiO₂ × Paw × 100 ÷ PaO₂，Paw = PEEP + ΔP/2，PaO₂ ≤ 0 时返回 null</li>
 * </ul>
 * 缺任一必要输入即返回 null（前端显示「待补」），绝不显示 0。
 */
class ArdsProneCalcTest {

    private ArdsProneServiceImpl svc;

    @BeforeEach
    void setUp() {
        svc = new ArdsProneServiceImpl();
    }

    // -------------------------------------------------------------- ΔP 驱动压

    @Test
    @DisplayName("ΔP：Pplat=25, PEEP=5 → 20")
    void deltaP_normal() {
        BigDecimal dp = svc.calcDeltaP(new BigDecimal("25"), new BigDecimal("5"));
        assertEquals(0, new BigDecimal("20").compareTo(dp), "实际=" + dp);
    }

    @Test
    @DisplayName("ΔP：Pplat=30, PEEP=8 → 22（超出肺保护性通气阈值 15）")
    void deltaP_aboveThreshold() {
        BigDecimal dp = svc.calcDeltaP(new BigDecimal("30"), new BigDecimal("8"));
        assertEquals(0, new BigDecimal("22").compareTo(dp));
    }

    @Test
    @DisplayName("ΔP：Pplat=18, PEEP=5 → 13（达标 ΔP<15）")
    void deltaP_withinThreshold() {
        BigDecimal dp = svc.calcDeltaP(new BigDecimal("18"), new BigDecimal("5"));
        assertEquals(0, new BigDecimal("13").compareTo(dp));
    }

    @Test
    @DisplayName("ΔP：Pplat 缺失 → null")
    void deltaP_missingPplat() {
        assertNull(svc.calcDeltaP(null, new BigDecimal("5")));
    }

    @Test
    @DisplayName("ΔP：PEEP 缺失 → null")
    void deltaP_missingPeep() {
        assertNull(svc.calcDeltaP(new BigDecimal("25"), null));
    }

    @Test
    @DisplayName("ΔP：都缺失 → null")
    void deltaP_bothMissing() {
        assertNull(svc.calcDeltaP(null, null));
    }

    @Test
    @DisplayName("ΔP：Pplat<PEEP 时为负（数学正确，临床异常由界面标红）")
    void deltaP_negative() {
        BigDecimal dp = svc.calcDeltaP(new BigDecimal("3"), new BigDecimal("5"));
        assertEquals(0, new BigDecimal("-2").compareTo(dp));
    }

    // -------------------------------------------------------------- P/F 氧合指数

    @Test
    @DisplayName("P/F：PaO2=90, FiO2=60 → 150（中度 ARDS 上限）")
    void pfRatio_normal() {
        BigDecimal pf = svc.calcPf(new BigDecimal("90"), new BigDecimal("60"));
        assertEquals(0, new BigDecimal("150").compareTo(pf), "实际=" + pf);
    }

    @Test
    @DisplayName("P/F：PaO2=60, FiO2=100 → 60（重度 ARDS）")
    void pfRatio_severe() {
        BigDecimal pf = svc.calcPf(new BigDecimal("60"), new BigDecimal("100"));
        assertEquals(0, new BigDecimal("60").compareTo(pf));
    }

    @Test
    @DisplayName("P/F：PaO2=100, FiO2=40 → 250（轻度 ARDS）")
    void pfRatio_mild() {
        BigDecimal pf = svc.calcPf(new BigDecimal("100"), new BigDecimal("40"));
        assertEquals(0, new BigDecimal("250").compareTo(pf));
    }

    @Test
    @DisplayName("P/F：PaO2=300, FiO2=100 → 300（ARDS 排除阈值）")
    void pfRatio_threshold() {
        BigDecimal pf = svc.calcPf(new BigDecimal("300"), new BigDecimal("100"));
        assertEquals(0, new BigDecimal("300").compareTo(pf));
    }

    @Test
    @DisplayName("P/F：四舍五入 95/60 → 158.33 → 158（HALF_UP）")
    void pfRatio_roundingDown() {
        BigDecimal pf = svc.calcPf(new BigDecimal("95"), new BigDecimal("60"));
        assertEquals(0, new BigDecimal("158").compareTo(pf), "实际=" + pf);
    }

    @Test
    @DisplayName("P/F：四舍五入 95/59 → 161.02 → 161")
    void pfRatio_roundingUp() {
        BigDecimal pf = svc.calcPf(new BigDecimal("95"), new BigDecimal("59"));
        assertEquals(0, new BigDecimal("161").compareTo(pf));
    }

    @Test
    @DisplayName("P/F：FiO2=0 → null（除零保护）")
    void pfRatio_zeroFio2() {
        assertNull(svc.calcPf(new BigDecimal("90"), BigDecimal.ZERO));
    }

    @Test
    @DisplayName("P/F：FiO2 负数 → null")
    void pfRatio_negativeFio2() {
        assertNull(svc.calcPf(new BigDecimal("90"), new BigDecimal("-5")));
    }

    @Test
    @DisplayName("P/F：PaO2 缺失 → null")
    void pfRatio_missingPao2() {
        assertNull(svc.calcPf(null, new BigDecimal("60")));
    }

    @Test
    @DisplayName("P/F：FiO2 缺失 → null")
    void pfRatio_missingFio2() {
        assertNull(svc.calcPf(new BigDecimal("90"), null));
    }

    @Test
    @DisplayName("P/F：都缺失 → null")
    void pfRatio_bothMissing() {
        assertNull(svc.calcPf(null, null));
    }

    // -------------------------------------------------------------- P/F 变化

    @Test
    @DisplayName("P/F变化：当前 200, T0 100 → +100（氧合改善）")
    void pfDelta_improved() {
        BigDecimal delta = svc.calcPfDelta(new BigDecimal("200"), new BigDecimal("100"));
        assertEquals(0, new BigDecimal("100").compareTo(delta));
    }

    @Test
    @DisplayName("P/F变化：当前 80, T0 150 → -70（氧合恶化）")
    void pfDelta_worsened() {
        BigDecimal delta = svc.calcPfDelta(new BigDecimal("80"), new BigDecimal("150"));
        assertEquals(0, new BigDecimal("-70").compareTo(delta));
    }

    @Test
    @DisplayName("P/F变化：当前 150, T0 150 → 0")
    void pfDelta_noChange() {
        BigDecimal delta = svc.calcPfDelta(new BigDecimal("150"), new BigDecimal("150"));
        assertEquals(0, BigDecimal.ZERO.compareTo(delta));
    }

    @Test
    @DisplayName("P/F变化：当前缺失 → null")
    void pfDelta_missingCurrent() {
        assertNull(svc.calcPfDelta(null, new BigDecimal("100")));
    }

    @Test
    @DisplayName("P/F变化：T0 缺失 → null")
    void pfDelta_missingBase() {
        assertNull(svc.calcPfDelta(new BigDecimal("200"), null));
    }

    @Test
    @DisplayName("P/F变化：都缺失 → null")
    void pfDelta_bothMissing() {
        assertNull(svc.calcPfDelta(null, null));
    }

    // -------------------------------------------------------------- OI 氧指数

    @Test
    @DisplayName("OI：FiO2=60, PEEP=5, Pplat=25, PaO2=90 → Paw=15, OI=1000")
    void oi_normal() {
        // ΔP=20, Paw=5+10=15, OI=60*15*100/90=1000
        BigDecimal oi = svc.calcOi(new BigDecimal("60"), new BigDecimal("5"),
                new BigDecimal("25"), new BigDecimal("90"));
        assertEquals(0, new BigDecimal("1000").compareTo(oi), "实际=" + oi);
    }

    @Test
    @DisplayName("OI：验证 Paw 计算 PEEP=8, Pplat=28 → ΔP=20, Paw=18, OI=1200")
    void oi_pawCalculation() {
        // OI=50*18*100/75=1200
        BigDecimal oi = svc.calcOi(new BigDecimal("50"), new BigDecimal("8"),
                new BigDecimal("28"), new BigDecimal("75"));
        assertEquals(0, new BigDecimal("1200").compareTo(oi), "实际=" + oi);
    }

    @Test
    @DisplayName("OI：重度氧合障碍 FiO2=100, PEEP=10, Pplat=30, PaO2=50 → OI=4000")
    void oi_severe() {
        // ΔP=20, Paw=20, OI=100*20*100/50=4000
        BigDecimal oi = svc.calcOi(new BigDecimal("100"), new BigDecimal("10"),
                new BigDecimal("30"), new BigDecimal("50"));
        assertEquals(0, new BigDecimal("4000").compareTo(oi), "实际=" + oi);
    }

    @Test
    @DisplayName("OI：PaO2=0 → null（除零保护）")
    void oi_zeroPao2() {
        assertNull(svc.calcOi(new BigDecimal("60"), new BigDecimal("5"),
                new BigDecimal("25"), BigDecimal.ZERO));
    }

    @Test
    @DisplayName("OI：PaO2 负数 → null")
    void oi_negativePao2() {
        assertNull(svc.calcOi(new BigDecimal("60"), new BigDecimal("5"),
                new BigDecimal("25"), new BigDecimal("-10")));
    }

    @Test
    @DisplayName("OI：FiO2 缺失 → null")
    void oi_missingFio2() {
        assertNull(svc.calcOi(null, new BigDecimal("5"),
                new BigDecimal("25"), new BigDecimal("90")));
    }

    @Test
    @DisplayName("OI：PEEP 缺失 → null")
    void oi_missingPeep() {
        assertNull(svc.calcOi(new BigDecimal("60"), null,
                new BigDecimal("25"), new BigDecimal("90")));
    }

    @Test
    @DisplayName("OI：Pplat 缺失 → null")
    void oi_missingPplat() {
        assertNull(svc.calcOi(new BigDecimal("60"), new BigDecimal("5"),
                null, new BigDecimal("90")));
    }

    @Test
    @DisplayName("OI：PaO2 缺失 → null")
    void oi_missingPao2() {
        assertNull(svc.calcOi(new BigDecimal("60"), new BigDecimal("5"),
                new BigDecimal("25"), null));
    }

    @Test
    @DisplayName("OI：都缺失 → null")
    void oi_allMissing() {
        assertNull(svc.calcOi(null, null, null, null));
    }

    @Test
    @DisplayName("OI：Pplat=PEEP 时 ΔP=0, Paw=PEEP → FiO2=40, PEEP=5, PaO2=80 → OI=250")
    void oi_zeroDeltaP() {
        // ΔP=0, Paw=5, OI=40*5*100/80=250
        BigDecimal oi = svc.calcOi(new BigDecimal("40"), new BigDecimal("5"),
                new BigDecimal("5"), new BigDecimal("80"));
        assertEquals(0, new BigDecimal("250").compareTo(oi), "实际=" + oi);
    }

    // -------------------------------------------------------------- 跨公式一致性

    @Test
    @DisplayName("跨公式一致性：同一组输入下 ΔP=20, P/F=150, OI=1000 可互相印证")
    void crossFormula_consistency() {
        BigDecimal peep = new BigDecimal("5");
        BigDecimal pplat = new BigDecimal("25");
        BigDecimal fio2 = new BigDecimal("60");
        BigDecimal pao2 = new BigDecimal("90");

        BigDecimal dp = svc.calcDeltaP(pplat, peep);
        BigDecimal pf = svc.calcPf(pao2, fio2);
        BigDecimal oi = svc.calcOi(fio2, peep, pplat, pao2);

        assertEquals(0, new BigDecimal("20").compareTo(dp));
        assertEquals(0, new BigDecimal("150").compareTo(pf));
        assertEquals(0, new BigDecimal("1000").compareTo(oi));

        // 一致性校验：OI = Paw * 10000 / P/F，其中 Paw = PEEP + ΔP/2
        BigDecimal paw = peep.add(dp.divide(new BigDecimal("2"), 4, java.math.RoundingMode.HALF_UP));
        BigDecimal oiFromPf = paw.multiply(new BigDecimal("10000"))
                .divide(pf, 0, java.math.RoundingMode.HALF_UP);
        assertEquals(0, oi.compareTo(oiFromPf),
                "OI 由 P/F 反推应一致：直接=" + oi + " 反推=" + oiFromPf);
    }
}
