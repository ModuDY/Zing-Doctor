package com.zing.doctor.module.sedation.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 镇痛镇静 / 谵妄评估的 ICU 只读 Mapper（达梦 schema：zing_icu_db_prod）。
 *
 * <p>数据源为重症系统正式评分文书三表（权威源），不是 observe 镜像表：
 * <ul>
 *   <li>{@code control_doc}             —— 文书定义（doc_code → 量表），静态配置。</li>
 *   <li>{@code patient_doc_record}      —— 一次评估主记录（record_time / 评估人）。</li>
 *   <li>{@code patient_doc_record_score}—— 评分明细（score_value + score_json_value 分项）。</li>
 * </ul>
 *
 * <p>口径约束（见 docs/20、docs/21）：
 * <ul>
 *   <li>doc 为权威数据源，observe（oi_ttpf 等）仅为镜像，<b>不与 doc UNION</b>，避免重复计数。</li>
 *   <li>所有查询强制带 patient_id + record_time 时间窗；doc 主表无复合索引，
 *       仅靠 patient_id 单列索引 + 时间窗收敛，禁止全院 / 无时间窗扫描。</li>
 *   <li>量表 doc_code：NRS={@code Z_ICU_SCORE_NRS}、CPOT={@code Z_ICU_SCORE_CPOT}、
 *       RASS={@code Z_ICU_RASS}、CAM-ICU={@code Z_ICU_ZWPF_CAM}。</li>
 * </ul>
 *
 * <p>达梦方言同 {@link com.zing.doctor.icu.mapper.IcuPatientMapper}：双引号保小写、
 * 时间参数传 ISO 字符串（'yyyy-MM-ddTHH:mm:ss'，达梦可与 TIMESTAMP 比较）、ROWNUM 限上限。
 */
@DS("icu")
@Mapper
public interface SedationAssessMapper {

    /**
     * 批量查询单个患者在时间窗内、指定一组 doc_code 的评分文书（主表 JOIN 明细，1:1）。
     *
     * <p>一次查回 NRS/CPOT/RASS/CAM 全部记录，由 Java 端按 doc_code 分组，杜绝逐量表 N+1。
     * RASS 需额外前推一段用于 CAM 可评估性配对时，由调用方把开始时间前移，
     * 并在 Java 端区分「展示窗口」与「配对扩展窗口」。
     *
     * @param patientId 患者 patient_info.id
     * @param docCodes  量表 doc_code 集合（参数化 IN，不拼字符串）
     * @param startTime 开始时间（含），ISO 字符串
     * @param endTime   结束时间（不含），ISO 字符串
     * @return 评估记录行，按 record_time 升序；单患者上限 2000 条
     */
    @Select("<script>"
            + "SELECT * FROM ( "
            + "  SELECT r.id AS record_id, r.doc_code AS doc_code, "
            + "         r.record_time AS record_time, "
            + "         r.record_staff_code AS staff_code, r.record_staff_name AS staff_name, "
            + "         r.is_audit AS is_audit, r.doc_source AS doc_source, "
            + "         s.score_value AS score_value, "
            + "         CAST(s.score_json_value AS VARCHAR(4000)) AS score_json "
            + "  FROM \"zing_icu_db_prod\".\"patient_doc_record\" r "
            + "  INNER JOIN \"zing_icu_db_prod\".\"patient_doc_record_score\" s "
            + "    ON s.doc_record_id = r.id AND s.del_flag = 0 "
            + "  WHERE r.patient_id = #{patientId} AND r.del_flag = 0 "
            + "    AND r.record_time &gt;= #{startTime} AND r.record_time &lt; #{endTime} "
            + "    AND r.doc_code IN "
            + "  <foreach collection='docCodes' item='c' open='(' separator=',' close=')'>"
            + "    #{c}"
            + "  </foreach> "
            + "  ORDER BY r.record_time ASC "
            + ") WHERE ROWNUM &lt;= 2000"
            + "</script>")
    List<Map<String, Object>> selectDocScoreRecords(@Param("patientId") String patientId,
                                                    @Param("docCodes") List<String> docCodes,
                                                    @Param("startTime") String startTime,
                                                    @Param("endTime") String endTime);
}
