package com.zing.doctor.module.antibiotic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zing.doctor.module.antibiotic.entity.AbxDrugDict;
import com.zing.doctor.module.antibiotic.mapper.AbxDrugDictMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 抗菌药物统一识别器 —— 全系统「这条医嘱/药品是不是抗菌药」的唯一判定入口。
 *
 * <h3>为什么需要它</h3>
 * 抗菌药识别原来在多个模块各写一份：脓毒症集束化（config_abx_word 白/黑名单）、
 * 医生交班览表（硬编码 61 个通用名数组）、当前抗菌药（硬编码 46 个关键词）。
 * 三份词表互不同步，新药/商品名/复方制剂极易漏判。本类把判定统一为一条链路，
 * 并以 HIS 药品字典（{@code is_antibiotics='1'} 同步至 config_abx_drug_dict）为权威依据。
 *
 * <h3>判定顺序（三级）</h3>
 * <ol>
 *   <li><b>黑名单优先排除</b>（config_abx_word.non_antibiotic）：电解质/营养/镇静镇痛/
 *       外用剂型等非抗菌药，命中即判为「非抗菌药」，即使字典或白名单命中也不认；</li>
 *   <li><b>药品字典精确匹配</b>（config_abx_drug_dict）：按 药品名 / 简称 / 通用名，
 *       以及「去商品名括号 + 去剂型词」的基名匹配，命中即判为抗菌药；</li>
 *   <li><b>白名单包含匹配</b>（config_abx_word.broad_spectrum）：兜底，
 *       覆盖字典尚未同步到的新药。</li>
 * </ol>
 *
 * <h3>「抗菌药」与「广谱抗菌药」是两个不同口径（务必区分）</h3>
 * <ul>
 *   <li>{@link #isAntibiotic}：是否抗菌药 → 字典 ∪ 白名单，再扣黑名单。
 *       用于「当前抗菌药」「在用抗菌药」「抗菌药开始时间」等<b>列表展示与时间节点</b>；</li>
 *   <li>{@link #isBroadSpectrum}：是否<b>广谱</b>抗菌药 → <b>仅白名单</b>（扣黑名单）。
 *       用于脓毒症集束化「1H 内使用广谱抗菌药」这类<b>有考核口径</b>的判定——
 *       甲硝唑、克林霉素虽属抗菌药但非广谱，若用字典口径会虚增达标率。</li>
 * </ul>
 *
 * <h3>性能与并发</h3>
 * 判定完全走内存快照（{@code volatile} 整体替换，读无锁），单次判定为 O(词表规模) 的
 * 字符串包含匹配，可安全用于「逐条医嘱」的循环场景；快照每 5 分钟或按需刷新。
 * 由于不产生数据库访问，本类可在 {@code @DS("icu")} 的 Service 内安全调用（不会污染数据源上下文）。
 */
@Slf4j
@Service
public class AbxDrugRecognizer {

    /** 词类型：广谱抗菌药白名单 */
    public static final String WORD_TYPE_BROAD = "broad_spectrum";

    /** 词类型：非抗菌药黑名单 */
    public static final String WORD_TYPE_NON = "non_antibiotic";

    /** 快照刷新间隔（毫秒）：5 分钟 */
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000L;

    /** 商品名括号：如 盐酸克林霉素胶囊(特丽仙) → 盐酸克林霉素胶囊 */
    private static final Pattern PAREN_PATTERN = Pattern.compile("[（(][^）)]*[）)]");

    /** 剂型/给药途径词：用于把「注射用哌拉西林钠他唑巴坦钠」归一化为「哌拉西林钠他唑巴坦钠」 */
    private static final List<String> FORM_TOKENS = Arrays.asList(
            "注射用", "注射液", "无菌粉末", "粉针剂", "粉针", "冻干粉针", "输液", "口服溶液", "口服液", "口服",
            "分散片", "咀嚼片", "缓释片", "控释片", "肠溶片", "薄膜衣片", "含片", "泡腾片", "舌下片",
            "颗粒剂", "颗粒", "干混悬剂", "混悬液", "胶囊", "软胶囊", "微丸",
            "滴眼液", "滴耳液", "滴鼻液", "眼膏", "乳膏", "软膏", "凝胶剂", "凝胶", "喷雾剂", "吸入剂",
            "气雾剂", "栓剂", "贴剂", "洗剂", "溶液剂", "溶液", "散剂", "片", "丸", "散", "栓", "贴");

    @Autowired(required = false)
    private AbxDrugDictMapper abxDrugDictMapper;

    @Autowired
    private AbxWordConfigService abxWordConfigService;

    /** 当前生效的词表快照（整体替换保证并发安全） */
    private volatile Snapshot snapshot = Snapshot.builtin();

    /** 上次刷新时间戳（毫秒）；失败也刷新，避免 DB 异常时每条医嘱都重试 */
    private volatile long lastRefreshAt = 0L;

    // ------------------------------------------------------------------
    // 对外判定 API
    // ------------------------------------------------------------------

    /**
     * 是否抗菌药物（字典 ∪ 白名单，黑名单优先排除）。
     * 用于「当前抗菌药」「在用抗菌药」等列表展示与抗菌药开始时间识别。
     */
    public boolean isAntibiotic(String adviceName) {
        if (isBlank(adviceName)) {
            return false;
        }
        Snapshot s = current();
        if (containsAny(s.nonAntibiotic, adviceName)) {
            return false;
        }
        return matchDict(s, adviceName) || containsAny(s.broadSpectrum, adviceName);
    }

    /**
     * 是否<b>广谱</b>抗菌药物（仅白名单，黑名单优先排除）。
     * 用于脓毒症集束化「1H 内使用广谱抗菌药」等考核口径判定，语义与旧实现保持一致。
     */
    public boolean isBroadSpectrum(String adviceName) {
        if (isBlank(adviceName)) {
            return false;
        }
        Snapshot s = current();
        if (containsAny(s.nonAntibiotic, adviceName)) {
            return false;
        }
        return containsAny(s.broadSpectrum, adviceName);
    }

    /**
     * 是否非抗菌药物（黑名单命中）。
     * 名称空白时返回 {@code true}（无法识别的一律不计入抗菌药）。
     */
    public boolean isNonAntibiotic(String adviceName) {
        if (isBlank(adviceName)) {
            return true;
        }
        return containsAny(current().nonAntibiotic, adviceName);
    }

    /** 是否命中 HIS 药品字典（精确名/基名，不含关键词包含匹配）——用于排查"为什么被判为抗菌药" */
    public boolean matchDrugDict(String adviceName) {
        if (isBlank(adviceName)) {
            return false;
        }
        return matchDict(current(), adviceName);
    }

    /** 词库快照概况（页面展示/排查用） */
    public Map<String, Object> snapshotInfo() {
        Snapshot s = snapshot;
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("dictDrugCount", s.dictDrugCount);
        info.put("dictNameCount", s.dictExactNames.size());
        info.put("broadSpectrumCount", s.broadSpectrum.size());
        info.put("nonAntibioticCount", s.nonAntibiotic.size());
        info.put("dictFromDb", s.dictDrugCount > 0);
        info.put("refreshTime", lastRefreshAt > 0 ? new java.util.Date(lastRefreshAt) : null);
        return info;
    }

    /** 强制刷新词库快照（同步任务写库后、或页面手动触发时调用） */
    public void refresh() {
        refresh(false);
    }

    /** 定时兜底刷新：每 5 分钟一次，保证页面改词条、夜间同步后能被自动感知 */
    @Scheduled(fixedDelayString = "300000", initialDelayString = "60000")
    public void scheduledRefresh() {
        refresh(false);
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private Snapshot current() {
        if (System.currentTimeMillis() - lastRefreshAt > REFRESH_INTERVAL_MS) {
            refresh(false);
        }
        return snapshot;
    }

    private synchronized void refresh(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastRefreshAt < 1000L) {
            return;
        }
        try {
            Set<String> exactNames = new HashSet<>();
            Set<String> baseNames = new HashSet<>();
            int dictDrugCount = 0;
            if (abxDrugDictMapper != null) {
                List<AbxDrugDict> dicts = abxDrugDictMapper.selectList(
                        new LambdaQueryWrapper<AbxDrugDict>()
                                .eq(AbxDrugDict::getStatus, 1)
                                .eq(AbxDrugDict::getDelFlag, 0));
                if (dicts != null) {
                    for (AbxDrugDict d : dicts) {
                        collectName(exactNames, baseNames, d.getDrugName());
                        collectName(exactNames, baseNames, d.getDrugShortName());
                        collectName(exactNames, baseNames, d.getDrugNormalName());
                    }
                    dictDrugCount = dicts.size();
                }
            }
            List<String> broad = abxWordConfigService.listEnabledKeywords(WORD_TYPE_BROAD);
            List<String> non = abxWordConfigService.listEnabledKeywords(WORD_TYPE_NON);
            snapshot = new Snapshot(
                    exactNames,
                    baseNames,
                    (broad == null || broad.isEmpty()) ? Arrays.asList(DEFAULT_BROAD_SPECTRUM_KEYWORDS) : broad,
                    (non == null || non.isEmpty()) ? Arrays.asList(DEFAULT_NON_ANTIBIOTIC_KEYWORDS) : non,
                    dictDrugCount);
            lastRefreshAt = now;
            log.info("[抗菌药识别] 词库刷新完成：字典药品={}条/名称={}个，白名单={}条，黑名单={}条",
                    dictDrugCount, exactNames.size(), snapshot.broadSpectrum.size(), snapshot.nonAntibiotic.size());
        } catch (Exception e) {
            // 刷新失败：保留上一版快照继续服务，5 分钟内不重试（避免逐条医嘱反复打 DB）
            lastRefreshAt = now;
            log.warn("[抗菌药识别] 词库刷新失败，继续使用上一版快照：{}", e.getMessage());
        }
    }

    private void collectName(Set<String> exactNames, Set<String> baseNames, String raw) {
        if (raw == null) {
            return;
        }
        String name = raw.replace(" ", "").replace("\u3000", "").trim();
        if (name.isEmpty()) {
            return;
        }
        exactNames.add(name);
        String base = baseName(name);
        if (!base.isEmpty() && !base.equals(name)) {
            baseNames.add(base);
        }
    }

    /**
     * 归一化基名：去商品名括号 + 去剂型词。
     * 例：「注射用哌拉西林钠他唑巴坦钠」→「哌拉西林钠他唑巴坦钠」；
     * 「盐酸克林霉素胶囊(特丽仙)」→「盐酸克林霉素」。
     */
    private static String baseName(String name) {
        if (name == null) {
            return "";
        }
        String s = name.replace('（', '(').replace('）', ')');
        s = PAREN_PATTERN.matcher(s).replaceAll("");
        for (String token : FORM_TOKENS) {
            if (s.contains(token)) {
                s = s.replace(token, "");
            }
        }
        return s.trim();
    }

    /** 字典匹配：原始名 / 归一化名 / 基名 三者任一精确命中即算抗菌药 */
    private boolean matchDict(Snapshot s, String adviceName) {
        if (s.dictExactNames.isEmpty() && s.dictBaseNames.isEmpty()) {
            return false;
        }
        String raw = adviceName.replace(" ", "").replace("\u3000", "").trim();
        if (s.dictExactNames.contains(raw)) {
            return true;
        }
        String base = baseName(raw);
        if (!base.isEmpty() && (s.dictExactNames.contains(base) || s.dictBaseNames.contains(base))) {
            return true;
        }
        return false;
    }

    private static boolean containsAny(List<String> keywords, String name) {
        for (String kw : keywords) {
            if (kw != null && !kw.isEmpty() && name.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 词表快照（不可变） */
    private static final class Snapshot {

        /** 字典精确名（药品名/简称/通用名，已去空格） */
        final Set<String> dictExactNames;
        /** 字典基名（去商品名括号 + 去剂型词） */
        final Set<String> dictBaseNames;
        /** 广谱白名单关键词 */
        final List<String> broadSpectrum;
        /** 非抗菌药黑名单关键词 */
        final List<String> nonAntibiotic;
        /** 字典药品条数（0 表示字典未同步，识别降级为纯关键词匹配） */
        final int dictDrugCount;

        Snapshot(Set<String> dictExactNames, Set<String> dictBaseNames,
                 List<String> broadSpectrum, List<String> nonAntibiotic, int dictDrugCount) {
            this.dictExactNames = dictExactNames;
            this.dictBaseNames = dictBaseNames;
            this.broadSpectrum = broadSpectrum;
            this.nonAntibiotic = nonAntibiotic;
            this.dictDrugCount = dictDrugCount;
        }

        /** 内置兜底快照：字典与配置表都不可用时的最小可用词表 */
        static Snapshot builtin() {
            return new Snapshot(Collections.emptySet(), Collections.emptySet(),
                    Arrays.asList(DEFAULT_BROAD_SPECTRUM_KEYWORDS),
                    Arrays.asList(DEFAULT_NON_ANTIBIOTIC_KEYWORDS), 0);
        }
    }

    // ------------------------------------------------------------------
    // 内置兜底词表（config_abx_word / config_abx_drug_dict 均无数据时使用）
    // 原实现散落在 SepsisBundleServiceImpl，统一收敛到此处，避免多份词表互相漂移。
    // ------------------------------------------------------------------

    /** 广谱抗菌药白名单（内置默认） */
    private static final String[] DEFAULT_BROAD_SPECTRUM_KEYWORDS = {
            "哌拉西林", "头孢哌酮", "头孢他啶", "头孢吡肟", "美罗培南", "亚胺培南",
            "比阿培南", "厄他培南", "左氧氟沙星", "莫西沙星", "环丙沙星", "万古霉素",
            "利奈唑胺", "替考拉宁", "达托霉素", "阿米卡星", "妥布霉素", "庆大霉素",
            "甲硝唑", "替硝唑", "氟康唑", "伏立康唑", "卡泊芬净", "米卡芬净",
            "两性霉素", "青霉素", "苄星青霉素", "阿莫西林", "阿莫西林克拉维酸", "头孢唑林",
            "头孢呋辛", "头孢克洛", "头孢丙烯", "头孢克肟", "头孢地尼", "头孢唑肟",
            "头孢噻肟", "头孢曲松", "头孢美唑", "头孢比罗酯", "氨曲南", "阿奇霉素",
            "克拉霉素", "罗红霉素", "克林霉素", "多西环素", "替加环素", "依拉环素",
            "异帕米星", "奥硝唑", "左奥硝唑", "吗啉硝唑", "呋喃妥因", "磷霉素",
            "利福平", "利福昔明", "伊曲康唑", "氟胞嘧啶", "特比萘芬", "艾沙康唑",
            "多黏菌素"
    };

    /** 非抗菌药黑名单（内置默认） */
    private static final String[] DEFAULT_NON_ANTIBIOTIC_KEYWORDS = {
            // 电解质/酸碱
            "氯化钾", "碳酸氢钠", "葡萄糖酸钙", "硫酸镁", "磷酸钠", "枸橼酸钾",
            "乳酸钠", "醋酸钠", "甘油磷酸钠", "门冬氨酸钾",
            // 维生素
            "维生素", "维C", "VC", "VB", "复合维",
            // 营养支持
            "脂肪乳", "氨基酸", "白蛋白", "肠内营养", "安素", "能全力", "百普力",
            "短肽", "整蛋白", "谷氨酰胺", "丙氨酰", "ω-3", "欧米伽", "鱼油",
            // 抑酸/胃肠
            "奥美拉唑", "泮托拉唑", "兰索拉唑", "雷贝拉唑", "艾司奥美拉唑", "埃索美拉唑",
            "罗沙替丁", "法莫替丁", "西咪替丁", "雷尼替丁", "尼扎替丁",
            "生长抑素", "奥曲肽", "加贝酯", "乌司他丁",
            "乳果糖", "开塞露", "蒙脱石", "双歧杆菌", "枯草杆菌", "地衣芽孢",
            "多潘立酮", "甲氧氯普胺", "莫沙必利", "铝碳酸镁", "碳酸钙", "复方消化酶",
            // 镇静/麻醉
            "丙泊酚", "咪达唑仑", "依托咪酯", "七氟烷", "异氟烷", "地氟烷",
            "右美托咪定", "氯胺酮", "戊巴比妥", "硫喷妥", "水合氯醛", "苯巴比妥",
            // 镇痛
            "瑞芬太尼", "芬太尼", "舒芬太尼", "吗啡", "布托啡诺", "地佐辛", "曲马多",
            "氢吗啡酮", "羟考酮", "纳布啡", "喷他佐辛", "美沙酮", "哌替啶", "可待因",
            // 肌松
            "阿曲库铵", "维库溴铵", "罗库溴铵", "泮库溴铵", "琥珀胆碱", "米库溴铵",
            // 抗凝/抗血小板/溶栓
            "肝素", "华法林", "利伐沙班", "达比加群", "阿司匹林", "氯吡格雷", "替格瑞洛",
            "替罗非班", "比伐卢定", "尿激酶", "阿替普酶", "链激酶", "达肝素", "亭扎肝素",
            "枸橼酸", "枸橼酸钠", "血液滤过", "置换液", "透析", "血液灌流", "血液净化", "血浆置换", "连续性肾脏替代", "CRRT",
            "磺达肝癸", "阿哌沙班", "替卡格雷",
            // 血管活性/升压/降压
            "去甲肾上腺素", "间羟胺", "多巴胺", "多巴酚丁胺", "肾上腺素", "异丙肾上腺素",
            "去氧肾上腺素", "垂体后叶", "血管加压素", "特利加压素", "硝普钠", "硝酸甘油",
            "硝酸异山梨酯", "乌拉地尔", "酚妥拉明", "艾司洛尔", "美托洛尔", "比索洛尔",
            "硝苯地平", "氨氯地平", "非洛地平", "缬沙坦", "氯沙坦", "厄贝沙坦", "贝那普利",
            "培哚普利", "卡托普利", "依那普利",
            "多沙唑嗪", "特拉唑嗪", "替米沙坦", "坎地沙坦", "奥美沙坦", "阿利沙坦",
            "尼卡地平", "尼莫地平", "拉贝洛尔", "卡维地洛", "可乐定", "甲基多巴", "肼屈嗪",
            // 抗心律失常/强心
            "胺碘酮", "利多卡因", "普罗帕酮", "维拉帕米", "地尔硫卓", "阿托品",
            "去乙酰毛花苷", "西地兰", "毒毛花苷", "洋地黄", "多非利特", "伊布利特",
            // 利尿/脱水
            "呋塞米", "托拉塞米", "螺内酯", "氢氯噻嗪", "布美他尼", "甘露醇",
            "乙酰唑胺", "吲达帕胺",
            // 止吐
            "昂丹司琼", "格拉司琼", "托烷司琼", "雷莫司琼", "阿扎司琼", "多拉司琼", "甲氧氯普胺",
            // 解毒/拮抗
            "纳洛酮", "氟马西尼", "戊乙奎醚", "亚甲蓝", "硫代硫酸钠", "依地酸", "青霉胺", "二巯丙醇", "N-乙酰半胱氨酸",
            // 化痰平喘
            "氨溴索", "乙酰半胱氨酸", "溴己新", "氨茶碱", "多索茶碱", "沙丁胺醇",
            "异丙托溴铵", "布地奈德", "特布他林", "茶碱",
            // 造影剂
            "泛影葡胺", "碘海醇", "碘帕醇", "碘佛醇", "碘普罗胺", "碘克沙醇",
            "钆", "优维显", "欧乃派克", "造影",
            // 激素
            "地塞米松", "甲泼尼龙", "氢化可的松", "泼尼松", "泼尼松龙", "倍他米松",
            "甲强龙", "强的松", "促肾上腺皮质",
            // 止血/血液制品
            "氨甲环酸", "凝血酶", "维生素K", "卡络磺钠", "酚磺乙胺", "血凝酶",
            "纤维蛋白原", "冷沉淀", "血小板",
            // 内分泌/代谢
            "胰岛素", "格列", "二甲双胍", "生长激素", "甲状腺", "左甲状腺",
            "阿卡波糖", "西格列汀", "达格列净", "恩格列净", "利拉鲁肽", "度拉糖肽", "艾塞那肽",
            // 降脂
            "阿托伐他汀", "瑞舒伐他汀", "辛伐他汀", "普伐他汀", "氟伐他汀", "匹伐他汀",
            "依折麦布", "非诺贝特", "吉非罗齐", "普罗布考",
            // 抗组胺/抗过敏
            "西替利嗪", "氯雷他定", "异丙嗪", "依巴斯汀", "苯海拉明", "氯苯那敏",
            "非索非那定", "地氯雷他定", "左西替利嗪", "酮替芬", "赛庚啶", "氯马斯汀",
            "扑尔敏", "息斯敏",
            // 血容量扩张（人工胶体）
            "明胶", "羟乙基淀粉", "聚明胶肽", "右旋糖酐", "佳乐施", "血定安", "万汶",
            // 胆碱酯酶抑制剂/神经肌肉接头药
            "新斯的明", "溴吡斯的明", "加兰他敏", "多奈哌齐", "卡巴拉汀", "安贝氯铵",
            // 抗精神病/抗癫痫/镇静类精神药物
            "氟哌啶醇", "奥氮平", "喹硫平", "利培酮", "氯氮平", "氟哌噻吨", "奋乃静",
            "舒必利", "阿立哌唑", "帕利哌酮", "齐拉西酮", "丙戊酸", "卡马西平", "苯妥英",
            "左乙拉西坦", "拉莫三嗪", "托吡酯", "奥卡西平", "加巴喷丁", "普瑞巴林",
            "舍曲林", "帕罗西汀", "氟西汀", "艾司西酞普兰", "文拉法辛", "度洛西汀",
            "阿普唑仑", "地西泮", "劳拉西泮", "艾司唑仑", "氯硝西泮", "丁螺环酮", "米氮平",
            "曲唑酮", "左旋多巴", "苯海索", "金刚烷胺",
            // 外用/皮肤科
            "炉甘石", "氧化锌", "洗剂", "软膏", "乳膏", "栓剂", "贴剂", "滴眼", "滴鼻", "滴耳",
            // 抗病毒（非抗菌药）
            "利巴韦林", "奥司他韦", "帕拉米韦", "阿昔洛韦", "更昔洛韦", "伐昔洛韦",
            "泛昔洛韦", "恩替卡韦", "替诺福韦", "拉米夫定", "阿德福韦", "干扰素",
            "利托那韦", "奈玛特韦", "玛巴洛沙韦",
            // 保肝/脑循环/营养神经
            "甘草酸", "水飞蓟", "双环醇", "多烯磷脂酰胆碱", "胞磷胆碱", "脑苷肌肽",
            "神经节苷脂", "依达拉奉", "奥拉西坦", "吡拉西坦", "小牛血",
            // 其他
            "银杏", "丹参", "血塞通", "疏血通", "醒脑静", "参附", "参麦", "生脉",
            "磷酸肌酸", "辅酶", "三磷酸腺苷", "门冬氨酸鸟氨酸", "还原型谷胱甘肽",
            "水溶性维生素", "脂溶性维生素",
            "丁苯酞", "奥扎格雷", "前列地尔", "左卡尼汀", "果糖二磷酸", "磷酸肌酸钠", "单唾液酸四己糖神经节苷脂",
            // 局麻/口腔护理/外用消毒
            "达克罗宁", "布比卡因", "罗哌卡因", "普鲁卡因", "丁卡因", "苯佐卡因", "利多卡因",
            "含漱", "漱口", "西吡氯铵", "氯己定", "聚维酮碘", "碘伏", "碘甘油", "锡类散", "冰硼散", "西瓜霜",
            // 中药/外敷
            "芒硝", "冰片", "金黄散", "青黛", "云南白药", "伤科灵", "正骨水", "红花油",
            // 微量元素/电解质营养
            "微量元素", "多种微量元素", "安达美", "门冬氨酸钾镁", "葡萄糖酸锌", "硫酸锌", "硒", "亚硒酸钠", "含D3"
    };

    /** 供 Service 层复用的只读视图（调试用） */
    public List<String> currentBroadSpectrumKeywords() {
        return new ArrayList<>(current().broadSpectrum);
    }

    /** 供 Service 层复用的只读视图（调试用） */
    public List<String> currentNonAntibioticKeywords() {
        return new ArrayList<>(current().nonAntibiotic);
    }
}
