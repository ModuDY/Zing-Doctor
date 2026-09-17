package com.zing.doctor.quality.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.zing.doctor.quality.dsl.FactDefinition;
import com.zing.doctor.quality.dsl.MetricDefinition;
import com.zing.doctor.quality.engine.PatientColumns;
import com.zing.doctor.quality.engine.QualityDslLoader;
import com.zing.doctor.quality.engine.QualityExpressionAnalyzer;
import com.zing.doctor.quality.engine.SqlCompiler;
import com.zing.doctor.quality.entity.QualityDefHistory;
import com.zing.doctor.quality.entity.QualityFactDef;
import com.zing.doctor.quality.entity.QualityMetricDef;
import com.zing.doctor.quality.repository.QualityConfigRepository;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 质控配置服务：查询、校验、保存、生效、回滚。
 *
 * <p>职责边界：本类只做「业务编排」—— 校验交给 {@link QualityConfigValidator}，
 * 映射交给 {@link QualityConfigRepository}，加载交给 {@link QualityDslLoader}。
 *
 * <p>三条贯穿全类的规则：
 * <ol>
 *   <li><b>保存 = 校验通过 + 落库 + 留快照 + 热生效</b>。四步缺一，就会出现
 *       「页面显示已保存，但看板数字没变」这类最难排查的问题。</li>
 *   <li><b>口径变化才递增版本</b>。只改名称、备注不应产生新版本，否则历史值无法对齐。
 *       判断依据是真正影响计算结果的字段（聚合方式、值类型、放大系数、三个表达式、维度）。</li>
 *   <li><b>真源不是 db 时拒绝保存</b>。否则写进 DB 的配置不会参与计算 ——
 *       页面显示保存成功、看板却毫无变化，是最典型的静默失败。</li>
 * </ol>
 */
@Service
@DS("doctor")
public class QualityConfigService {

    private static final Logger log = LoggerFactory.getLogger(QualityConfigService.class);

    private static final String TYPE_METRIC = "METRIC";
    private static final String TYPE_FACT = "FACT";

    /** 单次批量导入上限。防的不是恶意，是「把整个生产库快照拖进来」这种误操作。 */
    private static final int MAX_IMPORT_SIZE = 500;

    private final QualityConfigRepository repo;
    private final QualityConfigValidator validator;
    private final QualityDslLoader dsl;
    private final QualityIndexSyncService indexSyncService;
    private final QualityExpressionAnalyzer analyzer;
    private final SqlCompiler compiler;

    public QualityConfigService(QualityConfigRepository repo, QualityConfigValidator validator,
                                QualityDslLoader dsl, QualityIndexSyncService indexSyncService,
                                QualityExpressionAnalyzer analyzer, SqlCompiler compiler) {
        this.repo = repo;
        this.validator = validator;
        this.dsl = dsl;
        this.indexSyncService = indexSyncService;
        this.analyzer = analyzer;
        this.compiler = compiler;
    }

    // ==================================================================
    // 查询
    // ==================================================================

    /**
     * 指标列表。
     *
     * <p>刻意清空三个表达式字段：单条口径可达数 KB，127 条一次性返回会让列表接口变得很重，
     * 而列表页根本用不到它们。详情在 {@link #metricDetail(String)} 里单独取。
     */
    public List<QualityMetricDef> listMetrics(String domain, String keyword) {
        List<QualityMetricDef> rows = repo.listMetricRows(domain, keyword);
        for (QualityMetricDef row : rows) {
            row.setExprWhere(null);
            row.setExprNumerator(null);
            row.setExprDenominatorWhere(null);
        }
        return rows;
    }

    /** 指标详情（编辑页用）：返回可直接编辑的 DSL 对象。 */
    public MetricDefinition metricDetail(String code) {
        QualityMetricDef row = repo.metricRow(code);
        return row == null ? null : repo.toMetric(row);
    }

    public List<FactDefinition> listFacts() {
        return repo.loadFacts();
    }

    /** 事实层详情。 */
    public FactDefinition factDetail(String factName) {
        QualityFactDef row = repo.factRow(factName);
        return row == null ? null : repo.toFact(row);
    }

