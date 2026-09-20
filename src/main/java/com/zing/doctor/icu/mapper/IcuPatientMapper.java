package com.zing.doctor.icu.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * ICU 只读数据 Mapper（达梦 schema：zing_icu_db_prod）。
 *
 * <p>通过 {@code @DS("icu")} 走只读数据源（read-only: true），全部为查询。
 * 基于 ICU 系统真实表结构：patient_info / patient_info_diagnosis /
 * patient_info_lis_item / patient_observe_module_item_record /
 * config_observe_item / patient_advice_execute。
 *
 * <p>数据库为达梦 DM8，SQL 遵循达梦方言：
 * <ul>
 *   <li>标识符（schema/表/列）使用双引号保小写，与导出/实库一致："{@code zing_icu_db_prod}"."patient_info"</li>
 *   <li>日期减法用 SYSDATE - n（Oracle 兼容），不用 MySQL DATE_SUB/NOW</li>
 *   <li>单行取最新用 子查询 + ROWNUM &lt;= 1（Oracle 兼容），不用 LIMIT</li>
 *   <li>字符串比较/过滤用 LIKE/IN，避免 CAST 对非数字串抛错（数值过滤放 Java 端）</li>
 * </ul>
 */
@DS("icu")
@Mapper
public interface IcuPatientMapper {

    /**
     * 疑似感染/脓毒症在科患者（含基础信息与多耐药标记）。
     * 筛选条件：在科 + 未删除，且满足下列任一：
     *  1) 脓毒性休克标记；
     *  2) 诊断含感染/脓毒/肺炎/腹腔/血流/尿路/真菌等关键词；
     *  3) 近期有降钙素原（PCT）检验结果（>0.5 ng/mL 的数值过滤在 Java 端完成，避免达梦 CAST 报错）。
     */
    @Select("SELECT pi.id AS patient_id, pi.in_hospital_no AS patient_no, pi.name AS name, "
            + "pi.age AS age, pi.gender AS gender, pi.ward_name AS department, "
            + "pi.bed_code AS bed_no, pi.is_sepsis_shock AS septic_shock, "
            + "pi.multidrug_resistant_bacteria AS mdr_bacteria, "
            + "pi.resistant_bacteria AS resistant_bacteria, "
            + "pi.weight AS weight, pi.height AS height, pi.allergy_content AS allergy_content, "
            + "pi.in_depart_time AS in_depart_time "
            + "FROM \"zing_icu_db_prod\".\"patient_info\" pi "
            + "WHERE pi.is_in_depart = 1 AND pi.del_flag = 0 "
            + "AND (pi.is_sepsis_shock = 1 "
            + "  OR EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" d "
            + "             WHERE d.patient_id = pi.id AND d.del_flag = 0 AND d.status = 1 "
            + "               AND (d.diag_name LIKE '%脓毒%' OR d.diag_name LIKE '%感染%' "
            + "                    OR d.diag_name LIKE '%肺炎%' OR d.diag_name LIKE '%腹腔%' "
            + "                    OR d.diag_name LIKE '%腹膜炎%' OR d.diag_name LIKE '%血流%' "
            + "                    OR d.diag_name LIKE '%菌血症%' OR d.diag_name LIKE '%尿路%' "
            + "                    OR d.diag_name LIKE '%泌尿%' OR d.diag_name LIKE '%真菌%')) "
            + "  OR EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info_lis_item\" li "
            + "             WHERE li.in_hospital_no = pi.in_hospital_no AND li.del_flag = 0 "
            + "               AND li.lis_item_name LIKE '%降钙素原%' "
            + "               AND li.lis_item_result IS NOT NULL AND li.lis_item_result <> '')) "
            + "ORDER BY pi.in_depart_time DESC")
    List<Map<String, Object>> selectSuspectPatients();

    /** 单患者基本信息（评估页用） */
    @Select("SELECT pi.id AS patient_id, pi.in_hospital_no AS patient_no, pi.name AS name, "
            + "pi.age AS age, pi.gender AS gender, pi.ward_name AS department, "
            + "pi.bed_code AS bed_no, pi.is_sepsis_shock AS septic_shock, "
            + "pi.multidrug_resistant_bacteria AS mdr_bacteria, "
            + "pi.resistant_bacteria AS resistant_bacteria, "
            + "pi.weight AS weight, pi.height AS height, pi.allergy_content AS allergy_content, "
            + "pi.in_depart_time AS in_depart_time "
            + "FROM \"zing_icu_db_prod\".\"patient_info\" pi "
            + "WHERE pi.id = #{patientId} AND pi.del_flag = 0")
    Map<String, Object> selectPatientById(@Param("patientId") String patientId);

    /** 按住院号（ICU 外链 inHospitalNo）查单患者（取最新在科记录） */
    @Select("SELECT * FROM ( "
            + "  SELECT pi.id AS patient_id, pi.in_hospital_no AS patient_no, pi.name AS name, "
            + "  pi.age AS age, pi.gender AS gender, pi.ward_name AS department, "
            + "  pi.bed_code AS bed_no, pi.is_sepsis_shock AS septic_shock, "
            + "  pi.multidrug_resistant_bacteria AS mdr_bacteria, "
            + "  pi.resistant_bacteria AS resistant_bacteria, "
            + "  pi.weight AS weight, pi.height AS height, pi.allergy_content AS allergy_content, "
            + "  pi.in_depart_time AS in_depart_time, pi.out_depart_time AS out_depart_time, "
            + "  pi.depart_code AS depart_code, "
            + "  CAST(pi.diagnosis_content AS VARCHAR(2000)) AS diagnosis "
            + "  FROM \"zing_icu_db_prod\".\"patient_info\" pi "
            + "  WHERE pi.in_hospital_no = #{inHospitalNo} AND pi.del_flag = 0 "
            + "  ORDER BY pi.in_depart_time DESC "
            + ") WHERE ROWNUM <= 1")
    Map<String, Object> selectPatientByInHospitalNo(@Param("inHospitalNo") String inHospitalNo);

    /** 患者诊断记录（感染类型推断、真菌风险） */
    @Select("SELECT d.diag_name AS diag_name, d.diag_time AS diag_time "
            + "FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" d "
            + "WHERE d.patient_id = #{patientId} AND d.del_flag = 0 AND d.status = 1 "
            + "ORDER BY d.diag_time DESC")
    List<Map<String, Object>> selectDiagnoses(@Param("patientId") String patientId);

    /**
     * 职工字典搜索（医生下拉框用）。
     * 过滤：user_id 非空、status=1（正常）、del_flag=0（未删除）。
     * 搜索：pinyin 首字母（不区分大小写）/ work_no 工号 / realname 姓名，任一匹配。
     */
    @Select("SELECT * FROM ( "
            + "  SELECT s.user_id AS user_id, s.realname AS realname, s.pinyin AS pinyin, "
            + "  s.work_no AS work_no, s.depart_name AS depart_name "
            + "  FROM \"zing_icu_db_prod\".\"config_staff\" s "
            + "  WHERE s.user_id IS NOT NULL AND s.user_id <> '' "
            + "  AND s.status = 1 AND s.del_flag = 0 "
            + "  AND ( UPPER(s.pinyin) LIKE UPPER(#{keyword}) || '%' "
            + "    OR s.work_no LIKE '%' || #{keyword} || '%' "
            + "    OR s.realname LIKE '%' || #{keyword} || '%' ) "
            + "  ORDER BY s.sort_no NULLS LAST, s.realname "
            + ") WHERE ROWNUM <= 50")
    List<Map<String, Object>> selectStaff(@Param("keyword") String keyword);

    /** 患者近期（7 天）检验明细（PCT/WBC/CRP/肌酐/乳酸等，Java 端按名称匹配；取最新 300 条防大表全量） */
    @Select("SELECT * FROM ( "
            + "  SELECT li.lis_item_name AS item_name, li.lis_item_short_name AS short_name, "
            + "  li.lis_item_result AS result, "
            + "  li.lis_item_unit AS unit, li.check_time AS check_time, "
            + "  li.lis_item_low_value AS low_value, li.lis_item_height_value AS height_value, "
            + "  li.lis_item_limit AS item_limit "
            + "  FROM \"zing_icu_db_prod\".\"patient_info_lis_item\" li "
            + "  WHERE li.in_hospital_no = #{inHospitalNo} AND li.del_flag = 0 "
            + "  AND li.lis_item_result IS NOT NULL AND li.lis_item_result <> '' "
            + "  AND li.check_time >= SYSDATE - 7 "
            + "  ORDER BY li.check_time DESC "
            + ") WHERE ROWNUM <= 300")
    List<Map<String, Object>> selectRecentLabs(@Param("inHospitalNo") String inHospitalNo);

    /** 患者最新体温（观察项记录 join 配置取体温项目，按名称/体征代码匹配；ROWNUM 取最新 1 条） */
    @Select("SELECT * FROM ( "
            + "  SELECT r.item_value AS item_value, r.item_time AS item_time "
            + "  FROM \"zing_icu_db_prod\".\"patient_observe_module_item_record\" r "
            + "  LEFT JOIN \"zing_icu_db_prod\".\"config_observe_item\" c "
            + "    ON c.item_code = r.item_code AND c.del_flag = 0 "
            + "  WHERE r.patient_id = #{patientId} AND r.del_flag = 0 "
            + "    AND (c.item_name LIKE '%体温%' OR c.sign_code IN ('T','BT','TEMP','TW')) "
            + "  ORDER BY r.item_time DESC "
            + ") WHERE ROWNUM <= 1")
    Map<String, Object> selectLatestTemperature(@Param("patientId") String patientId);

    /** 患者执行中/未执行医嘱（抗菌药在 Java 端按关键词过滤；取最新 100 条） */
    @Select("SELECT * FROM ( "
            + "  SELECT name AS advice_name, freq_name AS freq_name, "
            + "  drug_method_name AS method_name, plan_start_time AS start_time, "
            + "  status AS status "
            + "  FROM \"zing_icu_db_prod\".\"patient_advice_execute\" "
            + "  WHERE patient_id = #{patientId} AND del_flag = 0 AND status IN (0,1) "
            + "  ORDER BY plan_start_time DESC "
            + ") WHERE ROWNUM <= 100")
    List<Map<String, Object>> selectRunningAdvice(@Param("patientId") String patientId);

