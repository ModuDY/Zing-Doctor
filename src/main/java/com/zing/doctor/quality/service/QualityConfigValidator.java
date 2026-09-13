package com.zing.doctor.quality.service;

import com.zing.doctor.quality.config.QualityProperties;
import com.zing.doctor.quality.dsl.FactDefinition;
import com.zing.doctor.quality.dsl.MetricDefinition;
import com.zing.doctor.quality.engine.QualityDslLoader;
import com.zing.doctor.quality.engine.QualityEngine;
import com.zing.doctor.quality.engine.QualityExpressionAnalyzer;
import com.zing.doctor.quality.engine.QualitySqlGuard;
import com.zing.doctor.quality.engine.QualitySqlMapper;
import com.zing.doctor.quality.engine.SqlCompiler;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 质控配置四级校验链。
 *
 * <table>
 *   <caption>校验级别</caption>
 *   <tr><th>级别</th><th>内容</th><th>拦得住什么</th></tr>
 *   <tr><td>L1 安全</td><td>表达式片段</td><td>注入、语句截断、括号/引号错配</td></tr>
 *   <tr><td>L2 编译</td><td>DSL → SQL</td><td>语法结构错误、事实层/来源表缺失</td></tr>
 *   <tr><td>L3 字段</td><td>列名引用</td><td>「引用了一个不存在的列」—— 最高频的手写错误</td></tr>
 *   <tr><td>L4 试跑</td><td>真跑一遍</td><td>只有跑起来才暴露的问题（类型不匹配、表无数据）</td></tr>
 * </table>
 *
 * <p><b>为什么必须四级都有</b>：L1–L3 是静态判断，快但覆盖不全；L4 最真实但代价高。
 * 只做静态检查，用户会遇到「保存成功但跑出来是空/报错」；只做试跑，用户要在一次昂贵查询后
 * 才知道括号少写了一个。前三级给快速反馈，第四级给最终信心。
 *
 * <p><b>试跑不物化事实层</b>：直接内联子查询执行，不在生产 schema 里建临时表 ——
 * 配置页的一次点错不应该在库里留下垃圾表。
 */
