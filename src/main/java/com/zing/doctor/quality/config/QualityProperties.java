package com.zing.doctor.quality.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 质控指标中台配置。
 *
 * <p>设计要点：指标、事实层、数据源三层全部来自 YAML 配置，
 * 新增/修改指标只改配置文件，不需要改任何 Java 代码、不需要重新发版。
 */
@Data
@Component
@ConfigurationProperties(prefix = "zing.quality")
public class QualityProperties {

    /** 是否启用质控中台（关闭后不加载 DSL、不注册任务） */
    private boolean enabled = true;

    /**
     * 外部配置目录（可选，留空则只用 jar 内打包配置，行为与改造前一致）。
     *
     * <p>非空时按「外部优先、缺失回退 classpath」读取：
     * <pre>
     *   {configDir}/sources.yaml     覆盖数据源定义（该文件存在时生效）
     *   {configDir}/facts/*.yaml     整体覆盖事实层定义（目录存在且含 yaml 时生效）
     *   {configDir}/metrics/*.yaml   整体覆盖指标定义（同上）
     * </pre>
     *
     * <p>存在的意义：jar 内的 classpath 资源<b>运行时不可写</b>。若不把配置放到外部目录，
     * {@code POST /api/quality/sync-index} 触发的 {@code QualityDslLoader#reload()} 重读到的
     * 仍是打包时的旧文件，「改配置即热生效」无从谈起。
     *
     * <p>例：{@code /data/zing-doctor/config/quality}
     */
    private String configDir;

    /**
     * 配置真源：
     * <ul>
     *   <li>{@code yaml}（默认）从 YAML 读取，行为与改造前完全一致</li>
     *   <li>{@code db} 从 {@code quality_metric_def} / {@code quality_fact_def} 读取，
     *       页面可编辑、保存即热生效；配置表为空时自动从 YAML 导入一次出厂种子</li>
     * </ul>
     *
     * <p>注意：数据源层（sources.yaml）不开放页面编辑，两种模式下都从 YAML 读取 ——
     * 它只声明物理表名，改错会导致整个域查不到数据，而一年也改不了几次。
     */
    private String configSource = "yaml";

    /**
     * 配置写接口令牌（可选，与 {@link #configWriteIpWhitelist} 至少配一个，否则写接口整体拒绝）。
     *
     * <p>页面属于「改一行 SQL 就能改生产口径」的高危操作，而 {@code /api/quality/config/**}
     * 与只读看板共用同一套外链鉴权 —— 拿到外链就能改口径，风险等级不匹配。
     * 配置本项后，所有<b>写请求</b>必须额外携带请求头 {@code X-Quality-Config-Token}。
     *
     * <p><b>默认留空且白名单也留空时，写接口一律拒绝</b>（fail-closed）。
     * 这是刻意选择：宁可让部署方显式开一次口子，也不要默认放开一个能改生产口径的入口。
     * 本项目尚无 SSO，此项与 IP 白名单是「待接 SSO」之前的过渡手段。
     *
     * <p>例：{@code ${QUALITY_CONFIG_WRITE_TOKEN:}}
     */
    private String configWriteToken;

    /**
     * 配置写接口 IP 白名单（可选，推荐）。
     *
     * <p>逗号分隔；元素支持<b>前缀匹配</b>，因此可写整个网段或一段前缀：
     * <pre>
     *   100.120.1.104,10.0.0.        → 单机 + 10.0.0.* 整段
     * </pre>
     * 配好之后只有信息科/质控科的工作站能提交配置，浏览器端无需携带任何密钥
     * （不需要重建前端），是当前最省事的隔离手段。
     *
     * <p>同时配置本项与 {@link #configWriteToken} 时<b>两者都必须通过</b>。
     */
    private String configWriteIpWhitelist;

    /** 数据源定义文件 */
    private String sourceLocation = "classpath*:quality/sources.yaml";

    /** 事实层（DWD）定义文件，可多个 */
    private String factLocation = "classpath*:quality/facts/*.yaml";

    /** 指标定义文件，可多个 */
    private String metricLocation = "classpath*:quality/metrics/*.yaml";

    /** 事实层物化落库的 schema（医生系统主库） */
    private String factSchema = "zing_doctor_db_prod";

    /** 事实层表名前缀，最终表名 = 前缀 + factName */
    private String factTablePrefix = "qc_";

    /** 是否把事实层物化为物理表（true=一次生成多指标复用，性能最优） */
    private boolean materializeFacts = true;

    /**
     * 是否在计算批次内预落库患者级明细（血缘第 4 层）。
     *
     * <p>默认 false：患者明细改为「页面下钻时按需生成」。原因是 127 条指标 × 单指标数百患者的
     * 预计算会显著拖慢月度批算，而下钻是人工节奏、一次只查一条指标，按需生成既快又不丢失追溯能力。
     * 若确实需要离线归档，可置为 true。
     */
    private boolean keepPatientDetail = false;

    /** 指标并行计算线程数 */
    private int calcThreads = 4;

    /** 引擎版本，写入计算批次供回溯 */
    private String engineVersion = "quality-engine/1.0.0";

    /** 定时任务：每月质量汇总刷新 cron，默认每月 1 日 03:30 */
    private String monthlyCron = "0 30 3 1 * ?";
}