    /**
     * 当前抗菌药医嘱（直接查 patient_advice 表，不用 patient_advice_pda）。
     * 按 group_id + name 去重（同一组同一种药只取一条）。
     * 开始时间取 patient_advice.start_time（开医嘱时间）。
     * 溶媒（氯化钠/葡萄糖等）在 Java 层排除，只保留溶质抗菌药。
     */
    @Select("SELECT * FROM ( "
            + "  SELECT pa.group_id AS group_id, pa.group_id_mark AS group_id_mark, pa.name AS name, "
            + "  pa.drug_method_name AS method_name, pa.freq_name AS freq_name, "
            + "  pa.start_time AS start_time, "
            + "  ROW_NUMBER() OVER (PARTITION BY pa.group_id, pa.name ORDER BY pa.start_time DESC) rn "
            + "  FROM \"zing_icu_db_prod\".\"patient_advice\" pa "
            + "  WHERE pa.in_hospital_no = #{inHospitalNo} "
            + "  AND pa.del_flag = 0 AND pa.status = '1' AND pa.type_code = 'drug' "
            + ") WHERE rn = 1 "
            + "ORDER BY start_time DESC")
    List<Map<String, Object>> selectCurrentAbxAdvice(@Param("inHospitalNo") String inHospitalNo);

    /**
     * 患者微生物培养/药敏结果明细（patient_info_lis_item）。
     * 识别方式：明细项目名含 培养/药敏/涂片/镜检/鉴定；结果值即检出菌名或药敏结论。
     * LEFT JOIN patient_info_lis 尽力带出报告头（标本类型/报告名），关联键 lis_code。
     */
    @Select("SELECT * FROM ( "
            + "  SELECT li.lis_item_name AS item_name, li.lis_item_result AS result, "
            + "  li.lis_item_unit AS unit, li.lis_item_alarm_flag AS alarm_flag, "
            + "  li.check_time AS check_time, li.lis_code AS lis_code, "
            + "  l.lis_name AS report_name, l.lis_short_name AS speciman, "
            + "  l.model_type AS model_type "
            + "  FROM \"zing_icu_db_prod\".\"patient_info_lis_item\" li "
            + "  LEFT JOIN \"zing_icu_db_prod\".\"patient_info_lis\" l "
            + "    ON l.lis_code = li.lis_code AND l.del_flag = 0 "
            + "  WHERE li.in_hospital_no = #{inHospitalNo} AND li.del_flag = 0 "
            + "  AND (li.lis_item_name LIKE '%培养%' OR li.lis_item_name LIKE '%药敏%' "
            + "       "
            + "       "
            + "       ) "
            + "  ORDER BY li.check_time DESC "
            + ") WHERE ROWNUM <= 100")
    List<Map<String, Object>> selectMicrobiology(@Param("inHospitalNo") String inHospitalNo);

    /**
     * 患者 CRRT（持续肾脏替代治疗）记录。
     * 取最新一条未撤机（is_end=0）的记录，若无则取最新一条。
     */
    @Select("SELECT * FROM ( "
            + "  SELECT c.start_time AS start_time, c.end_time AS end_time, c.is_end AS is_end, "
            + "  c.crrt_plan AS crrt_plan, c.liquid_blood_rate AS blood_rate, "
            + "  c.liquid_exchange_rate AS exchange_rate, c.liquid_dialysis_rate AS dialysis_rate, "
            + "  c.crrt_dilute_method AS dilute_method, c.crrt_anticaking_method AS anticaking_method, "
            + "  c.duration AS duration, c.now_times AS now_times "
            + "  FROM \"zing_icu_db_prod\".\"patient_crrt_record\" c "
            + "  WHERE c.patient_id = #{patientId} AND c.del_flag = 0 AND c.status = 1 "
            + "  ORDER BY c.is_end ASC, c.start_time DESC "
            + ") WHERE ROWNUM <= 1")
    Map<String, Object> selectCrrtRecord(@Param("patientId") String patientId);

    /**
     * 批量查询有CRRT记录（未撤机 is_end=0）的患者ID，用于总览卡片CRRT标记。
     * 和详情 selectCrrtRecord 用同一张表 patient_crrt_record，保持一致。
     */
    @Select("SELECT DISTINCT c.patient_id AS patient_id "
            + "FROM \"zing_icu_db_prod\".\"patient_crrt_record\" c "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_info\" pi "
            + "  ON pi.id = c.patient_id AND pi.del_flag = 0 AND pi.is_in_depart = 1 "
            + "WHERE c.del_flag = 0 AND c.status = 1 AND c.is_end = 0 "
            + "AND (#{departCode} = \'\' OR pi.depart_code = #{departCode})")
    List<String> selectHandoverCrrtPatients(@Param("departCode") String departCode);

    /**
     * 患者出科统计：按出科时间范围和科室查询已出科患者。
     * 字段：患者姓名、床号、住院号、入科诊断、入科时间、出科时间、出科诊断、出科转归、出院时间、主管医生。
     *
     * <p><b>出科诊断不在这条 SQL 里取</b>：需要的是「离出科时间最近的前 3 条」，
     * 用相关子查询 + ROWNUM 只能取一条，改为由 {@link #selectDischargedDiagnoses(String, String, String)}
     * 批量取出后在 Java 侧拼串回填 out_diagnosis（见 HandoverServiceImpl#listDischargedPatients）。
     *
     * <p>出科转归：patient_info.out_vest_type 字典转文字（1-转科 2-出院 3-死亡 4-临终 5-自动转院）。
     */
    @Select("SELECT pi.id AS patient_id, pi.name AS patient_name, pi.bed_code AS bed_code, "
            + "pi.in_hospital_no AS in_hospital_no, "
            + "CAST(pi.diagnosis_content AS VARCHAR(2000)) AS diagnosis, pi.in_depart_time AS in_depart_time, "
            + "pi.out_depart_time AS out_depart_time, "
            + "CASE pi.out_vest_type WHEN 1 THEN '转科' WHEN 2 THEN '出院' WHEN 3 THEN '死亡' "
            + "  WHEN 4 THEN '临终' WHEN 5 THEN '自动转院' END AS out_vest_type, "
            + "pi.out_hospital_time AS out_hospital_time, "
            + "pi.charge_doctor_name AS charge_doctor "
            + "FROM \"zing_icu_db_prod\".\"patient_info\" pi "
            + "WHERE pi.del_flag = 0 "
            + "AND pi.out_depart_time IS NOT NULL "
            + "AND pi.out_depart_time >= #{startTime} AND pi.out_depart_time < #{endTime} "
            + "AND (#{departCode} = \'\' OR pi.depart_code = #{departCode}) "
            + "ORDER BY pi.out_depart_time DESC")
    List<Map<String, Object>> selectDischargedPatients(@Param("startTime") String startTime,
                                                         @Param("endTime") String endTime,
                                                         @Param("departCode") String departCode);

    /**
     * 患者出科统计：批量取每个患者「离出科时间最近的前 3 条诊断」（patient_id + diag_name，按优先级升序返回）。
     *
     * <p>与 {@link #selectDischargedPatients(String, String, String)} 用同一套出科口径
     * （del_flag / out_depart_time 区间 / 科室），排序口径也一致：
     * 1) 出科时间之前（含）的诊断优先；
     * 2) 其中 diag_time 最大的即离出科最近；
     * 3) 若该患者所有诊断都晚于出科时间或时间缺失，兜底取时间最早的一条。
     * 同样不做时间减法（避免 TIMESTAMP 相减得到 INTERVAL 导致 ABS 报错）。
     *
     * <p>为什么不用「相关子查询 + LISTAGG」：达梦对多层嵌套相关子查询的兼容性不稳，
     * 且窗口函数 ROW_NUMBER() 在本项目其它查询里已验证可用，拼串交给 Java 更可控。
     */
    @Select("SELECT t.patient_id AS patient_id, t.diag_name AS diag_name FROM ( "
            + "  SELECT pi.id AS patient_id, d.diag_name AS diag_name, "
            + "         ROW_NUMBER() OVER (PARTITION BY pi.id ORDER BY "
            + "           CASE WHEN d.diag_time IS NOT NULL AND d.diag_time <= pi.out_depart_time THEN 0 ELSE 1 END ASC, "
            + "           CASE WHEN d.diag_time IS NOT NULL AND d.diag_time <= pi.out_depart_time THEN d.diag_time END DESC, "
            + "           d.diag_time ASC) AS rn "
            + "  FROM \"zing_icu_db_prod\".\"patient_info\" pi "
            + "  INNER JOIN \"zing_icu_db_prod\".\"patient_info_diagnosis\" d "
            + "    ON d.patient_id = pi.id AND d.del_flag = 0 AND d.status = 1 AND d.diag_name IS NOT NULL "
            + "  WHERE pi.del_flag = 0 AND pi.out_depart_time IS NOT NULL "
            + "  AND pi.out_depart_time >= #{startTime} AND pi.out_depart_time < #{endTime} "
            + "  AND (#{departCode} = \'\' OR pi.depart_code = #{departCode}) "
            + ") t WHERE t.rn <= 3 "
            + "ORDER BY t.patient_id ASC, t.rn ASC")
    List<Map<String, Object>> selectDischargedDiagnoses(@Param("startTime") String startTime,
                                                        @Param("endTime") String endTime,
                                                        @Param("departCode") String departCode);

    /**
     * 患者 ECMO（体外膜肺氧合）记录。
     * 取最新一条未撤机（is_end=0）的记录，若无则取最新一条。
     */
    @Select("SELECT * FROM ( "
            + "  SELECT e.start_time AS start_time, e.end_time AS end_time, e.is_end AS is_end, "
            + "  e.auxiliary_mode AS auxiliary_mode, e.pipeline_model AS pipeline_model, "
            + "  e.place AS place, e.piping_duration AS piping_duration, e.now_times AS now_times "
            + "  FROM \"zing_icu_db_prod\".\"patient_ecmo_record\" e "
            + "  WHERE e.patient_id = #{patientId} AND e.del_flag = 0 AND e.status = 1 "
            + "  ORDER BY e.is_end ASC, e.start_time DESC "
            + ") WHERE ROWNUM <= 1")
    Map<String, Object> selectEcmoRecord(@Param("patientId") String patientId);

    // ==================================================================
    // 第三维度：抗菌药物使用强度（DDD）统计
    // ==================================================================

    /**
     * 查询所有启用的科室列表（用于科室筛选下拉框）。
     * 数据来源：sys_depart 表，status=1, del_flag=0。
     */
    @Select("SELECT id AS id, org_code AS org_code, depart_name AS depart_name, "
            + "depart_order AS depart_order "
            + "FROM \"zing_icu_db_prod\".\"sys_depart\" "
            + "WHERE status = 1 AND del_flag = 0 "
            + "ORDER BY depart_order ASC, depart_name ASC")
    List<Map<String, Object>> selectAllDepartments();

