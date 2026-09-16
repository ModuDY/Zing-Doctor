package com.zing.doctor.quality.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.quality.entity.QualityMetricPatient;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 患者级明细 Mapper（血缘第 4 层）。
 */
@DS("doctor")
@Mapper
public interface QualityMetricPatientMapper extends BaseMapper<QualityMetricPatient> {

    @Select("SELECT * FROM \"zing_doctor_db_prod\".\"quality_metric_patient\" "
            + "WHERE \"metric_code\" = #{metricCode} AND \"period_start\" = #{periodStart} "
            + "AND \"depart_code\" = #{departCode} ORDER BY \"in_numerator\" DESC, \"in_hospital_no\"")
    List<QualityMetricPatient> selectByMetric(@Param("metricCode") String metricCode,
                                              @Param("periodStart") LocalDateTime periodStart,
                                              @Param("departCode") String departCode);

    @Delete("DELETE FROM \"zing_doctor_db_prod\".\"quality_metric_patient\" "
            + "WHERE \"metric_code\" = #{metricCode} AND \"period_start\" = #{periodStart} "
            + "AND \"depart_code\" = #{departCode}")
    int deleteByMetric(@Param("metricCode") String metricCode,
                       @Param("periodStart") LocalDateTime periodStart,
                       @Param("departCode") String departCode);

    @Insert("<script>"
            + "INSERT INTO \"zing_doctor_db_prod\".\"quality_metric_patient\" "
            + "(\"run_id\",\"metric_code\",\"period_start\",\"depart_code\",\"patient_id\",\"in_hospital_no\","
            + "\"patient_name\",\"in_numerator\",\"in_denominator\",\"exclude_reason\",\"raw_json\") VALUES "
            + "<foreach collection='list' item='it' separator=','>"
            + "(#{it.runId},#{it.metricCode},#{it.periodStart},#{it.departCode},#{it.patientId},#{it.inHospitalNo},"
            + "#{it.patientName},#{it.inNumerator},#{it.inDenominator},#{it.excludeReason},#{it.rawJson})"
            + "</foreach>"
            + "</script>")
    int batchInsert(@Param("list") List<QualityMetricPatient> list);
}
