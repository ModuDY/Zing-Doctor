package com.zing.doctor.quality.service;

import com.zing.doctor.quality.dto.PeriodRange;
import com.zing.doctor.quality.entity.QualityCountRule;
import com.zing.doctor.quality.entity.QualityIndex;
import com.zing.doctor.quality.entity.QualityMetricResult;
import com.zing.doctor.quality.mapper.QualityCountRuleMapper;
import com.zing.doctor.quality.mapper.QualityIndexMapper;
import com.zing.doctor.quality.mapper.QualityMetricResultMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 质控「真正指标」的组装服务。
 *
 * <p>此前系统把 {@code quality_xxx} 叫成「指标」，但它其实只是原子项（一个量），
 * 自身成不了率。真正的指标在 ICU 侧是 {@code quality_count_rule}：
 *
 * <pre>
 *   指标值 = 原子项(numeratorCode) ÷ 原子项(denominatorCode) × percentRate
 * </pre>
 *
 * <p>本服务不重算任何 SQL —— 分子、分母的值已经由引擎算好并落在
 * {@code quality_metric_result} 里，这里只做一次除法。这是刻意的设计：
 * 原子项被大量复用（quality_449 被 17 条指标当分母、quality_403 被 13 条当分母），
 * 每层各自算一遍既慢又容易不一致。
 */
@Slf4j
@Service
public class QualityCountRuleService {

    /**
     * 口径待确认的原子项 —— 现已清空，留着是给下一个存疑项用的位置。
     *
     * <p>原列的是 quality_15 / quality_30 / quality_31，疑问是「源表存的是率还是率之和」。
     * 已与 ICU 侧确认（2026-09）：
     * <ul>
     *   <li><b>quality_15 = APACHEⅡ 评分的「预计死亡率之和」</b>——不是率的均值。
     *       所以它作为分子再除以人数是正确的，得出的才是「预计病死率」。
     *       本系统原先按 AVG 出数（和 ÷ 人数），等于是把率先算了一遍，
     *       再被规则除一次，才出现数量级对不上；已改为原样输出「和」。</li>
     *   <li><b>quality_31 = ICU 患者预计病死率</b>、<b>quality_30 = ICU 实际病死率</b>，
     *       这两条本身就是率（人工录入），不再当原子量参与二次相除。</li>
     * </ul>
     * 三者语义已明确，故不再打 pendingConfirm 标记。
     */
    private static final Set<String> PENDING_CONFIRM_CODES = Collections.emptySet();

    /**
     * 可以作为分子/分母使用的结果状态。
     *
     * <p>只认 OK 与 MANUAL（人工录入是真值）。<b>刻意排除 PLACEHOLDER</b> ——
     * 占位值是源表还没接入时造出来的（如恒 0），拿它去除会得到一个看着像样、
     * 实则编造的率。质控数据宁可显示「—」，也不能给临床一个假数。
     */
    private static final Set<String> USABLE_STATUS = new HashSet<>(Arrays.asList("OK", "MANUAL"));

    private final QualityCountRuleMapper ruleMapper;
    private final QualityMetricResultMapper resultMapper;
    private final QualityIndexMapper indexMapper;
    private final QualityCalcService calcService;

    public QualityCountRuleService(QualityCountRuleMapper ruleMapper,
                                   QualityMetricResultMapper resultMapper,
                                   QualityIndexMapper indexMapper,
                                   QualityCalcService calcService) {
        this.ruleMapper = ruleMapper;
        this.resultMapper = resultMapper;
        this.indexMapper = indexMapper;
        this.calcService = calcService;
    }

    /**
     * 看板取数：真指标列表 + 本期值。
     *
     * @param periodType  周期类型 MONTH/QUARTER/YEAR/CUSTOM
     * @param periodStart 周期起点字符串（PeriodRange.of 解析）；为空则取上个月
     * @param departCode  科室，为空按全院 ALL
     * @param includeHidden 是否包含 is_show_page=0 的规则（配置页用 true，看板用 false）
     */
    public List<Map<String, Object>> list(String periodType, String periodStart,
                                          String departCode, boolean includeHidden) {
        PeriodRange range = PeriodRange.of(periodType, periodStart);
        String dept = deptOf(departCode);

        List<QualityCountRule> rules = includeHidden ? ruleMapper.selectAll() : ruleMapper.selectForBoard();
        Map<String, QualityMetricResult> byCode = loadResults(range, dept);
        Map<String, QualityIndex> indexByCode = loadIndex();

        List<Map<String, Object>> out = new ArrayList<>(rules.size());
        for (QualityCountRule rule : rules) {
            out.add(assemble(rule, byCode, indexByCode, range, dept));
        }
        return out;
    }