    /**
     * 查询指定时间范围内、指定科室的抗菌药物医嘱（用于 DDD 统计）。
     * 通过 patient_advice.in_hospital_no 关联 patient_info.depart_code 过滤科室。
     * departCode 为空时查全部科室。
     */
    @Select("SELECT * FROM ( "
            + "  SELECT pa.group_id AS group_id, pa.in_hospital_no AS in_hospital_no, "
            + "  pa.name AS name, pa.freq_name AS freq_name, "
            + "  pa.drug_method_name AS method_name, pa.start_time AS start_time, "
            + "  pa.plan_start_time AS plan_start_time, "
            + "  pa.drug_one_dosage AS drug_one_dosage, "
            + "  pa.drug_one_dosage_unit AS drug_one_dosage_unit, "
            + "  ROW_NUMBER() OVER (PARTITION BY pa.group_id, pa.name ORDER BY pa.start_time DESC) rn "
            + "  FROM \"zing_icu_db_prod\".\"patient_advice\" pa "
            + "  INNER JOIN \"zing_icu_db_prod\".\"patient_info\" pi "
            + "    ON pi.in_hospital_no = pa.in_hospital_no AND pi.del_flag = 0 "
            + "  WHERE pa.del_flag = 0 AND pa.status IN ('1','3') AND pa.type_code = 'drug' "
            + "  AND pa.start_time >= #{startTime} AND pa.start_time < #{endTime} "
            + "  AND pi.depart_code = #{departCode} "
            + ") WHERE rn = 1 "
            + "ORDER BY start_time DESC")
    List<Map<String, Object>> selectAbxAdviceForStats(@Param("startTime") String startTime,
                                                         @Param("endTime") String endTime,
                                                         @Param("departCode") String departCode);

    /**
     * 查询指定时间范围内、指定科室的在科患者列表（用于计算总床日数和使用率分母）。
     * departCode 为空时查全部科室。
     */
    @Select("SELECT pi.id AS patient_id, pi.in_hospital_no AS in_hospital_no, pi.name AS name, "
            + "pi.gender AS gender, pi.age AS age, "
            + "pi.ward_name AS department, pi.bed_code AS bed_no, "
            + "pi.in_depart_time AS in_depart_time, pi.out_depart_time AS out_depart_time, "
            + "pi.is_in_depart AS is_in_depart "
            + "FROM \"zing_icu_db_prod\".\"patient_info\" pi "
            + "WHERE pi.del_flag = 0 "
            + "AND pi.in_depart_time < #{endTime} "
            + "AND (pi.out_depart_time >= #{startTime} OR pi.out_depart_time IS NULL) "
            + "AND pi.depart_code = #{departCode} "
            + "ORDER BY pi.in_depart_time DESC")
    List<Map<String, Object>> selectPatientsForStats(@Param("startTime") String startTime,
                                                        @Param("endTime") String endTime,
                                                        @Param("departCode") String departCode);

    /**
     * 查询指定时间范围内、指定科室的细菌培养记录（用于第四维度统计）。
     * 通过 patient_info_lis_item.in_hospital_no 关联 patient_info.depart_code 过滤科室。
     * 筛选条件：lis_item_name LIKE '细菌培养+药敏%'（含普通瓶和厌氧瓶），lis_item_result 非空（阳性结果）。
     * departCode 为空时查全部科室。
     */
    @Select("SELECT li.id AS id, li.in_hospital_no AS in_hospital_no, "
            + "li.lis_code AS lis_code, li.lis_item_name AS lis_item_name, "
            + "li.lis_item_result AS bacteria_name, li.check_time AS detect_time, "
            + "l.lis_short_name AS specimen_type, l.lis_name AS specimen_name, "
            + "l.lis_order_ward_name AS order_ward, l.lis_order_doctor_name AS order_doctor, "
            + "pi.name AS patient_name, pi.gender AS gender, pi.age AS age, "
            + "pi.ward_name AS department, pi.bed_code AS bed_no, "
            + "pi.in_depart_time AS in_depart_time, pi.out_depart_time AS out_depart_time, "
            + "pi.is_in_depart AS is_in_depart "
            + "FROM \"zing_icu_db_prod\".\"patient_info_lis_item\" li "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_info_lis\" l "
            + "    ON l.lis_code = li.lis_code AND l.del_flag = 0 "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_info\" pi "
            + "    ON pi.in_hospital_no = li.in_hospital_no AND pi.del_flag = 0 "
            + "WHERE li.del_flag = 0 AND li.status = 1 "
            + "AND li.lis_item_name LIKE '细菌培养+药敏%' "
            + "AND li.lis_item_result IS NOT NULL AND li.lis_item_result <> '' "
            + "AND li.lis_item_result <> '未检出' AND li.lis_item_result <> '无细菌生长' "
            + "AND li.lis_item_result <> '阴性' "
            + "AND li.check_time >= #{startTime} AND li.check_time < #{endTime} "
            + "AND pi.in_depart_time < #{endTime} "
            + "AND (pi.out_depart_time >= #{startTime} OR pi.out_depart_time IS NULL) "
            + "AND pi.depart_code = #{departCode} "
            + "ORDER BY li.check_time DESC")
    List<Map<String, Object>> selectBacteriaCultureForStats(@Param("startTime") String startTime,
                                                               @Param("endTime") String endTime,
                                                               @Param("departCode") String departCode);

    /**
     * 查询指定时间范围内、指定科室的所有细菌培养送检记录（含阴性，用于计算送检率）。
     * lis_item_name LIKE '细菌培养+药敏%'（含普通瓶和厌氧瓶）。
     * departCode 为空时查全部科室。
     */
    @Select("SELECT li.id AS id, li.in_hospital_no AS in_hospital_no, "
            + "li.lis_code AS lis_code, li.lis_item_name AS lis_item_name, "
            + "li.lis_item_result AS bacteria_name, li.check_time AS detect_time, "
            + "l.lis_short_name AS specimen_type, "
            + "pi.name AS patient_name, pi.depart_code AS depart_code "
            + "FROM \"zing_icu_db_prod\".\"patient_info_lis_item\" li "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_info_lis\" l "
            + "    ON l.lis_code = li.lis_code AND l.del_flag = 0 "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_info\" pi "
            + "    ON pi.in_hospital_no = li.in_hospital_no AND pi.del_flag = 0 "
            + "WHERE li.del_flag = 0 AND li.status = 1 "
            + "AND li.lis_item_name LIKE '细菌培养+药敏%' "
            + "AND li.check_time >= #{startTime} AND li.check_time < #{endTime} "
            + "AND pi.in_depart_time < #{endTime} "
            + "AND (pi.out_depart_time >= #{startTime} OR pi.out_depart_time IS NULL) "
            + "AND pi.depart_code = #{departCode} "
            + "ORDER BY li.check_time DESC")
    List<Map<String, Object>> selectAllBacteriaCultureForStats(@Param("startTime") String startTime,
                                                                   @Param("endTime") String endTime,
                                                                   @Param("departCode") String departCode);

    /**
     * 查询患者乳酸检验结果（脓毒症集束化治疗用）
     * 精确匹配"乳酸"项目，排除"乳酸脱氢酶"等非乳酸项目
     */
    @Select("SELECT li.id AS id, li.in_hospital_no AS in_hospital_no, "
            + "li.lis_item_name AS lis_item_name, li.lis_item_result AS result, "
            + "li.lis_item_unit AS unit, li.check_time AS check_time, "
            + "l.lis_short_name AS specimen_type "
            + "FROM \"zing_icu_db_prod\".\"patient_info_lis_item\" li "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_info_lis\" l "
            + "    ON l.lis_code = li.lis_code AND l.del_flag = 0 "
            + "WHERE li.del_flag = 0 AND li.status = 1 "
            + "AND li.in_hospital_no = #{inHospitalNo} "
            + "AND (li.lis_item_name LIKE '%乳酸%' OR li.lis_item_short_name LIKE '%乳酸%') "
            + "AND li.lis_item_name NOT LIKE '%脱氢酶%' "
            + "AND li.lis_item_result IS NOT NULL AND li.lis_item_result <> '' "
            + "ORDER BY li.check_time ASC")
    List<Map<String, Object>> selectLactateResults(@Param("inHospitalNo") String inHospitalNo);

    /**
     * 查询患者晶体液医嘱（脓毒症集束化治疗液体复苏用）
     */
    @Select("SELECT pa.id AS id, pa.in_hospital_no AS in_hospital_no, "
            + "pa.name AS name, pa.freq_name AS freq_name, "
            + "pa.drug_method_name AS method_name, pa.start_time AS start_time, "
            + "pa.drug_one_dosage AS drug_one_dosage, "
            + "pa.drug_one_dosage_unit AS drug_one_dosage_unit "
            + "FROM \"zing_icu_db_prod\".\"patient_advice\" pa "
            + "WHERE pa.del_flag = 0 AND pa.status IN ('1','3') AND pa.type_code = 'drug' "
            + "AND pa.in_hospital_no = #{inHospitalNo} "
            + "AND (pa.name LIKE '%氯化钠%' OR pa.name LIKE '%葡萄糖%' "
            + "     OR pa.name LIKE '%乳酸钠林格%' OR pa.name LIKE '%林格%' "
            + "     OR pa.name LIKE '%复方氯化钠%' OR pa.name LIKE '%灭菌注射用水%') "
            + "ORDER BY pa.start_time ASC")
    List<Map<String, Object>> selectFluidAdvice(@Param("inHospitalNo") String inHospitalNo);

    /**
     * 查询患者去甲肾上腺素/血管升压药医嘱（脓毒症集束化治疗用）
     */
    @Select("SELECT pa.id AS id, pa.in_hospital_no AS in_hospital_no, "
            + "pa.name AS name, pa.freq_name AS freq_name, "
            + "pa.drug_method_name AS method_name, pa.start_time AS start_time, "
            + "pa.drug_one_dosage AS drug_one_dosage, "
            + "pa.drug_one_dosage_unit AS drug_one_dosage_unit "
            + "FROM \"zing_icu_db_prod\".\"patient_advice\" pa "
            + "WHERE pa.del_flag = 0 AND pa.status IN ('1','3') AND pa.type_code = 'drug' "
            + "AND pa.in_hospital_no = #{inHospitalNo} "
            + "AND pa.start_time >= #{startTime} AND pa.start_time < #{endTime} "
            + "AND (pa.name LIKE '%去甲肾上腺素%' OR pa.name LIKE '%多巴胺%' "
            + "     OR pa.name LIKE '%肾上腺素%' OR pa.name LIKE '%间羟胺%' "
            + "     OR pa.name LIKE '%多巴酚丁胺%' OR pa.name LIKE '%血管升压%') "
            + "ORDER BY pa.start_time ASC")
    List<Map<String, Object>> selectVasopressorAdvice(@Param("inHospitalNo") String inHospitalNo,
                                                        @Param("startTime") String startTime,
                                                        @Param("endTime") String endTime);

