package com.zing.doctor.module.tube.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 导管使用统计 Mapper。
 *
 * <p>数据源：{@code zing_icu_db_prod.patient_tube}（导管置管/拔管/带出事件）
 * join {@code patient_info}（拿科室 depart_code、出科时间 out_depart_time）。
 *
 * <p>口径（已与业务确认）：
 * <ul>
 *   <li>{@code take_source}：1=他科带入、2=外院带入、3=本科置管；</li>
 *   <li>未拔除导管的留置时长截止到患者 {@code out_depart_time}；</li>
 *   <li>{@code is_not_plan_extubation=1} 非计划拔管；{@code is_reintubation24=1} 拔管后48h内再插管；</li>
 *   <li>三管白名单由 Service 层按 tube_code 归类（CVC/导尿管/气管插管+气切），其余归「其他」。</li>
 * </ul>
 *
 * <p>本 Mapper 只做只读聚合，不写任何表。
 */
public interface TubeStatsMapper {

    /**
     * 按月×导管类型聚合置管/拔管/带出/非计划/再插管计数。
     *
     * @param start      月起始（含），如 2026-09-01 00:00:00
     * @param end        月结束（不含），如 2026-10-01 00:00:00
     * @param departCode 已按账号收敛的科室编码；null/ALL 表示全院
     */
    @Select("SELECT "
            + "  pt.tube_code AS tube_code, "
            + "  MAX(pt.tube_name) AS tube_name, "
            // 当月新置管（置管开始时间落在本月）
            + "  SUM(CASE WHEN pt.tube_set_start_time >= #{start} AND pt.tube_set_start_time < #{end} THEN 1 ELSE 0 END) AS placed, "
            + "  SUM(CASE WHEN pt.tube_set_start_time >= #{start} AND pt.tube_set_start_time < #{end} AND pt.take_source IN (1,2) THEN 1 ELSE 0 END) AS brought_in, "
            + "  SUM(CASE WHEN pt.tube_set_start_time >= #{start} AND pt.tube_set_start_time < #{end} AND pt.take_source = 3 THEN 1 ELSE 0 END) AS self_placed, "
            // 当月拔管（置管结束时间落在本月）
            + "  SUM(CASE WHEN pt.tube_set_end_time >= #{start} AND pt.tube_set_end_time < #{end} THEN 1 ELSE 0 END) AS removed, "
            // 当月转出 ICU 时带出
            + "  SUM(CASE WHEN pt.tube_take_away_time >= #{start} AND pt.tube_take_away_time < #{end} THEN 1 ELSE 0 END) AS taken_away, "
            // 当月拔管中属于非计划拔管
            + "  SUM(CASE WHEN pt.tube_set_end_time >= #{start} AND pt.tube_set_end_time < #{end} AND pt.is_not_plan_extubation = 1 THEN 1 ELSE 0 END) AS unplanned_removal, "
            // 当月新置管的气道导管中，48h内再插管
            + "  SUM(CASE WHEN pt.tube_set_start_time >= #{start} AND pt.tube_set_start_time < #{end} AND pt.is_reintubation24 = 1 THEN 1 ELSE 0 END) AS reintubation_24h "
            + "FROM \"zing_icu_db_prod\".\"patient_tube\" pt "
            + "JOIN \"zing_icu_db_prod\".\"patient_info\" pi ON pi.id = pt.patient_id "
            + "WHERE pt.del_flag = 0 AND pi.del_flag = 0 "
            + "  AND (#{departCode} IS NULL OR #{departCode} = 'ALL' OR pi.depart_code = #{departCode}) "
            // 粗过滤：只看本月前后有留置动作的，缩小扫描范围
            + "  AND (pt.tube_set_start_time >= #{start} AND pt.tube_set_start_time < #{end} "
            + "       OR pt.tube_set_end_time >= #{start} AND pt.tube_set_end_time < #{end} "
            + "       OR pt.tube_take_away_time >= #{start} AND pt.tube_take_away_time < #{end}) "
            + "GROUP BY pt.tube_code "
            + "ORDER BY placed DESC")
    List<Map<String, Object>> monthlyCounts(@Param("start") String start,
                                             @Param("end") String end,
                                             @Param("departCode") String departCode);

    /**
     * 当月新置管导管的留置时长（天）明细，供 Service 算平均留置日与覆盖率。
     *
     * <p>留置区间上界：已拔用 {@code tube_set_end_time}；未拔用患者 {@code out_depart_time}
     * （患者已出科）；都为空（仍在科）则用当月结束 {@code end} 作为统计截止。
     */
    @Select("SELECT "
            + "  pt.tube_code AS tube_code, "
            + "  pt.tube_set_start_time AS start_time, "
            + "  pt.tube_set_end_time AS end_time, "
            + "  pi.out_depart_time AS out_time "
            + "FROM \"zing_icu_db_prod\".\"patient_tube\" pt "
            + "JOIN \"zing_icu_db_prod\".\"patient_info\" pi ON pi.id = pt.patient_id "
            + "WHERE pt.del_flag = 0 AND pi.del_flag = 0 "
            + "  AND pt.tube_set_start_time >= #{start} AND pt.tube_set_start_time < #{end} "
            + "  AND (#{departCode} IS NULL OR #{departCode} = 'ALL' OR pi.depart_code = #{departCode})")
    List<Map<String, Object>> placementDurations(@Param("start") String start,
                                                  @Param("end") String end,
                                                  @Param("departCode") String departCode);
}
