package com.zing.doctor.module.ards.prone.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * ARDS 俯卧位自动取数 Mapper（走 ICU 只读库 zing_icu_db_prod）。
 *
 * <p>数据来源与既有评分模块一致：
 * <ul>
 *   <li>监护/呼吸机观察项：patient_observe_module_item_record JOIN patient_observe_module_item</li>
 *   <li>检验/血气：patient_info_lis_item</li>
 *   <li>患者基本信息：patient_info</li>
 * </ul>
 * 本 Mapper 只为「按时间窗口取原值」服务；取不到一律由上层置空转手工，不沿用历史值。
 */
@DS("icu")
@Mapper
public interface ArdsProneDataMapper {

    /**
     * 窗口内观察项记录（含项目名、值、时间、单位），按时间倒序，Java 端取最近一条。
     */
    @Select("SELECT r.item_code AS item_code, r.item_value AS item_value, "
            + "r.item_time AS item_time, r.item_unit AS item_unit, i.item_name AS item_name "
            + "FROM \"zing_icu_db_prod\".\"patient_observe_module_item_record\" r "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_observe_module_item\" i "
            + "  ON i.patient_id = r.patient_id AND i.item_code = r.item_code AND i.del_flag = 0 "
            + "WHERE r.patient_id = #{patientId} AND r.del_flag = 0 AND r.status = 1 "
            + "AND i.del_flag = 0 AND i.status = 1 "
            + "AND r.item_time >= #{startTime} AND r.item_time <= #{endTime} "
            + "ORDER BY r.item_time DESC")
    List<Map<String, Object>> selectObserveRecords(@Param("patientId") String patientId,
                                                   @Param("startTime") String startTime,
                                                   @Param("endTime") String endTime);

    /**
     * 窗口内检验/血气结果，按时间倒序，Java 端按 item_code / 项目名称匹配取最近一条。
     */
    @Select("SELECT li.lis_item_code AS item_code, li.lis_item_name AS item_name, "
            + "li.lis_item_result AS item_result, li.check_time AS check_time "
            + "FROM \"zing_icu_db_prod\".\"patient_info_lis_item\" li "
            + "WHERE li.in_hospital_no = #{inHospitalNo} AND li.del_flag = 0 "
            + "AND li.check_time >= #{startTime} AND li.check_time <= #{endTime} "
            + "AND li.lis_item_result IS NOT NULL AND li.lis_item_result <> '' "
            + "ORDER BY li.check_time DESC")
    List<Map<String, Object>> selectLabItems(@Param("inHospitalNo") String inHospitalNo,
                                             @Param("startTime") String startTime,
                                             @Param("endTime") String endTime);

    /**
     * 监护/呼吸机项目字典（配置页「选择候选」用）：ICU 侧观察项配置表。
     */
    @Select("<script>SELECT * FROM ( "
            + "  SELECT c.item_code AS item_code, c.item_name AS item_name, c.sign_code AS sign_code "
            + "  FROM \"zing_icu_db_prod\".\"config_observe_item\" c "
            + "  WHERE c.del_flag = 0 "
            + "  <if test='keyword != null and keyword != \"\"'> "
            + "    AND (c.item_code LIKE '%' || #{keyword} || '%' OR c.item_name LIKE '%' || #{keyword} || '%') "
            + "  </if> "
            + "  ORDER BY c.item_name ASC "
            + ") WHERE ROWNUM &lt;= #{limit}</script>")
    List<Map<String, Object>> selectObserveItemDict(@Param("keyword") String keyword,
                                                    @Param("limit") int limit);

    /**
     * 检验/血气项目候选（配置页「选择候选」用）：近 #{days} 天实际出现过的 LIS 项目。
     */
    @Select("<script>SELECT * FROM ( "
            + "  SELECT li.lis_item_code AS item_code, MAX(li.lis_item_name) AS item_name "
            + "  FROM \"zing_icu_db_prod\".\"patient_info_lis_item\" li "
            + "  WHERE li.del_flag = 0 AND li.check_time >= #{sinceTime} "
            + "  <if test='keyword != null and keyword != \"\"'> "
            + "    AND (li.lis_item_code LIKE '%' || #{keyword} || '%' OR li.lis_item_name LIKE '%' || #{keyword} || '%') "
            + "  </if> "
            + "  GROUP BY li.lis_item_code "
            + "  ORDER BY MAX(li.lis_item_name) ASC "
            + ") WHERE ROWNUM &lt;= #{limit}</script>")
    List<Map<String, Object>> selectLisItemDict(@Param("keyword") String keyword,
                                                @Param("sinceTime") String sinceTime,
                                                @Param("limit") int limit);

