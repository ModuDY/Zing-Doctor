package com.zing.doctor.quality.engine;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zing.doctor.quality.config.QualityProperties;
import com.zing.doctor.quality.dsl.FactDefinition;
import com.zing.doctor.quality.dsl.MetricDefinition;
import com.zing.doctor.quality.dsl.SourceConfig;
import com.zing.doctor.quality.entity.QualityFactDef;
import com.zing.doctor.quality.entity.QualityMetricDef;
import com.zing.doctor.quality.repository.QualityConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 质控 DSL 加载器。
 *
 * <p>从 YAML 或配置表加载三层配置（数据源 / 事实层 / 指标），并在启动时完成解析。
 * 这是「改指标不改代码」的入口：配置变更后调用 {@link #reload()} 即可热生效。
 *
 * <p>真源由 {@code zing.quality.config-source} 决定：{@code yaml}（默认）读配置文件；
 * {@code db} 读 {@code quality_metric_def} / {@code quality_fact_def}，使页面编辑成为可能。
 * 两种模式产出的内存结构完全一致，因此编译与计算链路不受影响。
 */
@Component
public class QualityDslLoader {

    private static final Logger log = LoggerFactory.getLogger(QualityDslLoader.class);

    private final QualityProperties props;
    private final QualityConfigRepository configRepo;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    private final ObjectMapper om = new ObjectMapper();

    private volatile SourceConfig sourceConfig = new SourceConfig();
    private volatile Map<String, FactDefinition> factMap = new LinkedHashMap<>();
    private volatile Map<String, MetricDefinition> metricMap = new LinkedHashMap<>();

    public QualityDslLoader(QualityProperties props, QualityConfigRepository configRepo) {
        this.props = props;
        this.configRepo = configRepo;
        this.om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @PostConstruct
    public void init() {
        if (!props.isEnabled()) {
            log.info("[质控] 中台未启用（zing.quality.enabled=false），跳过 DSL 加载");
            return;
        }
        try {
            reload();
        } catch (Exception e) {
            // 配置异常不应阻断应用启动：留空配置，接口返回空看板
            log.error("[质控] DSL 加载失败，质控看板将不可用: {}", e.getMessage(), e);
        }
    }

    /**
     * 重新加载全部 DSL 配置（热生效）。
     *
     * <p>三层配置<b>全部在局部变量中解析完成后才统一赋值</b>，保证「要么整体换成新配置、
     * 要么保持原来的旧配置」。若边解析边赋值，某一层失败时（如 YAML 语法错误）会留下
     * 「新数据源 + 旧事实层」这类错配，运行期反复热加载时尤其危险。
     */
    public synchronized void reload() {
        SourceConfig sc = readOne(resolveLocation(props.getSourceLocation(), "sources.yaml"));
        SourceConfig newSource = sc == null ? new SourceConfig() : sc;

        Map<String, FactDefinition> newFacts;
        Map<String, MetricDefinition> newMetrics;

        if (useDb()) {
            Map<String, FactDefinition> dbFacts = null;
            Map<String, MetricDefinition> dbMetrics = null;
            try {
                // 首次切到 db 真源时配置表是空的，此时把 classpath 的 YAML 作为「出厂种子」导入一次，
                // 免去人工准备上百条数据，也保证 DB 与既有配置逐字段一致。
                if (configRepo.isEmpty()) {
                    seedFromYaml();
                }
                dbFacts = indexByFact(configRepo.loadFacts());
                dbMetrics = indexByCode(configRepo.loadMetrics());
            } catch (Exception e) {
                // 配置表不可用（表未建 / 连不上库）不能拖垮整个质控：退回 YAML，看板照常有数。
                log.warn("[质控] 配置表加载失败，本次回退 classpath YAML: {}", e.getMessage());
            }
            if (dbMetrics == null || dbMetrics.isEmpty()) {
                newFacts = readFactsFromYaml();
                newMetrics = readMetricsFromYaml();
            } else {
                newFacts = dbFacts;
                newMetrics = dbMetrics;
            }
        } else {
            newFacts = readFactsFromYaml();
            newMetrics = readMetricsFromYaml();
        }

        // 三层均解析成功，此处一次性原子替换
        this.sourceConfig = newSource;
        this.factMap = newFacts;
        this.metricMap = newMetrics;

        log.info("[质控] DSL 加载完成（真源 {}）：数据源 {} 个，事实层 {} 个，指标 {} 条",
                useDb() ? "db" : "yaml", newSource.getDatasources().size(), newFacts.size(), newMetrics.size());
    }

    /** 当前是否以配置表为真源。表不可用时 {@link #reload()} 内部会临时回退 YAML。 */
    public boolean useDb() {
        return props.isEnabled() && "db".equalsIgnoreCase(props.getConfigSource());
    }

    // ------------------------------------------------------------------
    // 出厂种子导入
    // ------------------------------------------------------------------

    /**
     * 把 classpath 的 YAML 作为「出厂种子」导入配置表（仅在配置表为空时执行一次）。
     *
     * <p>注意事实层与指标层都导：指标通过 {@code fact_name} 引用事实层，
     * 只导指标会让新库上的每一条指标都因找不到事实层而无法编译。
     */
    private void seedFromYaml() {
        Map<String, FactDefinition> facts = readFactsFromYaml();
        Map<String, MetricDefinition> metrics = readMetricsFromYaml();
        if (facts.isEmpty() && metrics.isEmpty()) {
            log.warn("[质控] 配置表为空，且 classpath 未找到 YAML，跳过出厂种子导入");
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (FactDefinition f : facts.values()) {
            QualityFactDef row = new QualityFactDef();
            configRepo.fillRow(row, f);
            row.setOperator("seed");
            row.setCreateTime(now);
            row.setUpdateTime(now);
            configRepo.insertFact(row);
        }
        for (MetricDefinition m : metrics.values()) {
            QualityMetricDef row = new QualityMetricDef();
            configRepo.fillRow(row, m);
            row.setStatus(1);
            row.setOperator("seed");
            row.setCreateTime(now);
            row.setUpdateTime(now);
            configRepo.insertMetric(row);
        }
        log.info("[质控] 出厂种子导入完成：事实层 {} 条，指标 {} 条", facts.size(), metrics.size());
    }

    // ------------------------------------------------------------------
    // 配置读取（YAML 分支）
    // ------------------------------------------------------------------

    private Map<String, FactDefinition> readFactsFromYaml() {
        List<Map<String, Object>> nodes =
                readList(resolveLocation(props.getFactLocation(), "facts/*.yaml"), "facts");
        Map<String, FactDefinition> map = new LinkedHashMap<>();
        for (Map<String, Object> node : nodes) {
            FactDefinition f = om.convertValue(node, FactDefinition.class);
            if (f.getFact() == null || f.getFact().trim().isEmpty()) {
                continue;
            }
            map.put(f.getFact(), f);
        }
        return map;
    }

    private Map<String, MetricDefinition> readMetricsFromYaml() {
        List<Map<String, Object>> nodes =
                readList(resolveLocation(props.getMetricLocation(), "metrics/*.yaml"), "metrics");
        Map<String, MetricDefinition> map = new LinkedHashMap<>();
        for (Map<String, Object> node : nodes) {
            MetricDefinition m = om.convertValue(node, MetricDefinition.class);
            if (m.getCode() == null || m.getCode().trim().isEmpty()) {
                continue;
            }
            if (m.getSortNo() == null) {
                m.setSortNo(parseSeq(m.getCode()));
            }
            map.put(m.getCode(), m);
        }
        return map;
    }

    private Map<String, FactDefinition> indexByFact(List<FactDefinition> list) {
        Map<String, FactDefinition> map = new LinkedHashMap<>();
        for (FactDefinition f : list) {
            map.put(f.getFact(), f);
        }
        return map;
    }

    private Map<String, MetricDefinition> indexByCode(List<MetricDefinition> list) {
        Map<String, MetricDefinition> map = new LinkedHashMap<>();
        for (MetricDefinition m : list) {
            if (m.getSortNo() == null) {
                m.setSortNo(parseSeq(m.getCode()));
            }
            map.put(m.getCode(), m);
        }
        return map;
    }

    /**
     * 解析实际使用的配置路径：外部目录优先，缺失则回退 classpath。
     *
     * <p>{@code relative} 是相对 {@code zing.quality.config-dir} 的路径：
     * 单文件（如 {@code sources.yaml}）需真实存在才采用；通配（如 {@code facts/*.yaml}）
     * 需其所在目录存在且至少含一个 yaml 才采用。采用即「整体覆盖」，不做逐文件合并 ——
     * 否则外部目录只放一半时，会出现事实层与指标层来源不一致的错配。
     *
     * <p>{@code config-dir} 为空时直接返回原 pattern，行为与改造前完全一致。
     */
    private String resolveLocation(String classpathPattern, String relative) {
        if (!StringUtils.hasText(props.getConfigDir())) {
            return classpathPattern;
        }
        File target = new File(props.getConfigDir(), relative);
        if (relative.endsWith("*.yaml") || relative.endsWith("*.yml")) {
            File dir = target.getParentFile();
            if (dir != null && dir.isDirectory() && hasYaml(dir)) {
                return "file:" + slash(dir.getAbsolutePath()) + "/" + target.getName();
            }
        } else if (target.isFile()) {
            return "file:" + slash(target.getAbsolutePath());
        }
        return classpathPattern;
    }

    private boolean hasYaml(File dir) {
        File[] files = dir.listFiles((d, n) -> {
            String s = n.toLowerCase();
            return s.endsWith(".yaml") || s.endsWith(".yml");
        });
        return files != null && files.length > 0;
    }

    /** 统一为正斜杠：Windows 路径的反斜杠会破坏 Spring 的资源 pattern。 */
    private String slash(String path) {
        return path.replace('\\', '/');
    }

    public SourceConfig getSourceConfig() {
        return sourceConfig;
    }

    public Map<String, FactDefinition> getFacts() {
        return factMap;
    }

    public Map<String, MetricDefinition> getMetrics() {
        return metricMap;
    }

    /** 指标按「域 → 排序号 → 编号」排序输出（页面展示顺序）。 */
    public List<MetricDefinition> sortedMetrics() {
        List<MetricDefinition> list = new ArrayList<>(metricMap.values());
        list.sort(Comparator
                .comparing((MetricDefinition m) -> m.getDomain() == null ? "" : m.getDomain())
                .thenComparing(m -> m.getSortNo() == null ? 0 : m.getSortNo())
                .thenComparing(MetricDefinition::getCode));
        return list;
    }

    public FactDefinition factOf(MetricDefinition m) {
        return m == null || m.getFact() == null ? null : factMap.get(m.getFact());
    }

    // ------------------------------------------------------------------
    // internal
    // ------------------------------------------------------------------

    private SourceConfig readOne(String location) {
        List<Map<String, Object>> list = readList(location, null);
        if (list.isEmpty()) {
            log.warn("[质控] 未找到数据源配置: {}", location);
            return null;
        }
        return om.convertValue(list.get(0), SourceConfig.class);
    }

    /**
     * 读取 YAML 列表。兼容两种写法：根为 List，或根为 Map（取 wrapperKey 或首个 List 字段）。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readList(String pattern, String wrapperKey) {
        List<Map<String, Object>> out = new ArrayList<>();
        Resource[] resources;
        try {
            resources = resolver.getResources(pattern);
        } catch (IOException e) {
            throw new IllegalStateException("质控配置路径解析失败: " + pattern, e);
        }
        Yaml yaml = new Yaml();
        for (Resource r : resources) {
            if (r == null || !r.exists()) {
                continue;
            }
            try (InputStream in = r.getInputStream()) {
                Object root = yaml.load(in);
                collect(root, wrapperKey, out);
            } catch (Exception e) {
                throw new IllegalStateException("质控配置解析失败: " + r.getFilename(), e);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private void collect(Object root, String wrapperKey, List<Map<String, Object>> out) {
        if (root instanceof List) {
            for (Object item : (List<Object>) root) {
                if (item instanceof Map) {
                    out.add((Map<String, Object>) item);
                }
            }
            return;
        }
        if (root instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) root;
            if (wrapperKey != null && m.get(wrapperKey) instanceof List) {
                collect(m.get(wrapperKey), wrapperKey, out);
                return;
            }
            if (m.get("metrics") instanceof List) {
                collect(m.get("metrics"), "metrics", out);
                return;
            }
            if (m.get("facts") instanceof List) {
                collect(m.get("facts"), "facts", out);
                return;
            }
            out.add(m);
        }
    }

    /** quality_455 → 455，用于缺省排序。 */
    private int parseSeq(String code) {
        try {
            return Integer.parseInt(code.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }
}
