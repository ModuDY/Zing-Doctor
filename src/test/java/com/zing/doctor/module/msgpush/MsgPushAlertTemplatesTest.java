package com.zing.doctor.module.msgpush;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预警短信文案的模板契约。
 *
 * <p>钉住三件事：
 * <ol>
 *   <li>七类规则都有默认文案 —— 现场没配参数时，短信不能是空的；</li>
 *   <li>默认模板里用到的占位符，都能被预警变量集覆盖（渲染后不残留 {@code #xx#}）——
 *       这条防的是「模板改了变量名、变量没跟着改」，那种错误只会在现场收到
 *       「GCS由#gcsFrom#降至9」这种短信时才被发现；</li>
 *   <li>规则集合解析：配置写中文逗号、写小写、写多余空格都要认；写空则一条都不发。</li>
 * </ol>
 */
class MsgPushAlertTemplatesTest {

    /** 与 WorkbenchEnrichService.alertVarsOf + MsgPushServiceImpl 提供的变量集保持一致 */
    private Map<String, String> fullVars() {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("time", "10-16 08:30");
        v.put("patientName", "张三");
        v.put("bedNo", "3床");
        v.put("departName", "重症医学科");
        v.put("wardName", "ICU一区");
        v.put("inHospitalNo", "123456");
        v.put("age", "68");
        v.put("gender", "男");
        v.put("gcsFrom", "12");
        v.put("gcsTo", "9");
        v.put("gcsDrop", "3");
        v.put("proneStart", "10-15 14:00");
        v.put("proneHours", "18");
        v.put("rrValue", "7");
        v.put("brainDiag", "脑出血");
        return v;
    }

    @Test
    @DisplayName("七类规则的默认文案都存在，未知规则返回空串")
    void everyRuleHasDefaultTemplate() {
        for (String rule : MsgPushAlertTemplates.ALL_RULES) {
            assertFalse(MsgPushAlertTemplates.defaultTemplate(rule).isEmpty(),
                    rule + " 缺默认文案");
            assertTrue(MsgPushAlertTemplates.hasDefault(rule), rule + " 应能识别为有默认文案");
        }
        assertEquals("", MsgPushAlertTemplates.defaultTemplate("NOT_A_RULE"));
        assertEquals("", MsgPushAlertTemplates.defaultTemplate(null));
        // 大小写与空格容错：参数页里手写成小写也要认
        assertFalse(MsgPushAlertTemplates.defaultTemplate(" gcs_drop_2 ").isEmpty());
    }

    @Test
    @DisplayName("默认文案里的占位符全部有值可替，渲染后不留 # 号")
    void defaultTemplatesAreFullyRenderable() {
        for (String rule : MsgPushAlertTemplates.ALL_RULES) {
            String content = MsgPushAlertTemplates.render(
                    MsgPushAlertTemplates.defaultTemplate(rule), fullVars());
            assertFalse(content.contains("#"),
                    rule + " 渲染后仍残留占位符: " + content);
            assertTrue(content.startsWith("【ICU预警】"), rule + " 短信应带统一前缀，便于收信人一眼分辨");
        }
    }

    @Test
    @DisplayName("GCS 文案带得出首查→最新与降幅")
    void gcsContentCarriesValues() {
        String content = MsgPushAlertTemplates.render(
                MsgPushAlertTemplates.defaultTemplate(MsgPushAlertTemplates.RULE_GCS_DROP_2), fullVars());
        assertTrue(content.contains("由12降至9"), content);
        assertTrue(content.contains("降3分"), content);
        assertTrue(content.contains("住院号123456"), content);
        // 一条短信 70 字左右最经济，超了会被拆成两条计费；这里守住「不因文案冗长而拆条」
        assertTrue(content.length() <= 75, "文案过长(" + content.length() + "字): " + content);
    }

    @Test
    @DisplayName("未定义的占位符原样保留（拼错时短信里看得见，而不是静默变空白）")
    void unknownPlaceholderIsKept() {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("bedNo", "3床");
        assertEquals("3床 / #gcsFrom#", MsgPushAlertTemplates.render("#bedNo# / #gcsFrom#", vars));
        // 空模板不返回 null，调用方按「无文案」跳过即可
        assertEquals("", MsgPushAlertTemplates.render("", vars));
        assertEquals("", MsgPushAlertTemplates.render(null, vars));
    }

    @Test
    @DisplayName("规则集合解析：认中文逗号/分号/小写，写空则一条都不发")
    void parseRulesIsForgivingButNeverDefaultsToAll() {
        Set<String> rules = MsgPushAlertTemplates.parseRules("ARDS_VENTILATED， gcs_drop_2;PRONE_OVERTIME RR_DEPRESSION");
        assertEquals(4, rules.size());
        assertTrue(rules.contains(MsgPushAlertTemplates.RULE_GCS_DROP_2));

        assertTrue(MsgPushAlertTemplates.parseRules(null).isEmpty());
        assertTrue(MsgPushAlertTemplates.parseRules("   ").isEmpty());
        assertTrue(MsgPushAlertTemplates.parseRules(",，;").isEmpty(),
                "写一堆分隔符等于没配，不能退化成「全推」");

        // 默认值字符串要能被解析回全部规则（参数页展示的默认值与代码保持一致）
        assertEquals(MsgPushAlertTemplates.ALL_RULES.length,
                MsgPushAlertTemplates.parseRules(MsgPushAlertTemplates.allRulesCsv()).size());
    }
}
