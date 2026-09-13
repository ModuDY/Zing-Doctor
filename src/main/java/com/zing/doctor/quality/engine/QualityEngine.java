package com.zing.doctor.quality.engine;

import cn.hutool.crypto.digest.DigestUtil;
import com.zing.doctor.quality.config.QualityProperties;
import com.zing.doctor.quality.dsl.FactDefinition;
import com.zing.doctor.quality.dsl.MetricDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 质控计算引擎（本方案唯一承载计算的 Java 代码）。
 *
 * <p>职责：事实层物化 → 指标 SQL 编译执行 → 患者明细提取 → 血缘记录。
 * 引擎内部不含任何「某一条指标」的业务分支；指标差异全部由 YAML 表达。
 *
 * <p>性能设计：
 * <ol>
 *   <li><b>事实层一次物化，多指标复用</b>：一个周期内 qc_fact_* 只建一次，
 *       同域 N 条指标共享 → 1 次扫描 + N 次轻聚合，替代老系统 N 次全量扫描</li>
 *   <li><b>SQL 全下推</b>：不在 JVM 里循环明细，只搬运聚合结果</li>
 *   <li><b>按周期隔离事实表</b>：表名带周期后缀，跨周期下钻不会读到过期数据</li>
 * </ol>
 */
@Component
public class QualityEngine {

    private static final Logger log = LoggerFactory.getLogger(QualityEngine.class);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final QualityDslLoader dsl;
    private final SqlCompiler compiler;
    private final QualitySqlMapper sqlMapper;
    private final QualityProperties props;

    /** cacheKey(factName + 周期) → 物化表全限定名 */
    private final Map<String, String> factTableCache = new ConcurrentHashMap<>();

    public QualityEngine(QualityDslLoader dsl, SqlCompiler compiler,
                         QualitySqlMapper sqlMapper, QualityProperties props) {
        this.dsl = dsl;
        this.compiler = compiler;
        this.sqlMapper = sqlMapper;
        this.props = props;
    }

    /** 清空事实层缓存（新一轮计算前调用，确保按当前周期重建）。 */
    public void resetFacts() {
        factTableCache.clear();
    }