    /**
     * 患者候选入科记录（同一住院号可能多次入科），用于记录头、打印文书与患者 ID 解析。
     *
     * <p>注意：patient_info 表性别字段为 gender（非 sex），别名 sex 与上层取值保持一致；
     * diagnosis_content 为 CLOB，必须 CAST 成 VARCHAR，否则 Jackson 序列化会炸。
     */
    @Select("<script>SELECT * FROM ( "
            + "  SELECT pi.id AS patient_id, pi.in_hospital_no AS in_hospital_no, pi.name AS name, "
            + "  pi.gender AS sex, pi.bed_code AS bed_code, pi.depart_code AS depart_code, "
            + "  pi.age AS age, pi.age_unit AS age_unit, "
            + "  <if test='withInHospitalTime'>pi.in_hospital_time AS in_hospital_time,</if> "
            + "  pi.in_depart_time AS in_depart_time, pi.out_depart_time AS out_depart_time, "
            + "  pi.is_in_depart AS is_in_depart, "
            + "  CAST(pi.diagnosis_content AS VARCHAR(2000)) AS diagnosis "
            + "  FROM \"zing_icu_db_prod\".\"patient_info\" pi "
            + "  WHERE pi.in_hospital_no = #{inHospitalNo} AND pi.del_flag = 0 "
            + "  ORDER BY pi.in_depart_time DESC "
            + ") WHERE ROWNUM &lt;= 20</script>")
    List<Map<String, Object>> selectPatientCandidates(@Param("inHospitalNo") String inHospitalNo,
                                                      @Param("withInHospitalTime") boolean withInHospitalTime);

    /**
     * 按住院流水号精确定位（外链参数 inHospitalSerialNo）。
     *
     * <p><b>仅当 patient_info 确实存在 in_hospital_serial_no 列时才会被调用</b>
     * ——Service 先 {@link #countPatientInfoColumn(String)} 探测，避免列不存在时整条链路 500。
     */
    @Select("<script>SELECT * FROM ( "
            + "  SELECT pi.id AS patient_id, pi.in_hospital_no AS in_hospital_no, pi.name AS name, "
            + "  pi.gender AS sex, pi.bed_code AS bed_code, pi.depart_code AS depart_code, "
            + "  pi.age AS age, pi.age_unit AS age_unit, "
            + "  <if test='withInHospitalTime'>pi.in_hospital_time AS in_hospital_time,</if> "
            + "  pi.in_depart_time AS in_depart_time, pi.out_depart_time AS out_depart_time, "
            + "  pi.is_in_depart AS is_in_depart, "
            + "  CAST(pi.diagnosis_content AS VARCHAR(2000)) AS diagnosis "
            + "  FROM \"zing_icu_db_prod\".\"patient_info\" pi "
            + "  WHERE pi.in_hospital_no = #{inHospitalNo} AND pi.del_flag = 0 "
            + "  AND pi.in_hospital_serial_no = #{serialNo} "
            + "  ORDER BY pi.in_depart_time DESC "
            + ") WHERE ROWNUM &lt;= 20</script>")
    List<Map<String, Object>> selectPatientCandidatesBySerial(@Param("inHospitalNo") String inHospitalNo,
                                                              @Param("serialNo") String serialNo,
                                                              @Param("withInHospitalTime") boolean withInHospitalTime);

    /**
     * patient_info 是否存在某列（0/1），用于可选的入院时间 / 住院流水号字段探测。
     * 无权限或查询失败由上层按「不存在」处理，保证不因可选列缺失而整条链路报错。
     */
    @Select("SELECT COUNT(*) FROM ALL_TAB_COLUMNS "
            + "WHERE UPPER(OWNER) = 'ZING_ICU_DB_PROD' AND UPPER(TABLE_NAME) = 'PATIENT_INFO' "
            + "AND UPPER(COLUMN_NAME) = #{columnName}")
    int countPatientInfoColumn(@Param("columnName") String columnName);

    /** 诊断兜底：patient_info.diagnosis_content 为空时取诊断表最新一条（按 diag_time 倒序） */
    @Select("SELECT * FROM ( "
            + "  SELECT d.diag_name AS diag_name "
            + "  FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" d "
            + "  INNER JOIN \"zing_icu_db_prod\".\"patient_info\" pi "
            + "    ON pi.id = d.patient_id AND pi.del_flag = 0 "
            + "  WHERE d.del_flag = 0 AND d.status = 1 AND d.diag_name IS NOT NULL "
            + "  AND pi.in_hospital_no = #{inHospitalNo} "
            + "  ORDER BY d.diag_time DESC "
            + ") WHERE ROWNUM <= 1")
    String selectLatestDiagnosis(@Param("inHospitalNo") String inHospitalNo);
}
