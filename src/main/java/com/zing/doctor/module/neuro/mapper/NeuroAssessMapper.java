package com.zing.doctor.module.neuro.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 急性脑损伤 / 神经功能监测只读取数（ICU 库）。
 *
 * <p>权威源划分（与实施方案 docs/20 一致）：
 * <ul>
 *   <li>GCS 评分及 E/V/M 分项：正式评分文书 {@code patient_doc_record(_score)}，doc_code={@code Z_ICU_GCS}；</li>
 *   <li>高频神志 / 双侧瞳孔 / ICP：监护评估 {@code patient_observe_module_item_record}
 *       JOIN 患者级 {@code patient_observe_module_item} 取项目名；</li>
 *   <li>急性脑损伤识别：诊断表 {@code patient_info_diagnosis}，优先 ICD-10 编码前缀。</li>
 * </ul>
 *
 * <p>全部查询强制带 patient_id（doc 主表无 (patient_id,doc_code,record_time) 复合索引），
 * 不提供全院 / 全时间窗扫描入口。
 */
@Mapper
public interface NeuroAssessMapper {

    /**
     * 患者全部有效诊断（按诊断时间倒序），用于急性脑损伤 ICD-10 识别。
     * 不按展示时间窗裁剪（脑损伤常为入院 / 入科诊断，可能早于趋势窗口）。
     */
    @Select("SELECT * FROM (SELECT d.diag_code AS diag_code, d.diag_name AS diag_name, "
            + "d.diag_type_name AS diag_type_name, d.diag_kind_name AS diag_kind_name, "
            + "d.diag_time AS diag_time "
            + "FROM \"zing_icu_db_prod\".\"patient_info_diagnosis\" d "
            + "WHERE d.patient_id = #{patientId} AND d.del_flag = 0 AND d.status = 1 "
            + "ORDER BY d.diag_time DESC) WHERE ROWNUM <= 200")
    List<Map<String, Object>> selectDiagnoses(@Param("patientId") String patientId);

    /**
     * 时间窗内监护评估项（神志 / 左瞳孔 / 右瞳孔 / ICP），按评估时间升序。
     * item_name 取患者级 item 配置；status=1 为有效记录。
     */
    @Select("<script>"
            + "SELECT * FROM (SELECT r.item_code AS item_code, r.item_value AS item_value, "
            + "r.item_time AS item_time, r.record_staff_name AS staff_name, i.item_name AS item_name "
            + "FROM \"zing_icu_db_prod\".\"patient_observe_module_item_record\" r "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_observe_module_item\" i "
            + "  ON i.patient_id = r.patient_id AND i.item_code = r.item_code "
            + "  AND i.del_flag = 0 AND i.status = 1 "
            + "WHERE r.patient_id = #{patientId} AND r.del_flag = 0 AND r.status = 1 "
            + "AND r.item_time &gt;= #{startTime} AND r.item_time &lt; #{endTime} "
            + "AND r.item_code IN "
            + "<foreach collection='itemCodes' item='code' open='(' separator=',' close=')'>"
            + "#{code}"
            + "</foreach>"
            + " ORDER BY r.item_time ASC) WHERE ROWNUM &lt;= 3000"
            + "</script>")
    List<Map<String, Object>> selectObserveNeuro(@Param("patientId") String patientId,
                                                 @Param("itemCodes") List<String> itemCodes,
                                                 @Param("startTime") String startTime,
                                                 @Param("endTime") String endTime);

    /**
     * 时间窗内 GCS 正式评分文书（Z_ICU_GCS），按评估时间升序。
     * score_json_value 为 TEXT，CAST 成 VARCHAR 取出；item1=E 睁眼、item2/item21=V 言语、
     * item3=M 运动；插管 / 气切时 item21="VT"、totalScore 形如 {@code 1+VT+2}（言语不可评，记 3T）。
     */
    @Select("SELECT * FROM (SELECT r.record_time AS record_time, "
            + "r.record_staff_name AS staff_name, r.is_audit AS is_audit, "
            + "s.score_value AS score_value, CAST(s.score_json_value AS VARCHAR(4000)) AS score_json "
            + "FROM \"zing_icu_db_prod\".\"patient_doc_record\" r "
            + "INNER JOIN \"zing_icu_db_prod\".\"patient_doc_record_score\" s "
            + "  ON s.doc_record_id = r.id AND s.del_flag = 0 "
            + "WHERE r.patient_id = #{patientId} AND r.del_flag = 0 AND r.doc_code = 'Z_ICU_GCS' "
            + "AND r.record_time &gt;= #{startTime} AND r.record_time &lt; #{endTime} "
            + "ORDER BY r.record_time ASC) WHERE ROWNUM &lt;= 1000")
    List<Map<String, Object>> selectGcsDoc(@Param("patientId") String patientId,
                                           @Param("startTime") String startTime,
                                           @Param("endTime") String endTime);
}
