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

    /**
     * 药品名关键词（医嘱 {@code patient_advice.name} / 执行单 {@code drug_name}）。
     *
     * <p>同一类药物在不同医院、不同厂家的商品名下写法差异极大（「去甲肾上腺素」也可能叫
     * 「重酒石酸去甲肾上腺素注射液」），匹配一律用「名称包含」。
     *
     * <p>四个血管活性药键看起来重复，其实是四处口径不同的历史实现，**不能合并**：
     * 合并会改变脓毒症集束化达标率、SOFA 剂量还原这些已有指标的结果。
     */
    private static final Map<String, List<String>> DRUG;

    /** 诊断名关键词（{@code patient_info_diagnosis.diag_name}）。 */
    private static final Map<String, List<String>> DIAG;

    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put(DataItemConfigService.DICT_GAS_SAMPLE, Collections.singletonList("动脉血"));
        m.put(DataItemConfigService.DICT_GAS_REPORT, Collections.singletonList("血气分析"));
        m.put(DataItemConfigService.DICT_OXYGEN_INDEX, Collections.singletonList("氧合指数"));
        m.put(DataItemConfigService.DICT_CULTURE_ITEM, Arrays.asList("培养", "药敏"));
        m.put(DataItemConfigService.DICT_CULTURE_REPORT, Collections.singletonList("培养"));
        m.put(DataItemConfigService.DICT_TEMP_ITEM, Collections.singletonList("体温"));
        DICT = Collections.unmodifiableMap(m);

        Map<String, List<String>> d = new LinkedHashMap<>();
        d.put(DataItemConfigService.DRUG_VASOPRESSOR, Arrays.asList(
                "去甲肾上腺素", "多巴胺", "肾上腺素", "间羟胺",
                "多巴酚丁胺", "血管加压素", "垂体后叶", "特利加压素"));
        d.put(DataItemConfigService.DRUG_VASOPRESSOR_BUNDLE, Arrays.asList(
                "去甲肾上腺素", "多巴胺", "肾上腺素", "间羟胺", "多巴酚丁胺", "血管升压"));
        d.put(DataItemConfigService.DRUG_VASOPRESSOR_EXEC, Arrays.asList(
                "去甲肾上腺素", "多巴胺", "肾上腺素", "多巴酚丁胺"));
        d.put(DataItemConfigService.DRUG_OPIOID, Arrays.asList(
                "芬太尼", "吗啡", "地佐辛", "布托啡诺", "纳布啡", "氢吗啡酮"));
        d.put(DataItemConfigService.DRUG_SOLVENT_EXCLUDE, Arrays.asList(
                "氯化钠", "葡萄糖", "林格", "灭菌注射用水", "木糖醇", "转化糖", "果糖", "甘油果糖"));
        d.put(DataItemConfigService.DRUG_FLUID, Arrays.asList(
                "氯化钠", "葡萄糖", "乳酸钠林格", "林格", "复方氯化钠", "灭菌注射用水"));
        DRUG = Collections.unmodifiableMap(d);

        Map<String, List<String>> g = new LinkedHashMap<>();
        g.put(DataItemConfigService.DIAG_INFECTION, Arrays.asList(
                "脓毒", "感染", "肺炎", "腹腔", "腹膜炎", "血流", "菌血症", "尿路", "泌尿", "真菌"));
        g.put(DataItemConfigService.DIAG_ARDS, Arrays.asList("急性呼吸窘迫综合征", "ARDS"));
        DIAG = Collections.unmodifiableMap(g);
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

    /** 按域 + 键取默认值（DICT / DRUG / DIAG 三个域有键级默认） */
    public static List<String> ofKey(String domain, String itemKey) {
        if (domain == null || itemKey == null) {
            return Collections.emptyList();
        }
        Map<String, List<String>> table;
        if (DataItemConfigService.DOMAIN_DICT.equals(domain)) {
            table = DICT;
        } else if (DataItemConfigService.DOMAIN_DRUG.equals(domain)) {
            table = DRUG;
        } else if (DataItemConfigService.DOMAIN_DIAG.equals(domain)) {
            table = DIAG;
        } else {
            return Collections.emptyList();
        }
        List<String> v = table.get(itemKey);
        return v == null ? Collections.emptyList() : v;
    }
}
