package com.zing.doctor.module.config.dataitem;

import com.zing.doctor.module.config.dataitem.service.DataItemConfigService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    public static List<String> of(String domain) {
        if (DataItemConfigService.DOMAIN_VENT.equals(domain)) {
            return VENT;
        }
        if (DataItemConfigService.DOMAIN_IO.equals(domain)) {
            return IO;
        }
        return Collections.emptyList();
    }
}
