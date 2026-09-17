package com.zing.doctor.quality.service;

import com.zing.doctor.quality.config.QualityProperties;
import com.zing.doctor.quality.dsl.FactDefinition;
import com.zing.doctor.quality.dsl.MetricDefinition;
import com.zing.doctor.quality.dsl.PatientFieldDefinition;
import com.zing.doctor.quality.dto.PeriodRange;
import com.zing.doctor.quality.engine.MetricOutcome;
import com.zing.doctor.quality.engine.PatientColumns;
import com.zing.doctor.quality.engine.QualityDslLoader;
import com.zing.doctor.quality.engine.QualityEngine;
import com.zing.doctor.quality.engine.QualityExpressionAnalyzer;
import com.zing.doctor.quality.entity.QualityCalcRun;
import com.zing.doctor.quality.entity.QualityCalcTrace;
import com.zing.doctor.quality.entity.QualityIndex;
import com.zing.doctor.quality.entity.QualityMetricResult;
import com.zing.doctor.quality.mapper.QualityCalcRunMapper;
import com.zing.doctor.quality.mapper.QualityCalcTraceMapper;
import com.zing.doctor.quality.mapper.QualityIndexMapper;
import com.zing.doctor.quality.mapper.QualityMetricPatientMapper;
import com.zing.doctor.quality.mapper.QualityMetricResultMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 质控查询服务：看板、指标详情、血缘下钻、配置审阅。
 */
@Service
public class QualityQueryService {

    private final QualityProperties props;
    private final QualityDslLoader dsl;
    private final QualityEngine engine;
    private final QualityIndexMapper indexMapper;
    private final QualityMetricResultMapper resultMapper;
    private final QualityCalcTraceMapper traceMapper;
    private final QualityMetricPatientMapper patientMapper;
    private final QualityCalcRunMapper runMapper;
    private final QualityExpressionAnalyzer analyzer;

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public QualityQueryService(QualityProperties props, QualityDslLoader dsl, QualityEngine engine,
                              QualityIndexMapper indexMapper, QualityMetricResultMapper resultMapper,
                              QualityCalcTraceMapper traceMapper,
                              QualityMetricPatientMapper patientMapper,
                              QualityCalcRunMapper runMapper,
                              QualityExpressionAnalyzer analyzer) {
        this.props = props;
        this.dsl = dsl;
        this.engine = engine;
        this.indexMapper = indexMapper;
        this.resultMapper = resultMapper;
        this.traceMapper = traceMapper;
        this.patientMapper = patientMapper;
        this.runMapper = runMapper;
        this.analyzer = analyzer;
    }

