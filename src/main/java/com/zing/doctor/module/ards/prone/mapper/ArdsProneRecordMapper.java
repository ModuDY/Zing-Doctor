package com.zing.doctor.module.ards.prone.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.ards.prone.entity.ArdsProneRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * ARDS 俯卧位治疗记录主表 Mapper。
 */
public interface ArdsProneRecordMapper extends BaseMapper<ArdsProneRecord> {

    /**
     * 按住院号查记录列表（不返回 pdf_data 大字段）。
     */
    @Select("SELECT \"id\",\"record_no\",\"patient_id\",\"in_hospital_no\",\"patient_name\",\"sex\",\"age\",\"bed_code\","
            + "\"depart_code\",\"diagnosis\",\"ards_grade\",\"admit_date\",\"prone_day\",\"prone_times\","
            + "\"attending_doctor\",\"record_date\",\"start_time\",\"end_time\",\"duration_min\",\"apache2_score\","
            + "\"stop_type\",\"record_status\",\"pdf_name\",\"archive_status\",\"archive_time\",\"archive_doc_no\","
            + "\"file_path\",\"nurse_sign\",\"doctor_sign\",\"senior_sign\","
            + "\"doctor_work_no\" AS \"doctorWorkNo\",\"nurse_work_no\" AS \"nurseWorkNo\",\"senior_work_no\" AS \"seniorWorkNo\",\"status\","
            + "\"create_by\",\"create_time\",\"update_by\",\"update_time\" "
            + "FROM \"zing_doctor_db_prod\".\"ards_prone_record\" "
            + "WHERE \"in_hospital_no\" = #{inHospitalNo} AND \"status\" = 1 "
            + "ORDER BY \"start_time\" DESC")
    List<ArdsProneRecord> selectListByPatient(@Param("inHospitalNo") String inHospitalNo);

    /**
     * 科室记录列表（列表页用）。
     */
    @Select("SELECT \"id\",\"record_no\",\"patient_id\",\"in_hospital_no\",\"patient_name\",\"sex\",\"age\",\"bed_code\","
            + "\"depart_code\",\"ards_grade\",\"record_date\",\"start_time\",\"end_time\",\"duration_min\","
            + "\"record_status\",\"archive_status\",\"archive_doc_no\",\"nurse_sign\",\"doctor_sign\","
            + "\"doctor_work_no\" AS \"doctorWorkNo\",\"nurse_work_no\" AS \"nurseWorkNo\",\"senior_work_no\" AS \"seniorWorkNo\",\"status\","
            + "\"create_by\",\"create_time\",\"update_by\",\"update_time\" "
            + "FROM \"zing_doctor_db_prod\".\"ards_prone_record\" "
            + "WHERE \"status\" = 1 "
            + "AND (#{departCode} IS NULL OR \"depart_code\" = #{departCode}) "
            + "ORDER BY \"start_time\" DESC")
    List<ArdsProneRecord> selectListByDepart(@Param("departCode") String departCode);

    /** 只取 PDF Base64 与文件名（避免整行大字段回传） */
    @Select("SELECT \"id\", \"pdf_data\" AS \"pdfData\", \"pdf_name\" AS \"pdfName\" "
            + "FROM \"zing_doctor_db_prod\".\"ards_prone_record\" WHERE \"id\" = #{id}")
    ArdsProneRecord selectPdfById(@Param("id") Long id);

    /**
     * 列长度探测：admit_date / record_date 原为 VARCHAR(10)，扩列后支持 yyyy-MM-dd HH:mm（16 字符）。
     *
     * <p>未扩列的库由 Service 降级写入日期部分，避免「jar 已升级、SQL 未执行」导致建档失败。
     * 查询失败（无权限等）返回 null，同样按未扩列处理。
     */
    @Select("SELECT DATA_LENGTH FROM ALL_TAB_COLUMNS "
            + "WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD' AND UPPER(TABLE_NAME) = 'ARDS_PRONE_RECORD' "
            + "AND UPPER(COLUMN_NAME) = #{columnName}")
    Integer selectColumnLength(@Param("columnName") String columnName);
}
