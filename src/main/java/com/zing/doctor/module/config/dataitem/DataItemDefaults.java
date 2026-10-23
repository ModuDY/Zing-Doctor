package com.zing.doctor.module.config.dataitem;

import com.zing.doctor.module.config.dataitem.service.DataItemConfigService;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置默认映射值（配置表为空时的回退）。
 *
 * <p>与 {@code sql/66_config_data_item.sql} 的初始化数据同源，改这里必须同步改脚本。
 * 存在的意义是：现场用 {@code --skip-db-init} 升级、还没执行建表脚本时，取数行为与
 * 改造前完全一致，不会因为"配置表没学"而让全院指标全部空掉。
 */
public final class DataItemDefaults {

    private DataItemDefaults() {
    }

    /** 呼吸机参数：呼末潮气量 / PEEP / FiO₂ 设置值 / 呼吸频率设置值 / 呼末分钟通气量 */
    public static final List<String> VENT = Collections.unmodifiableList(Arrays.asList(
            "oi_呼末潮气量", "oi_peep", "oi_FiO2(设置值)", "oi_呼吸频率(设置值)", "oi_呼末分钟通气量"));

    /** 出入量：尿量 */
    public static final List<String> IO = Collections.unmodifiableList(Collections.singletonList("ii_nl"));

    /**
     * 术语字典：LIS 报告名 / 标本名 / 项目名 / 观察项名。
     *
     * <p>这些是检验系统与护理系统里由厂商实施时配的**名称**，不是本项目定义的业务键，
     * 换一家医院就可能换一套写法（「血气分析」vs「动脉血气分析」）。
     */
    private static final Map<String, List<String>> DICT;

    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put(DataItemConfigService.DICT_GAS_SAMPLE, Collections.singletonList("动脉血"));
        m.put(DataItemConfigService.DICT_GAS_REPORT, Collections.singletonList("血气分析"));
        m.put(DataItemConfigService.DICT_OXYGEN_INDEX, Collections.singletonList("氧合指数"));
        m.put(DataItemConfigService.DICT_CULTURE_ITEM, Arrays.asList("培养", "药敏"));
        m.put(DataItemConfigService.DICT_CULTURE_REPORT, Collections.singletonList("培养"));
        m.put(DataItemConfigService.DICT_TEMP_ITEM, Collections.singletonList("体温"));
        DICT = Collections.unmodifiableMap(m);
    }

    public static List<String> of(String domain) {
        if (DataItemConfigService.DOMAIN_VENT.equals(domain)) {
            return VENT;
        }
        if (DataItemConfigService.DOMAIN_IO.equals(domain)) {
            return IO;
        }
        return Collections.emptyList();
    }

    /** 按域 + 键取默认值（只有 DICT 域有键级默认） */
    public static List<String> ofKey(String domain, String itemKey) {
        if (!DataItemConfigService.DOMAIN_DICT.equals(domain) || itemKey == null) {
            return Collections.emptyList();
        }
        List<String> v = DICT.get(itemKey);
        return v == null ? Collections.emptyList() : v;
    }
}