    /** 重载：不限时间范围（脓毒症集束化治疗用） */
    default List<Map<String, Object>> selectVasopressorAdvice(String inHospitalNo) {
        return selectVasopressorAdvice(inHospitalNo, "1970-01-01 00:00:00", "2099-12-31 23:59:59");
    }

    /**
     * 当前在用血管活性/升压药医嘱（status='1' 执行中），按住院号查询。
     * 和总览卡片 selectHandoverVasopressor 逻辑保持一致，确保里外显示一致。
     */
    @Select("SELECT pa.id AS id, pa.in_hospital_no AS in_hospital_no, "
            + "pa.name AS name, pa.freq_name AS freq_name, "
            + "pa.drug_method_name AS method_name, pa.start_time AS start_time, "
            + "pa.drug_one_dosage AS drug_one_dosage, "
            + "pa.drug_one_dosage_unit AS drug_one_dosage_unit "
            + "FROM \"zing_icu_db_prod\".\"patient_advice\" pa "
            + "WHERE pa.del_flag = 0 AND pa.status = '1' AND pa.type_code = 'drug' "
            + "AND pa.in_hospital_no = #{inHospitalNo} "
            + "AND (pa.name LIKE '%去甲肾上腺素%' OR pa.name LIKE '%多巴胺%' "
            + "  OR pa.name LIKE '%肾上腺素%' OR pa.name LIKE '%间羟胺%' "
            + "  OR pa.name LIKE '%多巴酚丁胺%' OR pa.name LIKE '%血管加压素%' "
            + "  OR pa.name LIKE '%垂体后叶%' OR pa.name LIKE '%特利加压素%') "
            + "ORDER BY pa.start_time DESC")
    List<Map<String, Object>> selectCurrentVasopressorAdvice(@Param("inHospitalNo") String inHospitalNo);

    /**
     * 查询患者诊断信息（脓毒症集束化治疗感染部位/并发症判断用）
     */
    @Select("SELECT d.id AS id, d.patient_id AS patient_id, "
            + "d.diag_name AS diag_name, d.diag_type AS diag_type, "
            + "d.diag_time AS diag_time, d.create_time AS create_time "
            + "FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" d "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_info\" pi "
            + "    ON pi.id = d.patient_id AND pi.del_flag = 0 "
            + "WHERE d.del_flag = 0 AND d.status = 1 "
            + "AND pi.in_hospital_no = #{inHospitalNo} "
            + "ORDER BY d.diag_time DESC")
    List<Map<String, Object>> selectDiagnosis(@Param("inHospitalNo") String inHospitalNo);

    /**
     * 查询患者抗菌药物医嘱（脓毒症集束化治疗用，按开始时间排序）
     * 过滤条件：排除溶媒（氯化钠/葡萄糖/林格等），只保留抗菌药物
     */
    @Select("SELECT * FROM ( "
            + "  SELECT pa.group_id AS group_id, pa.in_hospital_no AS in_hospital_no, "
            + "  pa.name AS name, pa.freq_name AS freq_name, "
            + "  pa.drug_method_name AS method_name, pa.start_time AS start_time, "
            + "  pa.drug_one_dosage AS drug_one_dosage, pa.drug_one_dosage_unit AS drug_one_dosage_unit, "
            + "  ROW_NUMBER() OVER (PARTITION BY pa.group_id, pa.name ORDER BY pa.start_time DESC) rn "
            + "  FROM \"zing_icu_db_prod\".\"patient_advice\" pa "
            + "  WHERE pa.del_flag = 0 AND pa.status IN ('1','3') AND pa.type_code = 'drug' "
            + "  AND pa.in_hospital_no = #{inHospitalNo} "
            + "  AND pa.start_time >= #{startTime} AND pa.start_time < #{endTime} "
            + "  AND pa.name NOT LIKE '%氯化钠%' AND pa.name NOT LIKE '%葡萄糖%' "
            + "  AND pa.name NOT LIKE '%林格%' AND pa.name NOT LIKE '%灭菌注射用水%' "
            + "  AND pa.name NOT LIKE '%木糖醇%' AND pa.name NOT LIKE '%转化糖%' "
            + "  AND pa.name NOT LIKE '%果糖%' AND pa.name NOT LIKE '%甘油果糖%' "
            + ") WHERE rn = 1 "
            + "ORDER BY start_time ASC")
    List<Map<String, Object>> selectAntibioticAdvice(@Param("inHospitalNo") String inHospitalNo,
                                                       @Param("startTime") String startTime,
                                                       @Param("endTime") String endTime);

    /** 重载：不限时间范围（脓毒症集束化治疗用） */
    default List<Map<String, Object>> selectAntibioticAdvice(String inHospitalNo) {
        return selectAntibioticAdvice(inHospitalNo, "1970-01-01 00:00:00", "2099-12-31 23:59:59");
    }

    /**
     * 查询患者血培养送检记录（脓毒症集束化治疗用，只统计血液标本）
     * 筛选条件：lis_item_name LIKE '细菌培养+药敏%'，标本类型包含"血"
     */
    @Select("SELECT li.id AS id, li.in_hospital_no AS in_hospital_no, "
            + "li.lis_code AS lis_code, li.lis_item_name AS lis_item_name, "
            + "li.lis_item_result AS result, li.check_time AS check_time, "
            + "l.lis_short_name AS specimen_type, l.simple_time AS sample_time "
            + "FROM \"zing_icu_db_prod\".\"patient_info_lis_item\" li "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_info_lis\" l "
            + "    ON l.lis_code = li.lis_code AND l.del_flag = 0 "
            + "WHERE li.del_flag = 0 AND li.status = 1 "
            + "AND li.in_hospital_no = #{inHospitalNo} "
            + "AND li.lis_item_name LIKE '细菌培养+药敏%' "
            + "AND (l.lis_short_name LIKE '%血%' OR l.lis_name LIKE '%血%') "
            + "ORDER BY li.check_time ASC")
    List<Map<String, Object>> selectBloodCultureRecords(@Param("inHospitalNo") String inHospitalNo);

    /**
     * 查询患者监护评估项目数据（脓毒症集束化治疗"3小时后评估"用）
     * 关联 patient_observe_module_item 按 patient_id + item_code 取项目名称，
     * 项目名称过滤（CVP/MAP/ScvO2 等）在 Java 层按 item_name 匹配。
     * 返回指定时间窗口内的记录，按 item_time 倒序（Java 层取最新一条）。
     */
    @Select("SELECT r.item_code AS item_code, r.item_value AS item_value, "
            + "r.item_time AS item_time, r.item_unit AS item_unit, "
            + "i.item_name AS item_name "
            + "FROM \"zing_icu_db_prod\".\"patient_observe_module_item_record\" r "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_observe_module_item\" i "
            + "    ON i.patient_id = r.patient_id AND i.item_code = r.item_code AND i.del_flag = 0 "
            + "WHERE r.patient_id = #{patientId} AND r.del_flag = 0 AND r.status = 1 "
            + "AND i.del_flag = 0 AND i.status = 1 "
            + "AND r.item_time >= #{startTime} AND r.item_time < #{endTime} "
            + "ORDER BY r.item_time DESC")
    List<Map<String, Object>> selectObserveRecords(@Param("patientId") String patientId,
                                                   @Param("startTime") String startTime,
                                                   @Param("endTime") String endTime);

    /**
     * 按lis_item_code列表查询患者检验结果（APACHE II评分用）
     */
    @Select("<script>"
            + "SELECT li.lis_item_code AS item_code, li.lis_item_result AS item_value, "
            + "li.check_time AS item_time, li.lis_item_unit AS item_unit, "
            + "li.lis_item_name AS item_name "
            + "FROM \"zing_icu_db_prod\".\"patient_info_lis_item\" li "
            + "WHERE li.in_hospital_no = #{inHospitalNo} "
            + "AND li.del_flag = 0 "
            + "AND li.lis_item_result IS NOT NULL AND li.lis_item_result &lt;&gt; '' "
            + "AND li.check_time &gt;= #{startTime} AND li.check_time &lt; #{endTime} "
            + "AND li.lis_item_code IN "
            + "<foreach collection='itemCodes' item='code' open='(' separator=',' close=')'>"
            + "#{code}"
            + "</foreach>"
            + "ORDER BY li.check_time DESC"
            + "</script>")
    List<Map<String, Object>> selectLabRecordsByCodes(@Param("inHospitalNo") String inHospitalNo,
                                                        @Param("itemCodes") List<String> itemCodes,
                                                        @Param("startTime") String startTime,
                                                        @Param("endTime") String endTime);

    /**
     * 按lis_item_name列表查询动脉血气分析结果（APACHE II评分用：PH值、氧分压PaO2、二氧化碳分压PaCO2）。
     * 关联 patient_info_lis（动脉血+血气分析）→ patient_info_lis_item，按check_time倒序。
     */
    @Select("<script>"
            + "SELECT item.lis_item_name AS item_name, item.lis_item_result AS item_value, "
            + "item.check_time AS item_time, item.lis_item_unit AS item_unit "
            + "FROM \"zing_icu_db_prod\".\"patient_info_lis\" lis "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_info_lis_item\" item "
            + "  ON lis.lis_code = item.lis_code AND item.del_flag = 0 "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_info\" pi "
            + "  ON pi.in_hospital_no = item.in_hospital_no AND pi.del_flag = 0 "
            + "WHERE pi.id = #{patientId} "
            + "AND lis.del_flag = 0 "
            + "AND lis.lis_short_name = '动脉血' "
            + "AND lis.lis_name LIKE '%血气分析%' "
            + "AND item.lis_item_result IS NOT NULL AND item.lis_item_result &lt;&gt; '' "
            + "AND item.check_time &gt;= #{startTime} AND item.check_time &lt; #{endTime} "
            + "AND item.lis_item_name IN "
            + "<foreach collection='itemNames' item='name' open='(' separator=',' close=')'>"
            + "#{name}"
            + "</foreach>"
            + "ORDER BY item.check_time DESC"
            + "</script>")
    List<Map<String, Object>> selectBloodGasRecords(@Param("patientId") String patientId,
                                                     @Param("itemNames") List<String> itemNames,
                                                     @Param("startTime") String startTime,
                                                     @Param("endTime") String endTime);

