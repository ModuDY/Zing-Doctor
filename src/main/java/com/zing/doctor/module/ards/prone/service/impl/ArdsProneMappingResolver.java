package com.zing.doctor.module.ards.prone.service.impl;

import com.zing.doctor.module.ards.prone.dict.ArdsProneDict;
import com.zing.doctor.module.ards.prone.entity.ArdsProneConfig;
import com.zing.doctor.module.ards.prone.mapper.ArdsProneConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ARDS 俯卧位采集映射解析器：把「参数项 → 数据源项目」的映射从代码搬到配置表。
 *
 * <p>解析顺序（前者命中即返回，不做沿用）：
 * <ol>
 *   <li>{@code ards_prone_config} 启用规则：按 priority 升序（数字小优先），
 *       同一优先级先监护通道后退检验通道；规则值逗号分隔多值，任一命中即可</li>
 *   <li>该参数无规则或规则全部未命中 → 回退 {@code ArdsProneDict} 内置关键字（from=builtin）</li>
 * </ol>
 *
 * <p>匹配方式：{@code code} 按 item_code / lis_item_code 精确匹配；{@code name} 按项目名称包含匹配（忽略大小写与空格）。
 * 记录已按时间倒序，取窗口内最近一条；命中时间超出该参数采集窗口的跳过（支持按参数覆盖窗口）。
 * 窗口内无数据返回 {@code null}，由上层置空转手工。
 *
 * <p>配置读取带 60s TTL 缓存，配置保存/删除/启停后主动失效（单机部署即时生效；多实例靠 TTL 兜底）。
 */
@Slf4j
@Component
public class ArdsProneMappingResolver {

    /** 通道：监护 / 呼吸机观察项 */
    public static final String CH_OBSERVE = "observe_item";

    /** 通道：检验 / 血气 */
    public static final String CH_LIS = "lis_item";

    /** 匹配方式：item_code 精确 */
    public static final String MATCH_CODE = "code";

    /** 匹配方式：项目名称包含 */
    public static final String MATCH_NAME = "name";

    /** 命中来源：配置规则 */
    public static final String FROM_RULE = "rule";

    /** 命中来源：字典内置关键字 */
    public static final String FROM_BUILTIN = "builtin";

    /** 未命中（窗口内无数据 / 无规则） */
    public static final String FROM_MISS = "miss";

    /** 已有值保留（本次未覆盖） */
    public static final String FROM_KEEP = "keep";

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 缓存有效期（毫秒） */
    private static final long TTL_MS = 10_000L;

    @Autowired
    private ArdsProneConfigMapper configMapper;

    /** 规则缓存：参数编码 → 启用规则（已排序） */
    private volatile Map<String, List<ArdsProneConfig>> cached = null;

    private volatile long loadedAt = 0L;

    /** 配置变更后失效缓存 */
    public void invalidate() {
        this.cached = null;
        this.loadedAt = 0L;
    }

    /** 某参数的启用规则（priority 升序，同优先级监护优先） */
    public List<ArdsProneConfig> rulesOf(String paramKey) {
        if (!StringUtils.hasText(paramKey)) {
            return new ArrayList<>();
        }
        return rules().getOrDefault(paramKey.trim(), new ArrayList<>());
    }

    /**
     * 某通道实际生效的查询窗口（分钟）：取该通道所有启用规则 windowMin 的最大值，
     * 不小于字典默认窗口；各参数命中时再按自身窗口二次过滤。
     */
    public int windowFor(String channel, int defaultMin) {
        int win = defaultMin;
        for (List<ArdsProneConfig> list : rules().values()) {
            for (ArdsProneConfig c : list) {
                if (c.getWindowMin() == null || c.getWindowMin() <= 0) {
                    continue;
                }
                if (channel != null && !channel.equals(c.getConfigType())) {
                    continue;
                }
                if (c.getWindowMin() > win) {
                    win = c.getWindowMin();
                }
            }
        }
        return win;
    }

