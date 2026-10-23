package com.zing.doctor.module.config.dataitem.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.zing.doctor.common.BizException;
import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.module.config.dataitem.DataItemDefaults;
import com.zing.doctor.module.config.dataitem.entity.DataItemConfig;
import com.zing.doctor.module.config.dataitem.mapper.DataItemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据项映射配置服务：统一管理「程序内部键 → 本院实际编码/项目名」。
 *
 * <p>设计要点：
 * <ol>
 *   <li><b>一套配置服务所有页面</b>：同一批观察项编码被 13 个服务引用（生命体征、交班、
 *       ARDS、SOFA、APACHE II、体液平衡…），按页面拆分会让同一个 {@code oi_tiwen}
 *       在不同页面各配一遍，改一处漏一处、口径漂移。</li>
 *   <li><b>读多写少，整表缓存</b>：取数在每次请求里调用，不能每次查库；保存/启停/删除后失效重载。</li>
 *   <li><b>表不存在也能跑</b>：配置表缺失或查询异常时返回空集合，调用方回退内置常量，
 *       行为与改造前完全一致 —— 避免「配置表没建好 = 全院指标全部没数据」。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
// 配置表在医生库。调用方多是 @DS("icu") 的 Service（生命体征、ARDS、驾驶舱…），
// 不显式声明就会顺着当前数据源去 ICU 库查配置表 → 表不存在 → 静默回退内置常量，
// 现场表现为「页面上改了配置却不生效」。候选值/试跑内部再走 IcuPatientMapper 的 @DS("icu")，嵌套切换正常。
@DS("doctor")
public class DataItemConfigService {

    public static final String DOMAIN_VITAL = "VITAL";
    public static final String DOMAIN_VENT = "VENT";
    public static final String DOMAIN_IO = "IO";
    public static final String DOMAIN_LAB = "LAB";
    /** 术语字典：LIS 报告名 / 标本名 / 项目名 / 观察项名（厂商实施时配的名称，院院不同） */
    public static final String DOMAIN_DICT = "DICT";

    /** 血气标本名（lis_short_name）：动脉血 / 动血 / 动脉 */
    public static final String DICT_GAS_SAMPLE = "gas_sample";
    /** 血气报告名（lis_name 包含）：血气分析 / 动脉血气分析 */
    public static final String DICT_GAS_REPORT = "gas_report";
    /** 氧合指数项目名（lis_item_name） */
    public static final String DICT_OXYGEN_INDEX = "oxygen_index";
    /** 培养/药敏明细项目名（lis_item_name 包含） */
    public static final String DICT_CULTURE_ITEM = "culture_item";
    /** 培养类报告名（lis_name 包含） */
    public static final String DICT_CULTURE_REPORT = "culture_report";
    /** 体温观察项名（item_name 包含） */
    public static final String DICT_TEMP_ITEM = "temp_item";

    /** 药品名：医嘱、执行单里的药名关键词（厂家商品名差异大，一律按包含匹配） */
    public static final String DOMAIN_DRUG = "DRUG";
    /** 诊断名：{@code patient_info_diagnosis.diag_name} 关键词 */
    public static final String DOMAIN_DIAG = "DIAG";

    /** 在用/交班升压药（8 项，含血管加压素、垂体后叶、特利加压素） */
    public static final String DRUG_VASOPRESSOR = "vasopressor";
    /** 脓毒症集束化升压药（6 项，口径与改造前一致，勿与上面合并） */
    public static final String DRUG_VASOPRESSOR_BUNDLE = "vasopressor_bundle";
    /** 泵速执行单里的升压药（4 项，用于 SOFA 剂量还原） */
    public static final String DRUG_VASOPRESSOR_EXEC = "vasopressor_exec";
    /** 阿片类镇痛药（6 项） */
    public static final String DRUG_OPIOID = "opioid";
    /** 溶媒排除词：抗菌药医嘱里要把这些剔掉 */
    public static final String DRUG_SOLVENT_EXCLUDE = "solvent_exclude";
    /** 液体入量：脓毒症 30ml/kg 补液统计 */
    public static final String DRUG_FLUID = "fluid";

