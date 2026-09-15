package com.zing.doctor.quality.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.quality.dto.PeriodRange;
import com.zing.doctor.quality.entity.QualityCalcRun;
import com.zing.doctor.quality.service.QualityCalcService;
import com.zing.doctor.quality.service.QualityExportService;
import com.zing.doctor.quality.service.QualityCountRuleService;
import com.zing.doctor.quality.service.QualityIndexSyncService;
import com.zing.doctor.quality.service.QualityMonthlyService;
import com.zing.doctor.quality.service.QualityQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 质控指标中台接口。
 *
 * <p>与老系统「每个指标一个页面、一个接口」不同，这里是<b>一套通用接口服务全部 127 条指标</b>：
 * 指标差异全部由配置表达，接口无需随指标增减而变。
 *
 * <p>请求需携带外链鉴权头（由 ExternalLinkInterceptor 统一校验，见 {@code WebConfig}）。
 */
@Slf4j
@RestController
@RequestMapping("/api/quality")
public class QualityController {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final QualityQueryService queryService;
    private final QualityCalcService calcService;
    private final QualityMonthlyService monthlyService;
    private final QualityExportService exportService;
    private final QualityIndexSyncService indexSyncService;
    private final QualityCountRuleService countRuleService;

    public QualityController(QualityQueryService queryService, QualityCalcService calcService,
                             QualityMonthlyService monthlyService, QualityExportService exportService,
                             QualityIndexSyncService indexSyncService,
                             QualityCountRuleService countRuleService) {
        this.queryService = queryService;
        this.calcService = calcService;
        this.monthlyService = monthlyService;
        this.exportService = exportService;
        this.indexSyncService = indexSyncService;
        this.countRuleService = countRuleService;
    }

