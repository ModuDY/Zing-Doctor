package com.zing.doctor.quality.engine;

import com.zing.doctor.quality.dsl.FactDefinition;
import com.zing.doctor.quality.dsl.MetricDefinition;
import com.zing.doctor.quality.dsl.SourceConfig;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * DSL → SQL 编译器。
 *
 * <p>这是「把计算下推数据库」的落点：事实层与指标都被编译成单条 SQL，
 * 不在 JVM 里循环明细数据，因此性能与数据量解耦。
 */
@Component
public class SqlCompiler {

    public static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
        String base = resolveTable(f.getSource(), cfg);
        String alias = StringUtils.hasText(f.getAlias()) ? f.getAlias() : "t";

        List<String> cols = new ArrayList<>();
        cols.addAll(f.getSelect());
        cols.addAll(f.getDerive());
        if (cols.isEmpty()) {
            cols.add(alias + ".*");
        }

        StringBuilder sb = new StringBuilder(512);
        sb.append("SELECT ").append(String.join(", ", cols));
        sb.append(" FROM ").append(base).append(" ").append(alias);
        if (!f.getWhere().isEmpty()) {
            sb.append(" WHERE ").append(String.join(" AND ", f.getWhere()));
        }
        if (!f.getGroup().isEmpty()) {
            sb.append(" GROUP BY ").append(String.join(", ", f.getGroup()));
        }
        return bindPeriod(sb.toString(), start, end);
    }

    /**
     * 编译指标聚合 SQL（一行输出：metric_code / depart_code / num / den）。
     *
     * <p>计数对象取自事实层声明的 {@code patientKey}，因此同一套引擎既服务
     * 「按患者计数」的临床指标，也服务「按医师/床位计数」的资源类指标。
     */
    public String compileMetric(MetricDefinition m, String factTable, FactDefinition f) {
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

        return "SELECT '" + m.getCode() + "' AS metric_code, " + dim + " AS depart_code, "
                + numExpr + " AS num, " + denExpr + " AS den"
                + " FROM " + factTable
                + " GROUP BY " + dim;
    }

    /**
     * 编译「对象级」命中明细 SQL（血缘第 4 层：谁进分子、谁进分母）。
     *
     * <p>对象可能是患者、医师或床位，取决于事实层的声明；列名同样来自事实层，
     * 因此资源类指标下钻看到的是「医师明细」而非错位的「患者明细」。
     */
    public String compilePatients(MetricDefinition m, String factTable, FactDefinition f) {
        String pk = key(f == null ? null : f.getPatientKey(), "patient_id");
        String noKey = f == null ? null : f.getInHospitalNoKey();
        String nameKey = f == null ? null : f.getPatientNameKey();
        String deptKey = f == null ? null : f.getDepartKey();

        String numCond = numCondition(m);
        String denCond = StringUtils.hasText(m.getDenominatorWhere()) ? m.getDenominatorWhere() : "1 = 1";

        String noExpr = StringUtils.hasText(noKey) ? "MAX(" + noKey + ")" : "NULL";
        String nameExpr = StringUtils.hasText(nameKey) ? "MAX(" + nameKey + ")" : "NULL";
        String deptExpr = StringUtils.hasText(deptKey) ? deptKey : "NULL";
        String groupBy = StringUtils.hasText(deptKey) ? pk + ", " + deptKey : pk;

        String inner = "SELECT "
                + "  " + pk + " AS patient_id, "
                + "  " + noExpr + " AS in_hospital_no, "
                + "  " + nameExpr + " AS patient_name, "
                + "  " + deptExpr + " AS depart_code, "
                + "  MAX(CASE WHEN (" + numCond + ") THEN 1 ELSE 0 END) AS in_numerator, "
                + "  MAX(CASE WHEN (" + denCond + ") THEN 1 ELSE 0 END) AS in_denominator "
                + " FROM " + factTable
                + " GROUP BY " + groupBy;
        return "SELECT * FROM (" + inner + ") x WHERE x.in_numerator = 1 OR x.in_denominator = 1";
    }

    private String key(String declared, String fallback) {
        return StringUtils.hasText(declared) ? declared : fallback;
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
