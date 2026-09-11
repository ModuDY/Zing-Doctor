package com.zing.doctor.module.apache2.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.apache2.entity.Apache2ScoreRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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
            + "\"data_start_time\",\"data_end_time\",\"remark\",\"pdf_name\",\"status\",\"create_by\",\"create_time\",\"update_by\",\"update_time\", "
            + "CASE WHEN \"pdf_data\" IS NULL THEN 0 ELSE 1 END AS \"has_pdf\" "
            + "FROM \"zing_doctor_db_prod\".\"apache2_score_record\" "
            + "WHERE \"in_hospital_no\" = #{inHospitalNo} AND \"status\" = 1 "
            + "ORDER BY \"score_time\" DESC")
    List<Apache2ScoreRecord> selectRecordList(@Param("inHospitalNo") String inHospitalNo);

    /**
     * 只取某条记录的 PDF Base64 与文件名
     */
    @Select("SELECT \"id\", \"pdf_data\" AS \"pdfData\", \"pdf_name\" AS \"pdfName\" "
            + "FROM \"zing_doctor_db_prod\".\"apache2_score_record\" WHERE \"id\" = #{id}")
    Apache2ScoreRecord selectPdfById(@Param("id") Long id);
}