    /**
     * 单条规则的组装视图。
     *
     * <p>配完目标值、算完一条指标之后回吐给页面，页面就地更新这一行，
     * 不必把整张表重新拉一遍（127 条指标里改一条就全量刷新，既慢又会丢掉滚动位置）。
     */
    public Map<String, Object> one(String ruleId, String periodType, String periodStart, String departCode) {
        PeriodRange range = PeriodRange.of(periodType, periodStart);
        String dept = deptOf(departCode);
        return assemble(requireRule(ruleId), loadResults(range, dept), loadIndex(), range, dept);
    }

    /**
     * 配置本院自管的目标值 / 预警值（传 null 即清空）。
     *
     * <p>只写这两列，其余一概不动：本院配的目标值与源端同步互不干扰 ——
     * 同步的 INSERT 不覆盖已有行，刷新逻辑也刻意不填这两列。
     */
    public Map<String, Object> saveTarget(String ruleId, BigDecimal targetValue, BigDecimal warningValue,
                                          String targetDirection, String periodType, String periodStart,
                                          String departCode) {
        requireRule(ruleId);
        // 走显式 UPDATE 而非 updateById：传 null 清空时，updateById 会因为
        // 「只更新非 null 字段」而整列跳过，目标值就再也删不掉。
        String direction = normalizeDirection(targetDirection);
        ruleMapper.updateTarget(ruleId, targetValue, warningValue, direction);
        log.info("[质控] 配置指标目标值: ruleId={} target={} warning={} direction={}",
                ruleId, targetValue, warningValue, direction);
        return one(ruleId, periodType, periodStart, departCode);
    }

    // ==================================================================
    // 本院自建规则（origin=LOCAL）
    //
    // 为什么要有这一组：原先规则只能从 ICU 侧同步 —— 本院想加一条自己关心的
    // 「分子 ÷ 分母」，必须等重症侧先建好，等不来就没法做。这里开放本地自建，
    // 并用 origin 标记 + LOCAL_ 前缀与同步链路彻底隔离，互不干扰。
    // ==================================================================

    /**
     * 配置页列表：含未上板与已停用。
     *
     * <p>与 {@link #list} 的区别是不需要周期参数、不算本期值 ——
     * 配置关心的是「这条规则怎么定义的」，点开看数字是看板的事。
     */
    public List<Map<String, Object>> listForConfig() {
        List<QualityCountRule> rules = ruleMapper.selectAllForConfig();
        Map<String, QualityIndex> indexByCode = loadIndex();
        List<Map<String, Object>> out = new ArrayList<>(rules.size());
        for (QualityCountRule r : rules) {
            out.add(configView(r, indexByCode));
        }
        return out;
    }

    /**
     * 新增或编辑一条规则。
     *
     * <p>{@code ruleId} 为空 = 新增，由服务端分配 {@code LOCAL_n}；非空 = 编辑既有规则。
     * 编辑 ICU 来源的规则时会自动把 {@code localOverride} 置 1 —— 这是「跟随源端」
     * 与「本院自管」的分界点：一旦本院改过口径，此后的同步不再覆盖分子/分母/放大系数。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveRule(QualityCountRule form, String operator) {
        validateRule(form);

        QualityCountRule exist = StringUtils.hasText(form.getRuleId())
                ? ruleMapper.selectByRuleId(form.getRuleId().trim())
                : null;
        boolean isNew = exist == null;
        QualityCountRule row = isNew ? new QualityCountRule() : exist;

        row.setCountName(form.getCountName().trim());
        row.setQualityTypeCode(form.getQualityTypeCode());
        row.setNumeratorCode(form.getNumeratorCode().trim());
        row.setDenominatorCode(form.getDenominatorCode().trim());
        row.setPercentRate(form.getPercentRate() == null ? 100 : form.getPercentRate());
        row.setPercentPrecision(form.getPercentPrecision() == null ? 2 : form.getPercentPrecision());
        row.setPercentUnit(StringUtils.hasText(form.getPercentUnit())
                ? form.getPercentUnit().trim()
                : (row.getPercentRate() == 1000 ? "例/千日" : "%"));
        row.setIsShowPage(form.getIsShowPage() == null ? 1 : form.getIsShowPage());
        row.setSortNo(form.getSortNo() == null ? nextSortNo() : form.getSortNo());
        row.setRemark(form.getRemark());
        row.setDepartCode(form.getDepartCode());
        row.setTargetValue(form.getTargetValue());
        row.setWarningValue(form.getWarningValue());
        row.setTargetDirection(normalizeDirection(form.getTargetDirection()));
        // 本院一动口径就标记 override，之后同步不再覆盖它
        row.setLocalOverride(1);

        if (isNew) {
            row.setRuleId(nextLocalRuleId());
            row.setOrigin("LOCAL");
            row.setStatus(1);
            row.setIsVisible(1);
            row.setSourceStatus(1);
            row.setSyncTime(LocalDateTime.now());
            ruleMapper.insert(row);
            log.info("[质控] 新增指标规则: ruleId={} name={} {} ÷ {} × {} (operator={})",
                    row.getRuleId(), row.getCountName(), row.getNumeratorCode(),
                    row.getDenominatorCode(), row.getPercentRate(), operator);
        } else {
            // 口径列显式全量写（updateById 会跳过 null，清空备注/系数就再也删不掉）
            ruleMapper.updateRuleCaliber(row);
            // target/warning 不在口径列里，单独写，避免编辑口径时把已配好的目标值抹掉
            ruleMapper.updateTarget(row.getRuleId(), row.getTargetValue(), row.getWarningValue(),
                    normalizeDirection(row.getTargetDirection()));
            log.info("[质控] 更新指标规则: ruleId={} name={} {} ÷ {} × {} (operator={})",
                    row.getRuleId(), row.getCountName(), row.getNumeratorCode(),
                    row.getDenominatorCode(), row.getPercentRate(), operator);
        }
        return configView(ruleMapper.selectByRuleId(row.getRuleId()), loadIndex());
    }

    /** 启用 / 停用一条规则。停用不删除，历史结果仍可按 ruleId 回溯。 */
    public Map<String, Object> setRuleEnabled(String ruleId, boolean enabled) {
        QualityCountRule rule = requireRule(ruleId);
        ruleMapper.updateRuleStatus(rule.getRuleId(), enabled ? 1 : 0);
        log.info("[质控] 指标规则{}: ruleId={}", enabled ? "启用" : "停用", rule.getRuleId());
        return configView(ruleMapper.selectByRuleId(rule.getRuleId()), loadIndex());
    }

