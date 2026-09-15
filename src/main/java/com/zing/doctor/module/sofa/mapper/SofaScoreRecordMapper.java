package com.zing.doctor.module.sofa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.sofa.entity.SofaScoreRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * SOFA 评分记录 Mapper（主库 doctor）。
 *
 * <p>PDF 大字段（pdf_data）默认不随列表返回，列表用定制 SQL 附带 has_pdf 标志。
 */
@Mapper
public interface SofaScoreRecordMapper extends BaseMapper<SofaScoreRecord> {

    /**
     * 患者评分记录列表：不返回 pdf_data 大字段，附带 has_pdf 标志（1=已归档文书）。
     */
    @Select("SELECT \"id\",\"patient_id\",\"in_hospital_no\",\"patient_name\",\"depart_code\",\"score_time\",\"score_type\", "
            + "\"resp_score\",\"coag_score\",\"liver_score\",\"cardio_score\",\"neuro_score\",\"renal_score\",\"total_score\", "
            + "\"resp_data\",\"coag_data\",\"liver_data\",\"cardio_data\",\"neuro_data\",\"renal_data\",\"vasopressor_json\", "
            + "\"urine_ml\",\"gcs_total\",\"gcs_detail\",\"respiratory_support\",\"weight_used\",\"weight_source\",\"delta_sofa\", "
            + "\"data_start_time\",\"data_end_time\",\"remark\",\"archive_status\",\"archive_time\",\"file_path\",\"pdf_name\",\"status\",\"create_by\",\"create_time\",\"update_by\",\"update_time\", "
            + "CASE WHEN \"pdf_data\" IS NULL THEN 0 ELSE 1 END AS \"has_pdf\" "
            + "FROM \"zing_doctor_db_prod\".\"sofa_score_record\" "
            + "WHERE \"in_hospital_no\" = #{inHospitalNo} AND \"status\" = 1 "
            + "ORDER BY \"score_time\" DESC")
    List<SofaScoreRecord> selectRecordList(@Param("inHospitalNo") String inHospitalNo);

    /**
     * 只取某条记录的 PDF Base64 与文件名（避免整行大字段回传）。
     */
    @Select("SELECT \"id\", \"pdf_data\" AS \"pdfData\", \"pdf_name\" AS \"pdfName\" "
            + "FROM \"zing_doctor_db_prod\".\"sofa_score_record\" WHERE \"id\" = #{id}")
    SofaScoreRecord selectPdfById(@Param("id") Long id);
}
