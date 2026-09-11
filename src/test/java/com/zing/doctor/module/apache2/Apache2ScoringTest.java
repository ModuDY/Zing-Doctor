package com.zing.doctor.module.apache2;

import com.zing.doctor.module.apache2.service.impl.Apache2ServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * APACHE II 评分算法规格测试（对照 Knaus WA 1985 原文与 MDCalc 标准阈值）。
 *
 * <p>不加载 Spring 上下文、不连接数据库：{@link Apache2ServiceImpl#calculateScore} 为纯计算，
 * 直接 new 出实例即可验证每项阈值与总分/死亡率公式。
 */
class Apache2ScoringTest {

    private final Apache2ServiceImpl svc = new Apache2ServiceImpl();

    /** 全部取正常值的基线（各项应得 0 分，总分 0）。 */
    private Map<String, Object> base() {
        Map<String, Object> p = new HashMap<>();
        p.put("age", 40);
        p.put("chronicHealth", "none");
        p.put("gcsTotal", 15);
        p.put("temperature", 37.0);
        p.put("map", 90.0);
        p.put("heartRate", 80.0);
        p.put("respiratoryRate", 16.0);
        p.put("fio2", 21.0);
        p.put("pao2", 100.0);
        p.put("aado2", 10.0);
        p.put("ph", 7.40);
        p.put("sodium", 140.0);
        p.put("potassium", 4.0);
        p.put("creatinine", 80.0);
        p.put("hct", 40.0);
        p.put("wbc", 8.0);
        p.put("acuteRenalFailure", false);
        p.put("diagnosisType", "none");
        p.put("emergencySurgery", false);
        p.put("diagnosisWeight", 0.0);
        return p;
    }

    private Map<String, Object> with(String key, Object value) {
        Map<String, Object> p = base();
        p.put(key, value);
        return p;
    }

    private Map<String, Object> with(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> p = with(k1, v1);
        p.put(k2, v2);
        return p;
    }

    private int aps(Map<String, Object> params, String key) {
        Map<String, Object> r = svc.calculateScore(params);
        @SuppressWarnings("unchecked")
        Map<String, Integer> aps = (Map<String, Integer>) r.get("apsScores");
        return aps.get(key);
    }

    private int intOf(Map<String, Object> params, String key) {
        return ((Number) svc.calculateScore(params).get(key)).intValue();
    }

    // ---------------- A 年龄 ----------------

    @Test
    @DisplayName("A 年龄评分边界：≤44=0 / 45-54=2 / 55-64=3 / 65-74=5 / ≥75=6")
    void ageBoundaries() {
        assertEquals(0, intOf(with("age", 44), "ageScore"));
        assertEquals(2, intOf(with("age", 45), "ageScore"));
        assertEquals(2, intOf(with("age", 54), "ageScore"));
        assertEquals(3, intOf(with("age", 55), "ageScore"));
        assertEquals(3, intOf(with("age", 64), "ageScore"));
        assertEquals(5, intOf(with("age", 65), "ageScore"));
        assertEquals(5, intOf(with("age", 74), "ageScore"));
        assertEquals(6, intOf(with("age", 75), "ageScore"));
    }

    // ---------------- B 慢性健康 ----------------

    @Test
    @DisplayName("B 慢性健康：none=0 / elective(择期术后)=2 / nonoperative(非手术或急症术后)=5")
    void chronicBoundaries() {
        assertEquals(0, intOf(with("chronicHealth", "none"), "chronicScore"));
        assertEquals(2, intOf(with("chronicHealth", "elective"), "chronicScore"));
        assertEquals(5, intOf(with("chronicHealth", "nonoperative"), "chronicScore"));
    }

    // ---------------- C GCS ----------------

    @Test
    @DisplayName("C GCS：C=15-GCS，合法 3-15；缺评或越界一律按未评估计 0")
    void gcsRule() {
        assertEquals(12, intOf(with("gcsTotal", 3), "gcsScore"));
        assertEquals(0, intOf(with("gcsTotal", 15), "gcsScore"));
        assertEquals(0, intOf(with("gcsTotal", null), "gcsScore"));
        assertEquals(0, intOf(with("gcsTotal", 2), "gcsScore"));
        assertEquals(0, intOf(with("gcsTotal", 16), "gcsScore"));
        assertEquals(0, intOf(with("gcsTotal", ""), "gcsScore"));
    }

    // ---------------- D 12 项急性生理 ----------------

    @Test
    @DisplayName("D1 体温：≥41=4 / 39-40.9=3 / 38.5-38.9=1 / 36-38.4=0 / 34-35.9=1 / 32-33.9=2 / 30-31.9=3 / <30=4")
    void temperatureBoundaries() {
        assertEquals(4, aps(with("temperature", 41.0), "temperature"));
        assertEquals(3, aps(with("temperature", 39.0), "temperature"));
        assertEquals(1, aps(with("temperature", 38.5), "temperature"));
        assertEquals(0, aps(with("temperature", 36.0), "temperature"));
        assertEquals(1, aps(with("temperature", 34.0), "temperature"));
        assertEquals(2, aps(with("temperature", 32.0), "temperature"));
        assertEquals(3, aps(with("temperature", 30.0), "temperature"));
        assertEquals(4, aps(with("temperature", 29.9), "temperature"));
    }

    @Test
    @DisplayName("D2 平均动脉压：≥160=4 / 130-159=3 / 110-129=2 / 70-109=0 / 50-69=2 / <50=4")
    void mapBoundaries() {
        assertEquals(4, aps(with("map", 160.0), "map"));
        assertEquals(3, aps(with("map", 130.0), "map"));
        assertEquals(2, aps(with("map", 110.0), "map"));
        assertEquals(0, aps(with("map", 70.0), "map"));
        assertEquals(2, aps(with("map", 50.0), "map"));
        assertEquals(4, aps(with("map", 49.9), "map"));
    }

    @Test
    @DisplayName("D3 心率：≥180=4 / 140-179=3 / 110-139=2 / 70-109=0 / 55-69=2 / 40-54=3 / <40=4")
    void heartRateBoundaries() {
        assertEquals(4, aps(with("heartRate", 180.0), "heartRate"));
        assertEquals(3, aps(with("heartRate", 140.0), "heartRate"));
        assertEquals(2, aps(with("heartRate", 110.0), "heartRate"));
        assertEquals(0, aps(with("heartRate", 70.0), "heartRate"));
        assertEquals(2, aps(with("heartRate", 55.0), "heartRate"));
        assertEquals(3, aps(with("heartRate", 40.0), "heartRate"));
        assertEquals(4, aps(with("heartRate", 39.0), "heartRate"));
    }

    @Test
    @DisplayName("D4 呼吸频率：≥50=4 / 35-49=3 / 25-34=1 / 12-24=0 / 10-11=1 / 6-9=2 / <6=4")
    void respRateBoundaries() {
        assertEquals(4, aps(with("respiratoryRate", 50.0), "respiratoryRate"));
        assertEquals(3, aps(with("respiratoryRate", 35.0), "respiratoryRate"));
        assertEquals(1, aps(with("respiratoryRate", 25.0), "respiratoryRate"));
        assertEquals(0, aps(with("respiratoryRate", 12.0), "respiratoryRate"));
        assertEquals(1, aps(with("respiratoryRate", 10.0), "respiratoryRate"));
        assertEquals(2, aps(with("respiratoryRate", 6.0), "respiratoryRate"));
        assertEquals(4, aps(with("respiratoryRate", 5.0), "respiratoryRate"));
    }

    @Test
    @DisplayName("D5 氧合(FiO2≥0.5 用 A-aDO2)：≥500=4 / 350-499=3 / 200-349=2 / <200=0")
    void oxygenAado2Boundaries() {
        assertEquals(4, aps(with("fio2", 60.0, "aado2", 500.0), "oxygen"));
        assertEquals(3, aps(with("fio2", 60.0, "aado2", 350.0), "oxygen"));
        assertEquals(2, aps(with("fio2", 60.0, "aado2", 200.0), "oxygen"));
        assertEquals(0, aps(with("fio2", 60.0, "aado2", 199.0), "oxygen"));
    }

    @Test
    @DisplayName("D5 氧合(FiO2<0.5 用 PaO2)：PaO2 > 70 应为 0 分")
    void oxygenPao2Above70() {
        assertEquals(0, aps(with("pao2", 80.0), "oxygen"));
        assertEquals(0, aps(with("pao2", 71.0), "oxygen"));
    }

    @Test
    @DisplayName("D5 氧合(FiO2<0.5 用 PaO2)：PaO2 61-70 应为 1 分")
    void oxygenPao2MidRange() {
        assertEquals(1, aps(with("pao2", 70.0), "oxygen"));
        assertEquals(1, aps(with("pao2", 65.0), "oxygen"));
        assertEquals(1, aps(with("pao2", 62.0), "oxygen"));
    }

    @Test
    @DisplayName("D5 氧合(FiO2<0.5 用 PaO2)：PaO2 55-60=3 / <55=4")
    void oxygenPao2LowRange() {
        assertEquals(3, aps(with("pao2", 60.0), "oxygen"));
        assertEquals(3, aps(with("pao2", 55.0), "oxygen"));
        assertEquals(4, aps(with("pao2", 54.0), "oxygen"));
    }

    @Test
    @DisplayName("D6 动脉血pH：≥7.7=4 / 7.6-7.69=3 / 7.5-7.59=1 / 7.33-7.49=0 / 7.25-7.32=2 / 7.15-7.24=3 / <7.15=4")
    void phBoundaries() {
        assertEquals(4, aps(with("ph", 7.70), "ph"));
        assertEquals(3, aps(with("ph", 7.60), "ph"));
        assertEquals(1, aps(with("ph", 7.50), "ph"));
        assertEquals(0, aps(with("ph", 7.33), "ph"));
        assertEquals(2, aps(with("ph", 7.25), "ph"));
        assertEquals(3, aps(with("ph", 7.15), "ph"));
        assertEquals(4, aps(with("ph", 7.14), "ph"));
    }

    @Test
    @DisplayName("D7 血清钠：≥180=4 / 160-179=3 / 155-159=2 / 150-154=1 / 130-149=0 / 120-129=2 / 111-119=3 / <111=4")
    void sodiumBoundaries() {
        assertEquals(4, aps(with("sodium", 180.0), "sodium"));
        assertEquals(3, aps(with("sodium", 160.0), "sodium"));
        assertEquals(2, aps(with("sodium", 155.0), "sodium"));
        assertEquals(1, aps(with("sodium", 150.0), "sodium"));
        assertEquals(0, aps(with("sodium", 130.0), "sodium"));
        assertEquals(2, aps(with("sodium", 120.0), "sodium"));
        assertEquals(3, aps(with("sodium", 111.0), "sodium"));
        assertEquals(4, aps(with("sodium", 110.0), "sodium"));
    }

    @Test
    @DisplayName("D8 血清钾：≥7=4 / 6-6.9=3 / 5.5-5.9=1 / 3.5-5.4=0 / 3-3.4=1 / 2.5-2.9=2 / <2.5=4")
    void potassiumBoundaries() {
        assertEquals(4, aps(with("potassium", 7.0), "potassium"));
        assertEquals(3, aps(with("potassium", 6.0), "potassium"));
        assertEquals(1, aps(with("potassium", 5.5), "potassium"));
        assertEquals(0, aps(with("potassium", 3.5), "potassium"));
        assertEquals(1, aps(with("potassium", 3.0), "potassium"));
        assertEquals(2, aps(with("potassium", 2.5), "potassium"));
        assertEquals(4, aps(with("potassium", 2.4), "potassium"));
    }

    @Test
    @DisplayName("D9 血清肌酐(μmol/L→mg/dL)：≥3.5=4 / 2-3.4=3 / 1.5-1.9=2 / 0.6-1.4=0 / <0.6=2；急性肾衰翻倍")
    void creatinineBoundaries() {
        assertEquals(4, aps(with("creatinine", 310.0), "creatinine"));   // 3.51 mg/dL
        assertEquals(3, aps(with("creatinine", 177.0), "creatinine"));   // 2.00 mg/dL
        assertEquals(2, aps(with("creatinine", 133.0), "creatinine"));   // 1.50 mg/dL
        assertEquals(0, aps(with("creatinine", 80.0), "creatinine"));    // 0.90 mg/dL
        assertEquals(2, aps(with("creatinine", 50.0), "creatinine"));    // 0.57 mg/dL
        assertEquals(4, aps(with("creatinine", 133.0, "acuteRenalFailure", true), "creatinine"));
    }

    @Test
    @DisplayName("D10 血细胞比容：≥60=4 / 50-59.9=2 / 46-49.9=1 / 30-45.9=0 / 20-29.9=2 / <20=4")
    void hctBoundaries() {
        assertEquals(4, aps(with("hct", 60.0), "hct"));
        assertEquals(2, aps(with("hct", 50.0), "hct"));
        assertEquals(1, aps(with("hct", 46.0), "hct"));
        assertEquals(0, aps(with("hct", 30.0), "hct"));
        assertEquals(2, aps(with("hct", 20.0), "hct"));
        assertEquals(4, aps(with("hct", 19.9), "hct"));
    }

    @Test
    @DisplayName("D11 白细胞：≥40=4 / 20-39.9=2 / 15-19.9=1 / 3-14.9=0 / 1-2.9=2 / <1=4")
    void wbcBoundaries() {
        assertEquals(4, aps(with("wbc", 40.0), "wbc"));
        assertEquals(2, aps(with("wbc", 20.0), "wbc"));
        assertEquals(1, aps(with("wbc", 15.0), "wbc"));
        assertEquals(0, aps(with("wbc", 3.0), "wbc"));
        assertEquals(2, aps(with("wbc", 1.0), "wbc"));
        assertEquals(4, aps(with("wbc", 0.9), "wbc"));
    }

    // ---------------- 总分 ----------------

    @Test
    @DisplayName("总分 = A + B + C + D")
    void totalScoreIsSum() {
        Map<String, Object> p = base();
        p.put("age", 70);                        // A=5
        p.put("chronicHealth", "nonoperative");  // B=5
        p.put("gcsTotal", 9);                    // C=6
        p.put("temperature", 41.0);              // D=4
        p.put("map", 160.0);                     // D=4
        Map<String, Object> r = svc.calculateScore(p);
        int a = ((Number) r.get("ageScore")).intValue();
        int b = ((Number) r.get("chronicScore")).intValue();
        int c = ((Number) r.get("gcsScore")).intValue();
        int d = ((Number) r.get("physiologyScore")).intValue();
        int total = ((Number) r.get("totalScore")).intValue();
        assertEquals(5, a);
        assertEquals(5, b);
        assertEquals(6, c);
        assertEquals(8, d);
        assertEquals(a + b + c + d, total);
    }

    // ---------------- 死亡率预测 ----------------

    /**
     * 标准 APACHE II 死亡率方程（Knaus 1985 / MDCalc / ClinCalc）：
     * ln(R/1-R) = -3.517 + (APACHE II 总分 × 0.146) + 0.603(非手术或急症术后) + 诊断分类权重。
     * 其中 "Points" 为 APACHE II 总分（APS + 年龄 + 慢性健康），而非仅 APS。
     */
    @Test
    @DisplayName("死亡率方程应使用 APACHE II 总分(A+B+C+D)而非仅 APS")
    void mortalityUsesTotalApache2Score() {
        Map<String, Object> p = base();
        p.put("age", 70);                        // A=5
        p.put("chronicHealth", "nonoperative");  // B=5
        p.put("gcsTotal", 15);                   // C=0
        p.put("diagnosisType", "nonoperative");
        p.put("diagnosisWeight", -0.705);
        Map<String, Object> r = svc.calculateScore(p);
        int total = ((Number) r.get("totalScore")).intValue();
        assertEquals(10, total, "基线生理正常时总分应为年龄5+慢性5=10");

        double expectedLogit = -3.5174 + total * 0.1467 + 0.6031 + (-0.705);
        double expectedMortality = 1.0 / (1.0 + Math.exp(-expectedLogit)) * 100.0;
        double actual = ((Number) r.get("mortalityRate")).doubleValue();
        assertEquals(expectedMortality, actual, 0.2,
                "死亡率应基于 APACHE II 总分计算；实际值与基于总分的结果不一致，说明只用了 APS");
    }
}
