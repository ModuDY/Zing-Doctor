package com.zing.doctor.quality.dsl;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 事实层（DWD）定义（quality/facts/*.yaml）。
 *
 * <p>这是本方案与「手写 SQL 建中间表」的关键区别：事实层由本 DSL 声明，
 * 引擎编译成 SQL 并物化为物理表。加字段、改阈值、换过滤条件都只改 YAML。
 *
 * <p>编译规则（引擎实现）：
 * <pre>
 *   SELECT &lt;select + derive&gt;
 *     FROM "&lt;schema&gt;"."&lt;table&gt;" &lt;alias&gt;
 *    WHERE &lt;where 逐条 AND&gt;
 *    [GROUP BY &lt;group&gt;]
 * </pre>
 * 时间窗口由引擎统一注入占位符 {@code :periodStart} / {@code :periodEnd}。
 */
@Data
public class FactDefinition {

    /** 事实层名，同时作为物化表名后缀（qc_ + fact） */
    private String fact;

    /** 所属域 */
    private String domain;

    /** ACTIVE 可计算 / PENDING_SOURCE 待接数据源 / PLACEHOLDER 空壳 */
    private String status = "ACTIVE";

    /** 来源逻辑表名（对应 sources.yaml.tables 的 key） */
    private String source;

    /** 表别名 */
    private String alias = "t";

    /** 患者主键表达式（用于去重计数与患者明细） */
    private String patientKey = "patient_id";

    /** 住院号列名（追溯展示用，事实层须投影出该列） */
    private String inHospitalNoKey = "in_hospital_no";

    /** 姓名列名（追溯展示用，事实层须投影出该列） */
    private String patientNameKey = "patient_name";

    /** 科室列名（指标按科室分组用，事实层须投影出该列） */
    private String departKey = "depart_code";

    /** 选列（表达式 + AS 别名） */
    private List<String> select = new ArrayList<>();

    /** 派生列（表达式 + AS 别名），与 select 合并输出 */
    private List<String> derive = new ArrayList<>();

    /** 过滤条件，逐条 AND */
    private List<String> where = new ArrayList<>();

    /** 分组列，空表示患者级（不聚合） */
    private List<String> group = new ArrayList<>();

    /** 说明（待接数据源时写清楚缺什么） */
    private String note;
}
