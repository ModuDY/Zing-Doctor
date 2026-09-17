package com.zing.doctor.quality.engine;

import com.zing.doctor.quality.dsl.FactDefinition;
import com.zing.doctor.quality.dsl.MetricDefinition;
import com.zing.doctor.quality.dsl.PatientFieldDefinition;
import com.zing.doctor.quality.dsl.SourceConfig;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * DSL → SQL 编译器。
 *
 * <p>这是「把计算下推数据库」的落点：事实层与指标都被编译成单条 SQL，
 * 不在 JVM 里循环明细数据，因此性能与数据量解耦。
 */
@Component
public class SqlCompiler {

    public static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 合法列名：患者明细的补充字段会被直接拼进 SQL，必须先过这一关 */
    private static final Pattern COLUMN_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /** 事实层的算子链（写入血缘，便于定位口径）。 */
    public static String operatorsOf(FactDefinition f) {
        List<String> ops = new ArrayList<>();
        if (!f.getWhere().isEmpty()) ops.add("filter");
        if (!f.getSelect().isEmpty() || !f.getDerive().isEmpty()) ops.add("project");
        if (!f.getDerive().isEmpty()) ops.add("derive");
        if (!f.getGroup().isEmpty()) ops.add("aggregate");
        if (ops.isEmpty()) ops.add("scan");
        return String.join(">", ops);
    }

    /**
     * 编译事实层为 {@code SELECT ...}，并绑定周期占位符。
     *
     * @param schemaOverride 事实层物化目标 schema（事实表自身所在 schema）
     */
    public String compileFact(FactDefinition f, SourceConfig cfg, LocalDateTime start, LocalDateTime end) {
        return compileFact(f, cfg, start, end, null);
    }

    /**
     * 编译事实层；{@code deptValue} 非空时只物化该科室的数据。
     *
     * <p>「只算某科室」的加速点就在这里：整张事实表只装一个科室的行，
     * 扫描量与物化成本随科室规模下降，而不是全院算完再挑一行。
     *
     * @param deptValue 科室编码；null/空 = 全院（行为与旧版完全一致）
     */
    public String compileFact(FactDefinition f, SourceConfig cfg, LocalDateTime start, LocalDateTime end,
                              String deptValue) {
        String base = resolveTable(f.getSource(), cfg);
        String alias = StringUtils.hasText(f.getAlias()) ? f.getAlias() : "t";

        List<String> cols = new ArrayList<>();
        cols.addAll(f.getSelect());
        cols.addAll(f.getDerive());
        if (cols.isEmpty()) {
            cols.add(alias + ".*");
        }

        List<String> conds = new ArrayList<>(f.getWhere());
        if (StringUtils.hasText(deptValue)) {
            String deptExpr = deptFilterExpr(f);
            if (deptExpr == null) {
                throw new IllegalStateException("事实层 " + f.getFact()
                        + " 未声明科室列（departKey 或 depart_code 投影），无法按科室单独计算");
            }
            conds.add(deptExpr + " = " + sqlLiteral(deptValue));
        }

        StringBuilder sb = new StringBuilder(512);
        sb.append("SELECT ").append(String.join(", ", cols));
        sb.append(" FROM ").append(base).append(" ").append(alias);
        if (!conds.isEmpty()) {
            sb.append(" WHERE ").append(String.join(" AND ", conds));
        }
        if (!f.getGroup().isEmpty()) {
            sb.append(" GROUP BY ").append(String.join(", ", f.getGroup()));
        }
        return bindPeriod(sb.toString(), start, end);
    }