    /** 配置页视图：只回规则定义，不带本期值。 */
    private Map<String, Object> configView(QualityCountRule r, Map<String, QualityIndex> indexByCode) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ruleId", r.getRuleId());
        m.put("countName", r.getCountName());
        m.put("qualityTypeCode", r.getQualityTypeCode());
        m.put("numeratorCode", r.getNumeratorCode());
        m.put("numeratorName", nameOf(indexByCode, r.getNumeratorCode(), null));
        m.put("denominatorCode", r.getDenominatorCode());
        m.put("denominatorName", nameOf(indexByCode, r.getDenominatorCode(), null));
        m.put("percentRate", r.getPercentRate());
        m.put("percentPrecision", r.getPercentPrecision());
        m.put("percentUnit", r.getPercentUnit());
        m.put("displayUnit", displayUnit(r));
        m.put("isShowPage", r.getIsShowPage());
        m.put("sortNo", r.getSortNo());
        m.put("remark", r.getRemark());
        m.put("departCode", r.getDepartCode());
        m.put("targetValue", r.getTargetValue());
        m.put("warningValue", r.getWarningValue());
        m.put("targetDirection", normalizeDirection(r.getTargetDirection()));
        m.put("origin", isLocal(r) ? "LOCAL" : "ICU");
        m.put("localOverride", r.getLocalOverride() == null ? 0 : r.getLocalOverride());
        m.put("status", r.getStatus() == null ? 1 : r.getStatus());
        return m;
    }

    /**
     * 校验规则口径。报错信息里带上是「分子」还是「分母」，并指向下一步动作 ——
     * 只回一句「原子项不存在」，使用者不知道是去新增原子项还是去同步字典。
     */
    private void validateRule(QualityCountRule form) {
        if (form == null) {
            throw new IllegalArgumentException("缺少规则内容");
        }
        if (!StringUtils.hasText(form.getCountName())) {
            throw new IllegalArgumentException("指标名称不能为空");
        }
        if (!StringUtils.hasText(form.getNumeratorCode())) {
            throw new IllegalArgumentException("请选择分子原子项");
        }
        if (!StringUtils.hasText(form.getDenominatorCode())) {
            throw new IllegalArgumentException("请选择分母原子项");
        }
        String num = form.getNumeratorCode().trim();
        String den = form.getDenominatorCode().trim();
        if (num.equals(den)) {
            throw new IllegalArgumentException("分子与分母不能是同一个原子项");
        }
        Map<String, QualityIndex> indexByCode = loadIndex();
        // 字典只含启用项：查不到既可能是「没建过」，也可能是「建了但被停用」，
        // 文案要把两种可能都说清楚，否则使用者会去重复新增一个已存在的原子项。
        if (!indexByCode.containsKey(num)) {
            throw new IllegalArgumentException("分子原子项不可用: " + num
                    + "（字典里没有或已停用；请先在「指标配置」新增并启用该原子项，再点「重载配置」同步字典）");
        }
        if (!indexByCode.containsKey(den)) {
            throw new IllegalArgumentException("分母原子项不可用: " + den
                    + "（字典里没有或已停用；请先在「指标配置」新增并启用该原子项，再点「重载配置」同步字典）");
        }
        Integer rate = form.getPercentRate();
        if (rate != null && rate != 1 && rate != 100 && rate != 1000) {
            throw new IllegalArgumentException("放大系数只能是 1（原样）/ 100（百分比）/ 1000（例每千日）");
        }
        Integer precision = form.getPercentPrecision();
        if (precision != null && (precision < 0 || precision > 6)) {
            throw new IllegalArgumentException("小数位数应在 0-6 之间");
        }
    }

    /**
     * 生成下一条本院规则编号：{@code LOCAL_} + 序号。
     *
     * <p><b>必须与 ICU 的数字 id 空间隔离</b>：同步判重是
     * {@code src."id" NOT IN (SELECT rule_id ...)}，本地编号一旦与源端 id 相撞，
     * 那条 ICU 规则会永远同步不进来，而且不报任何错。
     */
    private String nextLocalRuleId() {
        int base = ruleMapper.countLocalRules();
        for (int i = 0; i < 1000; i++) {
            String candidate = "LOCAL_" + (base + 1 + i);
            if (ruleMapper.countByRuleId(candidate) == 0) {
                return candidate;
            }
        }
        // 连续空洞极多时退化为时间戳，保证唯一
        return "LOCAL_" + System.currentTimeMillis();
    }

    /** 本院自建规则的默认排序号：排在现有规则之后，不插队到 ICU 指标中间。 */
    private int nextSortNo() {
        int max = 0;
        for (QualityCountRule r : ruleMapper.selectAllForConfig()) {
            if (r.getSortNo() != null && r.getSortNo() > max) {
                max = r.getSortNo();
            }
        }
        return max + 10;
    }

    private static boolean isLocal(QualityCountRule r) {
        return r != null && "LOCAL".equalsIgnoreCase(r.getOrigin());
    }

    private static boolean isOverridden(QualityCountRule r) {
        return r != null && r.getLocalOverride() != null && r.getLocalOverride() == 1;
    }

    /**
     * 计算单条指标：把它依赖的分子、分母原子项各重算一遍，再除出指标值。
     *
     * <p>与「整批重算」的区别：只跑这一条指标用到的两个原子项，秒级返回，
     * 点完就能看到这一行的新值，不必等 127 条全跑完。
     *
     * <p>返回的 steps 说清每一步做了什么（算了 / 跳过 / 忙 / 出错）——
     * 分子算成功、分母被跳过这类情况，不给明细就只能看到一个「—」，
     * 使用者没法判断该去补数据还是该改口径。
     */
    public Map<String, Object> calcRule(String ruleId, String periodType, String periodStart,
                                        String departCode, String operator) {
        QualityCountRule rule = requireRule(ruleId);
        PeriodRange range = PeriodRange.of(periodType, periodStart);
        String dept = deptOf(departCode);
        Map<String, QualityIndex> indexByCode = loadIndex();

        Map<String, Object> steps = new LinkedHashMap<>();
        steps.put("numerator", recalcAtom(rule.getNumeratorCode(), indexByCode, range, dept, operator));
        steps.put("denominator", recalcAtom(rule.getDenominatorCode(), indexByCode, range, dept, operator));

        Map<String, Object> out = assemble(rule, loadResults(range, dept), indexByCode, range, dept);
        out.put("steps", steps);
        return out;
    }

    /**
     * 从 ICU 侧同步规则（幂等）。
     *
     * <p>两步走：
     * <ol>
     *   <li>{@code INSERT…SELECT} 补进 rule_id 尚不存在的新规则；</li>
     *   <li>比对刷新已存在的规则 —— 源端改名、改分子分母、把 is_show_page 从 0 放开为 1，
     *       以前都同步不过来，于是页面上就「少指标」。ICU 实际病死率正是这一类的典型：
     *       源端把它设成 is_show_page=0，同步进来却被看板过滤掉，看上去像没同步。</li>
     * </ol>
     *
     * <p>返回值带上 sourceTotal / localTotal / hiddenCount，是为了让「少没少」能被直接看见，
     * 而不是靠人去数两边各多少条。
     */
    public Map<String, Object> sync() {
        int added;
        try {
            added = ruleMapper.syncFromIcu();
        } catch (Exception e) {
            log.error("[质控] 同步 quality_count_rule 失败", e);
            throw new IllegalArgumentException("从 ICU 同步指标规则失败: " + e.getMessage());
        }

        List<Map<String, Object>> src = readIcuSource();
        int updated = src == null ? 0 : refreshExisting(src);

        List<QualityCountRule> all = ruleMapper.selectAll();
        int hidden = 0;
        for (QualityCountRule r : all) {
            if (r.getIsShowPage() != null && r.getIsShowPage() == 0) {
                hidden++;
            }
        }

        log.info("[质控] 同步 quality_count_rule 完成：新增 {} 条，刷新 {} 条，本地共 {} 条（其中 {} 条源端设为不上板）",
                added, updated, all.size(), hidden);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("added", added);
        res.put("updated", updated);
        res.put("sourceTotal", src == null ? null : src.size());
        res.put("localTotal", all.size());
        res.put("hiddenCount", hidden);
        return res;
    }

    // ------------------------------------------------------------------

    private static String deptOf(String departCode) {
        return departCode == null || departCode.trim().isEmpty() ? "ALL" : departCode.trim();
    }

    /** 一次取齐本周期本科室的全部原子项结果，避免 N 次单查。 */
    private Map<String, QualityMetricResult> loadResults(PeriodRange range, String dept) {
        Map<String, QualityMetricResult> byCode = new HashMap<>();
        List<QualityMetricResult> results = resultMapper.selectByPeriod(range.getPeriodType(), range.getStart(), dept);
        if (results == null) {
            return byCode;
        }
        for (QualityMetricResult r : results) {
            if (r.getMetricCode() != null) {
                byCode.put(r.getMetricCode(), r);
            }
        }
        return byCode;
    }

    /**
     * 原子项字典（code → 定义）。
     *
     * <p>名称取自字典而不是结果行：结果行只有算过才有名字，
     * 而本期还没算过的原子项同样要在页面上显示名称 —— 使用者看的是指标，不是 code。
     */
    private Map<String, QualityIndex> loadIndex() {
        Map<String, QualityIndex> byCode = new HashMap<>();
        List<QualityIndex> all = indexMapper.selectAllOrdered();
        if (all == null) {
            return byCode;
        }
        for (QualityIndex i : all) {
            if (i.getIndexCode() != null) {
                byCode.put(i.getIndexCode(), i);
            }
        }
        return byCode;
    }

    private QualityCountRule requireRule(String ruleId) {
        if (ruleId == null || ruleId.trim().isEmpty()) {
            throw new IllegalArgumentException("缺少 ruleId");
        }
        QualityCountRule rule = ruleMapper.selectByRuleId(ruleId.trim());
        if (rule == null) {
            throw new IllegalArgumentException("指标规则不存在: " + ruleId);
        }
        return rule;
    }

    /**
     * 重算一个原子项。
     *
     * <p>人工录入项（implStatus=MANUAL）<b>不重算</b>：它的真值是人填的，
     * 重算只会拿占位值把人填的数盖掉 —— 那是数据倒退，不是更新。
     */
    private Map<String, Object> recalcAtom(String code, Map<String, QualityIndex> indexByCode,
                                           PeriodRange range, String dept, String operator) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("code", code);
        if (code == null || code.trim().isEmpty()) {
            step.put("done", false);
            step.put("reason", "该规则未配置此原子项");
            return step;
        }
        QualityIndex idx = indexByCode.get(code);
        step.put("name", idx == null ? null : idx.getIndexName());
        if (idx == null) {
            step.put("done", false);
            step.put("reason", "指标字典里没有这个原子项，先点「同步字典」");
            return step;
        }
        if ("MANUAL".equalsIgnoreCase(String.valueOf(idx.getImplStatus()))) {
            step.put("done", false);
            step.put("reason", "人工录入项，保留人工值不重算");
            return step;
        }
        try {
            Map<String, Object> r = calcService.recalcOne(code, range.getPeriodType(),
                    range.getStart(), range.getEnd(), dept, operator);
            String st = r == null ? null : String.valueOf(r.get("calcStatus"));
            boolean busy = "BUSY".equals(st);
            step.put("done", !busy);
            step.put("busy", busy);
            step.put("calcStatus", st);
            if (busy) {
                step.put("reason", String.valueOf(r.get("errorMsg")));
            }
            return step;
        } catch (Exception e) {
            log.warn("[质控] 原子项重算失败: code={} {}", code, e.getMessage());
            step.put("done", false);
            step.put("calcStatus", "ERROR");
            step.put("reason", e.getMessage());
            return step;
        }
    }

    /** 原子项显示名：字典名 → 结果行名 → code（兜底仍给 code，比空白强）。 */
    private String nameOf(Map<String, QualityIndex> indexByCode, String code, QualityMetricResult r) {
        QualityIndex i = code == null ? null : indexByCode.get(code);
        if (i != null && i.getIndexName() != null && !i.getIndexName().trim().isEmpty()) {
            return i.getIndexName();
        }
        if (r != null && r.getMetricName() != null && !r.getMetricName().trim().isEmpty()) {
            return r.getMetricName();
        }
        return code;
    }

    /** 读源端规则；读失败返回 null（降级为只新增不刷新，不让新增也跟着失败）。 */
    private List<Map<String, Object>> readIcuSource() {
        try {
            return ruleMapper.selectIcuSource();
        } catch (Exception e) {
            log.warn("[质控] 读取 ICU 侧规则失败，本次只新增不刷新: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 刷新已存在规则的源端字段。
     *
     * <p>patch 里刻意不填 targetValue / warningValue 四列：MyBatis-Plus 的
     * updateById 跳过 null 字段，本院配好的目标值不会被源端的 NULL 冲掉。
     */
    private int refreshExisting(List<Map<String, Object>> src) {
        Map<String, QualityCountRule> localByRuleId = new HashMap<>();
        for (QualityCountRule r : ruleMapper.selectAll()) {
            if (r.getRuleId() != null) {
                localByRuleId.put(r.getRuleId().trim(), r);
            }
        }

        int n = 0;
        for (Map<String, Object> s : src) {
            String ruleId = strVal(val(s, "rule_id"));
            if (ruleId == null || ruleId.isEmpty()) {
                continue;
            }
            QualityCountRule local = localByRuleId.get(ruleId);
            if (local == null) {
                continue;
            }
            if (isLocal(local)) {
                // 本院自建规则不归源端管。正常情况 ruleId 带 LOCAL_ 前缀本就不会与源端的数字 id
                // 匹配上，这里再判一次是为了兜住「早期手工插入、没有前缀」的本地行 ——
                // 一旦被源端同名 id 命中，本院配的分子分母会被整条覆盖掉。
                continue;
            }

            QualityCountRule patch = new QualityCountRule();
            patch.setId(local.getId());
            patch.setCountName(strVal(val(s, "count_name")));
            patch.setQualityTypeCode(strVal(val(s, "quality_type_code")));
            patch.setIsVisible(intVal(val(s, "is_visible")));
            patch.setIsShowPage(intVal(val(s, "is_show_page")));
            patch.setSortNo(intVal(val(s, "sort_no")));
            patch.setRemark(strVal(val(s, "remark")));
            patch.setDepartCode(strVal(val(s, "depart_code")));
            patch.setSourceStatus(intVal(val(s, "status")));
            patch.setSyncTime(LocalDateTime.now());

            // 本院改过口径的行：名称、排序、是否上板仍跟随源端，但分子/分母/放大系数不再覆盖。
            // 否则本院好不容易改对的口径，会被下一次「同步指标规则」按源端旧值冲回去，
            // 而且只在源端值真的变了时才发生 —— 属于难排查的那一类问题。
            boolean keepCaliber = isOverridden(local);
            if (!keepCaliber) {
                patch.setNumeratorCode(strVal(val(s, "numerator_code")));
                patch.setDenominatorCode(strVal(val(s, "denominator_code")));
                patch.setPercentUnit(strVal(val(s, "percent_unit")));
                patch.setPercentRate(intVal(val(s, "percent_rate")));
                patch.setPercentPrecision(intVal(val(s, "percent_precision")));
            }

            if (changed(local, patch, keepCaliber)) {
                ruleMapper.updateById(patch);
                n++;
            }
        }
        return n;
    }

    /**
     * 比对是否需要刷新。
     *
     * @param skipCaliber true = 本院改过口径，不比较分子/分母/系数，
     *                    否则 patch 里这几个字段是 null，会被误判成「变了」而每次都白写一次库
     */
    private static boolean changed(QualityCountRule a, QualityCountRule b, boolean skipCaliber) {
        boolean base = !Objects.equals(a.getCountName(), b.getCountName())
                || !Objects.equals(a.getQualityTypeCode(), b.getQualityTypeCode())
                || !Objects.equals(a.getIsShowPage(), b.getIsShowPage())
                || !Objects.equals(a.getIsVisible(), b.getIsVisible())
                || !Objects.equals(a.getSortNo(), b.getSortNo())
                || !Objects.equals(a.getSourceStatus(), b.getSourceStatus())
                || !Objects.equals(a.getDepartCode(), b.getDepartCode())
                || !Objects.equals(a.getRemark(), b.getRemark());
        if (base || skipCaliber) {
            return base;
        }
        return !Objects.equals(a.getNumeratorCode(), b.getNumeratorCode())
                || !Objects.equals(a.getDenominatorCode(), b.getDenominatorCode())
                || !Objects.equals(a.getPercentUnit(), b.getPercentUnit())
                || !Objects.equals(a.getPercentRate(), b.getPercentRate())
                || !Objects.equals(a.getPercentPrecision(), b.getPercentPrecision());
    }

    /** 达梦驱动返回的列名大小写不固定，统一按列名大小写无关取值。 */
    private static Object val(Map<String, Object> row, String col) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        Object v = row.get(col);
        if (v != null) {
            return v;
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(col)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String strVal(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    /** 源端 percent_rate 等列可能是字符型（'100'），逐列转；空串按 null 处理。 */
    private static Integer intVal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------

    private Map<String, Object> assemble(QualityCountRule rule,
                                         Map<String, QualityMetricResult> byCode,
                                         Map<String, QualityIndex> indexByCode,
                                         PeriodRange range, String dept) {
        Map<String, Object> m = new LinkedHashMap<>();

        QualityMetricResult num = rule.getNumeratorCode() == null ? null : byCode.get(rule.getNumeratorCode());
        QualityMetricResult den = rule.getDenominatorCode() == null ? null : byCode.get(rule.getDenominatorCode());

        boolean pending = containsPending(rule);

        m.put("ruleId", rule.getRuleId());
        m.put("countName", rule.getCountName());
        m.put("sortNo", rule.getSortNo());
        m.put("remark", rule.getRemark());
        m.put("numeratorCode", rule.getNumeratorCode());
        m.put("denominatorCode", rule.getDenominatorCode());
        m.put("numeratorName", nameOf(indexByCode, rule.getNumeratorCode(), num));
        m.put("denominatorName", nameOf(indexByCode, rule.getDenominatorCode(), den));
        m.put("percentRate", rule.getPercentRate());
        m.put("percentPrecision", rule.getPercentPrecision());
        // percentUnit 取自 ICU 侧但不可直接当单位：rate=1000 的两条那里仍写 '%'
        m.put("displayUnit", displayUnit(rule));
        m.put("isShowPage", rule.getIsShowPage());
        m.put("sourceStatus", rule.getSourceStatus());
        m.put("targetValue", rule.getTargetValue());
        m.put("warningValue", rule.getWarningValue());
        m.put("pendingConfirm", pending);
        if (pending) {
            m.put("pendingReason", "分子/分母含口径待确认的原子项（quality_15/30/31 自带百分比语义），"
                    + "值按公式如实计算，待 ICU 侧确认源表语义后校准");
        }

        BigDecimal numVal = usable(num) ? num.getMetricValue() : null;
        BigDecimal denVal = usable(den) ? den.getMetricValue() : null;
        m.put("numeratorValue", numVal);
        m.put("denominatorValue", denVal);
        // 把原子项自身状态也带出去：分母「待接源表」和分母「本期为 0」
        // 在页面上都显示「—」，但排查时完全是两回事
        m.put("numeratorStatus", num == null ? null : num.getCalcStatus());
        m.put("denominatorStatus", den == null ? null : den.getCalcStatus());

        // 分子 / 分母各自的实现状态：前端据此在对应单元格给出「录入」入口。
        // 只给一个规则级合并状态不够用 —— 人工录入项的真值是人填的，
        // 使用者得知道到底哪一侧要录，否则只能自己猜。
        String numImpl = implOf(indexByCode, rule.getNumeratorCode());
        String denImpl = implOf(indexByCode, rule.getDenominatorCode());
        m.put("numeratorImplStatus", numImpl);
        m.put("denominatorImplStatus", denImpl);
        // 规则级实现状态：任一侧是人工录入 → MANUAL，操作列据此给出「录入」按钮。
        // 这个字段以前压根没有，前端 row.implStatus 恒为 undefined，
        // 「v-if="row.implStatus === 'MANUAL'"」永远不成立 —— 录入入口一直不显示。
        m.put("implStatus", "MANUAL".equals(numImpl) || "MANUAL".equals(denImpl) ? "MANUAL" : "IMPL");

        String status = "OK";
        BigDecimal value = null;
        if (numVal == null || denVal == null) {
            // 原子项还没算出真值（多为 PENDING_SOURCE 待接源表 / PLACEHOLDER 占位 / 本期未跑批）
            status = "NO_DATA";
        } else if (denVal.compareTo(BigDecimal.ZERO) == 0) {
            // 分母为 0：不是错误，是「本期没有分母人群」，避免除零把整页打挂
            status = "NO_DATA";
        } else {
            int rate = rule.getPercentRate() == null ? 100 : rule.getPercentRate();
            int prec = rule.getPercentPrecision() == null ? 2 : rule.getPercentPrecision();
            value = numVal.divide(denVal, 10, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(rate))
                    .setScale(prec, RoundingMode.HALF_UP);
        }
        m.put("value", value);
        m.put("calcStatus", status);

        // 达标判定：方向由 target_direction 显式给出，不再靠猜。
        // 质控指标既有「越高越好」（完成率、预防率）也有「越低越好」（发病率、病死率、重返率），
        // 同一个数值在两类指标上结论正好相反 —— 判反比不判更糟（会误导临床）。
        // 因此方向为空时判定落到 NONE，绝不默认一个方向替使用者下结论。
        Boolean higherBetter = higherBetterOf(rule);
        m.put("targetDirection", higherBetter == null ? null : (higherBetter ? "UP" : "DOWN"));
        m.put("judge", judge(value, rule.getTargetValue(), rule.getWarningValue(), higherBetter));

        m.put("periodType", range.getPeriodType());
        m.put("periodStart", range.getStart());
        m.put("periodEnd", range.getEnd());
        m.put("departCode", dept);
        return m;
    }

    /**
     * 达标判定。
     *
     * @return {@code OK} 达标 / {@code WARN} 未达目标但未跌破预警线 / {@code FAIL} 跌破预警线 /
     *         {@code NONE} 无法判定（本期无值、方向未配、或两个阈值都没配）
     *
     * <p>两个阈值<b>各自独立生效</b>：只配预警线也能判「是否跌破预警」，不强迫先填目标值 ——
     * 现场多数指标一开始只关心一条警戒线。
     *
     * <p>判定顺序固定为「先比目标、再比预警」：若有人把预警线填得比目标线还宽松，
     * 先判目标可以避免把已达标的行误标成预警。
     */
    private String judge(BigDecimal value, BigDecimal target, BigDecimal warning, Boolean higherBetter) {
        if (value == null || higherBetter == null || (target == null && warning == null)) {
            return "NONE";
        }
        if (target != null) {
            boolean reached = higherBetter ? value.compareTo(target) >= 0 : value.compareTo(target) <= 0;
            if (reached) {
                return "OK";
            }
        }
        if (warning != null) {
            boolean breached = higherBetter ? value.compareTo(warning) < 0 : value.compareTo(warning) > 0;
            return breached ? "FAIL" : "WARN";
        }
        // 只配了目标值且未达到：没有预警线可分档，直接算未达标
        return "FAIL";
    }

    /**
     * 是否「越高越好」；方向未配时返回 {@code null}。
     *
     * <p>刻意不在读侧兜底成 UP：库未升级（没有 target_direction 列值）时，
     * 兜底会把「越低越好」的发病率、病死率类指标判反 —— 判反比不判更糟。
     * 写入侧由 {@link #normalizeDirection} 保证新配的值一定有方向。
     */
    private Boolean higherBetterOf(QualityCountRule rule) {
        String d = rule.getTargetDirection() == null ? "" : rule.getTargetDirection().trim().toUpperCase();
        if ("UP".equals(d)) {
            return Boolean.TRUE;
        }
        if ("DOWN".equals(d)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /**
     * 达标方向兜底为 UP（写入侧）。
     *
     * <p>多数质控指标是「越高越好」（依从率、完成率、送检率），默认 UP 减少配置负担；
     * 越低越好的少数由使用者在页面上显式选一次。保证写入非空，避免出现
     * 「配了目标值却没有方向」这种只能不判的中间态（页面看着像坏了）。
     */
    private String normalizeDirection(String raw) {
        return "DOWN".equalsIgnoreCase(raw == null ? "" : raw.trim()) ? "DOWN" : "UP";
    }

    /**
     * 原子项的实现状态（字典表字段，由 DSL 配置同步而来）。
     *
     * <p>查不到给 null，前端显示「—」：字典里没有既可能是没建过、也可能是被停用，
     * 硬编成 IMPL 会让「待接源表」看起来像「已实现」。
     */
    private String implOf(Map<String, QualityIndex> indexByCode, String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        QualityIndex i = indexByCode.get(code);
        return i == null || i.getImplStatus() == null ? null : i.getImplStatus().trim().toUpperCase();
    }

    /** 原子项是否可作为分子/分母参与计算。 */
    private boolean usable(QualityMetricResult r) {
        return r != null && r.getMetricValue() != null
                && USABLE_STATUS.contains(String.valueOf(r.getCalcStatus()).trim().toUpperCase());
    }

    private boolean containsPending(QualityCountRule rule) {
        return PENDING_CONFIRM_CODES.contains(rule.getNumeratorCode())
                || PENDING_CONFIRM_CODES.contains(rule.getDenominatorCode());
    }

    /** rate=1000 实为「例/千日」，源表 percent_unit 仍写 '%'，不能照抄。 */
    private String displayUnit(QualityCountRule rule) {
        Integer rate = rule.getPercentRate();
        if (rate != null && rate == 1000) {
            return "‰";
        }
        if (rate != null && rate == 100) {
            return "%";
        }
        return rule.getPercentUnit() == null ? "" : rule.getPercentUnit();
    }
}