    /**
     * 解析某参数在某时点的取值。
     *
     * @param def     参数定义（含内置关键字，作为兜底）
     * @param observe 窗口内监护 / 呼吸机记录（item_code / item_name / item_value / item_time）
     * @param labs    窗口内检验 / 血气记录（item_code / item_name / item_result / check_time）
     * @param plan    时点计划时间，用于按参数窗口二次过滤（可空，不校验）
     * @return 命中明细，无命中返回 null。键：value / itemTime / source / from / matchType /
     *         itemCode / itemName / ruleId / ruleValue / windowMin
     */
    public Map<String, Object> resolve(ArdsProneDict.ParamDef def,
                                       List<Map<String, Object>> observe,
                                       List<Map<String, Object>> labs,
                                       LocalDateTime plan) {
        if (def == null) {
            return null;
        }
        // 1) 配置规则
        for (ArdsProneConfig rule : rulesOf(def.getKey())) {
            boolean lis = CH_LIS.equals(rule.getConfigType());
            List<Map<String, Object>> rows = lis ? labs : observe;
            int win = rule.getWindowMin() != null && rule.getWindowMin() > 0
                    ? rule.getWindowMin()
                    : defaultWindow(def, lis);
            Map<String, Object> hit = matchRows(rows, rule.getMatchType(),
                    splitKeys(rule.getConfigValue()), lis, plan, win);
            if (hit == null) {
                continue;
            }
            hit.put("source", lis ? ArdsProneDict.SRC_LIS : ArdsProneDict.SRC_AUTO);
            hit.put("from", FROM_RULE);
            hit.put("matchType", rule.getMatchType());
            hit.put("ruleId", rule.getId());
            hit.put("ruleValue", rule.getConfigValue());
            hit.put("windowMin", win);
            hit.put("unitScale", rule.getUnitScale());
            hit.put("unitOffset", rule.getUnitOffset());
            return hit;
        }
        // 2) 内置关键字兜底（保持历史行为：先观察项、后检验）
        if (def.getObserveKeywords() != null && !def.getObserveKeywords().isEmpty()) {
            Map<String, Object> hit = matchRows(observe, MATCH_NAME, def.getObserveKeywords(), false, plan,
                    defaultWindow(def, false));
            if (hit != null) {
                return decorateBuiltin(hit, def, false);
            }
        }
        if (def.getLisKeywords() != null && !def.getLisKeywords().isEmpty()) {
            Map<String, Object> hit = matchRows(labs, MATCH_NAME, def.getLisKeywords(), true, plan,
                    defaultWindow(def, true));
            if (hit != null) {
                return decorateBuiltin(hit, def, true);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 内部

    private Map<String, Object> decorateBuiltin(Map<String, Object> hit, ArdsProneDict.ParamDef def, boolean lis) {
        hit.put("source", lis ? ArdsProneDict.SRC_LIS : ArdsProneDict.SRC_AUTO);
        hit.put("from", FROM_BUILTIN);
        hit.put("matchType", MATCH_NAME);
        hit.put("windowMin", defaultWindow(def, lis));
        return hit;
    }

    /** 参数固有窗口：字典 windowMin 优先，未配置按通道默认（监护 ±15 / 检验 ±60） */
    private int defaultWindow(ArdsProneDict.ParamDef def, boolean lis) {
        if (def != null && def.getWindowMin() != null && def.getWindowMin() > 0) {
            return def.getWindowMin();
        }
        return lis ? ArdsProneDict.WINDOW_LAB : ArdsProneDict.WINDOW_CONTINUOUS;
    }

    private Map<String, List<ArdsProneConfig>> rules() {
        Map<String, List<ArdsProneConfig>> local = cached;
        if (local != null && System.currentTimeMillis() - loadedAt < TTL_MS) {
            return local;
        }
        synchronized (this) {
            if (cached != null && System.currentTimeMillis() - loadedAt < TTL_MS) {
                return cached;
            }
            Map<String, List<ArdsProneConfig>> map = new LinkedHashMap<>();
            try {
                for (ArdsProneConfig c : configMapper.selectEnabled()) {
                    if (!StringUtils.hasText(c.getConfigKey())) {
                        continue;
                    }
                    map.computeIfAbsent(c.getConfigKey().trim(), k -> new ArrayList<>()).add(c);
                }
            } catch (Exception e) {
                log.warn("加载 ARDS 俯卧位映射配置失败，本次全部回退内置关键字: {}", e.getMessage());
                map = new LinkedHashMap<>();
            }
            cached = map;
            loadedAt = System.currentTimeMillis();
            return map;
        }
    }

    /**
     * 在按时间倒序的记录里找第一条命中、值非空且未超窗的行。
     *
     * @param matchType code / name（为空按 name 处理，兼容手填配置）
     * @param keys      编码或关键字集合
     * @param plan      时点计划时间（可空）
     * @param windowMin 采集窗口（分钟），plan 与行时间都可得时才校验
     */
    private Map<String, Object> matchRows(List<Map<String, Object>> rows, String matchType,
                                          List<String> keys, boolean lis,
                                          LocalDateTime plan, int windowMin) {
        if (rows == null || rows.isEmpty() || keys == null || keys.isEmpty()) {
            return null;
        }
        boolean byCode = MATCH_CODE.equalsIgnoreCase(matchType == null ? "" : matchType.trim());
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            String code = str(row.get("item_code"));
            String name = str(row.get("item_name"));
            boolean hit = byCode
                    ? (code != null && containsIgnoreCase(keys, code))
                    : (name != null && containsAny(name, keys));
            if (!hit) {
                continue;
            }
            String value = lis ? str(row.get("item_result")) : str(row.get("item_value"));
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String timeText = lis ? str(row.get("check_time")) : str(row.get("item_time"));
            if (plan != null && windowMin > 0) {
                LocalDateTime t = parseTime(timeText);
                if (t != null && Math.abs(Duration.between(plan, t).toMinutes()) > windowMin) {
                    continue;
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("value", value.trim());
            m.put("itemTime", timeText);
            m.put("itemCode", code);
            m.put("itemName", name);
            return m;
        }
        return null;
    }

    /** 逗号 / 顿号 / 分号 / 换行 分隔拆分配置值（去空、去重保序） */
    public static List<String> splitKeys(String value) {
        List<String> keys = new ArrayList<>();
        if (!StringUtils.hasText(value)) {
            return keys;
        }
        for (String s : value.split("[,，;；\\n]")) {
            String t = s == null ? "" : s.trim();
            if (!t.isEmpty() && !keys.contains(t)) {
                keys.add(t);
            }
        }
        return keys;
    }

    /** 供「一键从内置生成」使用：内置关键字 → 规则值文本 */
    public static String joinKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return "";
        }
        return String.join(",", keys);
    }

    private boolean containsAny(String text, List<String> keywords) {
        String hay = normalize(text);
        for (String k : keywords) {
            if (StringUtils.hasText(k) && hay.contains(normalize(k))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsIgnoreCase(List<String> keys, String code) {
        for (String k : keys) {
            if (k != null && k.trim().equalsIgnoreCase(code.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toUpperCase().replace(" ", "").trim();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static LocalDateTime parseTime(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String v = text.trim();
        try {
            if (v.length() >= 19) {
                return LocalDateTime.parse(v.substring(0, 19).replace('T', ' '), DT_FMT);
            }
            if (v.length() == 16) {
                return LocalDateTime.parse(v + ":00", DT_FMT);
            }
            if (v.length() == 10) {
                return LocalDateTime.parse(v + " 00:00:00", DT_FMT);
            }
            return LocalDateTime.parse(v, DT_FMT);
        } catch (Exception e) {
            return null;
        }
    }
}