    /**
     * 事实层「源表侧」的科室过滤表达式（能直接进 {@code WHERE}）。
     *
     * <p>注意与 {@link #deptProjection} 的区别：源表 SQL 里不能写 {@code depart_code}
     * 这种投影别名（那时还不存在），必须用 {@code pi.depart_code} 这类原始列，
     * 因此这里从 {@code select} 声明里反推「别名 ← 原表达式」。
     */
    public String deptFilterExpr(FactDefinition f) {
        if (f == null) {
            return null;
        }
        String target = StringUtils.hasText(f.getDepartKey()) ? f.getDepartKey().trim() : "depart_code";
        for (String col : f.getSelect()) {
            if (col == null) {
                continue;
            }
            int i = col.toLowerCase().lastIndexOf(" as ");
            if (i < 0) {
                continue;
            }
            if (col.substring(i + 4).trim().equalsIgnoreCase(target)) {
                return col.substring(0, i).trim();
            }
        }
        // select 里没找到对应投影：departKey 本身就是源表列名时可直接用
        return StringUtils.hasText(f.getDepartKey()) ? f.getDepartKey() : null;
    }

    /**
     * 事实层物化后的科室列名（投影别名），用于明细与指标层的过滤/展示。
     *
     * <p>未声明 {@code departKey} 但投影了 {@code depart_code} 时按后者兜底 ——
     * 这修掉一个既有的静默问题：漏声明会让患者明细的「科室」列整列显示为空。
     */
    public String deptProjection(FactDefinition f) {
        if (f == null) {
            return null;
        }
        if (StringUtils.hasText(f.getDepartKey())) {
            return f.getDepartKey();
        }
        for (String col : f.getSelect()) {
            if (col != null && col.toLowerCase().endsWith(" as depart_code")) {
                return "depart_code";
            }
        }
        return null;
    }

    /**
     * 该指标能否按科室单独计算。
     *
     * <p>门槛刻意定得保守：只有「单科室维度」的指标才走科室模式。
     * 无维度的指标（如全院收治人数）算出来天然只有一行 {@code ALL}，
     * 拿科室数据去填它等于把「科室值」冒充「全院值」；多维或按名称分维的指标同理，
     * 都回退为全院计算，宁可慢一点也不能出错值。
     */
    public boolean supportsDeptFilter(MetricDefinition m, FactDefinition f) {
        if (m == null || f == null || deptFilterExpr(f) == null) {
            return false;
        }
        return deptDimensioned(m);
    }

    /**
     * 指标是否按科室分维（与事实层无关的那一半判定）。
     *
     * <p>抽出来给派生指标复用：它们不绑事实层，但分子分母都取自「被引用指标的同一科室行」，
     * 因此只要按科室分维就能按科室出数。判定只应有一处实现 —— 两处一旦漂移，
     * 删除范围与写入范围就会对不上，症状是整批撞唯一键。
     */
    public boolean deptDimensioned(MetricDefinition m) {
        List<String> dims = m == null ? null : m.getDims();
        if (dims == null || dims.size() != 1) {
            return false;
        }
        // 只认 depart_code：按科室名分维的事实层（医师/床位花名册）拿编码过滤会一行都筛不出来，
        // 与其静默出 0，不如回退全院计算
        String dim = dims.get(0) == null ? "" : dims.get(0).trim().toLowerCase();
        return "depart_code".equals(dim);
    }

