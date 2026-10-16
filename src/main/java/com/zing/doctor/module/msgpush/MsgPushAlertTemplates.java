package com.zing.doctor.module.msgpush;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 高风险预警短信文案：默认模板 + 规则集合解析 + 占位符渲染。
 *
 * <p><b>为什么单独一个类</b>：文案要能被单测直接钉住（纯静态、不碰 Spring、不碰库），
 * 否则「GCS 降了几分写错成 0」这类错误只能等现场收到短信才发现。
 *
 * <p><b>模板为什么落参数</b>：措辞是现场的事（要不要带住院号、要不要带科室、
 * 处置建议怎么写），代码里只放默认值。参数键为
 * {@code MSGPUSH_CONTENT_<规则代码>}，见 {@link MsgPushKeys#contentKey(String)}。
 *
 * <p><b>渲染语义</b>：只做纯文本替换，<b>不做 XML 转义</b> —— 转义统一在报文渲染阶段
 * （{@code MsgPushServiceImpl} 的 render）做，这里再转一次会变成 {@code &amp;amp;}。
 * 未定义的占位符<b>原样保留</b>（与报文的 render 一致）：现场把
 * {@code #gcsDropd#} 拼错时，短信里会直接看到这个错串，比静默变成空白容易查。
 */
public final class MsgPushAlertTemplates {

    private MsgPushAlertTemplates() {
    }

    // ---- 规则代码：与 WorkbenchEnrichService 生成待办时用的字符串逐一对应，不要各写各的 ----

    /** 已标识 ARDS 且机械通气中 */
    public static final String RULE_ARDS_VENTILATED = "ARDS_VENTILATED";
    /** 今日 GCS 较首查下降 ≥2 分 */
    public static final String RULE_GCS_DROP_2 = "GCS_DROP_2";
    /** 俯卧位治疗进行中已超 16 小时未记录结束 */
    public static final String RULE_PRONE_OVERTIME = "PRONE_OVERTIME";
    /** 今日监护呼吸频率最新一次 < 8 次/min */
    public static final String RULE_RR_DEPRESSION = "RR_DEPRESSION";
    /** 诊断含脑损伤关键字（监测类标记） */
    public static final String RULE_BRAIN_INJURY = "BRAIN_INJURY";
    /** 入科满 24 小时且今日尚未评 SOFA */
    public static final String RULE_SOFA_NOT_TODAY = "SOFA_NOT_TODAY";
    /** 入科满 24 小时且今日尚未评 APACHE II */
    public static final String RULE_APACHE_NOT_TODAY = "APACHE_NOT_TODAY";

    /** 全部规则，按「恶化类在前、催办类在后」排列（也决定 {@code MSGPUSH_RULES} 默认值的顺序） */
    public static final String[] ALL_RULES = {
            RULE_ARDS_VENTILATED, RULE_GCS_DROP_2, RULE_PRONE_OVERTIME, RULE_RR_DEPRESSION,
            RULE_BRAIN_INJURY, RULE_SOFA_NOT_TODAY, RULE_APACHE_NOT_TODAY
    };

    /** 占位符：#变量名# */
    private static final Pattern PH = Pattern.compile("#([A-Za-z0-9_]+)#");

    /**
     * 默认文案（「标准」体例）：时间 + 床位 + 姓名 + 住院号 + 触发依据 + 处置指向。
     *
     * <p>控制在 70 字以内是为了尽量不被拆成两条短信（长短信按 67 字拆分计费）。
     * 现场可在「参数设置 → 消息推送」逐条改写，改错不影响其它规则。
     */
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(RULE_ARDS_VENTILATED,
                "【ICU预警】#time# #bedNo# #patientName#（住院号#inHospitalNo#）已标识ARDS且机械通气中，"
                + "请关注肺保护通气与俯卧位评估。");
        DEFAULTS.put(RULE_GCS_DROP_2,
                "【ICU预警】#time# #bedNo# #patientName#（住院号#inHospitalNo#）今日GCS由#gcsFrom#降至#gcsTo#"
                + "（降#gcsDrop#分），警惕意识状态恶化，请查看意识监测。");
        DEFAULTS.put(RULE_PRONE_OVERTIME,
                "【ICU预警】#time# #bedNo# #patientName#（住院号#inHospitalNo#）俯卧位治疗自#proneStart#起"
                + "已持续#proneHours#小时未记录结束，请评估是否复位。");
        DEFAULTS.put(RULE_RR_DEPRESSION,
                "【ICU预警】#time# #bedNo# #patientName#（住院号#inHospitalNo#）最新呼吸频率#rrValue#次/分"
                + "（<8次/分），警惕呼吸抑制，请查看监护记录。");
        DEFAULTS.put(RULE_BRAIN_INJURY,
                "【ICU预警】#time# #bedNo# #patientName#（住院号#inHospitalNo#）诊断含#brainDiag#，"
                + "属急性脑损伤监测对象，请关注意识与瞳孔变化。");
        DEFAULTS.put(RULE_SOFA_NOT_TODAY,
                "【ICU预警】#time# #bedNo# #patientName#（住院号#inHospitalNo#）今日尚未评SOFA，请及时完成评分。");
        DEFAULTS.put(RULE_APACHE_NOT_TODAY,
                "【ICU预警】#time# #bedNo# #patientName#（住院号#inHospitalNo#）今日尚未评APACHE II，"
                + "请及时完成评分。");
    }

    /** 规则是否有默认文案（用于日志里区分「走默认」与「走了现场自定义」）。 */
    public static boolean hasDefault(String ruleCode) {
        return ruleCode != null && DEFAULTS.containsKey(ruleCode.trim().toUpperCase(Locale.ROOT));
    }

    /** 默认文案；未知规则返回空串（调用方据此跳过，不拿空短信去发）。 */
    public static String defaultTemplate(String ruleCode) {
        if (ruleCode == null) {
            return "";
        }
        String tpl = DEFAULTS.get(ruleCode.trim().toUpperCase(Locale.ROOT));
        return tpl == null ? "" : tpl;
    }

    /**
     * 解析参数 {@code MSGPUSH_RULES}：逗号（中英文）/ 分号 / 空白分隔，去掉空项并统一大写。
     *
     * <p>返回空集表示<b>不推送任何预警</b> —— 未配置时不能退化成"全推"：
     * 参数页没填就往外发短信，是现场最难解释的一类事故。
     */
    public static Set<String> parseRules(String raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw == null || raw.trim().isEmpty()) {
            return out;
        }
        for (String s : raw.split("[,，;；/\\s]+")) {
            String t = s.trim().toUpperCase(Locale.ROOT);
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** 全部规则的逗号串（{@code MSGPUSH_RULES} 的默认值）。 */
    public static String allRulesCsv() {
        return String.join(",", Arrays.asList(ALL_RULES));
    }

    /**
     * 用变量替换模板里的 {@code #占位符#}；取值缺失的占位符原样保留。
     */
    public static String render(String template, Map<String, String> vars) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        if (vars == null || vars.isEmpty()) {
            return template;
        }
        Matcher m = PH.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String val = vars.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(val == null ? m.group(0) : val));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
