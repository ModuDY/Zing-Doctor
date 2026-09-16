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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * 明细的固定列：其余列一律视为指标自配的补充字段。
     *
     * <p>与 {@link PatientColumns} 共用同一份常量 —— 这两处曾经各写一份，
     * 只要有一边改了名字，补充列就会被当成默认列漏掉（或反过来）而不报错。
     */
    private static final Set<String> BASE_PATIENT_COLUMNS = PatientColumns.reservedColumns();

    private final QualityDslLoader dsl;
    private final SqlCompiler compiler;
    private final QualitySqlMapper sqlMapper;
    private final QualityProperties props;
    private final QualitySqlGuard sqlGuard;

    /** cacheKey(factName + 周期) → 物化表全限定名 */
    private final Map<String, String> factTableCache = new ConcurrentHashMap<>();

    /**
     * 当前缓存所对应的 DSL 配置版本。
     *
     * <p>配置热更新（页面保存 / 同步字典 / 手动重载）只替换内存里的定义，不会动这张缓存；
     * 若不比对版本，改完事实层定义后下钻仍会命中加列之前建好的旧表，新增列报
     * 「无效的列名」。跟 {@link QualityDslLoader#version()} 比对即可自动失效。
     */
    private volatile long factVersion = -1L;

    /**
     * cacheKey → 物化锁。
     *
     * <p>原先这里用的是 {@code synchronized (this)}：一次批次里几十张事实表全部串行创建，
     * 指标层配的并行线程（calc-threads）在物化阶段完全空转，批算耗时基本等于所有事实表建表耗时之和。
     * 改成按 cacheKey 加锁后，不同事实表可并行建，只有同一张表才互斥（保证只建一次）。
     *
     * <p>该 Map 只增不删，条目数 = 事实层数 × 计算过的周期数（每天至多几十个、对象本身极小）；
     * 重启即释放，不需要额外清理。
     */
    private final Map<String, Object> factLocks = new ConcurrentHashMap<>();

    public QualityEngine(QualityDslLoader dsl, SqlCompiler compiler,
                         QualitySqlMapper sqlMapper, QualityProperties props,
                         QualitySqlGuard sqlGuard) {
        this.dsl = dsl;
        this.compiler = compiler;
        this.sqlMapper = sqlMapper;
        this.props = props;
        this.sqlGuard = sqlGuard;
    }

    /** 清空事实层缓存（新一轮计算前调用，确保按当前周期重建）。 */
    public void resetFacts() {
        factTableCache.clear();
    }

    /**
     * 配置版本变了就清空物化缓存，使下次访问按新定义重建事实表。
     *
     * <p>放在这里统一判断，而不是要求每个 reload 调用点自己清缓存：后者只要漏一处，
     * 就会出现「保存成功、试跑也通过，偏偏下钻报无效的列名」这种极难定位的现象。
     */
    private void invalidateOnConfigChange() {
        long v = dsl.version();
        if (v == factVersion) {
            return;
        }
        synchronized (this) {
            if (v != factVersion) {
                if (factVersion >= 0 && !factTableCache.isEmpty()) {
                    log.info("[质控] 配置已变更（版本 {} → {}），失效 {} 张事实表缓存，下次按新定义重建",
                            factVersion, v, factTableCache.size());
                }
                factTableCache.clear();
                factVersion = v;
            }
        }
    }

    /**
     * 物化事实层，返回可直接用于指标 SQL 的 FROM 片段。
     *
     * @return 物理表全限定名（materializeFacts=true）或内联子查询（false）
     */
    public String materializeFact(String factName, LocalDateTime start, LocalDateTime end) {
        return materializeFact(factName, start, end, false);
    }

    /**
     * 物化事实层，返回可直接用于指标 SQL 的 FROM 片段。
     *
     * @param refresh true = 忽略缓存强制重建。用于单指标重算：只重建这一条指标依赖的事实表，
     *                代价远小于整体 {@code resetFacts()}（那会连带丢弃全部已建好的事实表）
     * @return 物理表全限定名（materializeFacts=true）或内联子查询（false）
     */
    public String materializeFact(String factName, LocalDateTime start, LocalDateTime end, boolean refresh) {
        return materializeFact(factName, start, end, refresh, null);
    }

    /**
     * 物化事实层；{@code deptValue} 非空时只物化该科室。
     *
     * <p>科室专属事实表用「周期 + 科室指纹」后缀隔离：否则一个科室的增量计算会把全院事实表
     * 覆盖成只剩该科室的数据，其他指标跟着一起错。
     *
     * @param deptValue 科室编码；null/空 = 全院
     */
    public String materializeFact(String factName, LocalDateTime start, LocalDateTime end, boolean refresh,
                                  String deptValue) {
        invalidateOnConfigChange();
        FactDefinition f = dsl.getFacts().get(factName);
        if (f == null) {
            throw new IllegalStateException("未定义的事实层: " + factName);
        }
        String key = cacheKey(factName, start, deptValue);
        if (!refresh) {
            String cached = factTableCache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        Object lock = factLocks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            if (!refresh) {
                String cached = factTableCache.get(key);
                if (cached != null) {
                    return cached;
                }
            }
            String sql = compiler.compileFact(f, dsl.getSourceConfig(), start, end, deptValue);
            guard(sql);
            String target;
            if (props.isMaterializeFacts()) {
                String physical = "\"" + props.getFactSchema() + "\".\""
                        + compiler.factPhysicalName(props.getFactTablePrefix(), factName)
                        + "_" + DAY.format(start) + deptSuffix(deptValue) + "\"";
                dropIfExists(physical);
                sqlMapper.execute("CREATE TABLE " + physical + " AS " + sql);
                log.debug("[质控] 事实层物化 {} → {}{}", factName, physical,
                        deptValue == null ? "" : "（仅科室 " + deptValue + "）");
                target = physical;
            } else {
                target = "(" + sql + ")";
            }
            factTableCache.put(key, target);
            return target;
        }
    }

    public List<MetricOutcome> computeMetric(MetricDefinition m, LocalDateTime start, LocalDateTime end) {
        return computeMetric(m, start, end, false);
    }

    /**
     * 计算一条指标（维度非空时返回多行）。
     *
     * @param refreshFact true = 先重建该指标依赖的那一张事实表再算。
     *                    单指标重算走这条路径：只花「建一张表」的代价就能拿到最新源数据，
     *                    不必像批次计算那样重建全部事实层
     * <p>除逐科室结果外，额外产出一行 {@code depart_code = 'ALL'}：
     * 由各科室分子/分母汇总后重算，率类因此是加权率而非「各科室率的平均」。
     */
    public List<MetricOutcome> computeMetric(MetricDefinition m, LocalDateTime start, LocalDateTime end,
                                             boolean refreshFact) {
        return computeMetric(m, start, end, refreshFact, null);
    }

    /**
     * 该指标本次实际生效的科室过滤值。
     *
     * <p><b>为什么必须公开</b>：调用方要靠它决定「清掉哪一段旧结果再写新的」。删除范围与写入
     * 范围一旦对不上，多写出来的行就会撞唯一键 {@code uk_quality_result}，整批一起失败
     * （现场已踩：科室批次里无科室维度的指标仍按全院口径写出 ALL 行，删除时却只删了该科室那一行）。
     * 所以判定只能有一处实现，计算与删除都从这里取。
     *
     * @return 生效的科室编码；null 表示该指标本批次按全院口径写行（ALL 及/或各科室）
     */
    public String deptFilterOf(MetricDefinition m, String deptValue) {
        if (m == null || !StringUtils.hasText(deptValue)) {
            return null;
        }
        String dept = deptValue.trim();
        if (isDerived(m)) {
            // 派生指标不跑 SQL、不物化事实层：分子分母都取被引用指标的「同一科室」行，
            // 因此只要按科室分维就能按科室出数
            return compiler.deptDimensioned(m) ? dept : null;
        }
        FactDefinition f = dsl.factOf(m);
        if (!"IMPL".equals(effectiveStatus(m, f))) {
            // 占位类指标（PENDING_SOURCE / PLACEHOLDER…）不跑 SQL，只写一行 ALL，与科室无关
            return null;
        }
        return compiler.supportsDeptFilter(m, f) ? dept : null;
    }

    /**
     * 是否是「引用其它指标结果」的派生指标（率 = 指标A ÷ 指标B）。
     *
     * <p>这类指标不绑一张事实层就够了：{@code ICU患者预计病死率 = 预计病死率之和 ÷ 同期患者总数}，
     * 前者在评分事实层、后者在患者流转事实层，一条 SQL 串不起两层的聚合。
     * 它们不编译 SQL，由编排层在被引用指标算完之后用 {@link #composeDerived} 组合。
     */
    public boolean isDerived(MetricDefinition m) {
        return m != null && m.isDerived();
    }

    /**
     * 该指标是否与科室完全无关：不跑 SQL，本批次只会写一行 {@code depart_code='ALL'}。
     *
     * <p>科室批次可整条跳过这类指标（既不删也不写）—— 值不随科室变化，重算只是把
     * 同一行 ALL 删了再原样写回去。
     *
     * <p><b>它不等于 {@link #deptFilterOf} 返回 null</b>：后者还包含「IMPL 但事实层
     * 没有科室列」的指标，那些是要真跑 SQL 的，会产出 ALL 及各科室多行。跳过它们会让
     * 其它科室的旧行被删后没人补（科室批次先删该科室再写），所以不在可跳过范围内。
     *
     * @return true = 占位类指标（PENDING_SOURCE / PLACEHOLDER / MANUAL …）
     */
    public boolean isDeptIrrelevant(MetricDefinition m) {
        if (m == null || isDerived(m)) {
            // 派生指标必须算：它按科室取被引用指标的行，「值不随科室变化」对它不成立
            return false;
        }
        return !"IMPL".equals(effectiveStatus(m, dsl.factOf(m)));
    }

    /**
     * 计算一条指标；{@code deptValue} 非空时只算该科室。
     *
     * <p>能否按科室算由 {@link SqlCompiler#supportsDeptFilter} 判定，判不过就退回全院计算：
     * 没有科室维度的指标（如「全院收治人数」）若拿科室数据填，会把科室值冒充成全院值，
     * 那是比慢几十秒严重得多的错误。
     */
    public List<MetricOutcome> computeMetric(MetricDefinition m, LocalDateTime start, LocalDateTime end,
                                             boolean refreshFact, String deptValue) {
        List<MetricOutcome> outcomes = new ArrayList<>();

        if (isDerived(m)) {
            // 引用型指标不编译 SQL：调用方要先算被引用指标，再用 composeDerived 组合。
            // 这里抛异常而不是返回空行 —— 返回空会让页面静默缺行，比报错难查得多
            throw new IllegalStateException("引用型指标不参与 SQL 计算: " + m.getCode()
                    + "（分子 " + m.getNumeratorMetric() + " / 分母 " + m.getDenominatorMetric() + "）");
        }

        FactDefinition f = dsl.factOf(m);
        String effective = effectiveStatus(m, f);

        // 本指标本次生效的科室值，所有「提前返回」的分支都必须带上它。
        //
        // 原因：这些分支产出的 NO_DATA / ERROR 行若留着 null 科室，落到 toResult 会被折成
        // 'ALL'，而调用方的删除范围是按它自己那个科室删的（recalcOne 里 filter 非空时走
        // deleteByMetricPeriodDept）—— 删的是科室行、写的却是 ALL 行，库里那条旧 ALL 行没被
        // 删掉，插入即撞 uk_quality_result。
        // 现场表现极具迷惑性：筛着科室点「计算」，报「结果行全部撞唯一键」，日志里却写着
        // depart=ALL —— 看着像科室判定错了，实际是「这个科室没数据」被记成了「全院」。
        String filter = deptFilterOf(m, deptValue);
        String deptLabel = filter == null ? "ALL" : filter;

        if (!"IMPL".equals(effective)) {
            MetricOutcome o = baseOutcome(m);
            o.setDepartCode(deptLabel);
            o.setCalcStatus(effective);
            o.setFactName(m.getFact());
            outcomes.add(o);
            return outcomes;
        }

        String factTable;
        try {
            factTable = materializeFact(m.getFact(), start, end, refreshFact, filter);
        } catch (Exception e) {
            log.error("[质控] 事实层物化失败: metric={}, fact={}", m.getCode(), m.getFact(), e);
            MetricOutcome o = baseOutcome(m);
            o.setDepartCode(deptLabel);
            o.setCalcStatus("ERROR");
            o.setErrorMsg("事实层物化失败: " + e.getMessage());
            outcomes.add(o);
            return outcomes;
        }

        String sql = compiler.compileMetric(m, factTable, f, filter);
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
            o.setDepartCode(deptLabel);
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
            o.setDepartCode(deptLabel);
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

        // 按科室分组时，补一行全院汇总（率类为加权率，而非各科室率的平均）。
        // 科室模式下刻意不补：那会是用单科室数据算出来的「全院」行，把科室值冒充成全院值
        //
        // 科室为空的记录已被上面的 str(row,"ALL") 折成一行 ALL，此时汇总必须写回那一行、
        // 而不是再插一条：同一 (指标, 周期, 科室) 出现两行时，达梦不是只丢冲突的那一条，
        // 而是让整批 insert 一起失败（现场：127 条结果一条都没写进去）。
        if (filter == null && hasDimRows && hasNum) {
            MetricOutcome all = null;
            for (MetricOutcome o : outcomes) {
                if ("ALL".equals(o.getDepartCode())) {
                    all = o;
                    break;
                }
            }
            if (all != null) {
                // 事实层里有科室为空的记录：它的分子分母已在循环中计入汇总，
                // 用汇总值覆盖这一行，既不重复也不丢这部分患者
                outcomes.remove(all);
            } else {
                all = baseOutcome(m);
                all.setFactName(m.getFact());
                all.setDepartCode("ALL");
                all.setSqlText(sql);
                all.setSqlHash(hash);
                all.setOperators(operators);
                all.setSourceTables(sourceTables);
                all.setScannedRows(scanned);
                all.setDurationMs(cost);
            }
            all.setNumerator(sumNum);
            all.setDenominator(sumDen);
            all.setMetricValue(computeValue(m, sumNum, sumDen));
            all.setCalcStatus("OK");
            all.setNumRows(sumNum.intValue());
            all.setDenRows(sumDen.intValue());
            // 一律追加到末尾：与其它指标「先各科室、再全院汇总」的输出顺序一致
            outcomes.add(all);
        }
        return outcomes;
    }

    /**
     * 组合派生指标：从被引用指标的结果行取分子分母，按科室逐行相除。
     *
     * <p>取值原则是「同科室取同科室」：全院模式下被引用指标自己也产出各科室行与 ALL 行，
     * 派生指标的 ALL 行就取它们的 ALL 行 —— 那是各科室汇总后重算的加权值，
     * 不是把各科室率再平均一次（那会把「人多的科室」和「人少的科室」等权重看待）。
     *
     * @param base 被引用指标本批次算出的全部结果行
     */
    public List<MetricOutcome> composeDerived(MetricDefinition m, List<MetricOutcome> base, String deptValue) {
        String filter = deptFilterOf(m, deptValue);
        Map<String, MetricOutcome> numByDept = indexByDept(base, m.getNumeratorMetric());
        Map<String, MetricOutcome> denByDept = indexByDept(base, m.getDenominatorMetric());

        String missing = null;
        if (StringUtils.hasText(m.getNumeratorMetric()) && numByDept.isEmpty()) {
            missing = m.getNumeratorMetric();
        } else if (StringUtils.hasText(m.getDenominatorMetric()) && denByDept.isEmpty()) {
            missing = m.getDenominatorMetric();
        }
        if (missing != null) {
            return Collections.singletonList(errorOutcome(m, filter,
                    "被引用指标没有结果行：" + missing + "。可能已停用、状态为占位类，或引用了另一条派生指标"));
        }

        List<String> depts = new ArrayList<>();
        if (filter != null) {
            depts.add(filter);
        } else {
            depts.addAll(numByDept.keySet());
            for (String d : denByDept.keySet()) {
                if (!depts.contains(d)) {
                    depts.add(d);
                }
            }
            // ALL 行排在最后：与其它指标「先各科室、再全院汇总」的输出顺序一致
            depts.sort((a, b) -> "ALL".equals(a) ? 1 : ("ALL".equals(b) ? -1 : 0));
        }

        List<MetricOutcome> out = new ArrayList<>(depts.size());
        for (String d : depts) {
            MetricOutcome num = numByDept.get(d);
            MetricOutcome den = denByDept.get(d);
            MetricOutcome o = baseOutcome(m);
            o.setDepartCode(d);
            // 取被引用指标「显示出来的那个值」，而不是它内部的 num 字段：
            // COUNT / SUM 类两者相同，但引用一条 RATE 指标时，用户要的是那个率（已乘系数），
            // 取 num 会变成「引用它的分子」—— 那是另一回事，且看不出错
            o.setNumerator(num == null ? null : num.getMetricValue());
            o.setDenominator(den == null ? null : den.getMetricValue());
            o.setMetricValue(computeValue(m, o.getNumerator(), o.getDenominator()));
            // 分子缺失（被引用指标没算出数）与分母为 0 都落到 NO_DATA：
            // 派生指标没有自己的 SQL，硬造一个 0 会让「没数据」看起来像「真的是 0」
            o.setCalcStatus(o.getMetricValue() == null ? "NO_DATA" : "OK");
            o.setNumRows(o.getNumerator() == null ? null : o.getNumerator().intValue());
            o.setDenRows(o.getDenominator() == null ? null : o.getDenominator().intValue());
            o.setOperators("metricRef");
            o.setSqlText("引用指标 " + text(m.getNumeratorMetric(), "1") + " ÷ "
                    + text(m.getDenominatorMetric(), "同期对象数"));
            out.add(o);
        }
        return out;
    }

    /** 按科室给某条指标的结果行建索引；同科室只留第一条（一条指标一个科室只会有一行）。 */
    private Map<String, MetricOutcome> indexByDept(List<MetricOutcome> base, String code) {
        Map<String, MetricOutcome> map = new LinkedHashMap<>();
        if (!StringUtils.hasText(code) || base == null) {
            return map;
        }
        for (MetricOutcome o : base) {
            if (code.equals(o.getMetricCode())) {
                map.putIfAbsent(o.getDepartCode() == null ? "ALL" : o.getDepartCode(), o);
            }
        }
        return map;
    }

    private MetricOutcome errorOutcome(MetricDefinition m, String dept, String msg) {
        MetricOutcome o = baseOutcome(m);
        o.setDepartCode(dept == null ? "ALL" : dept);
        o.setCalcStatus("ERROR");
        o.setErrorMsg(msg);
        return o;
    }

    private String text(String v, String fallback) {
        return StringUtils.hasText(v) ? v : fallback;
    }

    /**
     * 按需生成某指标的患者级命中明细（血缘第 4 层）。
     *
     * <p>页面下钻时调用：复用同一事实层，成本是一次事实表扫描。
     */
    public List<MetricOutcome.PatientHit> loadPatients(MetricDefinition m,
                                                       LocalDateTime start, LocalDateTime end) {
        return loadPatients(m, start, end, null);
    }

    /**
     * 患者级明细；{@code deptValue} 非空时只取该科室的行。
     *
     * <p>明细同样按科室过滤：看板筛了科室、点开却列全院的人，数字和名单对不上，
     * 比少几行更让人不敢相信。
     */
    public List<MetricOutcome.PatientHit> loadPatients(MetricDefinition m,
                                                       LocalDateTime start, LocalDateTime end,
                                                       String deptValue) {
        return loadPatients(m, start, end, deptValue, false);
    }

    /**
     * 加载对象级命中明细。
     *
     * @param includeExcluded true = 连同「既没进分子也没进分母」的人一起返回，
     *        供排查「为什么这个人不算分母」；默认 false 只返回命中过的人。
     */
    public List<MetricOutcome.PatientHit> loadPatients(MetricDefinition m,
                                                       LocalDateTime start, LocalDateTime end,
                                                       String deptValue, boolean includeExcluded) {
        FactDefinition f = dsl.factOf(m);
        if (isDerived(m)) {
            // 引用型指标没有自己的明细：它的分子分母来自两条不同的事实层，
            // 按本指标声明的事实层列出来只会是「一批与本指标无关的人」
            return new ArrayList<>();
        }
        if (f == null || !"IMPL".equals(effectiveStatus(m, f))) {
            return new ArrayList<>();
        }
        // 与计算同源判定：事实层没有科室列时不能按科室物化（compileFact 会抛异常），
        // 判不过就退回全院明细，宁可多列几行，也不能让下钻整页报错
        String filter = deptFilterOf(m, deptValue);
        String factTable = materializeFact(m.getFact(), start, end, false, filter);
        String sql = compiler.compilePatients(m, factTable, f, filter, includeExcluded);
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
            // 默认列之外的取值就是指标自配的补充字段（SQL 已按配置 select）。
            // key 统一折成小写再带出去：达梦驱动返回的列名大小写不固定，
            // 而配置里写的是小写列名，原样透传会出现「页面配了列却整列为空」。
            for (Map.Entry<String, Object> e : row.entrySet()) {
                String k = e.getKey() == null ? "" : e.getKey().toLowerCase();
                if (!BASE_PATIENT_COLUMNS.contains(k)) {
                    h.getExtras().put(k, e.getValue());
                }
            }
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
                // 用 contains 而不是 endsWith：科室专属事实表在周期后缀后还有科室指纹，
                // 按后缀判尾会把本轮刚建的科室表当成历史表删掉，缓存随即指向一张不存在的表
                if (name == null || name.contains("_" + keepSuffix)) {
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

    /** 科室专属事实表的缓存键：与全院表分开，避免互相顶掉。 */
    private String cacheKey(String factName, LocalDateTime start, String deptValue) {
        return StringUtils.hasText(deptValue) ? cacheKey(factName, start) + "_d" + deptSuffix(deptValue)
                : cacheKey(factName, start);
    }

    /**
     * 科室指纹后缀（物理表名与缓存键共用）。
     *
     * <p>用哈希而不是科室编码原文：达梦对标识符长度有限制，科室编码一长表名就超限；
     * 且编码里可能带 {@code -} 等需要引号的字符。取 4 位足够区分院内科室数量级。
     */
    private String deptSuffix(String deptValue) {
        if (!StringUtils.hasText(deptValue)) {
            return "";
        }
        return "_d" + DigestUtil.md5Hex(deptValue.trim()).substring(0, 4);
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

    /**
     * 由分子/分母换算出指标值。
     *
     * <p>刻意做成 {@code public static}：配置页的「试算预览」必须与正式计算同源，
     * 否则页面显示的值和跑批落库的值可能不一致 —— 那种偏差极难被发现，却会直接误导配置者。
     */
    public static BigDecimal computeValue(MetricDefinition m, BigDecimal num, BigDecimal den) {
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

    /**
     * 运行时防线：拦截非法语句。
     *
     * <p>委托给 {@link QualitySqlGuard} 而非在此就地判断 —— 规则只应有一处定义，
     * 否则「保存前校验」与「执行时拦截」迟早不一致。且比原来的三符号检查更严：
     * 额外拦下 DML/DDL 与集合运算关键字，堵住 {@code 1=1) UNION SELECT ...} 这类绕过。
     *
     * <p>这一层不能省：配置表是可直接改库的，绕过页面写入的表达式同样会被执行。
     */
    private void guard(String sql) {
        sqlGuard.guardStatement(sql);
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
