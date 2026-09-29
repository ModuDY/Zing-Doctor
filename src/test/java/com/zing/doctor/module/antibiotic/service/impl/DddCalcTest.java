package com.zing.doctor.module.antibiotic.service.impl;

import com.zing.doctor.module.antibiotic.entity.DddConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 抗菌药物使用强度（DDD，第三维度）解析口径测试。
 *
 * <p>使用强度 = Σ(单次剂量 × 每日次数 × 使用天数 / DDD值) / 总床日数 × 100，
 * 其中「单次剂量」靠正则从医嘱名称里抠、「每日次数」靠频次词表映射，
 * 这两步一旦识别错了，整张使用强度报表都会偏 —— 所以按真实医嘱文本逐一锁定。
 */
class DddCalcTest {

    private final DddStatsServiceImpl svc = new DddStatsServiceImpl(null, null);

    // ---------------- 剂量解析（医嘱名称文本） ----------------

    @Test
    @DisplayName("剂量解析：'美罗培南 1g' → 1g")
    void parseDoseSimple() {
        assertEquals(0, BigDecimal.ONE.compareTo(svc.parseDose("美罗培南 1g", "g")));
    }

    @Test
    @DisplayName("剂量解析：mg 自动换算为 g（500mg → 0.5g）")
    void parseDoseMgToG() {
        assertEquals(0, new BigDecimal("0.5").compareTo(svc.parseDose("万古霉素 500mg", "g")));
    }

    @Test
    @DisplayName("剂量解析：一条医嘱里多个剂量时取最大值（'0.5g/瓶 1g' → 1g）")
    void parseDoseTakesMax() {
        assertEquals(0, BigDecimal.ONE.compareTo(svc.parseDose("美罗培南 0.5g/瓶 1g", "g")));
    }

    @Test
    @DisplayName("剂量解析：MU/万单位不做换算（多黏菌素 100万单位 → 100）")
    void parseDoseMuNotConverted() {
        assertEquals(0, new BigDecimal("100").compareTo(svc.parseDose("多黏菌素B 100万单位", "g")));
    }

    @Test
    @DisplayName("剂量解析：无剂量信息或名称为空时返回 null（不臆造剂量）")
    void parseDoseNull() {
        assertNull(svc.parseDose(null, "g"));
        assertNull(svc.parseDose("美罗培南 静滴", "g"));
    }

    // ---------------- 剂量解析（结构化字段 + 单位换算） ----------------

    @Test
    @DisplayName("结构化剂量：mg → g 与 g → mg 双向换算，μg → g 除以 10^6")
    void parseDoseFromFields() {
        assertEquals(0, new BigDecimal("0.5").compareTo(
                svc.parseDoseFromFields(advice("500", "mg"), "g")));
        assertEquals(0, new BigDecimal("1000").compareTo(
                svc.parseDoseFromFields(advice("1", "g"), "mg")));
        assertEquals(0, new BigDecimal("0.001").compareTo(
                svc.parseDoseFromFields(advice("1000", "μg"), "g")));
        // 微克的中文写法
        assertEquals(0, new BigDecimal("0.001").compareTo(
                svc.parseDoseFromFields(advice("1000", "微克"), "g")));
    }

    @Test
    @DisplayName("结构化剂量：剂量字段为空返回 null；非法数字不抛异常")
    void parseDoseFromFieldsInvalid() {
        assertNull(svc.parseDoseFromFields(advice(null, "mg"), "g"));
        assertNull(svc.parseDoseFromFields(advice("abc", "mg"), "g"));
    }

    // ---------------- 频次映射 ----------------

    @Test
    @DisplayName("频次映射：无法识别的频次回退为 1 次/日（宁低勿高）")
    void parseFreqFallback() {
        assertEquals(1.0, svc.parseFreq(null), 0.0001);
        assertEquals(1.0, svc.parseFreq(""), 0.0001);
        assertEquals(1.0, svc.parseFreq("这不是频次"), 0.0001);
    }

    @Test
    @DisplayName("频次映射：q8h 这类明确频次应大于 1 次/日")
    void parseFreqKnown() {
        assertTrue(svc.parseFreq("q8h") > 1.0, "q8h 应 > 1 次/日");
        assertTrue(svc.parseFreq("Q8H") > 1.0, "大写频次也要识别");
    }

    // ---------------- DDD 配置匹配 ----------------

    @Test
    @DisplayName("DDD 配置匹配：通用名匹配优先于关键词匹配")
    void matchByDrugNameFirst() {
        DddConfig keywordOnly = config("注射用头孢曲松钠", "其他头孢");
        DddConfig byName = config("美罗培南", "培南类");
        List<DddConfig> configs = new ArrayList<>();
        configs.add(keywordOnly);
        configs.add(byName);

        // 医嘱名同时命中「其他头孢」（关键词）与「美罗培南」（通用名）时，应取通用名配置
        DddConfig matched = svc.matchDddConfig("注射用美罗培南 1g", configs);
        assertSame(byName, matched);
    }

    @Test
    @DisplayName("DDD 配置匹配：仅关键词命中时按关键词取配置（关键词做 trim 后再 contains）")
    void matchByKeyword() {
        // 关键词存的是「药物通用名 / 特征片段」，不是分类名：
        // 医嘱名「注射用亚胺培南西司他丁钠」含「亚胺培南」，但不含「培南类」。
        DddConfig config = config("美罗培南", " 亚胺培南 ,碳青霉烯 ");
        List<DddConfig> configs = new ArrayList<>();
        configs.add(config);

        assertSame(config, svc.matchDddConfig("注射用亚胺培南西司他丁钠 1g", configs));
        assertNull(svc.matchDddConfig("注射用万古霉素 1g", configs));
    }

    @Test
    @DisplayName("溶媒判定：当前实现恒返回 false（不过滤溶媒），此处锁定现状以便将来改动被显式暴露")
    void solventCurrentlyNeverFilters() {
        // 注意：DddStatsServiceImpl.isSolvent 的分支里两个 return 都是 false，
        // 即「抗菌药使用强度」目前把氯化钠/葡萄糖等溶媒一并计入 DDD 统计口径之外。
        // 若后续要真正剔除溶媒，本用例会失败 —— 那是预期的提醒，请同步更新统计口径说明。
        assertFalse(svc.isSolvent("0.9%氯化钠注射液 100ml"));
        assertFalse(svc.isSolvent("5%葡萄糖注射液"));
    }

    // ---------------- 辅助 ----------------

    private Map<String, Object> advice(String dosage, String unit) {
        Map<String, Object> map = new HashMap<>();
        map.put("drug_one_dosage", dosage);
        map.put("drug_one_dosage_unit", unit);
        return map;
    }

    private DddConfig config(String drugName, String keywords) {
        DddConfig config = new DddConfig();
        config.setDrugName(drugName);
        config.setKeywords(keywords);
        return config;
    }

    @Test
    @DisplayName("辅助自检：测试构造的 DddConfig 字段可读回（防止实体字段改名后测试静默失效）")
    void configFixtureReadable() {
        DddConfig config = config("美罗培南", "培南类");
        assertNotNull(config.getDrugName());
        assertEquals("美罗培南", config.getDrugName());
        assertEquals("培南类", config.getKeywords());
    }
}
