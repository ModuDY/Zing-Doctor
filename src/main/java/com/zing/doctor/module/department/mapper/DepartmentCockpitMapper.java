package com.zing.doctor.module.department.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 科室驾驶舱聚合查询（只读，ICU 库 {@code zing_icu_db_prod}）。
 *
 * <p>口径与出处（阶段0核验结论，见 docs/23 §3）：
 * <ul>
 *   <li>出科时间 = {@code patient_info.out_depart_time}，区间<b>左闭右开</b> —— 与交班览表 /
 *       出科统计的 {@code IcuPatientMapper#selectDischargedPatients} 完全一致；</li>
 *   <li>「当前在科」= {@code is_in_depart = 1 AND del_flag = 0}，与患者工作台同口径，
 *       <b>不</b>用质控事实层 fact_patient_stay 推（那表不区分在科/已出科）；</li>
 *   <li>人数一律 {@code COUNT(DISTINCT in_hospital_no)}：同一住院号多次入科（转科）
 *       只算一个患者，避免流转数虚高；</li>
 *   <li>科室边界 {@code depart_code}（sys_depart.org_code），空串 = 全院。
 *       权限由上层 {@code UserDepartScopeService#resolveQueryDepart} 收敛，这里只按传入值过滤。</li>
 * </ul>
 *
 * <p>全部为聚合查询、一次取回，不逐患者循环；区块间相互独立，某块失败不影响其它块。
 */
@DS("icu")
@Mapper
public interface DepartmentCockpitMapper {

    /**
     * 今日科室快照：一次算出在科 / 今日入科 / 今日出科。
     *
     * @param departCode 科室编码，空串表示全院
     * @param todayStart 今日 00:00（ISO 字符串，达梦可与 TIMESTAMP 比较）
     * @param todayEnd   明日 00:00
     * @return inCount 当前在科人数、todayInCount 今日入科人数、todayOutCount 今日出科人数
     */
    @Select({"<script>",
            "SELECT",
            "  (SELECT COUNT(DISTINCT pi.in_hospital_no) FROM \"zing_icu_db_prod\".\"patient_info\" pi",
            "    WHERE pi.del_flag = 0 AND pi.is_in_depart = 1",
            "    <if test=\"departCode != null and departCode != ''\">AND pi.depart_code = #{departCode}</if>) AS in_count,",
            "  (SELECT COUNT(DISTINCT pi.in_hospital_no) FROM \"zing_icu_db_prod\".\"patient_info\" pi",
            "    WHERE pi.del_flag = 0 AND pi.in_depart_time IS NOT NULL",
            "      AND pi.in_depart_time &gt;= #{todayStart} AND pi.in_depart_time &lt; #{todayEnd}",
            "    <if test=\"departCode != null and departCode != ''\">AND pi.depart_code = #{departCode}</if>) AS today_in_count,",
            "  (SELECT COUNT(DISTINCT pi.in_hospital_no) FROM \"zing_icu_db_prod\".\"patient_info\" pi",
            "    WHERE pi.del_flag = 0 AND pi.out_depart_time IS NOT NULL",
            "      AND pi.out_depart_time &gt;= #{todayStart} AND pi.out_depart_time &lt; #{todayEnd}",
            "    <if test=\"departCode != null and departCode != ''\">AND pi.depart_code = #{departCode}</if>) AS today_out_count",
            "</script>"})
    Map<String, Object> selectTodaySnapshot(@Param("departCode") String departCode,
                                            @Param("todayStart") String todayStart,
                                            @Param("todayEnd") String todayEnd);

    /**
     * 近 N 天每日入科人数（按自然日聚合）。
     *
     * <p>用 {@code GROUP BY TRUNC(in_depart_time)} 一次扫描聚合，<b>不要</b>按天循环查：
     * 30 天就是 30 次全表扫描，且达梦里日期字符串不能直接 + 整数做步进
     * （{@code '2026-10-09T00:00:00' + 1} 会变成数值运算返回 1）。缺失日期由 Java 端补零。
     *
     * @param departCode 科室编码，空串表示全院
     * @param start      起始时刻（含），ISO 字符串
     * @param end        结束时刻（不含），ISO 字符串
     * @return 每日一行：day=当日 00:00、cnt=入科人数（按住院号去重）
     */
    @Select({"<script>",
            "SELECT TRUNC(pi.in_depart_time) AS day, COUNT(DISTINCT pi.in_hospital_no) AS cnt",
            "  FROM \"zing_icu_db_prod\".\"patient_info\" pi",
            " WHERE pi.del_flag = 0 AND pi.in_depart_time IS NOT NULL",
            "   AND pi.in_depart_time &gt;= #{start} AND pi.in_depart_time &lt; #{end}",
            "   <if test=\"departCode != null and departCode != ''\">AND pi.depart_code = #{departCode}</if>",
            " GROUP BY TRUNC(pi.in_depart_time)",
            "</script>"})
    List<Map<String, Object>> selectDailyInCount(@Param("departCode") String departCode,
                                                 @Param("start") String start,
                                                 @Param("end") String end);

    /**
     * 近 N 天每日出科人数（按自然日聚合）。口径同 {@link #selectDailyInCount}，
     * 区间左闭右开，与出科统计 / 交班览表一致。
     */
    @Select({"<script>",
            "SELECT TRUNC(pi.out_depart_time) AS day, COUNT(DISTINCT pi.in_hospital_no) AS cnt",
            "  FROM \"zing_icu_db_prod\".\"patient_info\" pi",
            " WHERE pi.del_flag = 0 AND pi.out_depart_time IS NOT NULL",
            "   AND pi.out_depart_time &gt;= #{start} AND pi.out_depart_time &lt; #{end}",
            "   <if test=\"departCode != null and departCode != ''\">AND pi.depart_code = #{departCode}</if>",
            " GROUP BY TRUNC(pi.out_depart_time)",
            "</script>"})
    List<Map<String, Object>> selectDailyOutCount(@Param("departCode") String departCode,
                                                  @Param("start") String start,
                                                  @Param("end") String end);

    /**
     * 近 N 天已出科患者的在科天数（用于平均 ICU 住院日）。
     *
     * <p>口径：按<b>患者</b>去重（同一住院号取最近一次已出科记录），在科天数 =
     * {@code out_depart_time - in_depart_time}，向上取整天（不足 1 天算 1 天）。
     * 只统计已出科者 —— 在科患者的"住院日"还在增长，混入会低估平均值。
     */
    @Select({"<script>",
            "SELECT AVG(t.stay_days) AS avg_stay_days, COUNT(*) AS patient_count FROM (",
            "  SELECT pi.in_hospital_no AS in_hospital_no,",
            "         CEIL(pi.out_depart_time - pi.in_depart_time) AS stay_days",
            "    FROM (",
            "      SELECT t2.*, ROW_NUMBER() OVER (PARTITION BY t2.in_hospital_no ORDER BY t2.out_depart_time DESC) AS rn",
            "        FROM (",
            "          SELECT pi2.in_hospital_no, pi2.in_depart_time, pi2.out_depart_time",
            "            FROM \"zing_icu_db_prod\".\"patient_info\" pi2",
            "           WHERE pi2.del_flag = 0 AND pi2.out_depart_time IS NOT NULL AND pi2.in_depart_time IS NOT NULL",
            "             AND pi2.out_depart_time &gt;= #{start} AND pi2.out_depart_time &lt; #{end}",
            "             <if test=\"departCode != null and departCode != ''\">AND pi2.depart_code = #{departCode}</if>",
            "        ) t2",
            "    ) pi",
            "   WHERE pi.rn = 1",
            ") t",
            "</script>"})
    Map<String, Object> selectAvgStayDays(@Param("departCode") String departCode,
                                          @Param("start") String start,
                                          @Param("end") String end);

    /**
     * 当前床位资源。开放床位必须同时满足未删除、启用且当前时刻落在启停区间内；
     * 占用床位只统计能匹配到有效开放床位的当前在科患者，避免脏床号把使用率推到 100% 以上。
     */
    @Select({"<script>",
            "SELECT COUNT(*) AS open_beds,",
            "       SUM(CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info\" pi",
            "                              WHERE pi.del_flag = 0 AND pi.is_in_depart = 1",
            "                                AND pi.depart_code = b.depart_code AND pi.bed_code = b.bed_code) THEN 1 ELSE 0 END) AS occupied_beds,",
            "       (SELECT COUNT(DISTINCT pi2.in_hospital_no) FROM \"zing_icu_db_prod\".\"patient_info\" pi2",
            "         WHERE pi2.del_flag = 0 AND pi2.is_in_depart = 1",
            "           <if test=\"departCode != null and departCode != ''\">AND pi2.depart_code = #{departCode}</if>",
            "           AND (pi2.bed_code IS NULL OR TRIM(pi2.bed_code) = '' OR NOT EXISTS (",
            "                SELECT 1 FROM \"zing_icu_db_prod\".\"config_bed\" bx",
            "                 WHERE bx.del_flag = 0 AND bx.status = 1",
            "                   AND (bx.begin_use_time IS NULL OR bx.begin_use_time &lt;= SYSDATE)",
            "                   AND (bx.stop_use_time IS NULL OR bx.stop_use_time &gt; SYSDATE)",
            "                   AND bx.depart_code = pi2.depart_code AND bx.bed_code = pi2.bed_code",
            "           )) AS unassigned_patients",
            "  FROM \"zing_icu_db_prod\".\"config_bed\" b",
            " WHERE b.del_flag = 0 AND b.status = 1",
            "   AND (b.begin_use_time IS NULL OR b.begin_use_time &lt;= SYSDATE)",
            "   AND (b.stop_use_time IS NULL OR b.stop_use_time &gt; SYSDATE)",
            "   <if test=\"departCode != null and departCode != ''\">AND b.depart_code = #{departCode}</if>",
            "</script>"})
    Map<String, Object> selectBedOverview(@Param("departCode") String departCode);

    /**
     * 设备接入态势。bound_count 通过 patient_info 的四个设备编码与配置 device_id 精确匹配；
     * device_status 是采集连接状态，不能解释为临床使用、维修或库存状态。
     */
    @Select({"<script>",
            "SELECT d.device_type AS device_type, COUNT(DISTINCT d.id) AS configured_count,",
            "       SUM(CASE WHEN EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info\" pi",
            "                              WHERE pi.del_flag = 0 AND pi.is_in_depart = 1",
            "                                AND pi.depart_code = d.depart_code",
            "                                AND (pi.monitor_code = d.device_id OR pi.ventilator_code = d.device_id",
            "                                     OR pi.crrt_device_code = d.device_id OR pi.ecmo_device_code = d.device_id))",
            "                THEN 1 ELSE 0 END) AS bound_count,",
            "       SUM(CASE WHEN d.device_status = '数据正在接收' THEN 1 ELSE 0 END) AS receiving_count,",
            "       SUM(CASE WHEN d.device_status = '端口已连接' THEN 1 ELSE 0 END) AS connected_count,",
            "       SUM(CASE WHEN d.device_status = '连接初始化' THEN 1 ELSE 0 END) AS initializing_count,",
            "       SUM(CASE WHEN d.device_status = '数据停止接收' THEN 1 ELSE 0 END) AS stopped_count,",
            "       SUM(CASE WHEN d.device_status LIKE '%异常%' OR d.device_status LIKE '%断开%' THEN 1 ELSE 0 END) AS abnormal_count,",
            "       SUM(CASE WHEN d.device_status IS NULL OR TRIM(d.device_status) = '' OR d.device_status = '未接入采集套件' THEN 1 ELSE 0 END) AS unknown_count,",
            "       MAX(d.update_time) AS latest_update",
            "  FROM \"zing_icu_db_prod\".\"config_device\" d",
            " WHERE d.del_flag = 0 AND d.status = 1",
            "   <if test=\"departCode != null and departCode != ''\">AND d.depart_code = #{departCode}</if>",
            " GROUP BY d.device_type ORDER BY d.device_type",
            "</script>"})
    List<Map<String, Object>> selectDeviceOverview(@Param("departCode") String departCode);
}
