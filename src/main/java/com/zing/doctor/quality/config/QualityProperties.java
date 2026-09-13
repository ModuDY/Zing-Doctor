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