    /**
     * 拼 SQL 字符串字面量。
     *
     * <p>科室值来自请求参数，必须自己兜住注入面：值整体落在单引号内，转义单引号即可；
     * 同时限制长度并拒绝控制字符，避免拼出畸形 SQL。
     */
    public String sqlLiteral(String value) {
        String v = value == null ? "" : value.trim();
        if (v.length() > 64) {
            throw new IllegalArgumentException("过滤值过长");
        }
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '\0' || c == '\n' || c == '\r') {
                throw new IllegalArgumentException("过滤值含非法字符");
            }
        }
        return "'" + v.replace("'", "''") + "'";
    }

    /**
     * 编译指标聚合 SQL（一行输出：metric_code / depart_code / num / den）。
     *
     * <p>计数对象取自事实层声明的 {@code patientKey}，因此同一套引擎既服务
     * 「按患者计数」的临床指标，也服务「按医师/床位计数」的资源类指标。
     */
    public String compileMetric(MetricDefinition m, String factTable, FactDefinition f) {
        return compileMetric(m, factTable, f, null);
    }

    /**
     * 编译指标聚合 SQL；{@code deptValue} 非空时只算该科室一行。
     *
     * <p>过滤加在维度列上而非重新拼口径：事实层若已按科室物化，这里是重复但无害的条件；
     * 事实层没法过滤时（无 departKey），它仍是保证「只算该科室」的第二道闸。
     */
    public String compileMetric(MetricDefinition m, String factTable, FactDefinition f, String deptValue) {
        String dim = dimExpr(m);
        String patientKey = key(f == null ? null : f.getPatientKey(), "patient_id");

        String numCond = numCondition(m);

        String numExpr;
        String agg = m.getAgg() == null ? "PT_COUNT" : m.getAgg().toUpperCase();
        switch (agg) {
            case "SUM":
                numExpr = "SUM(CASE WHEN (" + wrap(m.getWhere()) + ") THEN (" + safeNum(m.getNumerator()) + ") ELSE 0 END)";
                break;
            case "AVG":
                numExpr = "AVG(CASE WHEN (" + wrap(m.getWhere()) + ") THEN (" + safeNum(m.getNumerator()) + ") END)";
                break;
            case "PT_COUNT":
            default:
                numExpr = "COUNT(DISTINCT CASE WHEN (" + numCond + ") THEN " + patientKey + " END)";
                break;
        }

        String denExpr;
        if (StringUtils.hasText(m.getDenominatorWhere())) {
            denExpr = "COUNT(DISTINCT CASE WHEN (" + m.getDenominatorWhere() + ") THEN " + patientKey + " END)";
        } else {
            denExpr = "COUNT(DISTINCT " + patientKey + ")";
        }

        String filter = StringUtils.hasText(deptValue) ? " WHERE " + dim + " = " + sqlLiteral(deptValue) : "";

        return "SELECT '" + m.getCode() + "' AS metric_code, " + dim + " AS depart_code, "
                + numExpr + " AS num, " + denExpr + " AS den"
                + " FROM " + factTable + filter
                + " GROUP BY " + dim;
    }

    /**
     * 编译「对象级」命中明细 SQL（血缘第 4 层：谁进分子、谁进分母）。
     *
     * <p>对象可能是患者、医师或床位，取决于事实层的声明；列名同样来自事实层，
     * 因此资源类指标下钻看到的是「医师明细」而非错位的「患者明细」。
     */
    public String compilePatients(MetricDefinition m, String factTable, FactDefinition f) {
        return compilePatients(m, factTable, f, null);
    }

    /**
     * 编译明细 SQL；{@code deptValue} 非空时只取该科室的行。
     *
     * <p>不加这个条件时，看板选了科室、点开的明细却仍是全院的人 —— 数字和名单对不上，
     * 是比「少几行」严重得多的问题。
     */
    public String compilePatients(MetricDefinition m, String factTable, FactDefinition f, String deptValue) {
        return compilePatients(m, factTable, f, deptValue, false);
    }

    /**
     * 编译明细 SQL（可选包含「未纳入」的人）。
     *
     * @param includeExcluded 是否把「既没进分子也没进分母」的人一并列出。
     *        false（默认）只返回命中过的人，用于常规下钻；
     *        true 用于排查「这个人为什么不算进分母」—— 质控争议绝大多数出在这类人身上，
     *        而他们会被默认的外层 WHERE 直接滤掉，页面和接口里都看不到，
     *        使争议只能靠人工翻源表来回答。
     */
    public String compilePatients(MetricDefinition m, String factTable, FactDefinition f,
                                  String deptValue, boolean includeExcluded) {
        return compilePatients(m, factTable, f, deptValue, includeExcluded, Collections.emptySet());
    }

    /**
     * 编译明细 SQL（带事实层可用列）。
     *
     * @param availableColumns 事实层实际投影出的列名（小写）。扩展默认列（床号 / 诊断 /
     *        入科 / 出科）只有出现在这里才会被 select —— 各事实层投影的字段并不相同，
     *        缺列硬写会让整份明细报「无效的列名」。
     */
    public String compilePatients(MetricDefinition m, String factTable, FactDefinition f,
                                  String deptValue, boolean includeExcluded,
                                  Set<String> availableColumns) {
        String pk = key(f == null ? null : f.getPatientKey(), "patient_id");
        String noKey = f == null ? null : f.getInHospitalNoKey();
        String nameKey = f == null ? null : f.getPatientNameKey();
        String deptKey = deptProjection(f);

        String numCond = numCondition(m);
        String denCond = StringUtils.hasText(m.getDenominatorWhere()) ? m.getDenominatorWhere() : "1 = 1";

        String noExpr = StringUtils.hasText(noKey) ? "MAX(" + noKey + ")" : "NULL";
        String nameExpr = StringUtils.hasText(nameKey) ? "MAX(" + nameKey + ")" : "NULL";
        String deptExpr = StringUtils.hasText(deptKey) ? deptKey : "NULL";
        String groupBy = StringUtils.hasText(deptKey) ? pk + ", " + deptKey : pk;
        String filter = StringUtils.hasText(deptValue) && StringUtils.hasText(deptKey)
                ? " WHERE " + deptKey + " = " + sqlLiteral(deptValue) : "";

        // 指标自配的补充列：统一套 MAX 聚合，避免一患者多行时 GROUP BY 报错。
        // 与默认列重名的必须让路（保留 key 如 patientName 不是列名，一并跳过）：
        // 配置页的下拉里恰好能选到 patient_id / depart_code 这些名字，一旦真被选上，
        // 内层子查询就会出现两个同名列，外层 SELECT * 直接报「列名不明确」——
        // 整份明细返回空，而页面只显示「无患者明细」，看不出是配置写错了。
        StringBuilder extra = new StringBuilder();
        Set<String> selected = new LinkedHashSet<>();
        for (PatientFieldDefinition pf : safeFields(m)) {
            String key = pf.getKey() == null ? "" : pf.getKey().trim();
            if (key.isEmpty() || PatientColumns.isReservedKey(key)
                    || PatientColumns.isReservedColumn(key)) {
                continue;
            }
            if (!COLUMN_NAME.matcher(key).matches()) {
                // 列名会被直接拼进 SQL，非标识符一律拒绝（防注入），不改写成「跳过」
                throw new IllegalArgumentException("患者明细字段名非法：" + key);
            }
            // 同一列配了两次只留一个：内层出现两个同名列，外层 SELECT * 直接报
            // 「列名不明确」，整份明细为空而页面看不出原因
            if (!selected.add(key.toLowerCase())) {
                continue;
            }
            extra.append(", MAX(").append(key).append(") AS ").append(key);
        }
        // 扩展默认列（床号 / 诊断 / 入科时间 / 出科时间）：事实层有该列才带出来。
        // 它们是默认列，却不是每个事实层都投影 —— 写死进去，缺列的事实层整份明细
        // 都会报「无效的列名」，而那本来只是「这一列没数据」，不该赔上整张表。
        for (String col : PatientColumns.extended().keySet()) {
            if (!availableColumns.contains(col) || !selected.add(col)) {
                continue;
            }
            extra.append(", MAX(").append(col).append(") AS ").append(col);
        }

        String inner = "SELECT "
                + "  " + pk + " AS patient_id, "
                + "  " + noExpr + " AS in_hospital_no, "
                + "  " + nameExpr + " AS patient_name, "
                + "  " + deptExpr + " AS depart_code, "
                + "  MAX(CASE WHEN (" + numCond + ") THEN 1 ELSE 0 END) AS in_numerator, "
                + "  MAX(CASE WHEN (" + denCond + ") THEN 1 ELSE 0 END) AS in_denominator "
                + extra
                + " FROM " + factTable + filter
                + " GROUP BY " + groupBy;
        String outer = includeExcluded
                ? "" : " WHERE x.in_numerator = 1 OR x.in_denominator = 1";
        return "SELECT * FROM (" + inner + ") x" + outer;
    }

    private String key(String declared, String fallback) {
        return StringUtils.hasText(declared) ? declared : fallback;
    }

    /** 指标配置的患者明细补充字段（null 安全）。 */
    private List<PatientFieldDefinition> safeFields(MetricDefinition m) {
        return m == null || m.getPatientFields() == null
                ? new ArrayList<PatientFieldDefinition>() : m.getPatientFields();
    }

    /** 科室维度表达式：无维度时输出常量 'ALL'。 */
    private String dimExpr(MetricDefinition m) {
        if (m.getDims() == null || m.getDims().isEmpty()) {
            return "'ALL'";
        }
        Set<String> uniq = new LinkedHashSet<>(m.getDims());
        String first = uniq.iterator().next();
        if (uniq.size() == 1) {
            return first;
        }
        return "(" + String.join(" || '-' || ", uniq) + ")";
    }

    /**
     * 分子命中条件（PT_COUNT 用）。
     *
     * <p><b>where 片段必须整体加括号</b>：简单模式的「条件组」会产出
     * {@code (A AND B) OR (C)} 这种含顶层 OR 的过滤条件，若直接与分子附加条件用
     * {@code AND} 拼接，SQL 的 AND 优先级高于 OR，会被解析成
     * {@code A OR (B AND 分子条件)} —— 分子口径被悄悄放大，且页面上看不出来。
     * 括号对单个条件无副作用，因此这里统一加，不做「是否需要」的判断。
     */
    private String numCondition(MetricDefinition m) {
        boolean hasWhere = StringUtils.hasText(m.getWhere());
        boolean hasNum = StringUtils.hasText(m.getNumerator());
        if (hasWhere && hasNum) {
            return "(" + m.getWhere() + ") AND (" + m.getNumerator() + ")";
        }
        if (hasWhere) {
            return "(" + m.getWhere() + ")";
        }
        if (hasNum) {
            return m.getNumerator();
        }
        return "1 = 1";
    }

    private String safeNum(String expr) {
        return StringUtils.hasText(expr) ? expr : "1";
    }

    private String wrap(String expr) {
        return StringUtils.hasText(expr) ? expr : "1 = 1";
    }

    /** 解析逻辑表 → 物理全限定名。 */
    public String resolveTable(String logicalTable, SourceConfig cfg) {
        if (cfg == null || cfg.getTables() == null) {
            throw new IllegalStateException("数据源配置为空，无法解析表: " + logicalTable);
        }
        SourceConfig.TableDef t = cfg.getTables().get(logicalTable);
        if (t == null) {
            throw new IllegalStateException("未在 sources.yaml 中登记的逻辑表: " + logicalTable);
        }
        SourceConfig.DataSourceDef ds = cfg.getDatasources().get(t.getDs());
        String schema = ds == null || !StringUtils.hasText(ds.getSchema()) ? null : ds.getSchema();
        String name = "\"" + t.getName() + "\"";
        return schema == null ? name : "\"" + schema + "\"." + name;
    }

    /** 绑定 :periodStart / :periodEnd 占位符。 */
    public String bindPeriod(String sql, LocalDateTime start, LocalDateTime end) {
        String s = sql;
        if (start != null) {
            s = s.replace(":periodStart", "'" + DT.format(start) + "'");
        }
        if (end != null) {
            s = s.replace(":periodEnd", "'" + DT.format(end) + "'");
        }
        return s;
    }

    /** 事实层物化目标表名。 */
    public String factPhysicalName(String prefix, String factName) {
        return prefix + factName;
    }

    /** 供诊断输出：列出事实层引用的逻辑表，形成表级血缘。 */
    public List<String> sourceTables(FactDefinition f) {
        return f.getSource() == null ? new ArrayList<>(Arrays.asList("unknown"))
                : new ArrayList<>(Arrays.asList(f.getSource()));
    }
}
