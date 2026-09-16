package com.zing.doctor.quality.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 识别器注册中心（算子库的「条件算子」部分）。
 *
 * <p>作用：把已有 Java 判定能力（如抗菌药广谱判定）注册成 DSL 里可按名引用的操作数，
 * 指标配置写 {@code recognizer:isBroadSpectrum} 即可复用，<b>无需修改被复用的类</b>。
 *
 * <p>为保证计算仍能下推数据库，识别器返回的是可内联的 SQL 谓词片段；
 * 无法 SQL 化的判定（需逐行 Java 计算）请在事实层物化阶段完成。
 *
 * <p>算子库是一次性投入：新增指标只做「已有算子的组合」；仅当出现全新计算模式时才补一个算子。
 */
@Component
public class RecognizerRegistry {

    private static final Logger log = LoggerFactory.getLogger(RecognizerRegistry.class);

    /** 识别器名 → SQL 谓词生成器 */
    public interface Recognizer {
        /**
         * @param column 待判定的列表达式（如 t.name）
         * @return SQL 布尔谓词
         */
        String predicate(String column);
    }

    private final Map<String, Recognizer> registry = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        // 广谱抗菌药：以药名关键词判定，与 AbxDrugRecognizer 的词表口径保持一致。
        register("isBroadSpectrum", column ->
                "(" + likeAny(column, "碳青霉烯", "头孢", "哌拉西林", "美罗培南", "亚胺培南",
                        "万古霉素", "利奈唑胺", "替加环素", "头孢哌酮", "舒巴坦", "莫西沙星", "左氧氟沙星") + ")");
        // 抗菌药：类型标记
        register("isAntibiotic", column -> "(t.type_code = 'drug')");
        log.info("[质控] 识别器注册完成：{}", registry.keySet());
    }

    public void register(String name, Recognizer recognizer) {
        if (StringUtils.hasText(name) && recognizer != null) {
            registry.put(name, recognizer);
        }
    }

    public boolean contains(String name) {
        return registry.containsKey(name);
    }

    /** 按名取谓词；未注册则抛异常（配置错误应尽早暴露，而非静默算错）。 */
    public String predicate(String name, String column) {
        Recognizer r = registry.get(name);
        if (r == null) {
            throw new IllegalStateException("未注册的识别器: " + name);
        }
        return r.predicate(column);
    }

    private String likeAny(String column, String... keywords) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keywords.length; i++) {
            if (i > 0) {
                sb.append(" OR ");
            }
            sb.append(column).append(" LIKE '%").append(keywords[i]).append("%'");
        }
        return sb.toString();
    }
}
