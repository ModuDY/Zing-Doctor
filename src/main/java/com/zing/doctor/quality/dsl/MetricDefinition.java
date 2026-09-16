package com.zing.doctor.quality.dsl;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 指标定义（quality/metrics/*.yaml）。
 *
 * <p>一条指标 = 一段声明，不含任何 Java 分支。新增指标只需在 YAML 追加一个条目。
 */
@Data
public class MetricDefinition {

    /** 指标编号：quality_0 ... quality_490 */
    private String code;

    /** 指标名称 */
    private String name;

    /** 所属域 */
    private String domain;

    /** 绑定事实层（FactDefinition.fact） */
    private String fact;

    /** 单位：% / 人 / 天 / 例 */
    private String unit;

    /** 值类型：COUNT 数 / RATE 率 / AVG 均值 / SUM 求和 */
    private String valueType = "COUNT";

    /**
     * 计算方式，对应 {@code quality_index.calc_mode}：
     * <ul>
     *   <li>DSL 声明式（默认，fact + 表达式编译成 SQL）</li>
     *   <li>MANUAL 人工录入（{@link #implStatus} 为 MANUAL 时自动判定，无需显式配置）</li>
     *   <li>CUSTOM_SQL 自定义 SQL（需在此显式声明）</li>
     * </ul>
     */
    private String calcMode = "DSL";

    /**
     * 实现状态：
     * <ul>
     *   <li>IMPL 已实现（有 fact + 表达式）</li>
     *   <li>PLACEHOLDER 空壳（口径未定，页面占位显示）</li>
     *   <li>PENDING_SOURCE 待接数据源（表达式就绪，源表缺失）</li>
     *   <li>MANUAL 人工录入</li>
     * </ul>
     */
    private String implStatus = "IMPL";

    /**
     * 聚合方式：
     * <ul>
     *   <li>PT_COUNT 去重患者数（默认，"人数/例数"类）</li>
     *   <li>SUM 求和（床日/天数合计类）</li>
     *   <li>AVG 均值（平均天数类）</li>
     * </ul>
     */
    private String agg = "PT_COUNT";

    /** 分子过滤条件（与 numerator 共同构成"计入分子"的判定） */
    private String where;

    /** 分子表达式：PT_COUNT 时为布尔条件；SUM/AVG 时为数值表达式。默认 1 */
    private String numerator;

    /** 分母过滤条件；为空时分母 = 同期全部患者 */
    private String denominatorWhere;

    /**
     * 分子取自另一条指标的结果（跨事实层率类指标用）。
     *
     * <p><b>为什么需要它</b>：{@code ICU患者预计病死率 = ICU收治患者预计病死率之和 ÷ 同期患者总数}，
     * 前者在评分事实层、后者在患者流转事实层 —— 一条 {@code fact} 串不起两层的聚合。
     * 声明引用后本指标不编译 SQL，而是在批次里等被引用指标算完、按科室取它的分子分母相除。
     *
     * <p><b>为什么不复制被引用指标的口径</b>：口径只应有一处定义。复制一份出来，
     * 被引用指标改了条件而这里没改，两条指标就会长期给出互相矛盾的数，且没人会发现。
     *
     * <p>与 {@link #numerator} 互斥：两者都配时以本字段为准。
     */
    private String numeratorMetric;

    /** 分母取自另一条指标的结果；语义与 {@link #numeratorMetric} 对称。 */
    private String denominatorMetric;

    /** 率类放大系数，默认 100（即百分比） */
    private Integer scale;

    /** 口径版本，改口径即递增，用于回溯历史值 */
    private Integer version = 1;

    /**
     * 是否是「引用其它指标结果」的派生指标。
     *
     * <p>判定只在这里实现一处：引擎决定「要不要编译 SQL」、编排决定「要不要分两阶段算」、
     * 校验决定「走哪条校验路径」都依赖它。三处各自判断迟早会漂移，
     * 而漂移的症状是「配置页说能算、批次里算不出来」这类最难查的问题。
     */
    public boolean isDerived() {
        return hasText(numeratorMetric) || hasText(denominatorMetric);
    }

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /** 分组维度列（事实层列名），空或 [] 表示仅全院 'ALL' 一行 */
    private List<String> dims = new ArrayList<>();

    /**
     * 患者明细的补充展示字段（列必须是该事实层投影出的列）。
     *
     * <p>为空时明细只显示默认列（姓名/住院号/科室/入分子/入分母）；配置后按配置渲染，
     * 各指标可以看各自真正关心的那几列。
     */
    private List<PatientFieldDefinition> patientFields = new ArrayList<>();

    // ---- 老系统映射信息（字典展示与双跑核对用） ----

    private String categoryCode;
    private String groupCode;
    private String qualityTypeCode;
    private String indexStandardCode;
    /** 金额显示类型，对应 quality_index.amount_show_type（老字典属性，当前无 YAML 声明） */
    private String amountShowType;
    /** 分析计数类型，对应 quality_index.analysis_count_type（老字典属性，当前无 YAML 声明） */
    private String analysisCountType;
    /** 老系统脚本情况：是 / 否-空壳 / 不适用 */
    private String legacyScript;
    private String legacySource;
    private String newTarget;
    private String reuseLevel;
    private String remark;
    private Integer sortNo;
}
