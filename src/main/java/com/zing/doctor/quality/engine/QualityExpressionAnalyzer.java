package com.zing.doctor.quality.engine;

import com.zing.doctor.quality.dsl.FactDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表达式列名分析器（校验链 L3 与「字段下拉」共同的地基）。
 *
 * <p><b>只分析指标层表达式</b>（{@code expr_where} / {@code expr_numerator} /
 * {@code expr_denominator_where} / {@code dims}）。指标表达式只引用事实层投影出的列，
 * 因此可以静态判定列是否存在；而事实层表达式同时引用源表、多个关联表的别名
 * （{@code pi.} / {@code pa.} / {@code li.} …），静态判定必然误报，交由 L4 试跑兜底。
 *
 * <p>价值：把「引用了一个不存在的列」这个高频错误拦在保存那一刻，
 * 而不是等跑批时在几十条指标里冒出 {@code 无效的列名}。
 */
@Component
public class QualityExpressionAnalyzer {

    /** 事实层里 `表达式 AS 别名` 的别名（取行尾） */
    private static final Pattern AS_ALIAS =
            Pattern.compile("(?i)\\bas\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*$");

    /** 整条就是 `列名` 或 `别名.列名` 的简单情形 */
    private static final Pattern SIMPLE_COL =
            Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)(?:\\.([A-Za-z_][A-Za-z0-9_]*))?");

    /** `别名.列名` 或 `列名` */
    private static final Pattern IDENTIFIER =
            Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)(?:\\.([A-Za-z_][A-Za-z0-9_]*))?");

    /** 字符串字面量：`'是'`、`'%头孢%'`，需先剔除，否则引号内的词会被当成列名 */
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^']|'')*'");

    /** SQL 关键字与日期部分关键字（不是列名） */
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "and", "or", "not", "null", "is", "in", "between", "like", "case", "when", "then",
            "else", "end", "true", "false", "asc", "desc", "distinct", "as", "exists", "for",
            "select", "from", "where", "group", "by", "having", "order", "union", "all", "any",
            "second", "minute", "hour", "day", "dayofyear", "week", "month", "quarter", "year",
            "interval", "escape"));

    /** 常见函数名（后接 `(` 时也会被识别为函数，这里是额外保险） */
    private static final Set<String> FUNCTIONS = new HashSet<>(Arrays.asList(
            "nvl", "nvl2", "coalesce", "nullif", "decode", "cast", "round", "trunc", "floor",
            "ceil", "ceiling", "abs", "sign", "mod", "power", "sqrt", "exp", "ln", "log",
            "substr", "substring", "length", "char_length", "trim", "ltrim", "rtrim", "upper",
            "lower", "replace", "instr", "concat", "lpad", "rpad", "to_char", "to_date",
            "to_number", "datediff", "dateadd", "add_months", "months_between", "greatest",
            "least", "max", "min", "sum", "count", "avg", "ifnull", "isnull"));

    // ------------------------------------------------------------------
    // 事实层可引用列
    // ------------------------------------------------------------------

    /**
     * 列出事实层投影出的列名（指标可引用的全集），按字母序返回。
     *
     * <p>返回空集合表示<b>无法判定</b> —— 事实层未声明 select / derive 时会投影 {@code alias.*}，
     * 此时任何列都可能存在，调用方（校验链）应跳过 L3 而不是把用户写的列名全判为错。
     */
    public Set<String> availableColumns(FactDefinition f) {
        if (f == null || (isEmpty(f.getSelect()) && isEmpty(f.getDerive()))) {
            return Collections.emptySet();
        }
        Set<String> cols = new TreeSet<>();
        for (String expr : safe(f.getSelect())) {
            addIfPresent(cols, columnOf(expr));
        }
        for (String expr : safe(f.getDerive())) {
            addIfPresent(cols, columnOf(expr));
        }
        for (String expr : safe(f.getGroup())) {
            addIfPresent(cols, columnOf(expr));
        }
        // 事实层声明的四个键名一定可用（编译指标 SQL 时引擎直接引用它们）
        addIfPresent(cols, f.getPatientKey());
        addIfPresent(cols, f.getInHospitalNoKey());
        addIfPresent(cols, f.getPatientNameKey());
        addIfPresent(cols, f.getDepartKey());
        if (cols.isEmpty()) {
            cols.add("patient_id");
        }
        return cols;
    }

    /**
     * 患者明细「补充列」的可选清单：只含事实层真正物化出来的列，并剔除明细默认列。
     *
     * <p>与 {@link #availableColumns} 的两点差别都是刻意的：
     * <ol>
     *   <li><b>不掺入事实层声明的四个键名</b>（patientKey / inHospitalNoKey …）。
     *       它们是「编译指标 SQL 时可引用的列」，与物化表里的列名未必同名，
     *       选进明细会查出一个不存在的列；</li>
     *   <li><b>剔除明细默认列</b>。这些列引擎已固定产出，再配一次会让明细 SQL 的
     *       内层子查询出现两个同名列，外层 {@code SELECT *} 报「列名不明确」，
     *       整份明细返回空 —— 而页面只显示「无患者明细」，看不出是配置写错了。</li>
     * </ol>
     * 返回空集合表示无法判定（事实层用 {@code alias.*} 投影，任何列都可能存在）。
     */
    public Set<String> patientColumns(FactDefinition f) {
        if (f == null || (isEmpty(f.getSelect()) && isEmpty(f.getDerive()))) {
            return Collections.emptySet();
        }
        Set<String> cols = new TreeSet<>();
        for (String expr : safe(f.getSelect())) {
            addIfPresent(cols, columnOf(expr));
        }
        for (String expr : safe(f.getDerive())) {
            addIfPresent(cols, columnOf(expr));
        }
        for (String expr : safe(f.getGroup())) {
            addIfPresent(cols, columnOf(expr));
        }
        cols.removeIf(PatientColumns::isReservedColumn);
        return cols;
    }

    /** 从 `表达式 AS 别名` 中取列名；无法确定时返回 null。 */
    public String columnOf(String expr) {
        if (!StringUtils.hasText(expr)) {
            return null;
        }
        String e = expr.trim();
        Matcher as = AS_ALIAS.matcher(e);
        if (as.find()) {
            return as.group(1);
        }
        Matcher simple = SIMPLE_COL.matcher(e);
        if (simple.matches()) {
            return simple.group(2) != null ? simple.group(2) : simple.group(1);
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 表达式引用的列
    // ------------------------------------------------------------------

    /** 提取一组表达式中引用的列名（小写）。 */
    public Set<String> referencedColumns(Collection<String> exprs) {
        Set<String> out = new LinkedHashSet<>();
        for (String expr : safe(exprs)) {
            collect(expr, out);
        }
        return out;
    }

    /**
     * 校验指标表达式引用了事实层不存在的列。
     *
     * <p>事实层未声明投影列（可用集合为空）时返回空列表：无从判定，宁可放过也不误报。
     */
    public List<String> unknownColumns(FactDefinition f, Collection<String> exprs) {
        Set<String> available = availableColumns(f);
        if (available.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> known = new HashSet<>();
        for (String c : available) {
            known.add(c.toLowerCase());
        }
        List<String> unknown = new ArrayList<>();
        for (String col : referencedColumns(exprs)) {
            if (!known.contains(col)) {
                unknown.add(col);
            }
        }
        return unknown;
    }

    // ------------------------------------------------------------------
    // internal
    // ------------------------------------------------------------------

    private void collect(String expr, Set<String> out) {
        if (!StringUtils.hasText(expr)) {
            return;
        }
        // 先剔除字符串字面量，否则 '是' / '%头孢%' 里的词会被当成标识符
        String e = STRING_LITERAL.matcher(expr).replaceAll(" ");
        Matcher m = IDENTIFIER.matcher(e);
        while (m.find()) {
            String qualifier = m.group(1);
            String col = m.group(2);
            if (col != null) {
                // 形如 t.is_icu → 真正引用的是 is_icu，表别名不算列名
                if (!skip(col)) {
                    out.add(col.toLowerCase());
                }
                continue;
            }
            if (skip(qualifier)) {
                continue;
            }
            // 后接 `(` 的一律视为函数调用
            if (isFunctionCall(e, m.end())) {
                continue;
            }
            out.add(qualifier.toLowerCase());
        }
    }

    private boolean isFunctionCall(String expr, int from) {
        for (int i = from; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            return c == '(';
        }
        return false;
    }

    private boolean skip(String name) {
        if (!StringUtils.hasText(name)) {
            return true;
        }
        String n = name.toLowerCase();
        return KEYWORDS.contains(n) || FUNCTIONS.contains(n);
    }

    private void addIfPresent(Set<String> cols, String name) {
        if (StringUtils.hasText(name)) {
            cols.add(name.trim());
        }
    }

    private boolean isEmpty(Collection<String> c) {
        return c == null || c.isEmpty();
    }

    private Collection<String> safe(Collection<String> c) {
        return c == null ? Collections.emptyList() : c;
    }
}
