package com.zing.doctor.quality.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.quality.entity.QualityMonthlyReport;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 月度汇总宽表 Mapper。
 */
@DS("doctor")
@Mapper
public interface QualityMonthlyReportMapper extends BaseMapper<QualityMonthlyReport> {

    @Select("SELECT * FROM \"zing_doctor_db_prod\".\"quality_monthly_report\" "
            + "WHERE \"year\" = #{year} AND \"status\" = 1 "
            + "ORDER BY \"domain_code\", \"index_code\"")
    List<QualityMonthlyReport> selectByYear(@Param("year") Integer year);

    @Select("SELECT * FROM \"zing_doctor_db_prod\".\"quality_monthly_report\" "
            + "WHERE \"year\" = #{year} AND \"depart_code\" = #{departCode} AND \"status\" = 1 "
            + "ORDER BY \"domain_code\", \"index_code\"")
    List<QualityMonthlyReport> selectByYearAndDepart(@Param("year") Integer year,
                                                     @Param("departCode") String departCode);

    @Delete("DELETE FROM \"zing_doctor_db_prod\".\"quality_monthly_report\" "
            + "WHERE \"year\" = #{year} AND \"depart_code\" = #{departCode}")
    int deleteByYear(@Param("year") Integer year, @Param("departCode") String departCode);
}
