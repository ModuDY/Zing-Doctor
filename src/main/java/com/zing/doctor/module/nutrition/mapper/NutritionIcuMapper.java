package com.zing.doctor.module.nutrition.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 营养支持分析：ICU 业务库（zing_icu_db_prod）只读 Mapper。
 *
 * <p>数据口径（已连库核实，见 docs/24）：
 * <ul>
 *   <li>{@code patient_advice_execute} 营养医嘱执行主表；{@code liquid_amount} 是整袋/整瓶规格，
 *       <b>不是</b>已给入量。</li>
 *   <li>{@code patient_advice_execute_item.amount} 是<b>该小时实际泵入/给入的毫升数</b>
 *       （如能全力 1000ml 医嘱约 50ml/h），能量/蛋白按它累加，不能用 liquid_amount。</li>
 * </ul>
 *
 * <p>所有查询强制 patient_id + 时间窗；名称是否为营养制剂由 Java 端匹配
 * {@code config_nutrition_formula.match_keywords} 判定，SQL 不做无界 LIKE。
 */
@DS("icu")
@Mapper
public interface NutritionIcuMapper {

    /**
     * 单患者与时间窗<b>有交集</b>的医嘱执行单（不限名称；Java 端再按配方关键词识别营养制剂）。
     *
     * <p><b>口径要点（别改回 start_time 落在窗口内）</b>：营养泵入常是连续医嘱，一条执行单
     * 横跨 20 小时以上。若按「start_time 必须落在窗口内」过滤，凡是窗口开始前开立、窗口内
     * 仍在执行的执行单会被整条丢掉——实测某患者 24h 内 1425ml 能全力因此凭空消失
     * （只有窗口开始后才开的 65ml 被算上，漏掉 95%）。
     * 故改为区间相交：{@code start_time < 窗口结束} 且（{@code end_time} 为空表示仍在执行，
     * 或 {@code end_time > 窗口开始}）。已给入量仍由小时明细按窗口裁剪（见 {@link #selectHourItems}），
     * 所以窗口之前的泵入量不会被算进来，不会重复计数。
     *
     * @param startTime 窗口开始（含），ISO
     * @param endTime   窗口结束（不含），ISO
     */
    @Select("<script>"
            + "SELECT * FROM ( "
            + "  SELECT e.id AS execute_id, e.name AS name, e.drug_method_name AS method_name, "
            + "         e.freq_name AS freq_name, e.liquid_amount AS bag_volume, "
            + "         e.start_time AS start_time, e.end_time AS end_time, e.status AS status "
            + "  FROM \"zing_icu_db_prod\".\"patient_advice_execute\" e "
            + "  WHERE e.patient_id = #{patientId} AND e.del_flag = 0 "
            + "    AND e.start_time &lt; #{endTime} "
            + "    AND (e.end_time IS NULL OR e.end_time &gt; #{startTime}) "
            + "  ORDER BY e.start_time DESC) WHERE ROWNUM &lt;= 300"
            + "</script>")
    List<Map<String, Object>> selectOrders(@Param("patientId") String patientId,
                                          @Param("startTime") String startTime,
                                          @Param("endTime") String endTime);

    /**
     * 单患者在时间窗内全部小时执行明细（按 execute_id 分组在 Java 端累加）。
     */
    @Select("<script>"
            + "SELECT execute_id AS execute_id, execute_hour AS execute_hour, amount AS amount "
            + "FROM \"zing_icu_db_prod\".\"patient_advice_execute_item\" "
            + "WHERE patient_id = #{patientId} "
            + "  AND execute_hour &gt;= #{startTime} AND execute_hour &lt; #{endTime}"
            + "</script>")
    List<Map<String, Object>> selectHourItems(@Param("patientId") String patientId,
                                               @Param("startTime") String startTime,
                                               @Param("endTime") String endTime);
}