    /**
     * 事实层可引用的列清单 —— 简单模式下字段下拉的数据源。
     *
     * <p>这个接口是「简单模式」真正的地基：让使用者从下拉里挑列，
     * 而不是手打列名再去撞 L3 校验的报错。
     */
    public List<String> fieldsOf(String factName) {
        FactDefinition f = factDetail(factName);
        if (f == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(analyzer.availableColumns(f));
    }

    /**
     * 患者明细的列选项 = 明细默认列 + 事实层补充列。
     *
     * <p>特意不复用 {@link #fieldsOf}：那是「编译指标表达式可引用的列」，
     * 含 patient_id / depart_code 这类与默认列同名的名字，选进明细就会撞出重复列名。
     * 默认列排在最前，因为使用者的操作多数是「默认列要不要留、排第几」。
     */
    public List<Map<String, Object>> patientFieldsOf(String factName) {
        FactDefinition f = factDetail(factName);
        // 扩展默认列（床号 / 诊断 / 入科 / 出科）只有事实层真有这一列才给选：
        // 选了一个事实层没有的列，明细上就是一列空白，使用者会以为是数据漏了。
        Set<String> available = new HashSet<>();
        for (String c : analyzer.availableColumns(f)) {
            if (c != null) {
                available.add(c.trim().toLowerCase());
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        // 按默认显示顺序给出：使用者的操作多数是「默认列要不要留、排第几」
        for (String key : PatientColumns.defaultOrder()) {
            if (PatientColumns.isExtendedKey(key) && !available.contains(key)) {
                continue;
            }
            out.add(fieldOption(key, PatientColumns.reserved().get(key), true, true));
        }
        // 其余保留 key（患者ID / 科室）：不在默认顺序里，但仍可手工加回来。
        // isDefault 必须为 false —— 前端拿它当「留空即默认」的那份清单，
        // 若把这两列也算进去，使用者打开配置什么都没改就保存，明细会凭空多出两列。
        for (Map.Entry<String, String> e : PatientColumns.base().entrySet()) {
            if (PatientColumns.defaultOrder().contains(e.getKey())) {
                continue;
            }
            out.add(fieldOption(e.getKey(), e.getValue(), true, false));
        }
        if (f != null) {
            for (String c : analyzer.patientColumns(f)) {
                out.add(fieldOption(c, c, false, false));
            }
        }
        return out;
    }

    /**
     * @param isDefault 是否在「留空即默认」的那份清单里。
     *        reserved 表示「这是默认列（表头可留空）」，isDefault 表示「没配时它会出现」，
     *        两者不是一回事：患者ID / 科室 是默认列，却不在默认显示顺序里。
     */
    private Map<String, Object> fieldOption(String key, String label, boolean reserved, boolean isDefault) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("key", key);
        o.put("label", label);
        o.put("reserved", reserved);
        o.put("isDefault", isDefault);
        return o;
    }

    /**
     * 影响面：哪些指标引用了这个事实层。
     *
     * <p>改事实层前必须先看这个列表：一个域几十条指标共享同一事实层，
     * 改错一个过滤条件的连带影响远超改一条指标。
     */
    public List<MetricDefinition> metricsUsingFact(String factName) {
        List<MetricDefinition> hit = new ArrayList<>();
        for (MetricDefinition m : dsl.getMetrics().values()) {
            if (Objects.equals(m.getFact(), factName)) {
                hit.add(m);
            }
        }
        return hit;
    }

    public List<QualityDefHistory> history(String defType, String defKey, int limit) {
        return repo.listHistory(defType, defKey, limit);
    }

    /** 当前生效的事实层 SQL（配置页「看一眼它到底跑的是什么」）。 */
    public String currentFactSql(String factName) {
        FactDefinition f = dsl.getFacts().get(factName);
        if (f == null) {
            return null;
        }
        try {
            return compiler.compileFact(f, dsl.getSourceConfig(), null, null);
        } catch (RuntimeException e) {
            log.debug("[质控] 事实层 SQL 编译失败: fact={}, {}", factName, e.getMessage());
            return null;
        }
    }

    // ==================================================================
    // 保存
    // ==================================================================

    @Transactional(rollbackFor = Exception.class)
    public SaveResult saveMetric(MetricDefinition m, boolean trial, String operator) {
        String blocked = writableGuard();
        if (blocked != null) {
            return SaveResult.rejected(blocked);
        }
        QualityConfigValidator.ValidationResult v = validator.validateMetric(m, trial, null, null);
        if (!v.isOk()) {
            return SaveResult.validationFailed(v);
        }

        LocalDateTime now = LocalDateTime.now();
        QualityMetricDef existing = repo.metricRow(m.getCode());
        boolean isNew = existing == null;

        // 引用字段尚未在配置页开放编辑：页面保存（哪怕只改一句备注）不会回传它们，
        // 直接 fillRow 会把已配好的引用清空，指标随即算不出来 —— 而且要等下一批跑完才发现。
        // 因此这里补回旧值；确实要清空引用的场景请走 SQL 或配置页的对应字段（后续开放）。
        if (existing != null) {
            if (!StringUtils.hasText(m.getNumeratorMetric())) {
                m.setNumeratorMetric(existing.getNumeratorMetric());
            }
            if (!StringUtils.hasText(m.getDenominatorMetric())) {
                m.setDenominatorMetric(existing.getDenominatorMetric());
            }
        }

        QualityMetricDef row = isNew ? new QualityMetricDef() : existing;
        repo.fillRow(row, m);
        if (row.getSortNo() == null) {
            row.setSortNo(existing != null && existing.getSortNo() != null ? existing.getSortNo() : 9999);
        }
        row.setExprVersion(nextVersion(existing, m));
        row.setStatus(1);
        row.setOperator(operator);
        row.setUpdateTime(now);

        if (isNew) {
            row.setCreateTime(now);
            repo.insertMetric(row);
        } else {
            repo.updateMetric(row);
        }
        repo.saveHistory(TYPE_METRIC, m.getCode(), row.getExprVersion(),
                isNew ? "CREATE" : "UPDATE", repo.toJson(m), operator);

        int synced = reloadAndSync();
        log.info("[质控] 指标已保存: code={}, version={}, operator={}, 字典同步 {} 条",
                m.getCode(), row.getExprVersion(), operator, synced);
        return SaveResult.saved(v, row.getExprVersion(), synced,
                isNew ? "已新增并生效" : "已保存并生效");
    }

    @Transactional(rollbackFor = Exception.class)
    public SaveResult saveFact(FactDefinition f, boolean trial, String operator) {
        String blocked = writableGuard();
        if (blocked != null) {
            return SaveResult.rejected(blocked);
        }
        QualityConfigValidator.ValidationResult v = validator.validateFact(f, trial, null, null);
        if (!v.isOk()) {
            return SaveResult.validationFailed(v);
        }

        LocalDateTime now = LocalDateTime.now();
        QualityFactDef existing = repo.factRow(f.getFact());
        boolean isNew = existing == null;

        QualityFactDef row = isNew ? new QualityFactDef() : existing;
        repo.fillRow(row, f);
        row.setOperator(operator);
        row.setUpdateTime(now);
        if (isNew) {
            row.setCreateTime(now);
            repo.insertFact(row);
        } else {
            repo.updateFact(row);
        }
        repo.saveHistory(TYPE_FACT, f.getFact(), null,
                isNew ? "CREATE" : "UPDATE", repo.toJson(f), operator);

        int synced = reloadAndSync();
        log.info("[质控] 事实层已保存: fact={}, operator={}, 受影响指标 {} 条",
                f.getFact(), operator, synced);
        return SaveResult.saved(v, null, synced,
                isNew ? "已新增事实层并生效" : "已保存事实层并生效（关联指标同步重载）");
    }

    /**
     * 停用指标。
     *
     * <p>不物理删除：历史结果表按 {@code metric_code} 关联，删掉定义会让已有结果变成孤儿数据，
     * 历史月份再也解释不清。停用后不再参与计算，但历史值仍可查阅。
     */
    @Transactional(rollbackFor = Exception.class)
    public SaveResult disableMetric(String code, String operator) {
        String blocked = writableGuard();
        if (blocked != null) {
            return SaveResult.rejected(blocked);
        }
        QualityMetricDef existing = repo.metricRow(code);
        if (existing == null) {
            return SaveResult.rejected("指标不存在：" + code);
        }
        repo.disableMetric(code, operator);
        repo.saveHistory(TYPE_METRIC, code, existing.getExprVersion(), "DISABLE",
                repo.toJson(repo.toMetric(existing)), operator);
        int synced = reloadAndSync();
        log.info("[质控] 指标已停用: code={}, operator={}", code, operator);
        return SaveResult.saved(null, existing.getExprVersion(), synced, "已停用（历史结果保留）");
    }

    /**
     * 回滚到某一版口径。
     *
     * <p>回滚不是「撤销」，而是「把旧口径重新发布一次」：会再写一条 ROLLBACK 历史，
     * 因此审计链完整保留，任何一次变更都查得到。
     */
    @Transactional(rollbackFor = Exception.class)
    public SaveResult rollback(Long historyId, String operator) {
        String blocked = writableGuard();
        if (blocked != null) {
            return SaveResult.rejected(blocked);
        }
        QualityDefHistory h = repo.historyById(historyId);
        if (h == null) {
            return SaveResult.rejected("历史记录不存在：" + historyId);
        }
        if (!TYPE_METRIC.equals(h.getDefType())) {
            return SaveResult.rejected("目前仅支持回滚指标口径（该记录类型为 " + h.getDefType() + "）");
        }
        MetricDefinition snapshot = repo.metricFromJson(h.getSnapshot());
        QualityMetricDef existing = repo.metricRow(snapshot.getCode());
        if (existing == null) {
            return SaveResult.rejected("指标已被删除，无法回滚：" + snapshot.getCode());
        }

        repo.fillRow(existing, snapshot);
        existing.setExprVersion(snapshot.getVersion() == null ? 1 : snapshot.getVersion());
        existing.setStatus(1);
        existing.setOperator(operator);
        existing.setUpdateTime(LocalDateTime.now());
        repo.updateMetric(existing);

        repo.saveHistory(TYPE_METRIC, snapshot.getCode(), existing.getExprVersion(),
                "ROLLBACK", h.getSnapshot(), operator);
        int synced = reloadAndSync();
        log.info("[质控] 指标已回滚: code={}, 到版本={}, operator={}",
                snapshot.getCode(), existing.getExprVersion(), operator);
        return SaveResult.saved(null, existing.getExprVersion(), synced,
                "已回滚到版本 v" + existing.getExprVersion());
    }

    // ==================================================================
    // 批量导入 / 导出
    // ==================================================================

    /**
     * 导出当前<b>生效中</b>的全部指标口径（含表达式正文）。
     *
     * <p>数据取自内存中的生效配置（{@link QualityDslLoader#sortedMetrics()}），因此
     * yaml 真源下同样可用 —— 这条路径让「先在测试环境用 yaml 调好口径，再导入生产 DB」
     * 成为可能，不必手工敲 SQL。
     *
     * <p>与列表接口 {@link #listMetrics} 的关键差别是<b>不清空表达式</b>：导出必须完整，
     * 否则回灌时会把口径写空。
     */
    public List<MetricDefinition> exportMetrics() {
        return dsl.sortedMetrics();
    }

    /**
     * 批量导入指标口径。
     *
     * <p>三条刻意的设计：
     * <ol>
     *   <li><b>逐条校验、逐条落库，不用整体事务</b>。上百条里有 3 条写错是常态，
     *       整体回滚会让使用者只能「全部重来」；这里返回逐条结论，好的进去、坏的带着原因留下。</li>
     *   <li><b>复用单条保存的校验链</b>，不另写一套宽松校验 —— 否则导入就成了绕过护栏的后门。</li>
     *   <li><b>全部处理完只热生效一次</b>。逐条 reload 会重放 127 条口径，
     *       导入 100 条就是 100 次全量重载。</li>
     * </ol>
     *
     * @param mode skip=同编号跳过（默认）/ overwrite=同编号覆盖
     */
    public ImportResult importMetrics(List<MetricDefinition> metrics, String mode,
                                      boolean trial, String operator) {
        ImportResult result = new ImportResult();
        String blocked = writableGuard();
        if (blocked != null) {
            result.setRejected(true);
            result.setMessage(blocked);
            return result;
        }
        if (metrics == null || metrics.isEmpty()) {
            result.setMessage("导入内容为空：未解析到任何指标定义，请确认文件是导出接口产出的 JSON。");
            return result;
        }
        if (metrics.size() > MAX_IMPORT_SIZE) {
            result.setMessage("单次最多导入 " + MAX_IMPORT_SIZE + " 条指标，当前 " + metrics.size()
                    + " 条，请拆分后分批导入。");
            return result;
        }

        boolean overwrite = "overwrite".equalsIgnoreCase(mode == null ? "" : mode.trim());
        result.setTotal(metrics.size());
        LocalDateTime now = LocalDateTime.now();
        Set<String> seen = new LinkedHashSet<>();
        boolean anyWritten = false;

        for (int i = 0; i < metrics.size(); i++) {
            MetricDefinition m = metrics.get(i);
            String code = m == null ? null : m.getCode();
            if (!StringUtils.hasText(code)) {
                result.item(null, "FAILED", "第 " + (i + 1) + " 条缺少指标编号");
                continue;
            }
            if (!seen.add(code)) {
                result.item(code, "SKIPPED", "同一批次内编号重复，仅处理首次出现");
                continue;
            }

            QualityConfigValidator.ValidationResult v = validator.validateMetric(m, trial, null, null);
            if (!v.isOk()) {
                result.item(code, "FAILED", String.join("；", v.getErrors()));
                continue;
            }

            try {
                QualityMetricDef existing = repo.metricRow(code);
                if (existing != null && !overwrite) {
                    result.item(code, "SKIPPED", "已存在同编号指标（当前策略为 skip，如需覆盖请选 overwrite）");
                    continue;
                }
                boolean isNew = existing == null;
                QualityMetricDef row = isNew ? new QualityMetricDef() : existing;
                repo.fillRow(row, m);
                if (row.getSortNo() == null) {
                    row.setSortNo(isNew ? 9999 : (existing.getSortNo() == null ? 9999 : existing.getSortNo()));
                }
                row.setExprVersion(nextVersion(existing, m));
                row.setStatus(1);
                row.setOperator(operator);
                row.setUpdateTime(now);
                if (isNew) {
                    row.setCreateTime(now);
                    repo.insertMetric(row);
                } else {
                    repo.updateMetric(row);
                }
                repo.saveHistory(TYPE_METRIC, code, row.getExprVersion(),
                        isNew ? "CREATE" : "UPDATE", repo.toJson(m), operator);
                anyWritten = true;
                result.item(code, isNew ? "CREATED" : "UPDATED",
                        isNew ? "已新增" : "已覆盖为 v" + row.getExprVersion());
            } catch (Exception e) {
                // 单条写库失败不影响其余条目，但必须把原因带回页面，否则使用者无从下手
                result.item(code, "FAILED", "写入失败：" + e.getMessage());
                log.warn("[质控] 批量导入单条失败: code={}", code, e);
            }
        }

        String tail;
        if (anyWritten) {
            try {
                result.setSyncedCount(reloadAndSync());
                tail = "；配置已热生效";
            } catch (Exception e) {
                // 入库成功但热生效失败：必须显式说出来，否则又变成「保存成功但看板没变」
                tail = "；但热生效失败：" + e.getMessage() + "，请检查配置后点「重载配置」";
                log.error("[质控] 批量导入后热生效失败", e);
            }
        } else {
            tail = "；无有效改动，未触发热生效";
        }
        result.setMessage(String.format("导入完成：新增 %d、覆盖 %d、跳过 %d、失败 %d%s",
                result.getCreated(), result.getUpdated(), result.getSkipped(), result.getFailed(), tail));
        log.info("[质控] 指标批量导入: mode={}, trial={}, operator={}, {}",
                mode, trial, operator, result.getMessage());
        return result;
    }

    // ==================================================================
    // 生效
    // ==================================================================

    /**
     * 重载配置并同步指标字典。
     *
     * <p>顺序不能颠倒：先 {@code reload()} 把 DB 配置读进内存，再 {@code sync()} 落字典表。
     * 反过来会把旧配置重新写进字典表，看起来"同步成功"实则什么都没变。
     */
    public int reloadAndSync() {
        dsl.reload();
        return indexSyncService.sync();
    }

    // ==================================================================
    // internal
    // ==================================================================

    /**
     * 写操作前置检查：真源必须是 db。
     *
     * <p>默认真源是 yaml，此时保存只会写进 DB 而不参与计算 ——
     * 使用者看到「保存成功」但看板纹丝不动，会反复重试并最终不信任这个功能。
     * 与其如此，不如直接拒绝并说清怎么改。
     */
    private String writableGuard() {
        if (!dsl.useDb()) {
            return "当前配置真源为 yaml，页面保存不会生效。"
                    + "请将配置项 zing.quality.config-source 设为 db 并重启服务后再编辑。";
        }
        return null;
    }

    /**
     * 计算新版本号：只有真正影响结果的字段变化才递增。
     *
     * <p>名称、备注、单位这类改动不影响算出来的数字，若也递增版本，
     * 历史值会被无意义地切成多段，回溯时反而看不出「哪次改动改变了口径」。
     */
    private int nextVersion(QualityMetricDef existing, MetricDefinition m) {
        if (existing == null) {
            return 1;
        }
        int current = existing.getExprVersion() == null ? 1 : existing.getExprVersion();
        return caliberChanged(existing, m) ? current + 1 : current;
    }

    private boolean caliberChanged(QualityMetricDef row, MetricDefinition m) {
        return !eq(row.getValueType(), m.getValueType())
                || !eq(row.getAgg(), m.getAgg())
                || !eq(row.getScale(), m.getScale())
                || !eq(row.getExprWhere(), m.getWhere())
                || !eq(row.getExprNumerator(), m.getNumerator())
                || !eq(row.getExprDenominatorWhere(), m.getDenominatorWhere())
                // 改引用等于改口径：从「自己算」换成「取别人的数」，历史值必须能分段对齐
                || !eq(row.getNumeratorMetric(), m.getNumeratorMetric())
                || !eq(row.getDenominatorMetric(), m.getDenominatorMetric())
                || !eq(repo.writeList(repo.readList(row.getDims())), repo.writeList(m.getDims()));
    }

    private boolean eq(Object a, Object b) {
        String x = a == null ? null : String.valueOf(a).trim();
        String y = b == null ? null : String.valueOf(b).trim();
        return Objects.equals(x, y);
    }

    // ==================================================================
    // 结果模型
    // ==================================================================

    @Data
    public static class SaveResult {
        private boolean saved;
        private int exprVersion;
        private int syncedCount;
        private String message;
        /** 校验详情：被拒绝时页面要原样展示 errors / warnings */
        private QualityConfigValidator.ValidationResult validation;

        static SaveResult saved(QualityConfigValidator.ValidationResult v, Integer version,
                                int synced, String message) {
            SaveResult r = new SaveResult();
            r.saved = true;
            r.validation = v;
            r.exprVersion = version == null ? 0 : version;
            r.syncedCount = synced;
            r.message = message;
            return r;
        }

        static SaveResult validationFailed(QualityConfigValidator.ValidationResult v) {
            SaveResult r = new SaveResult();
            r.validation = v;
            r.message = "校验未通过，配置未保存";
            return r;
        }

        static SaveResult rejected(String message) {
            SaveResult r = new SaveResult();
            r.message = message;
            return r;
        }
    }

    /**
     * 批量导入结果：逐条结论 + 汇总计数。
     *
     * <p>刻意返回 {@code items} 而不是只给个总数：导入失败时使用者需要精确知道
     * 「哪几条、为什么」，否则只能靠一条条重试去猜。
     */
    @Data
    public static class ImportResult {
        /** 前置条件不满足（真源非 db / 内容为空 / 超量），此时 items 为空 */
        private boolean rejected;
        private String message;
        private int total;
        private int created;
        private int updated;
        private int skipped;
        private int failed;
        /** 热生效后同步的指标字典条数 */
        private int syncedCount;
        private List<Item> items = new ArrayList<>();

        void item(String code, String status, String message) {
            Item it = new Item();
            it.setCode(code);
            it.setStatus(status);
            it.setMessage(message);
            items.add(it);
            if ("CREATED".equals(status)) {
                created++;
            } else if ("UPDATED".equals(status)) {
                updated++;
            } else if ("SKIPPED".equals(status)) {
                skipped++;
            } else {
                failed++;
            }
        }

        @Data
        public static class Item {
            private String code;
            /** CREATED / UPDATED / SKIPPED / FAILED */
            private String status;
            private String message;
        }
    }

    /**
     * 配置真源与可写状态。
     *
     * <p>页面打开时先取一次：真源是 yaml 就整体置为只读并给出提示，
     * 而不是让使用者编辑半天、点保存才被拒绝 —— 那是一种很伤信任的交互。
     */
    public Map<String, Object> status() {
        boolean writable = dsl.useDb();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configSource", writable ? "db" : "yaml");
        m.put("writable", writable);
        m.put("hint", writable ? "配置真源为数据库，保存后立即生效" : writableGuard());
        m.put("metricCount", dsl.getMetrics().size());
        m.put("factCount", dsl.getFacts().size());
        return m;
    }

    /** 供控制器判断可选事实层时使用。 */
    public boolean hasFact(String factName) {
        return StringUtils.hasText(factName) && dsl.getFacts().containsKey(factName);
    }
}
