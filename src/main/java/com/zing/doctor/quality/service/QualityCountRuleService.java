package com.zing.doctor.quality.service;

import com.zing.doctor.quality.dto.PeriodRange;
import com.zing.doctor.quality.entity.QualityCountRule;
import com.zing.doctor.quality.entity.QualityMetricResult;
import com.zing.doctor.quality.mapper.QualityCountRuleMapper;
import com.zing.doctor.quality.mapper.QualityMetricResultMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * 口径待 ICU 侧确认的原子项。
     *
     * <p>这三条在我们 YAML 里自带「率」语义（quality_15 是 AVG、quality_30/31 是 RATE 且 unit 为 %），
     * 但 ICU 侧又拿它们去做分子与另一个原子项相除 —— 一个已经是百分率的值再除一次，
     * 数量级对不上。合理的推测是源端存的是「病死率之和」而非「率」，但这是推测。
     *
     * <p>处理：照公式如实计算，但打上 {@code pendingConfirm} 标记，
     * 由页面给出降级提示（不参与达标判定、不进汇总高亮），等 ICU 侧确认后再去掉。
     * 这样其余 59 条不受阻塞。
     */
    private static final Set<String> PENDING_CONFIRM_CODES = new HashSet<>(Arrays.asList(
            "quality_15", "quality_30", "quality_31"));

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

    public QualityCountRuleService(QualityCountRuleMapper ruleMapper,
                                   QualityMetricResultMapper resultMapper) {
        this.ruleMapper = ruleMapper;
        this.resultMapper = resultMapper;
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
        String dept = departCode == null || departCode.trim().isEmpty() ? "ALL" : departCode.trim();

        List<QualityCountRule> rules = includeHidden ? ruleMapper.selectAll() : ruleMapper.selectForBoard();

        // 一次取齐本周期本科室的全部原子项结果，避免 N 次单查
        List<QualityMetricResult> results = resultMapper.selectByPeriod(range.getStart(), dept);
        Map<String, QualityMetricResult> byCode = new HashMap<>();
        for (QualityMetricResult r : results) {
            if (r.getMetricCode() != null) {
                byCode.put(r.getMetricCode(), r);
            }
        }

        List<Map<String, Object>> out = new ArrayList<>(rules.size());
        for (QualityCountRule rule : rules) {
            out.add(assemble(rule, byCode, range, dept));
        }
        return out;
    }

    /** 从 ICU 侧同步规则（幂等）。返回新增条数。 */
    public int sync() {
        try {
            int n = ruleMapper.syncFromIcu();
            log.info("[质控] 同步 quality_count_rule 完成，新增 {} 条", n);
            return n;
        } catch (Exception e) {
            log.error("[质控] 同步 quality_count_rule 失败", e);
            throw new IllegalArgumentException("从 ICU 同步指标规则失败: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------

    private Map<String, Object> assemble(QualityCountRule rule,
                                         Map<String, QualityMetricResult> byCode,
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
        m.put("numeratorName", num == null ? null : num.getMetricName());
        m.put("denominatorName", den == null ? null : den.getMetricName());
        m.put("percentRate", rule.getPercentRate());
        m.put("percentPrecision", rule.getPercentPrecision());
        // percentUnit 取自 ICU 侧但不可直接当单位：rate=1000 的两条那里仍写 '%'
        m.put("displayUnit", displayUnit(rule));
        m.put("isShowPage", rule.getIsShowPage());
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

        // 达标判定刻意留白：不猜方向。
        // 质控指标既有「越高越好」（完成率、预防率）也有「越低越好」（发病率、病死率、重返率），
        // 而源表没有方向字段，凭名字猜会判反 —— 判反比不判更糟（会误导临床）。
        // 待业务明确后可在本表加 target_direction 字段再启用。
        m.put("judge", "NONE");

        m.put("periodType", range.getPeriodType());
        m.put("periodStart", range.getStart());
        m.put("periodEnd", range.getEnd());
        m.put("departCode", dept);
        return m;
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