@Component
public class QualityConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(QualityConfigValidator.class);

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 提示里最多列多少个可选列/事实层，避免报错信息刷屏 */
    private static final int HINT_LIMIT = 20;

    private final QualityDslLoader dsl;
    private final SqlCompiler compiler;
    private final QualitySqlGuard guard;
    private final QualityExpressionAnalyzer analyzer;
    private final QualitySqlMapper sqlMapper;
    private final QualityProperties props;

    public QualityConfigValidator(QualityDslLoader dsl, SqlCompiler compiler, QualitySqlGuard guard,
                                  QualityExpressionAnalyzer analyzer, QualitySqlMapper sqlMapper,
                                  QualityProperties props) {
        this.dsl = dsl;
        this.compiler = compiler;
        this.guard = guard;
        this.analyzer = analyzer;
        this.sqlMapper = sqlMapper;
        this.props = props;
    }

    // ==================================================================
    // 指标
    // ==================================================================

    public ValidationResult validateMetric(MetricDefinition m, boolean withTrial,
                                           LocalDateTime start, LocalDateTime end) {
        ValidationResult r = new ValidationResult();
        if (m == null) {
            r.addError("指标内容为空");
            return r.finish();
        }
        checkMetricBasics(m, r);
        if (!r.getErrors().isEmpty()) {
            return r.finish();
        }

        FactDefinition f = dsl.getFacts().get(m.getFact());
        String impl = text(m.getImplStatus(), "IMPL").toUpperCase();
        boolean runnable = "IMPL".equals(impl);

        if (f == null) {
            // 空壳/待接源本就没有事实层，不该报错；只有「声明为已实现」才必须绑到事实层
            if (runnable) {
                r.addError("绑定的事实层不存在：" + m.getFact() + "。" + factHint());
            } else {
                r.addWarning("该指标为 " + impl + " 状态，未绑定有效事实层，不参与计算");
            }
        } else if (runnable) {
            String fs = text(f.getStatus(), "ACTIVE").toUpperCase();
            if (!"ACTIVE".equals(fs)) {
                r.addWarning("事实层 " + f.getFact() + " 当前状态为 " + fs + "，指标不会被计算");
            }
        }

        // ---- L1 安全 ----
        checkMetricFragments(m, r);

        // ---- L3 字段 ----
        if (f != null) {
            List<String> unknown = analyzer.unknownColumns(f, metricExprs(m));
            if (!unknown.isEmpty()) {
                r.addError("事实层 " + f.getFact() + " 未产出这些列：" + String.join("、", unknown)
                        + "。" + availableHint(f));
            }
        }

        // ---- L2 编译 ----
        if (r.getErrors().isEmpty()) {
            try {
                String sql = compiler.compileMetric(m, placeholderFact(m.getFact()), f);
                guard.guardStatement(sql);
                r.setSql(sql);
            } catch (RuntimeException e) {
                r.addError("编译失败：" + e.getMessage());
            }
        }

        if (!r.getErrors().isEmpty()) {
            return r.finish();
        }

        // ---- L4 试跑 ----
        if (withTrial) {
            if (!runnable) {
                r.addWarning("该指标为 " + impl + " 状态，未做试跑");
            } else if (f != null) {
                trialMetric(m, f, start, end, r);
            }
        }
        return r.finish();
    }

    private void checkMetricBasics(MetricDefinition m, ValidationResult r) {
        if (!StringUtils.hasText(m.getCode())) {
            r.addError("指标编号不能为空");
        } else if (!m.getCode().matches("[A-Za-z_][A-Za-z0-9_]*")) {
            r.addError("指标编号只能由字母、数字、下划线组成，且不能以数字开头：" + m.getCode());
        }
        if (!StringUtils.hasText(m.getName())) {
            r.addError("指标名称不能为空");
        }
        if (!StringUtils.hasText(m.getDomain())) {
            r.addError("所属域不能为空");
        }
        if (!StringUtils.hasText(m.getFact())) {
            r.addError("必须绑定一个事实层");
        }
        Set<String> valueTypes = new LinkedHashSet<>(Arrays.asList("COUNT", "RATE", "AVG", "SUM"));
        String vt = text(m.getValueType(), "COUNT").toUpperCase();
        if (!valueTypes.contains(vt)) {
            r.addError("值类型只能是 COUNT / RATE / AVG / SUM，当前为：" + m.getValueType());
        }
        Set<String> aggs = new LinkedHashSet<>(Arrays.asList("PT_COUNT", "SUM", "AVG"));
        String agg = text(m.getAgg(), "PT_COUNT").toUpperCase();
        if (!aggs.contains(agg)) {
            r.addError("聚合方式只能是 PT_COUNT / SUM / AVG，当前为：" + m.getAgg());
        }
        if ("RATE".equals(vt)) {
            if (m.getScale() == null) {
                r.addWarning("率类未设置放大系数，将按默认 100（百分比）计算");
            } else if (m.getScale() <= 0) {
                r.addError("放大系数必须为正数");
            }
            if (!StringUtils.hasText(m.getDenominatorWhere())) {
                r.addWarning("未设置分母过滤条件，分母 = 同期全部对象（多数率类指标的预期分母）");
            }
        }
        if (("SUM".equals(agg) || "AVG".equals(agg)) && !StringUtils.hasText(m.getNumerator())) {
            r.addWarning("聚合方式为 " + agg + " 但分子表达式为空，将按 1 计算");
        }
        String impl = text(m.getImplStatus(), "IMPL").toUpperCase();
        if (!"IMPL".equals(impl) && "RATE".equals(vt) && StringUtils.hasText(m.getNumerator())) {
            r.addWarning("指标状态为 " + impl + "，口径已写好但不会参与计算");
        }
    }

    private void checkMetricFragments(MetricDefinition m, ValidationResult r) {
        guardInto(r, "分子过滤条件", () -> guard.guardMetricFragment("分子过滤条件", m.getWhere()));
        guardInto(r, "分子表达式", () -> guard.guardMetricFragment("分子表达式", m.getNumerator()));
        guardInto(r, "分母过滤条件", () -> guard.guardMetricFragment("分母过滤条件", m.getDenominatorWhere()));
        if (m.getDims() != null) {
            for (String d : m.getDims()) {
                guardInto(r, "分组维度", () -> {
                    guard.guardMetricFragment("分组维度", d);
                    if (!StringUtils.hasText(d) || !d.trim().matches("[A-Za-z_][A-Za-z0-9_]*")) {
                        throw new IllegalArgumentException("【分组维度】只能是单个列名，当前为：" + d
                                + "（如需按「科室+病区」组合分组，请分两次配置或改用事实层派生列）");
                    }
                });
            }
        }
    }

    private void trialMetric(MetricDefinition m, FactDefinition f,
                             LocalDateTime start, LocalDateTime end, ValidationResult r) {
        LocalDateTime[] win = window(start, end);
        try {
            String factSql = compiler.compileFact(f, dsl.getSourceConfig(), win[0], win[1]);
            guard.guardStatement(factSql);
            // 内联而非物化：配置页的一次试跑不该在生产 schema 里留下临时表
            String sql = compiler.compileMetric(m, "(" + factSql + ")", f);
            guard.guardStatement(sql);
            r.setSql(sql);

            long t0 = System.currentTimeMillis();
            List<Map<String, Object>> rows = sqlMapper.query(sql);
            r.setDurationMs(System.currentTimeMillis() - t0);
            r.setRows(rows == null ? new ArrayList<>() : rows);

            fillPreview(m, r);
            if (r.getPreview().isEmpty()) {
                r.addWarning("试跑成功但没有返回任何行 —— 该窗口内没有满足条件的数据（可能是口径问题，也可能确实为空）");
            }
        } catch (Exception e) {
            r.addError("试跑失败：" + rootMessage(e));
            log.warn("[质控] 试跑失败: metric={}", m.getCode(), e);
        }
    }

    /**
     * 按引擎口径把试跑结果换算成可读的指标值。
     *
     * <p>换算复用 {@link QualityEngine#computeValue}，且与引擎一样在按维度拆行后
     * 补一行 {@code ALL} 全院汇总（率类为分子分母汇总后重算的加权率，不是各科室率的平均）。
     */
    private void fillPreview(MetricDefinition m, ValidationResult r) {
        BigDecimal sumNum = BigDecimal.ZERO;
        BigDecimal sumDen = BigDecimal.ZERO;
        boolean hasNum = false;
        boolean hasDim = false;

        for (Map<String, Object> row : r.getRows()) {
            MetricPreview p = new MetricPreview();
            p.setDepartCode(text(pick(row, "depart_code"), "ALL"));
            p.setNumerator(dec(pick(row, "num")));
            p.setDenominator(dec(pick(row, "den")));
            p.setValue(QualityEngine.computeValue(m, p.getNumerator(), p.getDenominator()));
            r.getPreview().add(p);

            if (!"ALL".equals(p.getDepartCode())) {
                hasDim = true;
            }
            if (p.getNumerator() != null) {
                sumNum = sumNum.add(p.getNumerator());
                hasNum = true;
            }
            if (p.getDenominator() != null) {
                sumDen = sumDen.add(p.getDenominator());
            }
        }

        if (hasDim && hasNum) {
            MetricPreview all = new MetricPreview();
            all.setDepartCode("ALL");
            all.setNumerator(sumNum);
            all.setDenominator(sumDen);
            all.setValue(QualityEngine.computeValue(m, sumNum, sumDen));
            r.getPreview().add(all);
        }
    }

    // ==================================================================
    // 事实层
    // ==================================================================

    public ValidationResult validateFact(FactDefinition f, boolean withTrial,
                                         LocalDateTime start, LocalDateTime end) {
        ValidationResult r = new ValidationResult();
        if (f == null) {
            r.addError("事实层内容为空");
            return r.finish();
        }
        if (!StringUtils.hasText(f.getFact())) {
            r.addError("事实层名不能为空");
        } else if (!f.getFact().matches("[A-Za-z_][A-Za-z0-9_]*")) {
            r.addError("事实层名只能由字母、数字、下划线组成：" + f.getFact());
        }
        if (!StringUtils.hasText(f.getSource())) {
            r.addError("来源表不能为空（必须在数据源配置中登记）");
        }

        // ---- L1 安全（事实层允许子查询，见 QualitySqlGuard 说明） ----
        List<String> select = safe(f.getSelect());
        List<String> derive = safe(f.getDerive());
        List<String> where = safe(f.getWhere());
        List<String> group = safe(f.getGroup());
        for (String e : select) {
            guardInto(r, "选列", () -> guard.guardFactFragment("选列", e));
        }
        for (String e : derive) {
            guardInto(r, "派生列", () -> guard.guardFactFragment("派生列", e));
        }
        for (String e : where) {
            guardInto(r, "过滤条件", () -> guard.guardFactFragment("过滤条件", e));
        }
        for (String e : group) {
            guardInto(r, "分组列", () -> guard.guardFactFragment("分组列", e));
        }

        // ---- L2 编译（同时校验来源表是否已在数据源里登记） ----
        LocalDateTime[] win = window(start, end);
        if (r.getErrors().isEmpty()) {
            try {
                String sql = compiler.compileFact(f, dsl.getSourceConfig(), win[0], win[1]);
                guard.guardStatement(sql);
                r.setSql(sql);
            } catch (RuntimeException e) {
                r.addError("编译失败：" + e.getMessage());
            }
        }

        if (!r.getErrors().isEmpty()) {
            return r.finish();
        }

        // ---- L3 提示：事实层没投影出指标常用的四个键，会导致下钻/分组失效 ----
        if (!StringUtils.hasText(f.getDepartKey())) {
            r.addWarning("未声明科室列，指标无法按科室分组（将只产出全院一行）");
        }
        if (!StringUtils.hasText(f.getPatientKey())) {
            r.addWarning("未声明计数对象主键，去重计数将退回 patient_id");
        }

        // ---- L4 试跑 ----
        if (withTrial) {
            try {
                String sql = "SELECT COUNT(1) AS c FROM (" + r.getSql() + ") z";
                long t0 = System.currentTimeMillis();
                List<Map<String, Object>> rows = sqlMapper.query(sql);
                r.setDurationMs(System.currentTimeMillis() - t0);
                r.setFactRows(intOf(pick(rows == null || rows.isEmpty() ? null : rows.get(0), "c")));
                if (r.getFactRows() == 0) {
                    r.addWarning("事实层可编译执行，但该窗口内产出 0 行 —— 请确认来源表与过滤条件（"
                            + win[0].format(DT) + " ~ " + win[1].format(DT) + "）");
                }
            } catch (Exception e) {
                r.addError("试跑失败：" + rootMessage(e));
                log.warn("[质控] 事实层试跑失败: fact={}", f.getFact(), e);
            }
        }
        return r.finish();
    }

    // ==================================================================
    // 工具
    // ==================================================================

    private List<String> metricExprs(MetricDefinition m) {
        List<String> exprs = new ArrayList<>();
        if (StringUtils.hasText(m.getWhere())) {
            exprs.add(m.getWhere());
        }
        if (StringUtils.hasText(m.getNumerator())) {
            exprs.add(m.getNumerator());
        }
        if (StringUtils.hasText(m.getDenominatorWhere())) {
            exprs.add(m.getDenominatorWhere());
        }
        if (m.getDims() != null) {
            exprs.addAll(m.getDims());
        }
        return exprs;
    }

    /** 编译用占位表名：让页面能看到「这条口径会变成哪条 SQL」，同时保持与真实表名同形。 */
    private String placeholderFact(String factName) {
        return "\"" + props.getFactSchema() + "\".\""
                + props.getFactTablePrefix() + factName + "_" + DAY.format(LocalDateTime.now()) + "\"";
    }

    private String availableHint(FactDefinition f) {
        Set<String> cols = analyzer.availableColumns(f);
        if (cols.isEmpty()) {
            return "（该事实层未声明投影列，可引用任意列）";
        }
        List<String> list = new ArrayList<>(cols);
        String shown = String.join("、", list.subList(0, Math.min(HINT_LIMIT, list.size())));
        return "可引用列：" + shown + (list.size() > HINT_LIMIT ? " 等 " + list.size() + " 个" : "");
    }

    private String factHint() {
        List<String> names = new ArrayList<>(dsl.getFacts().keySet());
        if (names.isEmpty()) {
            return "当前没有任何事实层配置。";
        }
        String shown = String.join("、", names.subList(0, Math.min(HINT_LIMIT, names.size())));
        return "可选事实层：" + shown + (names.size() > HINT_LIMIT ? " 等 " + names.size() + " 个" : "");
    }

    private void guardInto(ValidationResult r, String label, Runnable check) {
        try {
            check.run();
        } catch (RuntimeException e) {
            r.addError(e.getMessage() == null ? (label + "不合法") : e.getMessage());
        }
    }

    private LocalDateTime[] window(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null) {
            return new LocalDateTime[]{start, end};
        }
        LocalDateTime now = LocalDateTime.now();
        return new LocalDateTime[]{now.withDayOfMonth(1).toLocalDate().atStartOfDay(), now};
    }

    private Object pick(Map<String, Object> row, String col) {
        if (row == null) {
            return null;
        }
        Object v = row.get(col);
        return v != null ? v : row.get(col.toUpperCase());
    }

    private String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.toString() : t.getMessage();
    }

    private String text(Object o, String def) {
        return o == null || String.valueOf(o).trim().isEmpty() ? def : String.valueOf(o).trim();
    }

    private BigDecimal dec(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal) {
            return (BigDecimal) o;
        }
        if (o instanceof Number) {
            return BigDecimal.valueOf(((Number) o).doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private int intOf(Object o) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return o == null ? 0 : Integer.parseInt(String.valueOf(o).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private <T> List<T> safe(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }

    // ==================================================================
    // 结果模型
    // ==================================================================

    /** 校验结果：{@code ok} 只由 errors 决定，warnings 不阻断保存。 */
    @Data
    public static class ValidationResult {
        private boolean ok = true;
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        /** 编译产物（试跑时是可直接执行的 SQL） */
        private String sql;
        /** 试跑原始行 */
        private List<Map<String, Object>> rows = new ArrayList<>();
        /** 试跑换算后的指标值 */
        private List<MetricPreview> preview = new ArrayList<>();
        /** 事实层试跑产出行数 */
        private int factRows;
        private long durationMs;

        void addError(String msg) {
            errors.add(msg);
        }

        void addWarning(String msg) {
            warnings.add(msg);
        }

        ValidationResult finish() {
            ok = errors.isEmpty();
            return this;
        }
    }

    @Data
    public static class MetricPreview {
        private String departCode;
        private BigDecimal numerator;
        private BigDecimal denominator;
        private BigDecimal value;
    }
}
