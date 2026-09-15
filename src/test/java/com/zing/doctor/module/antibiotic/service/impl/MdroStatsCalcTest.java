package com.zing.doctor.module.antibiotic.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 细菌培养检出监测（MDRO，第四维度）判定口径测试。
 *
 * <p>覆盖三类「数错会让整张监测表失真」的判定：
 * <ul>
 *   <li>把「厌氧培养5天未检出细菌」「杂菌生长」这类非菌株结果当成菌株统计</li>
 *   <li>在科天数与统计周期交集算错，导致阳性率分母偏大/偏小</li>
 *   <li>菌种分类映射错误，革兰阳/阴性构成比随之出错</li>
 * </ul>
 */
class MdroStatsCalcTest {

    private final MdroStatsServiceImpl svc = new MdroStatsServiceImpl(null, null);

    // ---------------- 非细菌/真菌结果过滤 ----------------

    @Test
    @DisplayName("非菌株结果：培养描述（含数字+天）与「生长」类描述都要被排除")
    void nonBacteriaFungiFiltered() {
        assertTrue(svc.isNonBacteriaFungi("厌氧培养5天未检出细菌"));
        assertTrue(svc.isNonBacteriaFungi("需氧培养5天未检出细菌"));
        assertTrue(svc.isNonBacteriaFungi("无细菌生长"));
        assertTrue(svc.isNonBacteriaFungi("杂菌生长"));
        assertTrue(svc.isNonBacteriaFungi("正常菌群生长"));
    }

    @Test
    @DisplayName("非菌株结果：空值按「非菌株」处理（不参与统计）")
    void nonBacteriaFungiEmpty() {
        assertTrue(svc.isNonBacteriaFungi(null));
        assertTrue(svc.isNonBacteriaFungi(""));
    }

    @Test
    @DisplayName("真实菌株：具体菌名不应被过滤")
    void realBacteriaKept() {
        assertFalse(svc.isNonBacteriaFungi("金黄色葡萄球菌"));
        assertFalse(svc.isNonBacteriaFungi("铜绿假单胞菌"));
        assertFalse(svc.isNonBacteriaFungi("白色念珠菌"));
        assertFalse(svc.isNonBacteriaFungi("鲍曼不动杆菌"));
    }

    // ---------------- 菌种分类映射 ----------------

    @Test
    @DisplayName("菌种分类：gram_positive / gram_negative / fungi 映射为中文，其余归「其他」")
    void classNameMapping() {
        assertEquals("革兰阳性菌", svc.getClassName("gram_positive"));
        assertEquals("革兰阴性菌", svc.getClassName("gram_negative"));
        assertEquals("真菌", svc.getClassName("fungi"));
        assertEquals("其他", svc.getClassName("unknown_class"));
        assertEquals("其他", svc.getClassName(null));
    }

    // ---------------- 在科天数 ----------------

    @Test
    @DisplayName("在科天数：入科当天计 1 天（9-01 至 9-30 在科 = 30 天）")
    void inDepartDaysFullMonth() {
        int days = svc.calcInDepartDays(
                "2026-09-01 08:00:00", "2026-09-30 18:00:00",
                "2026-09-01 00:00:00", "2026-09-30 23:59:59");
        assertEquals(30, days);
    }

    @Test
    @DisplayName("在科天数：与统计周期取交集（跨月患者只算周期内天数）")
    void inDepartDaysIntersectPeriod() {
        // 患者 08-20 入科、10-05 出科；统计周期 09 月 → 只算 09-01~09-30 共 30 天
        int days = svc.calcInDepartDays(
                "2026-08-20 10:00:00", "2026-10-05 09:00:00",
                "2026-09-01 00:00:00", "2026-09-30 23:59:59");
        assertEquals(30, days);
    }

    @Test
    @DisplayName("在科天数：完全落在统计周期外时为 0；入科时间缺失也为 0")
    void inDepartDaysNoOverlap() {
        assertEquals(0, svc.calcInDepartDays(
                "2026-08-01 08:00:00", "2026-08-10 18:00:00",
                "2026-09-01 00:00:00", "2026-09-30 23:59:59"));
        assertEquals(0, svc.calcInDepartDays(
                null, "2026-09-10 18:00:00",
                "2026-09-01 00:00:00", "2026-09-30 23:59:59"));
    }

    // ---------------- 与统计周期的交集判定 ----------------

    @Test
    @DisplayName("周期交集：入科在周期内 → 有交集；入科晚于周期结束 → 无交集")
    void overlapBasic() {
        assertTrue(svc.isInDepartOverlap(
                "2026-09-15 10:00:00", "2026-09-20 10:00:00",
                "2026-09-01 00:00:00", "2026-09-30 23:59:59"));
        assertFalse(svc.isInDepartOverlap(
                "2026-10-05 10:00:00", "2026-10-10 10:00:00",
                "2026-09-01 00:00:00", "2026-09-30 23:59:59"));
    }

    @Test
    @DisplayName("周期交集：出科早于周期开始 → 无交集；仍在科（出科为空）→ 有交集")
    void overlapOutDepart() {
        assertFalse(svc.isInDepartOverlap(
                "2026-08-01 10:00:00", "2026-08-10 10:00:00",
                "2026-09-01 00:00:00", "2026-09-30 23:59:59"));
        assertTrue(svc.isInDepartOverlap(
                "2026-09-01 10:00:00", null,
                "2026-09-01 00:00:00", "2026-09-30 23:59:59"));
    }

    @Test
    @DisplayName("周期交集：入科时间缺失时不过滤（保守保留，避免漏计患者）")
    void overlapMissingInDepart() {
        assertTrue(svc.isInDepartOverlap(
                null, "2026-09-20 10:00:00",
                "2026-09-01 00:00:00", "2026-09-30 23:59:59"));
    }
}