    /**
     * 指标看板：127 行按域分组 + 本期值。
     * 空壳指标同样返回，前端数值列显示「—」，页面始终完整。
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(@RequestParam(required = false) String periodType,
                                                @RequestParam(required = false) String periodStart,
                                                @RequestParam(required = false) String departCode) {
        try {
            return Result.ok(queryService.overview(periodType, periodStart, departCode));
        } catch (Exception e) {
            log.error("[质控] 看板查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /**
     * 真正的业务指标列表：quality_count_rule 组装视图。
     *
     * <p>与 {@code /overview} 的区别要讲清楚 —— {@code /overview} 列的是<strong>原子项</strong>
     * （quality_xxx，一个「多少人 / 多少天」的量，自身成不了率），
     * 这里列的才是质控要管的<strong>指标</strong>：{@code 值 = 分子 ÷ 分母 × 放大系数}。
     *
     * <p>值不重算 SQL：分子分母的值已由引擎落在 quality_metric_result，此处只做一次除法。
     *
     * <p>口径待确认的指标（分子/分母含 quality_15/30/31）照常返回并带
     * {@code pendingConfirm=true}，由页面降级提示，不阻塞其余指标。
     */
    @GetMapping("/rules")
    public Result<List<Map<String, Object>>> rules(@RequestParam(required = false) String periodType,
                                                   @RequestParam(required = false) String periodStart,
                                                   @RequestParam(required = false) String departCode,
                                                   @RequestParam(required = false) boolean includeHidden) {
        try {
            return Result.ok(countRuleService.list(periodType, periodStart, departCode, includeHidden));
        } catch (Exception e) {
            log.error("[质控] 指标规则查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /**
     * 从 ICU 侧同步指标规则（幂等，只插入 rule_id 尚不存在的）。
     *
     * <p><b>关于写保护</b>：这是写操作，但没纳入 {@code QualityConfigWriteInterceptor}
     * （该拦截器只覆盖 {@code /api/quality/config/**}）。这里刻意不扩大拦截范围 ——
     * 同步是幂等的「只增不删」，且覆盖不了本院已配置的 target/warning 四列，
     * 风险等级远低于改口径；真要卡死这个口子，等有明确要求再扩。
     */
    @PostMapping("/rules/sync")
    public Result<Map<String, Object>> syncRules() {
        try {
            int n = countRuleService.sync();
            // 项目是 Java 8，不能用 Map.of（Java 9+）
            Map<String, Object> data = new HashMap<>();
            data.put("added", n);
            return Result.ok(data);
        } catch (Exception e) {
            log.error("[质控] 同步指标规则失败", e);
            return Result.fail("同步失败: " + e.getMessage());
        }
    }

    /** 单指标详情：口径 + 本期值 + 血缘摘要 + 患者明细。 */
    @GetMapping("/metric")
    public Result<Map<String, Object>> metric(@RequestParam String code,
                                              @RequestParam(required = false) String periodStart,
                                              @RequestParam(required = false) String departCode) {
        try {
            return Result.ok(queryService.metricDetail(code, periodStart, departCode));
        } catch (Exception e) {
            log.error("[质控] 指标详情失败: code={}", code, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /** 追溯第 3 层：算子链 + 编译后 SQL + 扫描行数。 */
    @GetMapping("/metric/trace")
    public Result<Map<String, Object>> trace(@RequestParam String runId,
                                            @RequestParam String code) {
        try {
            return Result.ok(queryService.trace(runId, code));
        } catch (Exception e) {
            log.error("[质控] 追溯查询失败: code={}", code, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /** 追溯第 4 层：患者级命中明细（按需生成，点数字看到人）。 */
    @GetMapping("/metric/patients")
    public Result<Map<String, Object>> patients(@RequestParam String code,
                                                @RequestParam(required = false) String periodStart,
                                                @RequestParam(required = false) String departCode) {
        try {
            return Result.ok(queryService.patients(code, periodStart, departCode));
        } catch (Exception e) {
            log.error("[质控] 患者明细失败: code={}", code, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /** 覆盖率报告：127 条的「可算 / 空壳 / 待接数据源」承诺清单。 */
    @GetMapping("/coverage")
    public Result<Map<String, Object>> coverage() {
        try {
            return Result.ok(queryService.coverage());
        } catch (Exception e) {
            log.error("[质控] 覆盖率查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /** 事实层定义与编译后 SQL：改 YAML 前先在页面上确认口径。 */
    @GetMapping("/facts")
    public Result<List<Map<String, Object>>> facts(@RequestParam(required = false) String periodStart) {
        try {
            return Result.ok(queryService.facts(periodStart));
        } catch (Exception e) {
            log.error("[质控] 事实层查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /** 最近计算批次（血缘第 1 层）。 */
    @GetMapping("/runs")
    public Result<List<QualityCalcRun>> runs() {
        try {
            return Result.ok(queryService.recentRuns());
        } catch (Exception e) {
            log.error("[质控] 批次查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /**
     * 触发一次计算（幂等：同周期同科室重算覆盖）。
     *
     * @param async        true = 立即返回 runId，用 {@code GET /run} 轮询进度（页面请用这个）；
     *                     false = 同步跑完才返回（定时任务等无人值守场景）
     * @param withPatients 是否顺带预落库患者明细；默认 false，页面下钻时按需生成更快
     */
    @PostMapping("/recalc")
    public Result<Map<String, Object>> recalc(@RequestParam(required = false) String periodType,
                                              @RequestParam(required = false) String periodStart,
                                              @RequestParam(required = false) String departCode,
                                              @RequestParam(required = false) String startTime,
                                              @RequestParam(required = false) String endTime,
                                              @RequestParam(required = false, defaultValue = "false") Boolean withPatients,
                                              @RequestParam(required = false, defaultValue = "false") Boolean async,
                                              @RequestParam(required = false, defaultValue = "MANUAL") String triggerType,
                                              @RequestParam(required = false, defaultValue = "manual") String operator) {
        try {
            PeriodRange range = PeriodRange.of(periodType, periodStart);
            LocalDateTime s = startTime == null ? range.getStart() : parse(startTime, range.getStart());
            LocalDateTime e = endTime == null ? range.getEnd() : parse(endTime, range.getEnd());
            String dept = departCode == null ? "ALL" : departCode;
            if (Boolean.TRUE.equals(async)) {
                return Result.ok(calcService.submit(range.getPeriodType(), s, e, dept,
                        triggerType, operator, Boolean.TRUE.equals(withPatients)));
            }
            return Result.ok(calcService.recalc(range.getPeriodType(), s, e, dept,
                    triggerType, operator, Boolean.TRUE.equals(withPatients)));
        } catch (Exception e) {
            log.error("[质控] 计算触发失败", e);
            return Result.fail("计算失败: " + e.getMessage());
        }
    }

    /**
     * 查询批次进度，配合 {@code POST /recalc?async=true} 轮询。
     *
     * <p>返回里的 {@code running=false} 即终态，此时 ok/fail/placeholder/durationMs 才是结论。
     */
    @GetMapping("/run")
    public Result<Map<String, Object>> run(@RequestParam String runId) {
        try {
            return Result.ok(calcService.getRun(runId));
        } catch (Exception e) {
            log.error("[质控] 批次进度查询失败: runId={}", runId, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /**
     * 单指标重算：只算这一条，秒级返回。
     *
     * <p>解决「只改了一条指标的口径，却要等 127 条全跑完」的问题。
     * 返回里的 result 是该指标在当前筛选科室下的新值，页面可就地更新这一行。
     * 计算进行中（有批次在跑）时返回 calcStatus=BUSY，页面提示稍后重试即可。
     */
    @PostMapping("/recalc/metric")
    public Result<Map<String, Object>> recalcMetric(@RequestParam String code,
                                                    @RequestParam(required = false) String periodType,
                                                    @RequestParam(required = false) String periodStart,
                                                    @RequestParam(required = false) String departCode,
                                                    @RequestParam(required = false, defaultValue = "manual") String operator) {
        try {
            PeriodRange range = PeriodRange.of(periodType, periodStart);
            String dept = departCode == null || departCode.trim().isEmpty() ? "ALL" : departCode.trim();
            return Result.ok(calcService.recalcOne(code, range.getPeriodType(),
                    range.getStart(), range.getEnd(), dept, operator));
        } catch (Exception e) {
            log.error("[质控] 单指标重算失败: code={}", code, e);
            return Result.fail("计算失败: " + e.getMessage());
        }
    }

    /** 人工录入指标值（MANUAL 类指标）。 */
    @PostMapping("/manual")
    public Result<Map<String, Object>> manual(@RequestParam String code,
                                              @RequestParam BigDecimal value,
                                              @RequestParam(required = false) String periodType,
                                              @RequestParam(required = false) String periodStart,
                                              @RequestParam(required = false) String departCode,
                                              @RequestParam(required = false, defaultValue = "manual") String operator) {
        try {
            PeriodRange range = PeriodRange.of(periodType, periodStart);
            return Result.ok(calcService.saveManual(code, range.getPeriodType(),
                    range.getStart(), range.getEnd(), departCode, value, operator));
        } catch (Exception e) {
            log.error("[质控] 人工录入失败: code={}", code, e);
            return Result.fail("保存失败: " + e.getMessage());
        }
    }

    /** 月度汇总：1-12 月横排视图。 */
    @GetMapping("/monthly")
    public Result<Map<String, Object>> monthly(@RequestParam Integer year,
                                               @RequestParam(required = false) String departCode) {
        try {
            return Result.ok(monthlyService.view(year, departCode));
        } catch (Exception e) {
            log.error("[质控] 月度汇总查询失败: year={}", year, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /** 重建某年月度汇总宽表。 */
    @PostMapping("/monthly/rebuild")
    public Result<Map<String, Object>> rebuildMonthly(@RequestParam Integer year,
                                                      @RequestParam(required = false) String departCode) {
        try {
            return Result.ok(monthlyService.rebuild(year, departCode));
        } catch (Exception e) {
            log.error("[质控] 月度汇总重建失败: year={}", year, e);
            return Result.fail("重建失败: " + e.getMessage());
        }
    }

    /**
     * 重读配置并重新同步指标字典（改完 YAML 热生效，不必重启）。
     *
     * <p>这里是「重载 + 同步」两步，缺一不可：只同步字典不会让配置文件里的修改生效，
     * 因为 {@code sync()} 用的仍是内存中已加载的旧配置；必须先 {@code reload()} 重读文件。
     *
     * <p>前提是配置位于 jar 外部目录（见 {@code zing.quality.config-dir}）；
     * 若仍使用 classpath 打包配置，重读到的还是打包时的旧文件。
     */
    @PostMapping("/sync-index")
    public Result<Integer> syncIndex() {
        try {
            return Result.ok(indexSyncService.reloadAndSync());
        } catch (Exception e) {
            log.error("[质控] 字典同步失败", e);
            return Result.fail("同步失败: " + e.getMessage());
        }
    }

    /** 导出某年质控报表（xlsx，3 个 sheet，流式写出）。 */
    @GetMapping("/export")
    public void export(@RequestParam Integer year,
                       @RequestParam(required = false) String departCode,
                       HttpServletResponse response) {
        exportService.exportYear(response, year, departCode);
    }

    private LocalDateTime parse(String text, LocalDateTime def) {
        if (text == null || text.trim().isEmpty()) {
            return def;
        }
        try {
            String t = text.trim().replace('T', ' ');
            if (t.length() > 19) {
                t = t.substring(0, 19);
            }
            return t.length() == 10
                    ? LocalDateTime.parse(t + " 00:00:00", DT)
                    : LocalDateTime.parse(t, DT);
        } catch (Exception e) {
            return def;
        }
    }
}