    /**
     * 查询患者出入量项目数据（脓毒症集束化治疗"3小时后评估"用）
     * 关联 patient_io_module_item 按 patient_id + item_code 取项目名称，
     * 尿量等按 patient_io_module_item 的项目名称匹配（Java 层）。
     * 返回指定时间窗口内的记录，按 item_time 倒序（Java 层取最新一条）。
     */
    @Select("SELECT r.item_code AS item_code, r.item_value AS item_value, "
            + "r.item_time AS item_time, r.item_unit AS item_unit, "
            + "i.item_name AS item_name "
            + "FROM \"zing_icu_db_prod\".\"patient_io_module_item_record\" r "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_io_module_item\" i "
            + "    ON i.patient_id = r.patient_id AND i.item_code = r.item_code AND i.del_flag = 0 "
            + "WHERE r.patient_id = #{patientId} AND r.del_flag = 0 AND r.status = 1 "
            + "AND i.del_flag = 0 AND i.status = 1 "
            + "AND r.item_time >= #{startTime} AND r.item_time < #{endTime} "
            + "ORDER BY r.item_time DESC")
    List<Map<String, Object>> selectIoRecords(@Param("patientId") String patientId,
                                              @Param("startTime") String startTime,
                                              @Param("endTime") String endTime);

    /**
     * 单患者医嘱执行入量明细（is_to_io=1，用于交班详情出入量明细）。
     */
    @Select("SELECT paei.amount AS item_value, paei.execute_hour AS item_time, "
            + "pae.name AS item_name, '医嘱入量' AS item_unit "
            + "FROM \"zing_icu_db_prod\".\"patient_advice_execute\" pae "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_advice_execute_item\" paei "
            + "  ON pae.id = paei.execute_id "
            + "WHERE pae.is_to_io = \'1\' AND pae.del_flag = \'0\' "
            + "AND paei.patient_id = #{patientId} "
            + "AND paei.execute_hour >= #{startTime} AND paei.execute_hour < #{endTime} "
            + "ORDER BY paei.execute_hour DESC")
    List<Map<String, Object>> selectPatientAdviceIntake(@Param("patientId") String patientId,
                                                          @Param("startTime") String startTime,
                                                          @Param("endTime") String endTime);

    // ==================================================================
    // 第五维度：医生交班览表（批量取数，全部按科室一次查出，Java 端分组，禁止 N+1）
    // ==================================================================

    /**
     * 读取科室"全天"班次配置（config_shift.is_all=1），用于解析"上一完整全天班"区间。
     * 精确匹配科室优先；调用方在查不到时回退默认 07:01 -> 次日 07:00。
     * 业务数据来源：zing_icu_db_prod.config_shift
     */
    @Select("SELECT shift_name AS shift_name, shift_begin_time AS shift_begin_time, "
            + "shift_end_time AS shift_end_time, is_begin_now AS is_begin_now, "
            + "is_end_now AS is_end_now, is_all AS is_all "
            + "FROM \"zing_icu_db_prod\".\"config_shift\" "
            + "WHERE status = 1 AND del_flag = 0 AND is_all = 1 "
            + "AND (depart_code = #{departCode} OR depart_code IS NULL) "
            + "ORDER BY CASE WHEN depart_code = #{departCode} THEN 0 ELSE 1 END")
    List<Map<String, Object>> selectShiftConfig(@Param("departCode") String departCode);

    /**
     * 当前在科患者基础信息（交班览表主体，按床号排序）。
     * 业务数据来源：zing_icu_db_prod.patient_info，is_in_depart=1 且未删除。
     */
    @Select("SELECT pi.id AS patient_id, pi.in_hospital_no AS in_hospital_no, "
            + "pi.name AS name, pi.gender AS gender, pi.age AS age, pi.age_unit AS age_unit, "
            + "pi.bed_code AS bed_code, pi.in_depart_time AS in_depart_time, "
            + "pi.out_depart_time AS out_depart_time, pi.is_in_depart AS is_in_depart, "
            + "CAST(pi.diagnosis_content AS VARCHAR(2000)) AS diagnosis_content, pi.allergy_content AS allergy_content, "
            + "pi.charge_doctor_name AS charge_doctor_name, pi.resident_doctor_name AS resident_doctor_name, "
            + "pi.weight AS weight, pi.ventilator_code AS ventilator_code, "
            + "pi.crrt_device_code AS crrt_device_code, pi.ecmo_device_code AS ecmo_device_code, "
            + "pi.isolation_status AS isolation_status, pi.isolation_value AS isolation_value, "
            + "pi.is_sepsis_shock AS is_sepsis_shock, pi.is_ards AS is_ards, "
            + "pi.nursing_grade AS nursing_grade, pi.cure_grade AS cure_grade, "
            + "pi.depart_code AS depart_code, pi.ward_name AS ward_name "
            + "FROM \"zing_icu_db_prod\".\"patient_info\" pi "
            + "WHERE pi.del_flag = 0 AND pi.status = 1 AND pi.is_in_depart = 1 "
            + "AND pi.depart_code = #{departCode} "
            + "ORDER BY CASE WHEN REGEXP_LIKE(pi.bed_code, '^[A-Za-z]') THEN 0 ELSE 1 END, pi.bed_code ASC")
    List<Map<String, Object>> selectHandoverWardPatients(@Param("departCode") String departCode);

    /**
     * 批量监护记录（最新生命体征用）：record JOIN item 取项目名，再 JOIN patient_info 限定科室与在科。
     * 时间窗口取 [班次起点, 当前时刻]，Java 端按患者+项目类别归一并取最新一条。
     * 业务数据来源：patient_observe_module_item_record + patient_observe_module_item + patient_info
     */
    @Select("SELECT pi.id AS patient_id, r.item_code AS item_code, r.item_value AS item_value, "
            + "r.item_time AS item_time, r.item_unit AS item_unit, i.item_name AS item_name "
            + "FROM \"zing_icu_db_prod\".\"patient_observe_module_item_record\" r "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_observe_module_item\" i "
            + "  ON i.patient_id = r.patient_id AND i.item_code = r.item_code AND i.del_flag = 0 "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_info\" pi "
            + "  ON pi.id = r.patient_id AND pi.del_flag = 0 AND pi.is_in_depart = 1 "
            + "WHERE r.del_flag = 0 AND r.status = 1 AND i.status = 1 "
            + "AND r.item_time >= #{startTime} AND r.item_time <= #{endTime} "
            + "AND pi.depart_code = #{departCode} "
            + "ORDER BY r.item_time DESC")
    List<Map<String, Object>> selectHandoverObserve(@Param("departCode") String departCode,
                                                     @Param("startTime") String startTime,
                                                     @Param("endTime") String endTime);

    /**
     * 批量出入量记录（封板班次区间内求和）：record JOIN item，Java 端按项目名分类累加。
     * 业务数据来源：patient_io_module_item_record + patient_io_module_item + patient_info
     */
    /**
     * 出量（参考VW_PATIENT_IO：用 config_io_item.io_type='o' 区分出量）。
     * 业务来源：patient_io_module_item_record + config_io_item + patient_info
     */
    @Select("SELECT pi.id AS patient_id, r.item_code AS item_code, r.item_value AS item_value, "
            + "r.item_time AS item_time, r.item_unit AS item_unit "
            + "FROM \"zing_icu_db_prod\".\"patient_io_module_item_record\" r "
            + "INNER JOIN \"zing_icu_db_prod\".\"config_io_item\" c "
            + "  ON r.item_code = c.item_code AND c.io_type = \'o\' "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_info\" pi "
            + "  ON pi.id = r.patient_id AND pi.del_flag = 0 AND pi.is_in_depart = 1 "
            + "WHERE r.del_flag = 0 AND r.status = 1 "
            + "AND r.item_time >= #{startTime} AND r.item_time < #{endTime} "
            + "AND pi.depart_code = #{departCode} "
            + "ORDER BY r.item_time DESC")
    List<Map<String, Object>> selectHandoverOutput(@Param("departCode") String departCode,
                                                    @Param("startTime") String startTime,
                                                    @Param("endTime") String endTime);

    /**
     * 其它入量（非药品入量，参考VW_PATIENT_IO：module_code='io_in'）。
     * 业务来源：patient_io_module_item_record + patient_io_module_item + patient_info
     */
    @Select("SELECT pi.id AS patient_id, r.item_code AS item_code, r.item_value AS item_value, "
            + "r.item_time AS item_time, r.item_unit AS item_unit, i.item_name AS item_name "
            + "FROM \"zing_icu_db_prod\".\"patient_io_module_item_record\" r "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_io_module_item\" i "
            + "  ON i.patient_id = r.patient_id AND i.item_code = r.item_code AND i.del_flag = 0 "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_info\" pi "
            + "  ON pi.id = r.patient_id AND pi.del_flag = 0 AND pi.is_in_depart = 1 "
            + "WHERE r.del_flag = 0 AND r.status = 1 AND i.status = 1 "
            + "AND i.module_code = \'io_in\' "
            + "AND r.item_time >= #{startTime} AND r.item_time < #{endTime} "
            + "AND pi.depart_code = #{departCode} "
            + "ORDER BY r.item_time DESC")
    List<Map<String, Object>> selectHandoverOtherIntake(@Param("departCode") String departCode,
                                                          @Param("startTime") String startTime,
                                                          @Param("endTime") String endTime);

    /**
     * 尿量（参考VW_PATIENT_IO：固定 item_code='ii_nl'）。
     * 业务来源：patient_io_module_item_record + patient_info
     */
    @Select("SELECT pi.id AS patient_id, r.item_value AS item_value, r.item_time AS item_time "
            + "FROM \"zing_icu_db_prod\".\"patient_io_module_item_record\" r "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_info\" pi "
            + "  ON pi.id = r.patient_id AND pi.del_flag = 0 AND pi.is_in_depart = 1 "
            + "WHERE r.del_flag = 0 AND r.status = 1 "
            + "AND r.item_code = \'ii_nl\' "
            + "AND r.item_time >= #{startTime} AND r.item_time < #{endTime} "
            + "AND pi.depart_code = #{departCode} "
            + "ORDER BY r.item_time DESC")
    List<Map<String, Object>> selectHandoverUrine(@Param("departCode") String departCode,
                                                    @Param("startTime") String startTime,
                                                    @Param("endTime") String endTime);