    /**
     * 指标看板：127 行按域分组，含本期值与实现状态。
     *
     * <p>占位指标（PLACEHOLDER / PENDING_SOURCE / MANUAL）同样返回元信息，
     * 前端渲染成正常行、数值列显示「—」，因此页面永远是完整的 127 行。
     */
    public Map<String, Object> overview(String periodType, String periodStart, String departCode) {
        PeriodRange range = PeriodRange.of(periodType, periodStart);
        String dept = normalizeDepart(departCode);

        List<QualityIndex> indices = indexMapper.selectAllOrdered();
        Map<String, QualityMetricResult> resultMap = new LinkedHashMap<>();
        try {
            for (QualityMetricResult r : resultMapper.selectByPeriod(range.getStart(), dept)) {
                resultMap.put(r.getMetricCode(), r);
            }
        } catch (Exception e) {
            // 未执行过计算的周期：返回纯字典视图，便于前端先行展示
        }

        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        int ok = 0, placeholder = 0, noData = 0, error = 0;
        List<String> errorCodes = new ArrayList<>();
        for (QualityIndex idx : indices) {
            QualityMetricResult r = resultMap.get(idx.getIndexCode());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", idx.getIndexCode());
            row.put("name", idx.getIndexName());
            row.put("domain", idx.getDomainCode());
            row.put("unit", idx.getUnit());
            row.put("valueType", idx.getValueType());
            row.put("implStatus", idx.getImplStatus());
            row.put("factName", idx.getFactName());
            row.put("expressionVersion", idx.getExpressionVersion());
            row.put("computable", "IMPL".equals(idx.getImplStatus()) ? 1 : 0);
            row.put("remark", idx.getRemark());
            row.put("value", r == null ? null : r.getMetricValue());
            row.put("numerator", r == null ? null : r.getNumerator());
            row.put("denominator", r == null ? null : r.getDenominator());
            row.put("calcStatus", r == null ? "NOT_CALC" : r.getCalcStatus());
            row.put("runId", r == null ? null : r.getRunId());

            groups.computeIfAbsent(idx.getDomainCode() == null ? "其他" : idx.getDomainCode(),
                    k -> new ArrayList<>()).add(row);

            String st = r == null ? "NOT_CALC" : r.getCalcStatus();
            if ("OK".equals(st)) {
                ok++;
            } else if ("NO_DATA".equals(st)) {
                noData++;
            } else if ("ERROR".equals(st)) {
                // ERROR 单独计数并留编号。此前它混在 placeholder 里，页面上与「待接源表」
                // 视觉无差别 —— 源表改结构导致某条指标连续几个月算不出来，也不会有人注意到。
                error++;
                if (errorCodes.size() < 10) {
                    errorCodes.add(idx.getIndexCode());
                }
            } else {
                placeholder++;
            }
        }

        List<Map<String, Object>> groupList = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : groups.entrySet()) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("domain", e.getKey());
            g.put("metrics", e.getValue());
            groupList.add(g);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", indices.size());
        summary.put("ok", ok);
        summary.put("noData", noData);
        summary.put("placeholder", placeholder);
        // 计算异常必须能被一眼看到：这两个字段供看板顶部出红条并直接列出出错指标编号
        summary.put("error", error);
        summary.put("errorCodes", errorCodes);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("periodType", range.getPeriodType());
        out.put("periodStart", range.getStart());
        out.put("periodEnd", range.getEnd());
        out.put("departCode", dept);
        out.put("summary", summary);
        out.put("groups", groupList);
        return out;
    }

    /** 单指标详情：口径 + 本期值 + 追溯摘要 + 患者明细。 */
    public Map<String, Object> metricDetail(String code, String periodStart, String departCode) {
        MetricDefinition m = dsl.getMetrics().get(code);
        QualityIndex idx = indexMapper.selectByCode(code);
        PeriodRange range = PeriodRange.of("MONTH", periodStart);
        String dept = normalizeDepart(departCode);

        QualityMetricResult result = null;
        try {
            for (QualityMetricResult r : resultMapper.selectByPeriod(range.getStart(), dept)) {
                if (code.equals(r.getMetricCode())) {
                    result = r;
                    break;
                }
            }
        } catch (Exception ignore) {
            // 未计算过
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        out.put("name", idx != null ? idx.getIndexName() : (m != null ? m.getName() : code));
        out.put("domain", idx != null ? idx.getDomainCode() : (m != null ? m.getDomain() : null));
        out.put("unit", idx != null ? idx.getUnit() : (m != null ? m.getUnit() : null));
        // 实现状态以配置真源（DSL ← quality_metric_def）为准：看板据此决定要不要给「录入」入口，
        // 而 quality_index 是字典同步的产物、可能滞后一轮。idx 兜底是因为老库里可能只有字典行。
        String implStatus = m != null ? m.getImplStatus() : null;
        if (implStatus == null || implStatus.trim().isEmpty()) {
            implStatus = idx != null ? idx.getImplStatus() : null;
        }
        out.put("implStatus", implStatus);
        out.put("factName", m != null ? m.getFact() : (idx != null ? idx.getFactName() : null));
        out.put("expressionVersion", m != null ? m.getVersion() : null);
        out.put("remark", idx != null ? idx.getRemark() : null);
        out.put("periodStart", range.getStart());
        out.put("periodEnd", range.getEnd());
        out.put("departCode", dept);
        out.put("result", result);
        // 明细列由指标自己配：抽屉按这份定义出列，未配置时给出默认列
        out.put("patientFields", patientFieldDefs(m));
        if (result != null && result.getRunId() != null) {
            // 走 trace() 的组装：实体本身不带 SQL 原文，drawer 展开行要看的就是它
            out.put("traces", trace(result.getRunId(), code).get("traces"));
            // patients() 返回的是 {code, patients:[...], size, message} 信封，抽屉要的只是里面的列表：
            // 直接塞整包过去，前端 el-table :data 会拿到对象，迭代时抛 "is not iterable"。
            // message 必须一并带出：明细失败（例如患者明细配了与默认列同名的字段，
            // SQL 报「列名不明确」）时列表是空的，页面只会显示「无患者明细」，
            // 不把原因送到前端就只能翻服务器日志。
            // includeExcluded=true：抽屉里要能切到「未纳入」，而常规明细的外层条件把
            // 「既没进分子也没进分母」的人滤掉了 —— 不取全，那一档永远是空的
            //（现场表现就是「未纳入」点开没有一个人，而这批人恰恰是质控争议的焦点）。
            Map<String, Object> pd = patients(code, periodStart, departCode, true);
            out.put("patients", pd.get("patients"));
            out.put("viewCounts", pd.get("viewCounts"));
            if (pd.get("message") != null) {
                out.put("patientsMessage", pd.get("message"));
            }
        }
        return out;
    }

    /** 追溯第 3 层：算子链 + SQL 原文 + 扫描行数。 */
    public Map<String, Object> trace(String runId, String code) {
        List<QualityCalcTrace> traces = traceMapper.selectByMetric(runId, code);
        List<Map<String, Object>> list = new ArrayList<>();
        for (QualityCalcTrace t : traces) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dimKey", t.getDimKey());
            row.put("factName", t.getFactName());
            row.put("operators", t.getOperators());
            row.put("sourceTables", t.getSourceTables());
            row.put("sqlHash", t.getSqlHash());
            row.put("scannedRows", t.getScannedRows());
            row.put("numRows", t.getNumRows());
            row.put("denRows", t.getDenRows());
            row.put("durationMs", t.getDurationMs());
            row.put("expressionVersion", t.getExpressionVersion());
            row.put("sql", traceMapper.selectSqlText(runId, code, t.getDimKey()));
            list.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", runId);
        out.put("code", code);
        out.put("traces", list);
        return out;
    }

    /**
     * 追溯第 4 层：患者级命中明细。
     *
     * <p>按需生成而非预计算——下钻是人工节奏、一次一条指标，因此这里直接复用事实层重算，
     * 既省掉月度批算的巨大开销，又不牺牲「点数字能看到人」的能力。
     */
    public Map<String, Object> patients(String code, String periodStart, String departCode) {
        return patients(code, periodStart, departCode, false);
    }

    /**
     * 对象级命中明细。
     *
     * @param includeExcluded 一并返回「既没进分子也没进分母」的人。常规下钻为 false；
     *        排查「这个人为什么不算分母」时置 true —— 这类人此前被明细 SQL 的外层条件
     *        直接滤掉，接口和页面都无法看到，而质控争议绝大多数正出在他们身上。
     */
    public Map<String, Object> patients(String code, String periodStart, String departCode,
                                        boolean includeExcluded) {
        return patients(code, periodStart, departCode, includeExcluded, null);
    }

    /**
     * 对象级命中明细，可按「纳入视角」过滤。
     *
     * <p>视角分两个总览档与四个互斥档：进分母 = 已达标 + 未达标，进分子 = 已达标 + 口径异常；
     * 四个互斥档（已达标 / 未达标 / 未纳入 / 口径异常）两两不重叠、并集为全部扫到的对象 ——
     * 于是各档人数可以直接相加对账。旧的「仅分子 / 仅分母」做不到：
     * 分子通常也落在分母里、被数了两遍，导致点「分母 20 人」进去看到 20 行里混着
     * 12 个已达标的人，而真正要找的那 8 个未达标者只能肉眼从里面挑。
     *
     * @param view null / "all" 不过滤；"inDenominator" 进分母、"inNumerator" 进分子；
     *             其余取 inclusionState 的四个取值之一
     */
    public Map<String, Object> patients(String code, String periodStart, String departCode,
                                        boolean includeExcluded, String view) {
        MetricDefinition m = dsl.getMetrics().get(code);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        if (m == null) {
            out.put("message", "指标未在配置中定义");
            out.put("patients", new ArrayList<>());
            return out;
        }
        // 结构上不可能有明细的指标，把原因直接说出来。否则点指标名打开一张空表，
        // 使用者只会当成本期没数据，而实际是这类指标根本不产明细 ——
        // 空表不解释，就是「看起来完整」的典型陷阱。
        if (m.isDerived()) {
            return emptyPatients(out, "该指标为引用型（分子 / 分母取自其它指标的结果），不产生患者明细");
        }
        String declared = m.getImplStatus() == null ? "IMPL" : m.getImplStatus().toUpperCase();
        if ("MANUAL".equals(declared)) {
            return emptyPatients(out, "该指标为人工录入类，没有患者级明细（数值由科室手工填写）");
        }
        if ("PENDING_SOURCE".equals(declared)) {
            return emptyPatients(out, "该指标的事实层尚未接入数据源，暂无患者明细");
        }
        if ("PLACEHOLDER".equals(declared)) {
            return emptyPatients(out, "该指标口径待业务确认（空壳占位），暂无患者明细");
        }
        PeriodRange range = PeriodRange.of("MONTH", periodStart);
        // 明细必须跟着看板的科室筛选走：筛选值是编码，'ALL' 表示全院
        String dept = normalizeDepart(departCode);
        try {
            List<MetricOutcome.PatientHit> hits = engine.loadPatients(m, range.getStart(), range.getEnd(),
                    "ALL".equals(dept) ? null : dept, includeExcluded);
            List<Map<String, Object>> list = new ArrayList<>();
            Set<String> timeKeys = new LinkedHashSet<>();
            Map<String, Object> counts = newViewCounts();
            for (MetricOutcome.PatientHit h : hits) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("patientId", h.getPatientId());
                row.put("inHospitalNo", h.getInHospitalNo());
                row.put("patientName", h.getPatientName());
                row.put("departCode", h.getDepartCode());
                row.put("inNumerator", h.getInNumerator());
                row.put("inDenominator", h.getInDenominator());
                // 指标自配的补充字段平铺在行上：前端按 patientFields 里的 key 直接取。
                // 时间值在这里统一格式化成可读字符串（原因见 formatCellValue）：
                // 补充列的值由事实层 SQL 直接查出，类型随驱动而变，前端无法可靠区分。
                if (h.getExtras() != null) {
                    for (Map.Entry<String, Object> e : h.getExtras().entrySet()) {
                        Object formatted = formatCellValue(e.getValue());
                        row.put(e.getKey(), formatted);
                        // 引用变了说明这列是时间列：列定义是照配置生成的、看不见数据，
                        // 靠这里把「实际是时间」这件事带出去，页面才好做对齐与提示
                        if (formatted != e.getValue()) {
                            timeKeys.add(e.getKey());
                        }
                    }
                }
                // 纳入状态随行带出去：页面据此上色与加角标，前端不必再重算一遍判定
                String state = inclusionState(h.getInNumerator(), h.getInDenominator());
                row.put("inclusionState", state);
                tally(counts, state);
                list.add(row);
            }
            // 页签人数恒按「未过滤」统计：否则每切一次档，其它档位的人数就跟着当前列表走，
            // 而它正是用户拿来对账的东西（各档相加 = 总数），一变就废了。
            out.put("viewCounts", counts);
            List<Map<String, Object>> shown = new ArrayList<>();
            for (Map<String, Object> row : list) {
                if (matchesView(String.valueOf(row.get("inclusionState")), view)) {
                    shown.add(row);
                }
            }
            out.put("patients", shown);
            out.put("fields", patientFieldDefs(m));
            out.put("timeKeys", timeKeys);
            out.put("size", shown.size());
            out.put("totalSize", list.size());
            out.put("view", view == null || view.trim().isEmpty() ? "all" : view.trim());
            out.put("departCode", dept);
        } catch (Exception e) {
            out.put("message", "明细生成失败: " + e.getMessage());
            out.put("patients", new ArrayList<>());
            // 异常分支也必须带 viewCounts：缺了它前端只能兜底，而兜底是拿 patients 算的，
            // patients 此刻为空 —— 于是四个档位显示成「全部（0）分子（0）…」，
            // 看着像统计坏了，实际是明细没生成出来。补上全 0 的计数，配合上方黄色告警，
            // 使用者才看得出是「明细失败」而不是「本期一个人都没有」。
            out.put("viewCounts", newViewCounts());
        }
        return out;
    }

    /**
     * 纳入状态：四档互斥，并集为全部扫到的对象。
     *
     * <p>分子与分母是两个各自独立的 CASE WHEN（见 SqlCompiler），分子并不必然落在分母里，
     * 所以四种组合都要显式处理，不能靠「进分子 ⇒ 进分母」这个经验假设。
     */
    private String inclusionState(Integer inNumerator, Integer inDenominator) {
        boolean n = inNumerator != null && inNumerator == 1;
        boolean d = inDenominator != null && inDenominator == 1;
        if (n && d) {
            return "achieved";
        }
        if (!n && d) {
            return "missed";
        }
        if (!n && !d) {
            return "excluded";
        }
        // 进了分子却没进分母：通常是 YAML 里分子分母条件写反了，会静默算错率值，
        // 比多显示几个人严重，因此单列一档而不是悄悄并进「已达标」
        return "abnormal";
    }

    /** 无明细的指标：给出「为什么没有」再返回空列表，好过让页面显示一张空表。 */
    private Map<String, Object> emptyPatients(Map<String, Object> out, String message) {
        out.put("message", message);
        out.put("patients", new ArrayList<>());
        out.put("viewCounts", newViewCounts());
        return out;
    }

    /**
     * 视角过滤。null / "all" 不过滤。
     *
     * <p>两个总览档与被点的那个数字一一对应，这是「点数字进来，行数就必须等于那个数字」
     * 的前提：点分母 → inDenominator（已达标 + 未达标）；点分子 → inNumerator
     * （已达标 + 口径异常）。后者不能省 —— 分子与分母是两个独立条件，
     * 进了分子却没进分母的人客观存在，若把点分子映射成 achieved，这些人就凭空消失，
     * 页面上「分子 113」和「明细 N 行」永远对不上。
     */
    private boolean matchesView(String state, String view) {
        if (view == null || view.trim().isEmpty() || "all".equals(view.trim())) {
            return true;
        }
        String v = view.trim();
        if ("inDenominator".equals(v)) {
            return "achieved".equals(state) || "missed".equals(state);
        }
        if ("inNumerator".equals(v)) {
            return "achieved".equals(state) || "abnormal".equals(state);
        }
        return v.equals(state);
    }

    /** 计数容器，键顺序即页面档位顺序（两个总览档在前，四个互斥档在后）。 */
    private Map<String, Object> newViewCounts() {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("all", 0);
        c.put("inNumerator", 0);
        c.put("inDenominator", 0);
        c.put("achieved", 0);
        c.put("missed", 0);
        c.put("excluded", 0);
        c.put("abnormal", 0);
        return c;
    }

    /** 把一行人计入各档。state 取 {@link #inclusionState} 的四个互斥取值之一。 */
    private void tally(Map<String, Object> counts, String state) {
        bump(counts, "all");
        if ("achieved".equals(state) || "missed".equals(state)) {
            bump(counts, "inDenominator");
        }
        if ("achieved".equals(state) || "abnormal".equals(state)) {
            bump(counts, "inNumerator");
        }
        bump(counts, state);
    }

    private void bump(Map<String, Object> counts, String key) {
        counts.put(key, ((Number) counts.get(key)).intValue() + 1);
    }

    /** 覆盖率报告：配置层对 127 条指标的可实现性承诺。 */
    public Map<String, Object> coverage() {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, int[]> stat = new LinkedHashMap<>();
        for (MetricDefinition m : dsl.sortedMetrics()) {
            FactDefinition f = dsl.factOf(m);
            String impl = m.getImplStatus() == null ? "IMPL" : m.getImplStatus().toUpperCase();
            String effective = impl;
            if ("IMPL".equals(impl)) {
                if (f == null) {
                    effective = "PENDING_SOURCE";
                } else if (f.getStatus() != null && !"ACTIVE".equalsIgnoreCase(f.getStatus())) {
                    effective = f.getStatus().toUpperCase();
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", m.getCode());
            row.put("name", m.getName());
            row.put("domain", m.getDomain());
            row.put("fact", m.getFact());
            row.put("factStatus", f == null ? null : f.getStatus());
            row.put("implStatus", effective);
            row.put("valueType", m.getValueType());
            row.put("expressionVersion", m.getVersion());
            row.put("remark", m.getRemark());
            list.add(row);

            String domain = m.getDomain() == null ? "其他" : m.getDomain();
            int[] c = stat.computeIfAbsent(domain, k -> new int[4]);
            c[0]++;
            if ("IMPL".equals(effective)) {
                c[1]++;
            } else if ("PLACEHOLDER".equals(effective)) {
                c[2]++;
            } else {
                c[3]++;
            }
        }
        List<Map<String, Object>> summary = new ArrayList<>();
        int t = 0, i = 0, p = 0, o = 0;
        for (Map.Entry<String, int[]> e : stat.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("domain", e.getKey());
            row.put("total", e.getValue()[0]);
            row.put("impl", e.getValue()[1]);
            row.put("placeholder", e.getValue()[2]);
            row.put("pending", e.getValue()[3]);
            summary.add(row);
            t += e.getValue()[0];
            i += e.getValue()[1];
            p += e.getValue()[2];
            o += e.getValue()[3];
        }
        Map<String, Object> total = new LinkedHashMap<>();
        total.put("domain", "合计");
        total.put("total", t);
        total.put("impl", i);
        total.put("placeholder", p);
        total.put("pending", o);
        summary.add(total);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("metrics", list);
        out.put("summary", summary);
        return out;
    }

    /** 事实层定义与编译后 SQL，供业务审口径（改 YAML 前先在页面上看 SQL）。 */
    public List<Map<String, Object>> facts(String periodStart) {
        PeriodRange range = PeriodRange.of("MONTH", periodStart);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, FactDefinition> e : dsl.getFacts().entrySet()) {
            FactDefinition f = e.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fact", e.getKey());
            row.put("domain", f.getDomain());
            row.put("status", f.getStatus());
            row.put("source", f.getSource());
            row.put("note", f.getNote());
            row.put("sourceTables", engine.currentFactSql(e.getKey(), range.getStart(), range.getEnd()));
            list.add(row);
        }
        return list;
    }

    public List<QualityCalcRun> recentRuns() {
        try {
            return runMapper.selectRecent();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 已预落库的患者明细（仅当开启 keepPatientDetail 时有值）。 */
    public List<Map<String, Object>> storedPatients(String code, LocalDateTime periodStart, String departCode) {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            for (com.zing.doctor.quality.entity.QualityMetricPatient p
                    : patientMapper.selectByMetric(code, periodStart, normalizeDepart(departCode))) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("inHospitalNo", p.getInHospitalNo());
                row.put("patientName", p.getPatientName());
                row.put("inNumerator", p.getInNumerator());
                row.put("inDenominator", p.getInDenominator());
                row.put("excludeReason", p.getExcludeReason());
                list.add(row);
            }
        } catch (Exception ignore) {
            // 未预落库时返回空
        }
        return list;
    }

    /**
     * 某指标的明细列定义（有序）。
     *
     * <p>导出复用它，而不是另写一套出列逻辑：页面看到的列与导出文件的列必须一致，
     * 否则「屏幕上第 3 列是科室、导出后跑到第 5 列」这类差异会被当成数据问题。
     */
    public List<Map<String, Object>> patientColumnsOf(String code) {
        return patientFieldDefs(dsl.getMetrics().get(code));
    }

    public QualityMetricResult findResult(String code, LocalDateTime periodStart, String departCode) {
        try {
            for (QualityMetricResult r : resultMapper.selectByPeriod(periodStart, normalizeDepart(departCode))) {
                if (code.equals(r.getMetricCode())) {
                    return r;
                }
            }
        } catch (Exception ignore) {
            // ignore
        }
        return null;
    }

    public boolean enabled() {
        return props.isEnabled();
    }

    private String normalizeDepart(String departCode) {
        return departCode == null || departCode.trim().isEmpty() ? "ALL" : departCode.trim();
    }

    /**
     * 患者明细的列定义（key / 中文表头 / 宽度 / 渲染类型），前端据此整表出列。
     *
     * <p>与数据一起返回而不是写死在前端：列是按指标配的，前端不可能预知某条指标配了哪些列。
     *
     * <p>配置里的 key 有三种情形，按下面顺序判定：
     * <ol>
     *   <li><b>没配</b> → 默认列（姓名 / 床号 / 住院号 / 诊断 / 入科时间 / 出科时间 / 入分子 / 入分母）。
     *       其中床号 / 诊断 / 入科 / 出科要看这条指标的事实层有没有投影出该列，
     *       没有就不出这一列 —— 摆一列永远空白的「床号」，比不摆更让人怀疑数据漏了；</li>
     *   <li><b>配了但一个默认列都没提到</b>（升级前只配过补充列的老数据）→ 默认列 + 补充列，
     *       保证升级前后表头不变，老配置不会因为这次改动突然少列；</li>
     *   <li><b>配了且提到默认列</b> → 完全按配置顺序出列：没提到就是不显示、顺序即显示顺序。
     *       「去掉一列」「把这列调到最前」都是这么表达的。</li>
     * </ol>
     */
    private List<Map<String, Object>> patientFieldDefs(MetricDefinition m) {
        List<PatientFieldDefinition> configured = m == null || m.getPatientFields() == null
                ? Collections.emptyList() : m.getPatientFields();
        boolean mentionsDefaults = false;
        for (PatientFieldDefinition pf : configured) {
            if (pf != null && PatientColumns.isReservedKey(pf.getKey())) {
                mentionsDefaults = true;
                break;
            }
        }

        // 扩展默认列出不出，取决于这条指标的事实层有没有投影出该列
        Set<String> available = availableColumns(dsl.factOf(m));
        List<Map<String, Object>> list = new ArrayList<>();
        if (!mentionsDefaults) {
            for (String key : PatientColumns.defaultOrder()) {
                if (PatientColumns.isExtendedKey(key) && !available.contains(key)) {
                    continue;
                }
                list.add(reservedFieldDef(key, PatientColumns.reserved().get(key)));
            }
        }
        for (PatientFieldDefinition pf : configured) {
            if (pf == null || pf.getKey() == null || pf.getKey().trim().isEmpty()) {
                continue;
            }
            String key = pf.getKey().trim();
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("key", key);
            f.put("label", pf.getLabel() == null || pf.getLabel().trim().isEmpty()
                    ? defaultLabel(key) : pf.getLabel().trim());
            f.put("width", pf.getWidth());
            f.put("type", fieldType(key));
            list.add(f);
        }
        return list;
    }

    /**
     * 事实层实际投影出的列名（小写）。
     *
     * <p>与明细 SQL 那侧（QualityEngine）用的是同一个判断：只有事实层真的有这一列，
     * 扩展默认列才可能显示得出来，否则就别占一列。
     */
    private Set<String> availableColumns(FactDefinition f) {
        Set<String> out = new HashSet<>();
        for (String c : analyzer.availableColumns(f)) {
            if (c != null) {
                out.add(c.trim().toLowerCase());
            }
        }
        return out;
    }

    /** 默认列的列定义：表头与宽度都取 PatientColumns 的同源常量。 */
    private Map<String, Object> reservedFieldDef(String key, String label) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("key", key);
        f.put("label", label);
        f.put("width", PatientColumns.defaultWidth(key));
        f.put("type", fieldType(key));
        return f;
    }

    /** 默认列的 key 本身就带表头；补充列没写表头时退回列名。 */
    private String defaultLabel(String key) {
        String label = PatientColumns.reserved().get(key);
        return label == null ? key : label;
    }

    /**
     * 渲染类型：入分子 / 入分母渲染成「是 / 否」标签，科室要按编码换成名称，其余按文本。
     *
     * <p>由后端给类型而不是前端猜：前端只拿到 key 与值，分不清「值 1 是布尔真还是数字 1」，
     * 而明细里恰好两者都有（in_numerator 是 0/1，某些补充列也可能是数字）。
     *
     * <p>时间列按列名判定。列定义是照配置生成的、看不见数据，所以这里只能靠命名习惯
     * （admit_time / discharge_date / _at / _dt）；兜底在 {@link #formatCellValue} ——
     * 值已在后端格式化成字符串，即使列名不含时间字样，显示也仍然正确。
     */
    private String fieldType(String key) {
        if ("inNumerator".equals(key) || "inDenominator".equals(key)) {
            return "bool";
        }
        if ("departCode".equals(key)) {
            return "depart";
        }
        String lower = key.toLowerCase();
        if (lower.contains("time") || lower.contains("date") || lower.endsWith("_at")
                || lower.endsWith("_dt") || lower.endsWith("dt")) {
            return "time";
        }
        return "text";
    }

    /**
     * 明细单元格里的时间值格式化。
     *
     * <p>为什么在后端做而不是留给前端：补充列的值由事实层 SQL 直接查出，类型随 JDBC 驱动
     * 而变（java.sql.Timestamp / LocalDateTime / java.util.Date 都可能出现），Jackson 对它们
     * 的序列化结果互不相同——数字时间戳、带 T 的 ISO 串乃至数组都有可能。前端拿到
     * 「1785500000000」无法判断它是时间戳还是编号，只能原样显示。这里统一转成可读字符串，
     * 页面与 xlsx 导出也因此天然一致（两者走的是同一份行数据）。
     */
    /**
     * CLOB 转文本时最多读取的字符数。
     *
     * <p>诊断内容这类列动辄上万字，全量塞进明细既拖慢接口又撑爆页面；
     * 而明细列的用途是「扫一眼确认是不是要找的那个人」，截断到这个长度足够。
     */
    private static final int LOB_TEXT_LIMIT = 4000;

    private Object formatCellValue(Object v) {
        if (v instanceof java.sql.Clob) {
            return readClob((java.sql.Clob) v);
        }
        if (v instanceof java.sql.Blob) {
            return describeBlob((java.sql.Blob) v);
        }
        if (v instanceof java.io.Reader) {
            return readReader((java.io.Reader) v);
        }
        // java.sql.Date / Time 的 toInstant() 会抛 UnsupportedOperationException，必须先于 java.util.Date 拦下
        if (v instanceof java.sql.Date || v instanceof java.sql.Time) {
            return v.toString();
        }
        LocalDateTime dt = null;
        if (v instanceof LocalDateTime) {
            dt = (LocalDateTime) v;
        } else if (v instanceof Timestamp) {
            dt = ((Timestamp) v).toLocalDateTime();
        } else if (v instanceof Date) {
            dt = LocalDateTime.ofInstant(((Date) v).toInstant(), ZoneId.systemDefault());
        } else if (v instanceof LocalDate) {
            return ((LocalDate) v).format(DATE_FMT);
        }
        if (dt == null) {
            return v;
        }
        // 零点整的按纯日期显示：入院日期这类列若一律带 00:00:00，既撑宽表格又读起来像「凌晨入院」
        if (dt.getHour() == 0 && dt.getMinute() == 0 && dt.getSecond() == 0 && dt.getNano() == 0) {
            return dt.format(DATE_FMT);
        }
        return dt.format(DATE_TIME_FMT);
    }

    /**
     * CLOB → 文本。
     *
     * <p><b>为什么必须在这里兜底，而不是指望配置方记得 CAST</b>：事实层可以直接引用
     * CLOB 列（如 {@code pi.diagnosis_content}），患者明细的补充列值会原样进响应，
     * 而驱动返回的是 {@code DmdbClob} 这类 JDBC 对象，Jackson 序列化到它就抛异常。
     *
     * <p>更麻烦的是报错形态：异常发生在<b>响应体写入阶段</b>，HTTP 状态已按 200 发出、
     * body 写到一半断裂 —— 既没有 {@code Result.fail()} 包装，也进不了前端错误拦截器，
     * 前端只见 {@code res.code} 为 undefined，控制台只剩一行
     * 「[api] /quality/metric undefined undefined」。接口是「点了没反应」，
     * 服务器日志里也没有对应的 ERROR，极易被当成前端 bug 或网络问题。
     *
     * <p>统一转成文本后，「忘了 CAST」退化成「值被截断」，而不是整个接口静默挂掉。
     */
    private String readClob(java.sql.Clob c) {
        try {
            long len = c.length();
            int n = (int) Math.min(len, (long) LOB_TEXT_LIMIT);
            return n <= 0 ? "" : c.getSubString(1, n);
        } catch (Exception e) {
            return "[大字段读取失败]";
        }
    }

    /** BLOB 不转文本：二进制转字符串只会得到乱码且可能极大，这里只给出体积说明。 */
    private String describeBlob(java.sql.Blob b) {
        try {
            return "[二进制数据 " + b.length() + " 字节]";
        } catch (Exception e) {
            return "[二进制数据]";
        }
    }

    /** 部分驱动把大字段以字符流返回，同样不能原样交给 JSON 序列化。 */
    private String readReader(java.io.Reader r) {
        try (java.io.Reader in = r) {
            char[] buf = new char[1024];
            StringBuilder sb = new StringBuilder();
            int n;
            while (sb.length() < LOB_TEXT_LIMIT && (n = in.read(buf)) > 0) {
                sb.append(buf, 0, Math.min(n, LOB_TEXT_LIMIT - sb.length()));
            }
            return sb.toString();
        } catch (Exception e) {
            return "[大字段读取失败]";
        }
    }

    /** 两值相对差异率，供双跑核对。 */
    public static BigDecimal diff(BigDecimal mine, BigDecimal other) {
        if (mine == null || other == null || other.signum() == 0) {
            return null;
        }
        return mine.subtract(other).abs()
                .divide(other.abs(), 6, RoundingMode.HALF_UP);
    }
}
