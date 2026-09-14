package com.zing.doctor.quality.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.quality.config.QualityConfigGuard;
import com.zing.doctor.quality.config.QualityMetricImportRequest;
import com.zing.doctor.quality.dsl.FactDefinition;
import com.zing.doctor.quality.dsl.MetricDefinition;
import com.zing.doctor.quality.entity.QualityDefHistory;
import com.zing.doctor.quality.entity.QualityMetricDef;
import com.zing.doctor.quality.service.QualityConfigService;
import com.zing.doctor.quality.service.QualityConfigValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 质控指标可视化配置接口。
 *
 * <p>与 {@link QualityController}（只读看板）分开的原因：配置类接口会写库、会影响计算口径，
 * 风险等级与只读查询完全不同，分开后便于单独加权限、单独做审计。
 *
 * <p>所有写接口都受两层前置条件约束：
 * <ol>
 *   <li><b>能否改</b>：{@link QualityConfigGuard} 的 IP 白名单 / 写接口令牌，
 *       由 {@code QualityConfigWriteInterceptor} 在进入本类之前拦下（403）；</li>
 *   <li><b>改了是否生效</b>：{@code zing.quality.config-source=db}，
 *       真源为 yaml 时拒绝并说明原因（见 {@code QualityConfigService#writableGuard}）。</li>
 * </ol>
 * 两层都通过才真正落库，避免「保存成功但看板没变」这类静默失败。
 *
 * <p>调用前建议先看 {@link #status(HttpServletRequest)}，据此决定页面是「可编辑」还是「只读预览」。
 *
 * <p><b>操作人一律由服务端解析</b>（{@link QualityConfigGuard#operator}），不再接受前端传入的
 * operator 参数 —— 那等于让审计字段由被审计者自己填写。
 */
@Slf4j
@RestController
@RequestMapping("/api/quality/config")
public class QualityConfigController {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final QualityConfigService configService;
    private final QualityConfigValidator validator;
    private final QualityConfigGuard guard;

    public QualityConfigController(QualityConfigService configService,
                                   QualityConfigValidator validator,
                                   QualityConfigGuard guard) {
        this.configService = configService;
        this.validator = validator;
        this.guard = guard;
    }

    // ------------------------------------------------------------------
    // 状态
    // ------------------------------------------------------------------

    /**
     * 配置真源与可写状态：页面打开时先调它，决定是否禁用编辑。
     *
     * <p>把「写权限」也放在这里一并返回，是为了让页面<b>一进来就知道能不能改</b>。
     * 否则使用者会编辑半天、点保存才收到 403，是很伤信任的交互。
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status(HttpServletRequest request) {
        try {
            Map<String, Object> status = new LinkedHashMap<>(configService.status());
            String reason = guard.denyReason(QualityConfigGuard.clientIp(request),
                    request == null ? null : request.getHeader(QualityConfigGuard.HEADER_TOKEN));
            status.put("writeAllowed", reason == null);
            status.put("writeHint", reason);
            // 审计归属：让页面能把「本次操作将以谁的名义记入历史」显示出来
            status.put("operator", guard.operator(request));
            return Result.ok(status);
        } catch (Exception e) {
            log.error("[质控] 配置状态查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 指标
    // ------------------------------------------------------------------

    /** 指标列表（不含表达式正文，列表页无需要）。 */
    @GetMapping("/metrics")
    public Result<List<QualityMetricDef>> metrics(@RequestParam(required = false) String domain,
                                                  @RequestParam(required = false) String keyword) {
        try {
            return Result.ok(configService.listMetrics(domain, keyword));
        } catch (Exception e) {
            log.error("[质控] 指标列表查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /** 指标详情：返回可直接编辑的口径对象。 */
    @GetMapping("/metric")
    public Result<MetricDefinition> metric(@RequestParam String code) {
        try {
            MetricDefinition m = configService.metricDetail(code);
            return m == null ? Result.fail("指标不存在: " + code) : Result.ok(m);
        } catch (Exception e) {
            log.error("[质控] 指标详情查询失败: code={}", code, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /** 只校验不保存：供页面在编辑过程中随时点「校验」。 */
    @PostMapping("/metric/validate")
    public Result<QualityConfigValidator.ValidationResult> validateMetric(
            @RequestBody MetricDefinition metric,
            @RequestParam(required = false, defaultValue = "true") Boolean trial) {
        try {
            return Result.ok(validator.validateMetric(metric, Boolean.TRUE.equals(trial), null, null));
        } catch (Exception e) {
            log.error("[质控] 指标校验失败: code={}", metric == null ? null : metric.getCode(), e);
            return Result.fail("校验失败: " + e.getMessage());
        }
    }

    /**
     * 保存指标：校验 → 落库 → 留快照 → 热生效。
     *
     * <p>{@code trial=true} 时保存前会真跑一遍（默认开启）—— 静态检查过了不代表跑得动。
     */
    @PostMapping("/metric")
    public Result<QualityConfigService.SaveResult> saveMetric(
            @RequestBody MetricDefinition metric,
            @RequestParam(required = false, defaultValue = "true") Boolean trial,
            HttpServletRequest request) {
        String operator = guard.operator(request);
        try {
            return Result.ok(configService.saveMetric(metric, Boolean.TRUE.equals(trial), operator));
        } catch (Exception e) {
            log.error("[质控] 指标保存失败: code={}", metric == null ? null : metric.getCode(), e);
            return Result.fail("保存失败: " + e.getMessage());
        }
    }

    /** 停用指标（不删除，历史结果保留）。 */
    @PostMapping("/metric/disable")
    public Result<QualityConfigService.SaveResult> disableMetric(
            @RequestParam String code,
            HttpServletRequest request) {
        String operator = guard.operator(request);
        try {
            return Result.ok(configService.disableMetric(code, operator));
        } catch (Exception e) {
            log.error("[质控] 指标停用失败: code={}", code, e);
            return Result.fail("停用失败: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 批量导入 / 导出
    // ------------------------------------------------------------------

    /**
     * 导出全部生效中的指标口径（含表达式正文）。
     *
     * <p>返回 JSON 结构而非直接下发文件流：前端拿到后用 Blob 落盘，
     * 避免在 Result 统一封装之外再开一条文件下载通道（那会绕过 ResultStatusAdvice 的错误处理）。
     */
    @GetMapping("/metrics/export")
    public Result<Map<String, Object>> exportMetrics(HttpServletRequest request) {
        try {
            List<MetricDefinition> metrics = configService.exportMetrics();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "quality-metric-def");
            payload.put("formatVersion", 1);
            payload.put("exportTime", LocalDateTime.now().format(STAMP));
            payload.put("operator", guard.operator(request));
            payload.put("count", metrics.size());
            // 导出即快照：带上当时的真源，便于判断这份文件来自哪种环境
            payload.put("configSource", configService.status().get("configSource"));
            payload.put("metrics", metrics);
            return Result.ok(payload);
        } catch (Exception e) {
            log.error("[质控] 指标配置导出失败", e);
            return Result.fail("导出失败: " + e.getMessage());
        }
    }

    /**
     * 批量导入指标口径（部分成功语义：好的进去，坏的带原因返回）。
     *
     * <p>默认 {@code mode=skip}：宁可不动已有指标，也不要因为一份来路不明的文件
     * 把线上口径整体覆盖掉；确认无误后由使用者显式选 overwrite。
     */
    @PostMapping("/metrics/import")
    public Result<QualityConfigService.ImportResult> importMetrics(
            @RequestBody QualityMetricImportRequest body,
            HttpServletRequest request) {
        String operator = guard.operator(request);
        QualityMetricImportRequest req = body == null ? new QualityMetricImportRequest() : body;
        try {
            return Result.ok(configService.importMetrics(req.getMetrics(), req.getMode(),
                    req.isTrial(), operator));
        } catch (Exception e) {
            log.error("[质控] 指标配置导入失败", e);
            return Result.fail("导入失败: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 事实层
    // ------------------------------------------------------------------

    @GetMapping("/facts")
    public Result<List<FactDefinition>> facts() {
        try {
            return Result.ok(configService.listFacts());
        } catch (Exception e) {
            log.error("[质控] 事实层列表查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/fact")
    public Result<FactDefinition> fact(@RequestParam String fact) {
        try {
            FactDefinition f = configService.factDetail(fact);
            return f == null ? Result.fail("事实层不存在: " + fact) : Result.ok(f);
        } catch (Exception e) {
            log.error("[质控] 事实层详情查询失败: fact={}", fact, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/fact/validate")
    public Result<QualityConfigValidator.ValidationResult> validateFact(
            @RequestBody FactDefinition fact,
            @RequestParam(required = false, defaultValue = "true") Boolean trial) {
        try {
            return Result.ok(validator.validateFact(fact, Boolean.TRUE.equals(trial), null, null));
        } catch (Exception e) {
            log.error("[质控] 事实层校验失败: fact={}", fact == null ? null : fact.getFact(), e);
            return Result.fail("校验失败: " + e.getMessage());
        }
    }

    @PostMapping("/fact")
    public Result<QualityConfigService.SaveResult> saveFact(
            @RequestBody FactDefinition fact,
            @RequestParam(required = false, defaultValue = "true") Boolean trial,
            HttpServletRequest request) {
        String operator = guard.operator(request);
        try {
            return Result.ok(configService.saveFact(fact, Boolean.TRUE.equals(trial), operator));
        } catch (Exception e) {
            log.error("[质控] 事实层保存失败: fact={}", fact == null ? null : fact.getFact(), e);
            return Result.fail("保存失败: " + e.getMessage());
        }
    }

    /**
     * 事实层可引用列 —— 简单模式的字段下拉数据源。
     *
     * <p>让使用者从下拉里选列，而不是手打列名去撞校验报错，这是简单模式可用的前提。
     */
    @GetMapping("/fields")
    public Result<List<String>> fields(@RequestParam String fact) {
        try {
            return Result.ok(configService.fieldsOf(fact));
        } catch (Exception e) {
            log.error("[质控] 字段清单查询失败: fact={}", fact, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /**
     * 影响面：哪些指标引用了该事实层。
     *
     * <p>改事实层前必看 —— 一个域几十条指标共享同一事实层，改错的连带影响远超改一条指标。
     */
    @GetMapping("/impact")
    public Result<List<MetricDefinition>> impact(@RequestParam String fact) {
        try {
            return Result.ok(configService.metricsUsingFact(fact));
        } catch (Exception e) {
            log.error("[质控] 影响面查询失败: fact={}", fact, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /** 当前生效的事实层 SQL。 */
    @GetMapping("/fact/sql")
    public Result<String> factSql(@RequestParam String fact) {
        try {
            return Result.ok(configService.currentFactSql(fact));
        } catch (Exception e) {
            log.error("[质控] 事实层 SQL 查询失败: fact={}", fact, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 历史与回滚
    // ------------------------------------------------------------------

    /** 变更历史（每次保存留一份完整快照）。 */
    @GetMapping("/history")
    public Result<List<QualityDefHistory>> history(@RequestParam(required = false) String defType,
                                                   @RequestParam(required = false) String defKey,
                                                   @RequestParam(required = false, defaultValue = "20") Integer limit) {
        try {
            return Result.ok(configService.history(defType, defKey, limit == null ? 20 : limit));
        } catch (Exception e) {
            log.error("[质控] 变更历史查询失败", e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /** 回滚到指定历史版本（等价于把旧口径重新发布一次，审计链保留）。 */
    @PostMapping("/rollback")
    public Result<QualityConfigService.SaveResult> rollback(
            @RequestParam Long historyId,
            HttpServletRequest request) {
        String operator = guard.operator(request);
        try {
            return Result.ok(configService.rollback(historyId, operator));
        } catch (Exception e) {
            log.error("[质控] 回滚失败: historyId={}", historyId, e);
            return Result.fail("回滚失败: " + e.getMessage());
        }
    }

    /** 手动触发「重载配置 + 同步字典」（保存时已自动执行，此处用于排查与恢复）。 */
    @PostMapping("/reload")
    public Result<Integer> reload(HttpServletRequest request) {
        try {
            log.info("[质控] 手动重载配置: operator={}", guard.operator(request));
            return Result.ok(configService.reloadAndSync());
        } catch (Exception e) {
            log.error("[质控] 配置重载失败", e);
            return Result.fail("重载失败: " + e.getMessage());
        }
    }
}