    /**
     * 导尿管判断（参考VW_PATIENT_IO：patient_tube 表 tube_code='tube_导尿管'，班次内未拔除）。
     * 返回有导尿管的 patient_id 列表。
     */
    @Select("SELECT DISTINCT pt.patient_id "
            + "FROM \"zing_icu_db_prod\".\"patient_tube\" pt "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_info\" pi "
            + "  ON pi.id = pt.patient_id AND pi.del_flag = 0 AND pi.is_in_depart = 1 "
            + "WHERE pt.tube_code = \'tube_导尿管\' "
            + "AND (pt.tube_set_start_time < #{endTime} OR pt.take_time < #{endTime}) "
            + "AND ( "
            + "  (pt.tube_set_end_time IS NULL AND pt.tube_take_away_time IS NULL) "
            + "  OR pt.tube_set_end_time >= #{startTime} "
            + "  OR pt.tube_take_away_time >= #{startTime} "
            + ") "
            + "AND (#{departCode} = '' OR pi.depart_code = #{departCode})")
    List<String> selectHandoverCatheter(@Param("departCode") String departCode,
                                         @Param("startTime") String startTime,
                                         @Param("endTime") String endTime);

    /**
     * 药品入量（参考VW_PATIENT_IO：is_to_io=1，含07:00交接班边界处理）。
     * 边界逻辑：
     *   - 当天07:00执行且07:01后有执行/恢复步骤 → 全额计入
     *   - 当天07:00执行且无07:01后步骤 → 扣除前一分钟(speed/60)
     *   - 次日07:00执行且07:01后有步骤 → 不计入(0)
     *   - 次日07:00执行且无07:01后步骤 → 只计前一分钟(speed/60)
     *   - 其他 → 全额计入
     * 业务来源：patient_advice_execute + patient_advice_execute_item + patient_advice_execute_step + patient_info
     */
    @Select("SELECT pi.id AS patient_id, "
            + "CAST(CASE "
            + "  WHEN paei.execute_hour = CAST(#{shiftDate} || \' 07:00:00\' AS DATETIME) "
            + "   AND EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice_execute_step\" step "
            + "     WHERE step.execute_id = paei.execute_id AND step.step_type_name IN (\'执行\',\'恢复\') "
            + "     AND step.step_time >= CAST(#{shiftDate} || \' 07:01:00\' AS DATETIME) "
            + "     AND step.step_time < CAST(#{shiftDate} || \' 08:00:00\' AS DATETIME)) "
            + "  THEN paei.amount "
            + "  WHEN paei.execute_hour = CAST(#{shiftDate} || \' 07:00:00\' AS DATETIME) AND paei.amount <> 0 "
            + "  THEN paei.amount - CAST((SELECT step.speed FROM \"zing_icu_db_prod\".\"patient_advice_execute_step\" step "
            + "     WHERE step.execute_id = paei.execute_id AND step.step_time <= paei.execute_hour "
            + "     AND step.del_flag = \'0\' ORDER BY step.step_time DESC LIMIT 1) / 60 AS DECIMAL(10,2)) "
            + "  WHEN paei.execute_hour = CAST(#{shiftDate} || \' 07:00:00\' AS DATETIME) + 1 "
            + "   AND EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice_execute_step\" step "
            + "     WHERE step.execute_id = paei.execute_id AND step.step_type_name IN (\'执行\',\'恢复\') "
            + "     AND step.step_time >= CAST(#{shiftDate} || \' 07:00:00\' AS DATETIME) + 1 "
            + "     AND step.step_time < CAST(#{shiftDate} || \' 08:00:00\' AS DATETIME) + 1) "
            + "  THEN 0 "
            + "  WHEN paei.execute_hour = CAST(#{shiftDate} || \' 07:00:00\' AS DATETIME) + 1 "
            + "  THEN CAST((SELECT step.speed FROM \"zing_icu_db_prod\".\"patient_advice_execute_step\" step "
            + "     WHERE step.execute_id = paei.execute_id AND step.step_time <= paei.execute_hour "
            + "     AND step.del_flag = \'0\' ORDER BY step.step_time DESC LIMIT 1) / 60 AS DECIMAL(10,2)) "
            + "  ELSE paei.amount "
            + "END AS DECIMAL(10,2)) AS item_value, "
            + "paei.execute_hour AS item_time "
            + "FROM \"zing_icu_db_prod\".\"patient_advice_execute\" pae "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_advice_execute_item\" paei "
            + "  ON pae.id = paei.execute_id "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_info\" pi "
            + "  ON pi.id = pae.patient_id AND pi.del_flag = 0 AND pi.is_in_depart = 1 "
            + "WHERE pae.is_to_io = \'1\' "
            + "AND pae.del_flag = \'0\' "
            + "AND paei.execute_hour >= CAST(#{shiftDate} || \' 07:00:00\' AS DATETIME) "
            + "AND paei.execute_hour <= CAST(#{shiftDate} || \' 07:00:00\' AS DATETIME) + 1 "
            + "AND (#{departCode} = \'\' OR pi.depart_code = #{departCode})")
    List<Map<String, Object>> selectHandoverAdviceIntake(@Param("departCode") String departCode,
                                                           @Param("shiftDate") String shiftDate,
                                                           @Param("startTime") String startTime,
                                                           @Param("endTime") String endTime);

    // ==================== 交班详情：按患者查的出入量汇总（和总览逻辑一致，去掉is_in_depart过滤） ====================

    /**
     * 出量（按患者）：config_io_item.io_type='o' 的所有记录，含 item_name。
     */
    @Select("SELECT r.item_code AS item_code, r.item_value AS item_value, "
            + "r.item_time AS item_time, r.item_unit AS item_unit, c.item_name AS item_name "
            + "FROM \"zing_icu_db_prod\".\"patient_io_module_item_record\" r "
            + "INNER JOIN \"zing_icu_db_prod\".\"config_io_item\" c "
            + "  ON r.item_code = c.item_code AND c.io_type = \'o\' "
            + "WHERE r.del_flag = 0 AND r.status = 1 "
            + "AND r.patient_id = #{patientId} "
            + "AND r.item_time >= #{startTime} AND r.item_time < #{endTime} "
            + "ORDER BY r.item_time DESC")
    List<Map<String, Object>> selectOutputByPatient(@Param("patientId") String patientId,
                                                      @Param("startTime") String startTime,
                                                      @Param("endTime") String endTime);

    /**
     * 非药品入量（按患者）：module_code='io_in' 的非药品入量。
     */
    @Select("SELECT r.item_code AS item_code, r.item_value AS item_value, "
            + "r.item_time AS item_time, r.item_unit AS item_unit, i.item_name AS item_name "
            + "FROM \"zing_icu_db_prod\".\"patient_io_module_item_record\" r "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_io_module_item\" i "
            + "  ON i.patient_id = r.patient_id AND i.item_code = r.item_code AND i.del_flag = 0 "
            + "WHERE r.del_flag = 0 AND r.status = 1 AND i.status = 1 "
            + "AND i.module_code = \'io_in\' "
            + "AND r.patient_id = #{patientId} "
            + "AND r.item_time >= #{startTime} AND r.item_time < #{endTime} "
            + "ORDER BY r.item_time DESC")
    List<Map<String, Object>> selectOtherIntakeByPatient(@Param("patientId") String patientId,
                                                            @Param("startTime") String startTime,
                                                            @Param("endTime") String endTime);

    /**
     * 尿量（按患者）：固定 item_code='ii_nl'。
     */
    @Select("SELECT r.item_value AS item_value, r.item_time AS item_time "
            + "FROM \"zing_icu_db_prod\".\"patient_io_module_item_record\" r "
            + "WHERE r.del_flag = 0 AND r.status = 1 "
            + "AND r.item_code = \'ii_nl\' "
            + "AND r.patient_id = #{patientId} "
            + "AND r.item_time >= #{startTime} AND r.item_time < #{endTime} "
            + "ORDER BY r.item_time DESC")
    List<Map<String, Object>> selectUrineByPatient(@Param("patientId") String patientId,
                                                     @Param("startTime") String startTime,
                                                     @Param("endTime") String endTime);

    /**
     * 导尿管判断（按患者）：patient_tube 表 tube_code='tube_导尿管'，班次内未拔除。
     */
    @Select("SELECT COUNT(1) AS cnt "
            + "FROM \"zing_icu_db_prod\".\"patient_tube\" pt "
            + "WHERE pt.tube_code = \'tube_导尿管\' "
            + "AND pt.patient_id = #{patientId} "
            + "AND (pt.tube_set_start_time < #{endTime} OR pt.take_time < #{endTime}) "
            + "AND ( "
            + "  (pt.tube_set_end_time IS NULL AND pt.tube_take_away_time IS NULL) "
            + "  OR pt.tube_set_end_time >= #{startTime} "
            + "  OR pt.tube_take_away_time >= #{startTime} "
            + ")")
    Map<String, Object> selectCatheterByPatient(@Param("patientId") String patientId,
                                                  @Param("startTime") String startTime,
                                                  @Param("endTime") String endTime);

    /**
     * 药品入量（按患者，含07:00边界处理）：和总览 selectHandoverAdviceIntake 逻辑一致。
     */
    @Select("SELECT "
            + "CAST(CASE "
            + "  WHEN paei.execute_hour = CAST(#{shiftDate} || \' 07:00:00\' AS DATETIME) "
            + "   AND EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice_execute_step\" step "
            + "     WHERE step.execute_id = paei.execute_id AND step.step_type_name IN (\'执行\',\'恢复\') "
            + "     AND step.step_time >= CAST(#{shiftDate} || \' 07:01:00\' AS DATETIME) "
            + "     AND step.step_time < CAST(#{shiftDate} || \' 08:00:00\' AS DATETIME)) "
            + "  THEN paei.amount "
            + "  WHEN paei.execute_hour = CAST(#{shiftDate} || \' 07:00:00\' AS DATETIME) AND paei.amount <> 0 "
            + "  THEN paei.amount - CAST((SELECT step.speed FROM \"zing_icu_db_prod\".\"patient_advice_execute_step\" step "
            + "     WHERE step.execute_id = paei.execute_id AND step.step_time <= paei.execute_hour "
            + "     AND step.del_flag = \'0\' ORDER BY step.step_time DESC LIMIT 1) / 60 AS DECIMAL(10,2)) "
            + "  WHEN paei.execute_hour = CAST(#{shiftDate} || \' 07:00:00\' AS DATETIME) + 1 "
            + "   AND EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_advice_execute_step\" step "
            + "     WHERE step.execute_id = paei.execute_id AND step.step_type_name IN (\'执行\',\'恢复\') "
            + "     AND step.step_time >= CAST(#{shiftDate} || \' 07:00:00\' AS DATETIME) + 1 "
            + "     AND step.step_time < CAST(#{shiftDate} || \' 08:00:00\' AS DATETIME) + 1) "
            + "  THEN 0 "
            + "  WHEN paei.execute_hour = CAST(#{shiftDate} || \' 07:00:00\' AS DATETIME) + 1 "
            + "  THEN CAST((SELECT step.speed FROM \"zing_icu_db_prod\".\"patient_advice_execute_step\" step "
            + "     WHERE step.execute_id = paei.execute_id AND step.step_time <= paei.execute_hour "
            + "     AND step.del_flag = \'0\' ORDER BY step.step_time DESC LIMIT 1) / 60 AS DECIMAL(10,2)) "
            + "  ELSE paei.amount "
            + "END AS DECIMAL(10,2)) AS item_value, "
            + "paei.execute_hour AS item_time, pae.name AS item_name "
            + "FROM \"zing_icu_db_prod\".\"patient_advice_execute\" pae "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_advice_execute_item\" paei "
            + "  ON pae.id = paei.execute_id "
            + "WHERE pae.is_to_io = \'1\' "
            + "AND pae.del_flag = \'0\' "
            + "AND pae.patient_id = #{patientId} "
            + "AND paei.execute_hour >= CAST(#{shiftDate} || \' 07:00:00\' AS DATETIME) "
            + "AND paei.execute_hour <= CAST(#{shiftDate} || \' 07:00:00\' AS DATETIME) + 1")
    List<Map<String, Object>> selectAdviceIntakeByPatient(@Param("patientId") String patientId,
                                                             @Param("shiftDate") String shiftDate,
                                                             @Param("startTime") String startTime,
                                                             @Param("endTime") String endTime);

