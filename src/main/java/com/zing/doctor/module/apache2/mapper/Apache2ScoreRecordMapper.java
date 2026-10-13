package com.zing.doctor.module.apache2.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.apache2.entity.Apache2ScoreRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * APACHE II 评分记录 Mapper
 */
@Mapper
public interface Apache2ScoreRecordMapper extends BaseMapper<Apache2ScoreRecord> {

    /**
     * 患者评分记录列表：不返回 pdf_data 大字段，附带 has_pdf 标志
     */
    @Select("SELECT \"id\",\"patient_id\",\"in_hospital_no\",\"patient_name\",\"depart_code\",\"score_time\",\"score_type\", "
            + "\"age_score\",\"chronic_score\",\"gcs_score\",\"physiology_score\",\"total_score\",\"mortality_rate\",\"aps_data\", "
            + "\"diagnosis_type\",\"diagnosis_weight\",\"emergency_surgery\",\"chronic_health\",\"gcs_detail\", "
            + "\"data_start_time\",\"data_end_time\",\"remark\",\"archive_status\",\"archive_time\",\"file_path\",\"pdf_name\",\"status\",\"create_by\",\"create_time\",\"update_by\",\"update_time\", "
            + "CASE WHEN \"pdf_data\" IS NULL THEN 0 ELSE 1 END AS \"has_pdf\" "
            + "FROM \"zing_doctor_db_prod\".\"patient_doc_apache2_score_record\" "
            + "WHERE \"in_hospital_no\" = #{inHospitalNo} AND \"status\" = 1 "
            + "ORDER BY \"score_time\" DESC")
    List<Apache2ScoreRecord> selectRecordList(@Param("inHospitalNo") String inHospitalNo);

    /**
     * 只取某条记录的 PDF Base64 与文件名
     */
    @Select("SELECT \"id\", \"pdf_data\" AS \"pdfData\", \"pdf_name\" AS \"pdfName\" "
            + "FROM \"zing_doctor_db_prod\".\"patient_doc_apache2_score_record\" WHERE \"id\" = #{id}")
    Apache2ScoreRecord selectPdfById(@Param("id") Long id);

    /**
     * 科室周期内每个患者的最新一条有效 APACHE II 总分（驾驶舱分布用）。
     *
     * <p>按住院号去重：分布的统计单位是「患者」而非「评分次数」（docs/23 §E）。
     * 另取 {@code mortality_rate}（预测病死率），前端单独标注为「预测值」，
     * 不与实际病死率（quality_30）混用。
     */
    @Select({"<script>",
            "SELECT t.* FROM (",
            "  SELECT s.\"in_hospital_no\" AS in_hospital_no, s.\"total_score\" AS total_score,",
            "         s.\"mortality_rate\" AS mortality_rate,",
            "         ROW_NUMBER() OVER (PARTITION BY s.\"in_hospital_no\" ORDER BY s.\"score_time\" DESC) AS rn",
            "    FROM \"zing_doctor_db_prod\".\"patient_doc_apache2_score_record\" s",
            "   WHERE s.\"status\" = 1 AND s.\"total_score\" IS NOT NULL",
            "     AND s.\"score_time\" &gt;= #{start} AND s.\"score_time\" &lt; #{end}",
            "   <if test=\"departCode != null and departCode != ''\">AND s.\"depart_code\" = #{departCode}</if>",
            ") t WHERE t.rn = 1",
            "</script>"})
    List<Map<String, Object>> selectLatestPerEpisode(@Param("departCode") String departCode,
                                                     @Param("start") String start,
                                                     @Param("end") String end);
}
