package com.zing.doctor.quality.dsl;

import lombok.Data;

/**
 * 患者明细（血缘第 4 层）的展示字段配置，挂在指标上。
 *
 * <p>默认列（姓名 / 住院号 / 科室 / 入分子 / 入分母）全指标一致，是「这是谁」的最小集；
 * 但不同指标真正想看的补充字段并不相同 —— 感染类要看培养结果时间，评分类要看分值，
 * 资源类要看床位。写死在页面里就只能对某几个指标好看。
 *
 * <p>取值只允许引用<b>该事实层已投影的列</b>：配置错了在保存时就被拦下，
 * 不会等到护士长点开明细才报 SQL 错。
 */
@Data
public class PatientFieldDefinition {

    /** 事实层投影出的列名，例如 gender / in_depart_time */
    private String key;

    /** 表头名称，例如「性别」；留空时退化为 {@link #key} */
    private String label;

    /** 列宽（像素），留空时前端给默认宽度 */
    private Integer width;
}
