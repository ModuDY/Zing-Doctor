package com.zing.doctor.quality.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.quality.entity.QualityCalcTrace;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 追溯 Mapper（血缘第 3 层）。
 */
@DS("doctor")
@Mapper
public interface QualityCalcTraceMapper extends BaseMapper<QualityCalcTrace> {

    @Select("SELECT \"id\",\"run_id\",\"metric_code\",\"dim_key\",\"fact_name\",\"expression_version\",\"sql_hash\","
            + "\"source_tables\",\"operators\",\"scanned_rows\",\"num_rows\",\"den_rows\",\"duration_ms\",\"calc_time\" "
            + "FROM \"zing_doctor_db_prod\".\"quality_calc_trace\" "
            + "WHERE \"run_id\" = #{runId} AND \"metric_code\" = #{metricCode}")
    List<QualityCalcTrace> selectByMetric(@Param("runId") String runId, @Param("metricCode") String metricCode);

    /** 单独取 SQL 原文（CLOB 大字段，列表查询不携带）。 */
    @Select("SELECT \"sql_text\" FROM \"zing_doctor_db_prod\".\"quality_calc_trace\" "
            + "WHERE \"run_id\" = #{runId} AND \"metric_code\" = #{metricCode} AND \"dim_key\" = #{dimKey}")
    String selectSqlText(@Param("runId") String runId, @Param("metricCode") String metricCode,
                         @Param("dimKey") String dimKey);

    @Delete("DELETE FROM \"zing_doctor_db_prod\".\"quality_calc_trace\" WHERE \"run_id\" = #{runId}")
    int deleteByRun(@Param("runId") String runId);

    @Insert("<script>"
            + "INSERT INTO \"zing_doctor_db_prod\".\"quality_calc_trace\" "
            + "(\"run_id\",\"metric_code\",\"dim_key\",\"fact_name\",\"expression_version\",\"sql_hash\",\"sql_text\","
            + "\"source_tables\",\"operators\",\"scanned_rows\",\"num_rows\",\"den_rows\",\"duration_ms\",\"calc_time\") VALUES "
            + "<foreach collection='list' item='it' separator=','>"
            + "(#{it.runId},#{it.metricCode},#{it.dimKey},#{it.factName},#{it.expressionVersion},#{it.sqlHash},#{it.sqlText},"
            + "#{it.sourceTables},#{it.operators},#{it.scannedRows},#{it.numRows},#{it.denRows},#{it.durationMs},#{it.calcTime})"
            + "</foreach>"
            + "</script>")
    int batchInsert(@Param("list") List<QualityCalcTrace> list);
}