    /** 感染相关诊断（脓毒症/肺炎/腹腔感染/菌血症…） */
    public static final String DIAG_INFECTION = "infection";
    /** ARDS 诊断 */
    public static final String DIAG_ARDS = "ards";

    public static final String MATCH_CODE = "CODE";
    public static final String MATCH_NAME_LIKE = "NAME_LIKE";
    public static final String MATCH_NAME_EXACT = "NAME_EXACT";

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DataItemConfigMapper mapper;
    private final IcuPatientMapper icuPatientMapper;

    /** 全表启用项缓存：domain → 配置行。写操作后置空，下次读重建 */
    private volatile Map<String, List<DataItemConfig>> cache;

    // ------------------------------------------------------------------
    // 读
    // ------------------------------------------------------------------

    /** 页面 Tab 元信息：domain / 名称 / 是否已接入取数 / 说明 */
    public List<Map<String, Object>> domains() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(domainMeta(DOMAIN_VITAL, "生命体征", true,
                "心率、血压、体温等。影响：生命体征页、交班、ARDS、SOFA、APACHE II、工作台"));
        list.add(domainMeta(DOMAIN_VENT, "呼吸机参数", true,
                "潮气量、PEEP、FiO₂ 等。影响：ARDS 监测、驾驶舱通气计数、危重标签"));
        list.add(domainMeta(DOMAIN_IO, "出入量", true,
                "尿量等。影响：体液平衡、AKI 判定"));
        list.add(domainMeta(DOMAIN_LAB, "检验项目", true,
                "肝功、凝血、肾功电解质、感染指标。按项目名关键字匹配，一行一个关键字"));
        list.add(domainMeta(DOMAIN_DICT, "术语字典", true,
                "血气/培养报告名、标本名、氧合指数、体温项。影响：氧合指数与 ARDS 分级、"
                        + "SOFA/APACHE II、感染与抗菌药判断、体温曲线"));
        list.add(domainMeta(DOMAIN_DRUG, "药品名", true,
                "升压药、镇痛药、溶媒、液体。影响：脓毒症集束化、SOFA/驾驶舱升压药标记、"
                        + "镇痛镇静、抗菌药医嘱"));
        list.add(domainMeta(DOMAIN_DIAG, "诊断名", true,
                "感染相关诊断、ARDS 诊断。影响：疑似感染列表、ARDS 患者名单"));
        return list;
    }

    private Map<String, Object> domainMeta(String code, String name, boolean wired, String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("domain", code);
        m.put("name", name);
        m.put("wired", wired);
        m.put("note", note);
        return m;
    }

    /** 后台列表（含停用项）：domain 空 = 全部 */
    public List<DataItemConfig> list(String domain) {
        try {
            return mapper.selectForAdmin(StringUtils.hasText(domain) ? domain.trim() : null);
        } catch (Exception e) {
            log.warn("[数据项配置] 查询失败（是否未执行 sql/66_config_data_item.sql？）: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 某域的全部启用项（缓存）；表不可用时返回空集合，调用方回退内置常量 */
    public List<DataItemConfig> enabled(String domain) {
        if (!StringUtils.hasText(domain)) {
            return Collections.emptyList();
        }
        Map<String, List<DataItemConfig>> c = cache;
        if (c == null) {
            c = load();
            cache = c;
        }
        List<DataItemConfig> rows = c.get(domain);
        return rows == null ? Collections.emptyList() : rows;
    }

    /** 某域某键的全部匹配值（多个编码/关键字），已按逗号/竖线拆开 */
    public List<String> values(String domain, String itemKey) {
        List<String> out = new ArrayList<>();
        for (DataItemConfig row : enabled(domain)) {
            if (itemKey != null && !itemKey.equals(row.getItemKey())) {
                continue;
            }
            out.addAll(split(row.getMatchValue()));
        }
        return out;
    }

    /**
     * 某域全部启用项的匹配值（不区分 item_key），配置为空时回退内置默认。
     *
     * <p>呼吸机参数、尿量这类「一查就是整组编码」的取数用它，
     * 免得到 4 个调用点各抄一份默认值。
     */
    public List<String> codesOrBuiltin(String domain) {
        List<String> v = values(domain, null);
        if (!v.isEmpty()) {
            return v;
        }
        List<String> builtin = DataItemDefaults.of(domain);
        return builtin.isEmpty() ? v : builtin;
    }

    /**
     * 名称类取值：{@code names(DRUG, DRUG_VASOPRESSOR)}、{@code names(DIAG, DIAG_ARDS)}。
     * 未配置该键时回退内置默认。
     *
     * <p>返回空集合只有一种情况：键名写错了。调用方仍要能跑（SQL 里 foreach 为空会拼出
     * {@code IN ()} 或空条件，轻则报错重则全表命中），所以这里保证非空。
     */
    public List<String> names(String domain, String itemKey) {
        List<String> v = values(domain, itemKey);
        if (!v.isEmpty()) {
            return v;
        }
        List<String> d = DataItemDefaults.ofKey(domain, itemKey);
        return d.isEmpty() ? v : d;
    }

    /** 术语字典取值：等价于 {@code names(DICT, itemKey)} */
    public List<String> dict(String itemKey) {
        return names(DOMAIN_DICT, itemKey);
    }

    private Map<String, List<DataItemConfig>> load() {
        Map<String, List<DataItemConfig>> map = new LinkedHashMap<>();
        try {
            List<DataItemConfig> all = mapper.selectEnabled();
            if (all != null) {
                for (DataItemConfig row : all) {
                    if (row == null || !StringUtils.hasText(row.getDomain())) {
                        continue;
                    }
                    map.computeIfAbsent(row.getDomain().trim(), k -> new ArrayList<>()).add(row);
                }
            }
            log.info("[数据项配置] 已加载 {} 条启用配置，覆盖 {} 个域", all == null ? 0 : all.size(), map.size());
        } catch (Exception e) {
            // 表未建（现场用 --skip-db-init 升级时常见）：不抛异常，让业务回退内置常量
            log.warn("[数据项配置] 配置表不可用，业务回退内置常量: {}", e.getMessage());
        }
        return map;
    }

    /** 写操作后调用：下次读重新加载 */
    public void invalidate() {
        cache = null;
    }

    // ------------------------------------------------------------------
    // 写
    // ------------------------------------------------------------------

    public boolean save(DataItemConfig row) {
        if (row == null) {
            throw new BizException(400, "配置内容不能为空");
        }
        String domain = trim(row.getDomain());
        String matchValue = trim(row.getMatchValue());
        String itemKey = trim(row.getItemKey());
        String itemName = trim(row.getItemName());
        String matchType = trim(row.getMatchType());
        if (!StringUtils.hasText(domain)) {
            throw new BizException(400, "业务域不能为空");
        }
        if (!StringUtils.hasText(itemKey)) {
            throw new BizException(400, "内部键不能为空（程序按它取值，如 hr / peep / alt）");
        }
        if (!StringUtils.hasText(itemName)) {
            throw new BizException(400, "显示名不能为空");
        }
        if (!StringUtils.hasText(matchValue)) {
            throw new BizException(400, "匹配值不能为空（本院实际的编码或项目名）");
        }
        if (!StringUtils.hasText(matchType)) {
            matchType = MATCH_CODE;
        }
        if (!MATCH_CODE.equals(matchType) && !MATCH_NAME_LIKE.equals(matchType)
                && !MATCH_NAME_EXACT.equals(matchType)) {
            throw new BizException(400, "匹配方式只能是 CODE / NAME_LIKE / NAME_EXACT");
        }
        if (matchValue.length() > 500) {
            throw new BizException(400, "匹配值过长（上限 500 字符）");
        }

        String groupCode = StringUtils.hasText(row.getGroupCode()) ? trim(row.getGroupCode()) : "";
        int dup = mapper.countSame(domain, groupCode, itemKey, matchValue);
        if (dup > 0 && (row.getId() == null || isDuplicateSelf(row.getId(), domain, groupCode, itemKey, matchValue))) {
            throw new BizException(400, "该业务域下「" + itemKey + " → " + matchValue + "」已存在，请直接编辑原行");
        }

        LocalDateTime now = LocalDateTime.now();
        row.setDomain(domain);
        row.setGroupCode(groupCode);
        row.setItemKey(itemKey);
        row.setItemName(itemName);
        row.setMatchType(matchType);
        row.setMatchValue(matchValue);
        row.setExcludeValue(trim(row.getExcludeValue()));
        row.setUnit(trim(row.getUnit()));
        row.setSource(trim(row.getSource()));
        row.setGroupName(trim(row.getGroupName()));
        row.setRemark(trim(row.getRemark()));
        if (row.getSortNo() == null) {
            row.setSortNo(0);
        }
        if (row.getStatus() == null) {
            row.setStatus(1);
        }
        row.setUpdateTime(now);

        boolean ok;
        if (row.getId() == null) {
            row.setCreateTime(now);
            ok = mapper.insert(row) > 0;
        } else {
            ok = mapper.updateById(row) > 0;
        }
        invalidate();
        return ok;
    }

    /** 判重时排除自己（编辑时不改值也不算重复） */
    private boolean isDuplicateSelf(Long id, String domain, String groupCode,
                                    String itemKey, String matchValue) {
        try {
            DataItemConfig old = mapper.selectById(id);
            if (old == null) {
                return false;
            }
            String og = old.getGroupCode() == null ? "" : old.getGroupCode().trim();
            return domain.equals(trim(old.getDomain()))
                    && groupCode.equals(og)
                    && itemKey.equals(trim(old.getItemKey()))
                    && matchValue.equals(trim(old.getMatchValue()));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean setStatus(Long id, Integer status) {
        if (id == null) {
            throw new BizException(400, "配置 ID 不能为空");
        }
        DataItemConfig row = new DataItemConfig();
        row.setId(id);
        row.setStatus(status == null || status == 0 ? 0 : 1);
        row.setUpdateTime(LocalDateTime.now());
        boolean ok = mapper.updateById(row) > 0;
        invalidate();
        return ok;
    }

    public boolean remove(Long id) {
        if (id == null) {
            throw new BizException(400, "配置 ID 不能为空");
        }
        boolean ok = mapper.deleteById(id) > 0;
        invalidate();
        return ok;
    }

    // ------------------------------------------------------------------
    // 现场辅助：候选值 / 试跑
    // ------------------------------------------------------------------

    /** 候选值：从 ICU 源库拉真实存在的项目（按使用人数倒序），现场不用猜编码 */
    public List<Map<String, Object>> candidates(String domain, String keyword) {
        return candidates(domain, null, keyword);
    }

    /**
     * 候选值（带 itemKey）。
     *
     * <p>DICT 域的候选来源取决于键：体温是观察项字典，其余都是 LIS 项目名。
     * 分不清就会给现场列出「体温」这个词在观察项里的 3 行记录，看着对、其实用不上。
     */
    public List<Map<String, Object>> candidates(String domain, String itemKey, String keyword) {
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String dom = StringUtils.hasText(domain) ? domain.trim() : DOMAIN_VITAL;
        String start = LocalDateTime.now().minusDays(90).format(DT);
        if (DOMAIN_IO.equals(dom)) {
            return icuPatientMapper.selectIoItemCandidates(kw);
        }
        if (DOMAIN_LAB.equals(dom)) {
            return icuPatientMapper.selectLisItemCandidates(kw, start);
        }
        if (DOMAIN_DICT.equals(dom)) {
            return DICT_TEMP_ITEM.equals(itemKey)
                    ? icuPatientMapper.selectObserveItemCandidates(kw)
                    : icuPatientMapper.selectLisItemCandidates(kw, start);
        }
        if (DOMAIN_DRUG.equals(dom)) {
            return icuPatientMapper.selectDrugNameCandidates(kw);
        }
        if (DOMAIN_DIAG.equals(dom)) {
            return icuPatientMapper.selectDiagNameCandidates(kw);
        }
        return icuPatientMapper.selectObserveItemCandidates(kw);
    }

    /**
     * 试跑：按当前匹配方式在源库统计命中条数 + 返回最新 5 条样例。
     *
     * <p>现场填完编码最怕「保存成功但一条也取不到」，这个接口让人在配置页当场看到
     * 「近 7 天命中 12,438 条 / 最新 36.8℃」，不用切到业务页面碰运气。
     */
    public Map<String, Object> probe(String domain, String value, Integer days) {
        return probe(domain, null, value, days);
    }

    /** 试跑（带 itemKey）：DICT 域按不同键去不同表统计 */
    public Map<String, Object> probe(String domain, String itemKey, String value, Integer days) {
        String dom = StringUtils.hasText(domain) ? domain.trim() : DOMAIN_VITAL;
        String v = StringUtils.hasText(value) ? value.trim() : null;
        if (!StringUtils.hasText(v)) {
            throw new BizException(400, "请先填写匹配值");
        }
        int d = days == null ? 7 : Math.max(1, Math.min(days, 90));
        String start = LocalDateTime.now().minusDays(d).format(DT);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("domain", dom);
        out.put("value", v);
        out.put("days", d);
        out.put("startTime", start);
        if (DOMAIN_DICT.equals(dom)) {
            return probeDict(out, itemKey, v, start);
        }
        if (DOMAIN_DRUG.equals(dom)) {
            return probeList(out, icuPatientMapper.countAdviceByDrugName(v, start), "条医嘱");
        }
        if (DOMAIN_DIAG.equals(dom)) {
            // 诊断表没有可靠的时间窗列（diag_time 存在空值），只给全量命中数
            return probeList(out, icuPatientMapper.countDiagByName(v), "条诊断");
        }
        if (DOMAIN_IO.equals(dom)) {
            out.put("count", icuPatientMapper.countIoByCode(v, start));
            out.put("samples", icuPatientMapper.selectIoSamples(v, start));
        } else if (DOMAIN_LAB.equals(dom)) {
            out.put("count", icuPatientMapper.countLisByKeyword(v, start));
            out.put("samples", icuPatientMapper.selectLisSamples(v, start));
        } else {
            out.put("count", icuPatientMapper.countObserveByCode(v, start));
            out.put("samples", icuPatientMapper.selectObserveSamples(v, start));
        }
        return out;
    }

    /** 只统计不返样例的试跑结果（药名 / 诊断名这类没有「值」可看的对象） */
    private Map<String, Object> probeList(Map<String, Object> out, int count, String unit) {
        out.put("count", count);
        out.put("unit", unit);
        out.put("samples", Collections.emptyList());
        return out;
    }

    /**
     * 术语字典试跑：不同键落在不同的表 / 列上，命中数含义也不同。
     *
     * <p>「血气分析」按报告名统计得到的是报告份数，「动脉血」按标本统计也是报告份数，
     * 「氧合指数」按项目名统计是检验明细条数 —— 三个数不能横向比较，页面文案要分开看。
     */
    private Map<String, Object> probeDict(Map<String, Object> out, String itemKey, String v, String start) {
        String key = StringUtils.hasText(itemKey) ? itemKey.trim() : DICT_CULTURE_ITEM;
        out.put("itemKey", key);
        if (DICT_TEMP_ITEM.equals(key)) {
            // 观察项字典是「每患者一份配置」，这里统计的是字典行数，不是体温条数
            out.put("count", icuPatientMapper.countObserveItemByName(v));
            out.put("unit", "条观察项定义");
        } else if (DICT_GAS_SAMPLE.equals(key)) {
            out.put("count", icuPatientMapper.countLisSpecimenByKeyword(v, start));
            out.put("unit", "份报告");
        } else if (DICT_GAS_REPORT.equals(key) || DICT_CULTURE_REPORT.equals(key)) {
            out.put("count", icuPatientMapper.countLisReportByKeyword(v, start));
            out.put("unit", "份报告");
        } else {
            out.put("count", icuPatientMapper.countLisByKeyword(v, start));
            out.put("samples", icuPatientMapper.selectLisSamples(v, start));
            out.put("unit", "条明细");
        }
        if (!out.containsKey("samples")) {
            out.put("samples", Collections.emptyList());
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 匹配值拆分：逗号、中文逗号、竖线都算分隔符 */
    public static List<String> split(String raw) {
        List<String> out = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return out;
        }
        for (String s : raw.split("[,，|]")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