    /**
     * 出量项目分组汇总（按患者）：按 item_name 分组累加。
     */
    @Select("SELECT c.item_name AS item_name, SUM(CAST(r.item_value AS DECIMAL(10,2))) AS total_value "
            + "FROM \"zing_icu_db_prod\".\"patient_io_module_item_record\" r "
            + "INNER JOIN \"zing_icu_db_prod\".\"config_io_item\" c "
            + "  ON r.item_code = c.item_code AND c.io_type = \'o\' "
            + "WHERE r.del_flag = 0 AND r.status = 1 "
            + "AND r.patient_id = #{patientId} "
            + "AND r.item_time >= #{startTime} AND r.item_time < #{endTime} "
            + "GROUP BY c.item_name "
            + "ORDER BY total_value DESC")
    List<Map<String, Object>> selectOutputItemSummaryByPatient(@Param("patientId") String patientId,
                                                                  @Param("startTime") String startTime,
                                                                  @Param("endTime") String endTime);

    /**
     * 批量检验结果（封板班次区间）：Java 端按 alarm_flag 或参考区间判定异常。
     * 业务数据来源：patient_info_lis_item + patient_info
     */
    @Select("SELECT pi.id AS patient_id, li.in_hospital_no AS in_hospital_no, "
            + "li.lis_item_name AS item_name, li.lis_item_result AS result, "
            + "li.lis_item_unit AS unit, li.lis_item_alarm_flag AS alarm_flag, "
            + "li.lis_item_low_value AS low_value, li.lis_item_height_value AS high_value, "
            + "li.check_time AS check_time "
            + "FROM \"zing_icu_db_prod\".\"patient_info_lis_item\" li "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_info\" pi "
            + "  ON pi.in_hospital_no = li.in_hospital_no AND pi.del_flag = 0 AND pi.is_in_depart = 1 "
            + "WHERE li.del_flag = 0 AND li.status = 1 "
            + "AND li.lis_item_result IS NOT NULL AND li.lis_item_result <> '' "
            + "AND li.check_time >= #{startTime} AND li.check_time < #{endTime} "
            + "AND pi.depart_code = #{departCode} "
            + "ORDER BY li.check_time DESC")
    List<Map<String, Object>> selectHandoverLabs(@Param("departCode") String departCode,
                                                  @Param("startTime") String startTime,
                                                  @Param("endTime") String endTime);

    /**
     * 当前在用血管活性/升压药医嘱（status='1' 执行中），Java 端按患者去重药名。
     * 业务数据来源：patient_advice + patient_info
     */
    @Select("SELECT pi.id AS patient_id, pa.name AS name, pa.freq_name AS freq_name, "
            + "pa.drug_one_dosage AS dosage, pa.drug_one_dosage_unit AS dosage_unit, "
            + "pa.start_time AS start_time "
            + "FROM \"zing_icu_db_prod\".\"patient_advice\" pa "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_info\" pi "
            + "  ON pi.in_hospital_no = pa.in_hospital_no AND pi.del_flag = 0 AND pi.is_in_depart = 1 "
            + "WHERE pa.del_flag = 0 AND pa.status = '1' AND pa.type_code = 'drug' "
            + "AND (pa.name LIKE '%去甲肾上腺素%' OR pa.name LIKE '%多巴胺%' "
            + "  OR pa.name LIKE '%肾上腺素%' OR pa.name LIKE '%间羟胺%' "
            + "  OR pa.name LIKE '%多巴酚丁胺%' OR pa.name LIKE '%血管加压素%' "
            + "  OR pa.name LIKE '%垂体后叶%' OR pa.name LIKE '%特利加压素%') "
            + "AND pi.depart_code = #{departCode} "
            + "ORDER BY pa.start_time DESC")
    List<Map<String, Object>> selectHandoverVasopressor(@Param("departCode") String departCode);

    /**
     * 本班（封板区间）出科患者人数（distinct 住院号），用于汇总条"本班出科"。
     * 业务数据来源：patient_info
     */
    @Select("SELECT COUNT(DISTINCT pi.in_hospital_no) AS cnt "
            + "FROM \"zing_icu_db_prod\".\"patient_info\" pi "
            + "WHERE pi.del_flag = 0 "
            + "AND pi.out_depart_time >= #{startTime} AND pi.out_depart_time < #{endTime} "
            + "AND (#{departCode} = '' OR pi.depart_code = #{departCode})")
    Map<String, Object> selectHandoverDischargeCount(@Param("startTime") String startTime,
                                                      @Param("endTime") String endTime,
                                                      @Param("departCode") String departCode);

    // ==================== ARDS 监测 ====================

    /**
     * ARDS患者列表（基本信息）：is_ards=1 或 诊断包含急性呼吸窘迫综合征/ARDS。
     * 诊断时间限定在入科后、出科前。
     */
    @Select("SELECT pi.id AS patient_id, pi.in_hospital_no, pi.name AS patient_name, "
            + "pi.bed_code, pi.gender, pi.height, pi.weight, "
            + "pi.in_depart_time, pi.out_depart_time, pi.is_ards "
            + "FROM \"zing_icu_db_prod\".\"patient_info\" pi "
            + "WHERE pi.del_flag = 0 "
            + "AND pi.depart_code = #{departCode} "
            + "AND pi.out_depart_time >= #{startTime} "
            + "AND pi.out_depart_time < #{endTime} "
            + "AND EXISTS (SELECT 1 FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" pid "
            + "  WHERE pid.in_hospital_no = pi.in_hospital_no AND pid.del_flag = 0 "
            + "  AND (pid.diag_name LIKE '%急性呼吸窘迫综合征%' OR pid.diag_name LIKE '%ARDS%')) "
            + "ORDER BY pi.out_depart_time DESC")
    List<Map<String, Object>> selectArdsPatients(@Param("departCode") String departCode,
                                                    @Param("startTime") String startTime,
                                                    @Param("endTime") String endTime);

    /**
     * 批量查询患者最新呼吸机参数（每个患者每个item取最新一条）。
     * item_code: oi_呼末潮气量, oi_peep, oi_FiO2(设置值), oi_呼吸频率(设置值), oi_呼末分钟通气量
     */
    @Select("<script>"
            + "SELECT patient_id, item_code, item_value, item_time FROM ("
            + "  SELECT r.patient_id, r.item_code, r.item_value, r.item_time, "
            + "         ROW_NUMBER() OVER (PARTITION BY r.patient_id, r.item_code ORDER BY r.item_time DESC) rn "
            + "  FROM \"zing_icu_db_prod\".\"patient_observe_module_item_record\" r "
            + "  WHERE r.patient_id IN "
            + "  <foreach collection='patientIds' item='pid' open='(' separator=',' close=')'>"
            + "    #{pid}"
            + "  </foreach>"
            + "  AND r.item_code IN ('oi_呼末潮气量','oi_peep','oi_FiO2(设置值)','oi_呼吸频率(设置值)','oi_呼末分钟通气量') "
            + ") t WHERE rn = 1"
            + "</script>")
    List<Map<String, Object>> selectLatestVentilatorParams(@Param("patientIds") List<String> patientIds);

    /**
     * 查询患者氧合指数历史（动脉血血气分析→氧合指数），按时间升序。
     * patient_info_lis 无 patient_id 字段，通过 patient_info_lis_item.in_hospital_no 关联 patient_info。
     */
    @Select("SELECT pi.id AS patient_id, item.check_time AS check_time, "
            + "CAST(item.lis_item_result AS DECIMAL(10,2)) AS oxygenation_index "
            + "FROM \"zing_icu_db_prod\".\"patient_info_lis\" lis "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_info_lis_item\" item "
            + "  ON lis.lis_code = item.lis_code AND item.del_flag = 0 "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_info\" pi "
            + "  ON pi.in_hospital_no = item.in_hospital_no AND pi.del_flag = 0 "
            + "WHERE pi.id = #{patientId} "
            + "AND lis.del_flag = 0 "
            + "AND lis.lis_short_name = '动脉血' "
            + "AND lis.lis_name LIKE '%血气分析%' "
            + "AND item.lis_item_name = '氧合指数' "
            + "AND item.lis_item_result IS NOT NULL AND item.lis_item_result <> '' "
            + "ORDER BY item.check_time ASC")
    List<Map<String, Object>> selectOxygenationHistory(@Param("patientId") String patientId);

    /**
     * 查询患者呼吸机参数趋势（最近N天），按时间升序。
     */
    @Select("SELECT r.item_code, r.item_value, r.item_time "
            + "FROM \"zing_icu_db_prod\".\"patient_observe_module_item_record\" r "
            + "WHERE r.patient_id = #{patientId} "
            + "AND r.item_code IN ('oi_呼末潮气量','oi_peep','oi_FiO2(设置值)','oi_呼吸频率(设置值)','oi_呼末分钟通气量') "
            + "AND r.item_time >= #{startTime} "
            + "AND r.item_time < #{endTime} "
            + "ORDER BY r.item_time ASC")
    List<Map<String, Object>> selectVentilatorTrend(@Param("patientId") String patientId,
                                                      @Param("startTime") String startTime,
                                                      @Param("endTime") String endTime);
    /**
     * 患者在重症系统中的 GCS 评估文书记录（doc_code=Z_ICU_GCS），按评估时间倒序。
     * score_json_value 为 TEXT，CAST 成 VARCHAR 取出（GCS JSON 很小）；item1=E睁眼、item2=V言语、item3=M运动、item4=ET(插管/气切)。
     */
    @Select("SELECT pdr.id AS doc_record_id, pdr.record_time AS record_time, "
            + "pdr.record_staff_code AS record_staff_code, pdr.record_staff_name AS record_staff_name, pdr.is_audit AS is_audit, "
            + "pdrs.id AS score_id, pdrs.score_value AS score_value, "
            + "CAST(pdrs.score_json_value AS VARCHAR(4000)) AS score_json "
            + "FROM \"zing_icu_db_prod\".\"patient_doc_record\" pdr "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_doc_record_score\" pdrs ON pdr.id = pdrs.doc_record_id "
            + "WHERE pdr.doc_code = 'Z_ICU_GCS' AND pdr.patient_id = #{patientId} "
            + "AND pdr.del_flag = 0 AND pdrs.del_flag = 0 "
            + "ORDER BY pdr.record_time DESC")
    List<Map<String, Object>> selectGcsDocRecords(@Param("patientId") String patientId);