    /**
     * 物化事实层，返回可直接用于指标 SQL 的 FROM 片段。
     *
     * @return 物理表全限定名（materializeFacts=true）或内联子查询（false）
     */
    public String materializeFact(String factName, LocalDateTime start, LocalDateTime end) {
        FactDefinition f = dsl.getFacts().get(factName);
        if (f == null) {
            throw new IllegalStateException("未定义的事实层: " + factName);
        }
        String key = cacheKey(factName, start);
        String cached = factTableCache.get(key);
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            cached = factTableCache.get(key);
            if (cached != null) {
                return cached;
            }
            String sql = compiler.compileFact(f, dsl.getSourceConfig(), start, end);
            guard(sql);
            String target;
            if (props.isMaterializeFacts()) {
                String physical = "\"" + props.getFactSchema() + "\".\""
                        + compiler.factPhysicalName(props.getFactTablePrefix(), factName)
                        + "_" + DAY.format(start) + "\"";
                dropIfExists(physical);
                sqlMapper.execute("CREATE TABLE " + physical + " AS " + sql);
                log.debug("[质控] 事实层物化 {} → {}", factName, physical);
                target = physical;
            } else {
                target = "(" + sql + ")";
            }
            factTableCache.put(key, target);
            return target;
        }
    }

    /**
     * 计算一条指标（维度非空时返回多行）。
     *
     * <p>除逐科室结果外，额外产出一行 {@code depart_code = 'ALL'}：
     * 由各科室分子/分母汇总后重算，率类因此是加权率而非「各科室率的平均」。
     */
    public List<MetricOutcome> computeMetric(MetricDefinition m, LocalDateTime start, LocalDateTime end) {
        List<MetricOutcome> outcomes = new ArrayList<>();

        FactDefinition f = dsl.factOf(m);
        String effective = effectiveStatus(m, f);
        if (!"IMPL".equals(effective)) {
            MetricOutcome o = baseOutcome(m);
            o.setCalcStatus(effective);
            o.setFactName(m.getFact());
            outcomes.add(o);
            return outcomes;
        }

        String factTable;
        try {
            factTable = materializeFact(m.getFact(), start, end);
        } catch (Exception e) {
            log.error("[质控] 事实层物化失败: metric={}, fact={}", m.getCode(), m.getFact(), e);
            MetricOutcome o = baseOutcome(m);
            o.setCalcStatus("ERROR");
            o.setErrorMsg("事实层物化失败: " + e.getMessage());
            outcomes.add(o);
            return outcomes;
        }

        String sql = compiler.compileMetric(m, factTable, f);
        guard(sql);
        long t0 = System.currentTimeMillis();
        String hash = DigestUtil.md5Hex(sql);
        long scanned = scannedRows(factTable);

        List<Map<String, Object>> rows;
        try {
            rows = sqlMapper.query(sql);
        } catch (Exception e) {
            log.error("[质控] 指标计算失败: metric={}, SQL={}", m.getCode(), sql, e);
            MetricOutcome o = baseOutcome(m);
            o.setCalcStatus("ERROR");
            o.setErrorMsg(e.getMessage());
            o.setSqlText(sql);
            o.setSqlHash(hash);
            o.setDurationMs(System.currentTimeMillis() - t0);
            outcomes.add(o);
            return outcomes;
        }

        long cost = System.currentTimeMillis() - t0;
        String operators = compiler.operatorsOf(f) + ">metric";
        String sourceTables = String.join(",", compiler.sourceTables(f));

        if (rows == null || rows.isEmpty()) {
            MetricOutcome o = baseOutcome(m);
            o.setCalcStatus("NO_DATA");
            o.setFactName(m.getFact());
            o.setSqlText(sql);
            o.setSqlHash(hash);
            o.setOperators(operators);
            o.setSourceTables(sourceTables);
            o.setScannedRows(scanned);
            o.setDurationMs(cost);
            outcomes.add(o);
            return outcomes;
        }

        BigDecimal sumNum = BigDecimal.ZERO;
        BigDecimal sumDen = BigDecimal.ZERO;
        boolean hasNum = false;
        boolean hasDimRows = false;

        for (Map<String, Object> row : rows) {
            MetricOutcome o = baseOutcome(m);
            o.setFactName(m.getFact());
            o.setDepartCode(str(row.get("depart_code"), "ALL"));
            o.setNumerator(dec(row.get("num")));
            o.setDenominator(dec(row.get("den")));
            o.setMetricValue(computeValue(m, o.getNumerator(), o.getDenominator()));
            o.setCalcStatus(o.getNumerator() == null ? "NO_DATA" : "OK");
            o.setSqlText(sql);
            o.setSqlHash(hash);
            o.setOperators(operators);
            o.setSourceTables(sourceTables);
            o.setScannedRows(scanned);
            o.setNumRows(o.getNumerator() == null ? null : o.getNumerator().intValue());
            o.setDenRows(o.getDenominator() == null ? null : o.getDenominator().intValue());
            o.setDurationMs(cost);
            outcomes.add(o);

            if (!"ALL".equals(o.getDepartCode())) {
                hasDimRows = true;
            }
            if (o.getNumerator() != null) {
                sumNum = sumNum.add(o.getNumerator());
                hasNum = true;
            }
            if (o.getDenominator() != null) {
                sumDen = sumDen.add(o.getDenominator());
            }
        }

        // 按科室分组时，补一行全院汇总（率类为加权率，而非各科室率的平均）
        if (hasDimRows && hasNum) {
            MetricOutcome all = baseOutcome(m);
            all.setFactName(m.getFact());
            all.setDepartCode("ALL");
            all.setNumerator(sumNum);
            all.setDenominator(sumDen);
            all.setMetricValue(computeValue(m, sumNum, sumDen));
            all.setCalcStatus("OK");
            all.setSqlText(sql);
            all.setSqlHash(hash);
            all.setOperators(operators);
            all.setSourceTables(sourceTables);
            all.setScannedRows(scanned);
            all.setNumRows(sumNum.intValue());
            all.setDenRows(sumDen.intValue());
            all.setDurationMs(cost);
            outcomes.add(all);
        }
        return outcomes;
    }

    /**
     * 按需生成某指标的患者级命中明细（血缘第 4 层）。
     *
     * <p>页面下钻时调用：复用同一事实层，成本是一次事实表扫描。
     */
    public List<MetricOutcome.PatientHit> loadPatients(MetricDefinition m,
                                                       LocalDateTime start, LocalDateTime end) {
        FactDefinition f = dsl.factOf(m);
        if (f == null || !"IMPL".equals(effectiveStatus(m, f))) {
            return new ArrayList<>();
        }
        String factTable = materializeFact(m.getFact(), start, end);
        String sql = compiler.compilePatients(m, factTable, f);
        guard(sql);
        List<Map<String, Object>> rows = sqlMapper.query(sql);
        List<MetricOutcome.PatientHit> hits = new ArrayList<>();
        if (rows == null) {
            return hits;
        }
        for (Map<String, Object> row : rows) {
            MetricOutcome.PatientHit h = new MetricOutcome.PatientHit();
            h.setPatientId(str(row.get("patient_id"), null));
            h.setInHospitalNo(str(row.get("in_hospital_no"), null));
            h.setPatientName(str(row.get("patient_name"), null));
            h.setDepartCode(str(row.get("depart_code"), null));
            h.setInNumerator(intOf(row.get("in_numerator")));
            h.setInDenominator(intOf(row.get("in_denominator")));
            hits.add(h);
        }
        return hits;
    }

    /** 清理非当前周期的事实表，避免逐月累积。失败不影响业务。 */
    public void purgeOldFacts(String keepSuffix) {
        if (!props.isMaterializeFacts()) {
            return;
        }
        try {
            String like = props.getFactTablePrefix() + "fact_%";
            List<Map<String, Object>> tables = sqlMapper.query(
                    "SELECT TABLE_NAME AS TN FROM ALL_TABLES WHERE TABLE_NAME LIKE '" + like + "'");
            for (Map<String, Object> t : tables) {
                String name = str(t.get("TN") != null ? t.get("TN") : t.get("tn"), null);
                if (name == null || name.endsWith("_" + keepSuffix)) {
                    continue;
                }
                dropIfExists("\"" + props.getFactSchema() + "\".\"" + name + "\"");
            }
        } catch (Exception e) {
            log.debug("[质控] 历史事实表清理跳过: {}", e.getMessage());
        }
    }

    /** 供诊断：当前配置的事实层 SQL。 */
    public String currentFactSql(String factName, LocalDateTime start, LocalDateTime end) {
        FactDefinition f = dsl.getFacts().get(factName);
        return f == null ? null : compiler.compileFact(f, dsl.getSourceConfig(), start, end);
    }

    // ------------------------------------------------------------------
    // internal
    // ------------------------------------------------------------------

    private String cacheKey(String factName, LocalDateTime start) {
        return factName + "_" + DAY.format(start);
    }

    /** 有效状态：指标声明与事实层可用性取较严者。 */
    private String effectiveStatus(MetricDefinition m, FactDefinition f) {
        String s = m.getImplStatus() == null ? "IMPL" : m.getImplStatus().toUpperCase();
        if ("IMPL".equals(s)) {
            if (f == null) {
                return "PENDING_SOURCE";
            }
            String fs = f.getStatus() == null ? "ACTIVE" : f.getStatus().toUpperCase();
            if (!"ACTIVE".equals(fs)) {
                return fs;
            }
        }
        return s;
    }

    private MetricOutcome baseOutcome(MetricDefinition m) {
        MetricOutcome o = new MetricOutcome();
        o.setMetricCode(m.getCode());
        o.setMetricName(m.getName());
        o.setDomain(m.getDomain());
        o.setUnit(m.getUnit());
        o.setExpressionVersion(m.getVersion());
        return o;
    }

    private BigDecimal computeValue(MetricDefinition m, BigDecimal num, BigDecimal den) {
        if (num == null) {
            return null;
        }
        String vt = m.getValueType() == null ? "COUNT" : m.getValueType().toUpperCase();
        switch (vt) {
            case "RATE": {
                if (den == null || den.signum() == 0) {
                    return null;
                }
                int scale = m.getScale() == null ? 100 : m.getScale();
                return num.multiply(BigDecimal.valueOf(scale)).divide(den, 4, RoundingMode.HALF_UP);
            }
            case "AVG": {
                if (den == null || den.signum() == 0) {
                    return null;
                }
                return num.divide(den, 4, RoundingMode.HALF_UP);
            }
            case "SUM":
            case "COUNT":
            default:
                return num;
        }
    }

    private long scannedRows(String factTable) {
        try {
            List<Map<String, Object>> r = sqlMapper.query("SELECT COUNT(1) AS c FROM " + factTable);
            if (r != null && !r.isEmpty()) {
                Long c = longOf(r.get(0).get("c"));
                return c == null ? 0L : c;
            }
        } catch (Exception e) {
            log.debug("[质控] 事实层行数统计失败: {}", e.getMessage());
        }
        return 0L;
    }

    private void dropIfExists(String table) {
        try {
            sqlMapper.execute("DROP TABLE " + table);
        } catch (Exception ignore) {
            // 首次执行时表不存在，属正常
        }
    }

    /** 危险语句拦截：配置表达式不得注入语句分隔符或注释。 */
    private void guard(String sql) {
        if (sql == null) {
            throw new IllegalStateException("SQL 为空");
        }
        String lower = sql.toLowerCase();
        if (lower.contains(";") || lower.contains("--") || lower.contains("/*")) {
            throw new IllegalStateException("质控配置存在非法字符（; -- /*），已拒绝执行");
        }
    }

    public boolean hasFact(String factName) {
        return dsl.getFacts().containsKey(factName);
    }

    public LinkedHashSet<String> registeredFacts() {
        return new LinkedHashSet<>(dsl.getFacts().keySet());
    }

    private String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
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

    private Integer intOf(Object o) {
        Long l = longOf(o);
        return l == null ? null : l.intValue();
    }

    private Long longOf(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
