package com.zing.doctor.quality.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.zing.doctor.quality.dsl.FactDefinition;
import com.zing.doctor.quality.dsl.MetricDefinition;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
