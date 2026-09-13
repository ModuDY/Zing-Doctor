package com.zing.doctor.quality.engine;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 质控 SQL 片段安全守卫。
 *
 * <p><b>为什么需要它</b>：可视化配置把「写表达式」的权力交给了页面使用者，而这些表达式最终会被
 * 拼进 SQL 直接下发数据库。原先的防护只有 {@code QualityEngine} 里一个 private 方法，
 * 只拦 {@code ; -- /*} 三个符号。这挡不住 {@code 1=1) UNION SELECT ...} 这类写法 ——
 * UNION 不需要分号，分隔符检查形同虚设。
 *
 * <p>本类把防护拆成两级，且<b>同时用于「保存前校验」与「每次执行的运行时防线」</b>：
 * <ul>
 *   <li>{@link #guardStatement} 整条编译产物：拦分隔符、注释、DML/DDL 关键字与危险过程</li>
 *   <li>{@link #guardMetricFragment} / {@link #guardFactFragment} 用户手写的表达式片段，
 *       按层分级：<b>指标层禁止子查询，事实层允许</b> —— 现有事实层的
 *       {@code CASE WHEN EXISTS (SELECT ...)} 派生列是合法且必需的写法，
 *       一刀切禁掉会让既有配置整体失效；两者都校验括号与引号配平</li>
 * </ul>
 *
 * <p>关键字一律用 {@code \b} 词边界匹配，避免误伤 {@code update_time}、{@code create_time}
 * 这类含关键字字样的正常列名（{@code update} 后紧跟 {@code _} 属词内，不构成词边界）。
 */
@Component
public class QualitySqlGuard {

    /** 分隔符与注释：可截断语句、可绕过后续一切检查 */
    private static final String[] BLOCKED_SYMBOLS = {";", "--", "/*", "*/"};

    /** DML / DDL / DCL 与集合运算：配置表达式只应是「表达式」，不该出现这些 */
    private static final String[] BLOCKED_KEYWORDS = {
            "insert", "update", "delete", "merge", "upsert",
            "drop", "alter", "create", "truncate", "rename",
            "grant", "revoke",
            "exec", "execute", "declare", "call", "commit", "rollback",
            "union", "intersect", "minus"
    };

    /** 危险内置过程/函数：可拖垮数据库或用于探测 */
    private static final String[] BLOCKED_FUNCS = {
            "sleep", "benchmark", "dbms_lock", "dbms_pipe",
            "utl_inaddr", "utl_http", "utl_file", "xp_cmdshell"
    };

    private static final Pattern BLOCKED_PATTERN = buildPattern(BLOCKED_KEYWORDS, BLOCKED_FUNCS);
    private static final Pattern SELECT_PATTERN = Pattern.compile("(?i)\\bselect\\b");

    // ------------------------------------------------------------------
    // 对外入口
    // ------------------------------------------------------------------

    /**
     * 校验整条已编译 SQL（运行时防线）。
     *
     * <p>编译产物形如 {@code SELECT ... FROM ...}，天然含 {@code SELECT}，因此这里不拦 SELECT，
     * 但拦住 DML/DDL 与集合运算 —— 正常情况下编译产物里绝不会出现它们。
     */
    public void guardStatement(String sql) {
        if (!StringUtils.hasText(sql)) {
            throw new IllegalArgumentException("SQL 为空，已拒绝执行");
        }
        for (String s : BLOCKED_SYMBOLS) {
            if (sql.contains(s)) {
                throw new IllegalArgumentException("质控 SQL 含非法字符（" + s + "），已拒绝执行");
            }
        }
        if (BLOCKED_PATTERN.matcher(sql).find()) {
            throw new IllegalArgumentException("质控 SQL 含写操作或集合运算关键字，已拒绝执行");
        }
    }

    /**
     * 校验指标层的表达式片段（保存前校验，最严一级）。
     *
     * <p>指标层表达式一律不允许子查询：现有 127 条指标里没有一例需要子查询，
     * 而 {@code x IN (SELECT ...)} 既是注入入口，又会在逐行求值时退化成性能灾难
     * （事实层已经把需要子查询的派生列算好了，指标直接引用列名即可）。
     */
    public void guardMetricFragment(String label, String fragment) {
        guardFragment(label, fragment, false);
    }

    /**
     * 校验事实层的表达式片段。
     *
     * <p>事实层<b>允许子查询</b>：{@code CASE WHEN EXISTS (SELECT 1 FROM ...) THEN 1 ELSE 0 END AS xxx}
     * 是现有事实层广泛使用的合法写法（抗菌药物使用、培养送检、48 小时再入 ICU 等派生列全靠它），
     * 一刀切禁掉会让既有配置整体失效。但分隔符、注释、写操作关键字同样禁止。
     */
    public void guardFactFragment(String label, String fragment) {
        guardFragment(label, fragment, true);
    }

    /**
     * 校验用户手写的表达式片段（默认按指标层口径，不允许子查询）。
     *
     * @param label    片段的中文名，用于拼出「能看懂、知道去哪改」的报错
     * @param fragment 表达式片段；空片段视为合法（表示该口径未设置）
     */
    public void guardFragment(String label, String fragment) {
        guardFragment(label, fragment, false);
    }

    /** 片段是否含子查询（不抛异常，供保存时给出性能提示）。 */
    public boolean hasSubquery(String fragment) {
        return StringUtils.hasText(fragment) && SELECT_PATTERN.matcher(fragment).find();
    }

    private void guardFragment(String label, String fragment, boolean allowSubquery) {
        if (!StringUtils.hasText(fragment)) {
            return;
        }
        String where = StringUtils.hasText(label) ? "【" + label + "】" : "";
        for (String s : BLOCKED_SYMBOLS) {
            if (fragment.contains(s)) {
                throw new IllegalArgumentException(where + "不允许出现 " + s + "（语句分隔符与注释会截断 SQL）");
            }
        }
        if (BLOCKED_PATTERN.matcher(fragment).find()) {
            throw new IllegalArgumentException(where + "不允许出现写操作或集合运算关键字（如 insert / drop / union）");
        }
        if (!allowSubquery && SELECT_PATTERN.matcher(fragment).find()) {
            throw new IllegalArgumentException(where + "不允许写子查询（SELECT）—— 需要子查询的派生列应放到事实层定义");
        }
        checkBalance(where, fragment);
    }

    /** 片段是否含有会被拦截的内容（不抛异常，供批量体检使用）。 */
    public boolean isBlocked(String fragment) {
        try {
            guardFragment(null, fragment);
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    // ------------------------------------------------------------------
    // internal
    // ------------------------------------------------------------------

    /**
     * 括号与引号配平检查。
     *
     * <p>提前拦住「少写一个右括号」这类高频手误，报错比数据库返回的语法错误清楚得多；
     * 同时防止用未闭合引号把后续 SQL 变成字符串字面量。
     */
    private void checkBalance(String where, String fragment) {
        int depth = 0;
        int quotes = 0;
        boolean inQuote = false;
        for (int i = 0; i < fragment.length(); i++) {
            char c = fragment.charAt(i);
            if (c == '\'') {
                inQuote = !inQuote;
                quotes++;
                continue;
            }
            if (inQuote) {
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth < 0) {
                    throw new IllegalArgumentException(where + "括号不配平：多了一个右括号 )");
                }
            }
        }
        if (depth > 0) {
            throw new IllegalArgumentException(where + "括号不配平：还缺 " + depth + " 个右括号 )");
        }
        if (quotes % 2 != 0) {
            throw new IllegalArgumentException(where + "字符串引号不闭合：单引号 ' 必须成对出现");
        }
    }

    private static Pattern buildPattern(String[]... groups) {
        StringBuilder sb = new StringBuilder();
        for (String[] group : groups) {
            for (String kw : group) {
                if (sb.length() > 0) {
                    sb.append('|');
                }
                sb.append("\\b").append(Pattern.quote(kw)).append("\\b");
            }
        }
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }
}
