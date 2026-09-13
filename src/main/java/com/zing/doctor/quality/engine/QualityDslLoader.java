package com.zing.doctor.quality.engine;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zing.doctor.quality.config.QualityProperties;
import com.zing.doctor.quality.dsl.FactDefinition;
import com.zing.doctor.quality.dsl.MetricDefinition;
import com.zing.doctor.quality.dsl.SourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 质控 DSL 加载器。
 *
 * <p>从 classpath 加载三层配置（数据源 / 事实层 / 指标），并在启动时完成解析。
 * 这是「改指标不改代码」的入口：配置变更后调用 {@link #reload()} 即可热生效。
 */
@Component
public class QualityDslLoader {

    private static final Logger log = LoggerFactory.getLogger(QualityDslLoader.class);

    private final QualityProperties props;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    private final ObjectMapper om = new ObjectMapper();

    private volatile SourceConfig sourceConfig = new SourceConfig();
    private volatile Map<String, FactDefinition> factMap = new LinkedHashMap<>();
    private volatile Map<String, MetricDefinition> metricMap = new LinkedHashMap<>();

    public QualityDslLoader(QualityProperties props) {
        this.props = props;
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

    /** 重新加载全部 DSL 配置（热生效）。 */
    public synchronized void reload() {
        SourceConfig sc = readOne(props.getSourceLocation());
        this.sourceConfig = sc == null ? new SourceConfig() : sc;

        List<Map<String, Object>> factNodes = readList(props.getFactLocation(), "facts");
        Map<String, FactDefinition> fm = new LinkedHashMap<>();
        for (Map<String, Object> node : factNodes) {
            FactDefinition f = om.convertValue(node, FactDefinition.class);
            if (f.getFact() == null || f.getFact().trim().isEmpty()) {
                continue;
            }
            fm.put(f.getFact(), f);
        }
        this.factMap = fm;

        List<Map<String, Object>> metricNodes = readList(props.getMetricLocation(), "metrics");
        Map<String, MetricDefinition> mm = new LinkedHashMap<>();
        for (Map<String, Object> node : metricNodes) {
            MetricDefinition m = om.convertValue(node, MetricDefinition.class);
            if (m.getCode() == null || m.getCode().trim().isEmpty()) {
                continue;
            }
            if (m.getSortNo() == null) {
                m.setSortNo(parseSeq(m.getCode()));
            }
            mm.put(m.getCode(), m);
        }
        this.metricMap = mm;

        log.info("[质控] DSL 加载完成：数据源 {} 个，事实层 {} 个，指标 {} 条",
                sourceConfig.getDatasources().size(), fm.size(), mm.size());
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
