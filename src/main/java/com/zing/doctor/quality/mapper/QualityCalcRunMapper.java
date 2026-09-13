package com.zing.doctor.quality.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.quality.entity.QualityCalcRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 计算批次 Mapper（血缘第 1 层）。
 */
@DS("doctor")
@Mapper
public interface QualityCalcRunMapper extends BaseMapper<QualityCalcRun> {

    @Select("SELECT * FROM \"zing_doctor_db_prod\".\"quality_calc_run\" "
            + "WHERE \"run_id\" = #{runId}")
    QualityCalcRun selectByRunId(@Param("runId") String runId);

    /** 最近批次（用 ROWNUM 而非 LIMIT，达梦原生兼容）。 */
    @Select("SELECT * FROM (SELECT * FROM \"zing_doctor_db_prod\".\"quality_calc_run\" "
            + "ORDER BY \"start_time\" DESC) WHERE ROWNUM <= 20")
    List<QualityCalcRun> selectRecent();
}