    /**
     * 指定取数时间范围内的 GCS 评估文书记录（APACHE II 自动取数同步 GCS 用），按评估时间倒序。
     * 与 {@link #selectGcsDocRecords(String)} 同源，仅增加 record_time 范围过滤，
     * 确保同步到的 GCS 严格落在本次取数范围内。
     */
    @Select("SELECT pdr.id AS doc_record_id, pdr.record_time AS record_time, "
            + "pdr.record_staff_code AS record_staff_code, pdr.record_staff_name AS record_staff_name, pdr.is_audit AS is_audit, "
            + "pdrs.id AS score_id, pdrs.score_value AS score_value, "
            + "CAST(pdrs.score_json_value AS VARCHAR(4000)) AS score_json "
            + "FROM \"zing_icu_db_prod\".\"patient_doc_record\" pdr "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_doc_record_score\" pdrs ON pdr.id = pdrs.doc_record_id "
            + "WHERE pdr.doc_code = 'Z_ICU_GCS' AND pdr.patient_id = #{patientId} "
            + "AND pdr.del_flag = 0 AND pdrs.del_flag = 0 "
            + "AND pdr.record_time >= #{startTime} AND pdr.record_time < #{endTime} "
            + "ORDER BY pdr.record_time DESC")
    List<Map<String, Object>> selectGcsDocRecordsByRange(@Param("patientId") String patientId,
                                                          @Param("startTime") String startTime,
                                                          @Param("endTime") String endTime);

    /**
     * 当前在科且入科已超过指定小时数的患者（用于 APACHE II 自动初评）。
     * 达梦日期减法用 SYSDATE - n（单位天），hours/24 即小时换算；departCode 为空时不限科室。
     */
    @Select("SELECT pi.id AS patient_id, pi.in_hospital_no AS in_hospital_no, pi.name AS name, "
            + "pi.gender AS gender, pi.age AS age, pi.bed_code AS bed_code, "
            + "pi.depart_code AS depart_code, pi.in_depart_time AS in_depart_time "
            + "FROM \"zing_icu_db_prod\".\"patient_info\" pi "
            + "WHERE pi.del_flag = 0 AND pi.status = 1 AND pi.is_in_depart = 1 "
            + "AND pi.in_depart_time IS NOT NULL "
            + "AND pi.in_depart_time <= SYSDATE - (#{hours} / 24.0) "
            + "AND (#{departCode} IS NULL OR pi.depart_code = #{departCode}) "
            + "ORDER BY pi.depart_code ASC, "
            + "CASE WHEN REGEXP_LIKE(pi.bed_code, '^[A-Za-z]') THEN 0 ELSE 1 END, pi.bed_code ASC")
    List<Map<String, Object>> selectInDepartPatientsOverHours(@Param("hours") int hours,
                                                               @Param("departCode") String departCode);

    // ==================== SOFA 评分所需查询 ====================

    /**
     * SOFA 患者基础信息（体重 / 身高 / 年龄 / 性别 / 呼吸机标记）。
     * 体重用于血管活性药 µg/kg/min 换算；`ventilator_code` 非空视为有呼吸支持。
     */
    @Select("SELECT pi.id AS patient_id, pi.in_hospital_no AS in_hospital_no, pi.name AS name, "
            + "pi.age AS age, pi.age_unit AS age_unit, pi.gender AS gender, "
            + "pi.weight AS weight, pi.height AS height, pi.bed_code AS bed_code, "
            + "pi.depart_code AS depart_code, pi.depart_name AS depart_name, pi.ventilator_code AS ventilator_code, "
            + "pi.in_depart_time AS in_depart_time "
            + "FROM \"zing_icu_db_prod\".\"patient_info\" pi "
            + "WHERE pi.id = #{patientId} AND pi.del_flag = 0")
    Map<String, Object> selectSofaPatientBase(@Param("patientId") String patientId);

    /**
     * 指定取数范围内的氧合指数（PaO2/FiO2，ICU 血气报告中已计算好）。
     * 与 {@link #selectOxygenationHistory(String)} 同源，仅增加 check_time 范围过滤。
     */
    @Select("SELECT item.check_time AS check_time, "
            + "CAST(item.lis_item_result AS DECIMAL(10,2)) AS oxygenation_index "
            + "FROM \"zing_icu_db_prod\".\"patient_info_lis\" lis "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_info_lis_item\" item "
            + "  ON lis.lis_code = item.lis_code AND item.del_flag = 0 "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_info\" pi "
            + "  ON pi.in_hospital_no = item.in_hospital_no AND pi.del_flag = 0 "
            + "WHERE pi.id = #{patientId} "
            + "AND lis.del_flag = 0 "
            + "AND lis.lis_short_name = '动脉血' "
            + "AND lis.lis_name LIKE '%血气分析%' "
            + "AND item.lis_item_name = '氧合指数' "
            + "AND item.lis_item_result IS NOT NULL AND item.lis_item_result <> '' "
            + "AND item.check_time >= #{startTime} AND item.check_time < #{endTime} "
            + "ORDER BY item.check_time ASC")
    List<Map<String, Object>> selectOxygenationHistoryByRange(@Param("patientId") String patientId,
                                                              @Param("startTime") String startTime,
                                                              @Param("endTime") String endTime);

    /**
     * 取数窗口内在用的血管活性药执行记录（SOFA 循环项剂量还原用）。
     * 关联 patient_advice_execute（泵速 / 总液量）与 patient_advice_execute_drug（药名 / 药物总量 / 规格）。
     * 是否在用按时间重叠判定：start_time &lt; 窗口末 且（end_time 为空 或 end_time &ge; 窗口起）。
     *
     * <p><b>关于 status 过滤</b>：作废/停止但未删除的执行单会被时间重叠判定误认为"在用"，
     * 从而高估循环分。但各院 {@code patient_advice_execute.status} 字典不一定一致，
     * 盲目加 {@code status = 1} 可能把"已完成但未删除"的历史医嘱剔出 24h 回顾窗口、反而**漏计**。
     * 故此处交由 {@code statusFilter} 参数注入（由配置 {@code vasopressor.status_filter}
     * 生成并做白名单校验），默认传空串 = 不过滤（保持原有口径）。
     *
     * <p>{@code patient_advice_execute_drug.status} 未在本项目其他查询中出现过，
     * 未经现场核实不参与过滤，避免引用不存在的列导致整条 SQL 失败。
     */
    @Select("<script>"
            + "SELECT e.id AS execute_id, e.in_hospital_no AS in_hospital_no, e.name AS advice_name, "
            + "e.liquid_amount AS liquid_amount, e.drug_now_speed AS drug_now_speed, "
            + "e.drug_start_speed AS drug_start_speed, e.drug_speed_unit AS drug_speed_unit, "
            + "e.start_time AS start_time, e.end_time AS end_time, "
            + "d.drug_name AS drug_name, d.drug_dose AS drug_dose, d.drug_dose_unit AS drug_dose_unit, "
            + "d.drug_one_dosage AS drug_one_dosage, d.drug_one_dosage_unit AS drug_one_dosage_unit, "
            + "d.spec AS spec, d.liquid_amount AS drug_volume "
            + "FROM \"zing_icu_db_prod\".\"patient_advice_execute\" e "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_advice_execute_drug\" d "
            + "  ON d.execute_id = e.id AND d.del_flag = 0 "
            + "WHERE e.del_flag = 0 AND e.in_hospital_no = #{inHospitalNo} "
            + "AND e.start_time &lt; #{endTime} "
            + "AND (e.end_time IS NULL OR e.end_time &gt;= #{startTime}) "
            + "${statusFilter} "
            + "AND (d.drug_name LIKE '%去甲肾上腺素%' OR d.drug_name LIKE '%多巴胺%' "
            + "  OR d.drug_name LIKE '%肾上腺素%' OR d.drug_name LIKE '%多巴酚丁胺%') "
            + "ORDER BY e.start_time ASC"
            + "</script>")
    List<Map<String, Object>> selectVasopressorExecuteByRange(@Param("inHospitalNo") String inHospitalNo,
                                                              @Param("startTime") String startTime,
                                                              @Param("endTime") String endTime,
                                                              @Param("statusFilter") String statusFilter);

    /**
     * 批量取血管活性药调速历史（用于还原取数窗口内的最高泵速）。
     * <p>`speed` 与执行主表 `drug_speed_unit` 同单位（实测全库仅 `ml/h`）。
     *
     * <p><b>刻意不设时间下界</b>（只有上界 endTime）：除窗口内的调速记录外，还需要拿到
     * 「窗口开始前最后一次调速」，否则某泵在窗口开始前已调到高速度、窗口内未再调速时，
     * 该起始速度会被漏掉（旧实现只查窗口内记录，直接退回 drug_now_speed，
     * 补评历史窗口时会低估循环分）。调用方在 Java 侧按 step_time 与窗口起点比较后分流。
     *
     * <p>注意：executeIds 为空时不要调用（会生成 IN () 非法 SQL）。
     */
    @Select("<script>"
            + "SELECT s.execute_id AS execute_id, s.step_time AS step_time, s.speed AS speed, "
            + "s.step_type_name AS step_type_name "
            + "FROM \"zing_icu_db_prod\".\"patient_advice_execute_step\" s "
            + "WHERE s.del_flag = 0 "
            + "AND s.step_time &lt; #{endTime} "
            + "AND s.execute_id IN "
            + "<foreach collection='executeIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " ORDER BY s.execute_id ASC, s.step_time ASC"
            + "</script>")
    List<Map<String, Object>> selectVasopressorSpeedSteps(@Param("executeIds") List<String> executeIds,
                                                         @Param("endTime") String endTime);

}

