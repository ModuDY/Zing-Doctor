package com.zing.doctor.quality.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.quality.entity.QualityMetricResult;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 指标结果 Mapper（血缘第 2 层）。
 *
 * <p>幂等策略：同一 (周期, 部门) 重算前先删后插，不依赖数据库特有的 UPSERT 语法，
 * 达梦 / MySQL 均可运行。
 */
@DS("doctor")
@Mapper
public interface QualityMetricResultMapper extends BaseMapper<QualityMetricResult> {

    @Select("SELECT * FROM \"zing_doctor_db_prod\".\"quality_metric_result\" "
            + "WHERE \"period_start\" = #{periodStart} AND \"depart_code\" = #{departCode} "
            + "ORDER BY \"domain_code\", \"metric_code\"")
    List<QualityMetricResult> selectByPeriod(@Param("periodStart") LocalDateTime periodStart,
                                             @Param("departCode") String departCode);

    @Delete("DELETE FROM \"zing_doctor_db_prod\".\"quality_metric_result\" "
            + "WHERE \"period_type\" = #{periodType} AND \"period_start\" = #{periodStart} "
            + "AND \"depart_code\" = #{departCode}")
    int deleteByPeriod(@Param("periodType") String periodType,
                       @Param("periodStart") LocalDateTime periodStart,
                       @Param("departCode") String departCode);

    /** 删除单条指标结果（人工录入覆盖用）。 */
    @Delete("DELETE FROM \"zing_doctor_db_prod\".\"quality_metric_result\" "
            + "WHERE \"metric_code\" = #{metricCode} AND \"period_type\" = #{periodType} "
            + "AND \"period_start\" = #{periodStart} AND \"depart_code\" = #{departCode}")
    int deleteOne(@Param("metricCode") String metricCode,
                  @Param("periodType") String periodType,
                  @Param("periodStart") LocalDateTime periodStart,
                  @Param("departCode") String departCode);

    /** 取某年全部月度结果，供月度宽表透视。 */
    @Select("SELECT * FROM \"zing_doctor_db_prod\".\"quality_metric_result\" "
            + "WHERE \"period_type\" = 'MONTH' AND \"depart_code\" = #{departCode} "
            + "AND \"period_start\" >= #{yearStart} AND \"period_start\" < #{yearEnd} "
            + "ORDER BY \"metric_code\", \"period_start\"")
    List<QualityMetricResult> selectYear(@Param("yearStart") LocalDateTime yearStart,
                                         @Param("yearEnd") LocalDateTime yearEnd,
                                         @Param("departCode") String departCode);

    /** 批量落库（一次计算几百条结果，逐条 insert 太慢）。 */
    @Insert("<script>"
            + "INSERT INTO \"zing_doctor_db_prod\".\"quality_metric_result\" "
            + "(\"run_id\",\"metric_code\",\"metric_name\",\"domain_code\",\"period_type\",\"period_start\",\"period_end\","
            + "\"depart_code\",\"numerator\",\"denominator\",\"metric_value\",\"unit\",\"calc_status\",\"error_msg\","
            + "\"expression_version\",\"calc_time\") VALUES "
            + "<foreach collection='list' item='it' separator=','>"
            + "(#{it.runId},#{it.metricCode},#{it.metricName},#{it.domainCode},#{it.periodType},#{it.periodStart},#{it.periodEnd},"
            + "#{it.departCode},#{it.numerator},#{it.denominator},#{it.metricValue},#{it.unit},#{it.calcStatus},#{it.errorMsg},"
            + "#{it.expressionVersion},#{it.calcTime})"
            + "</foreach>"
            + "</script>")
    int batchInsert(@Param("list") List<QualityMetricResult> list);
}
