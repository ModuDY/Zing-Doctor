package com.zing.doctor.quality.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.quality.entity.QualityIndex;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 指标字典 Mapper（主库 doctor）。
 */
@DS("doctor")
@Mapper
public interface QualityIndexMapper extends BaseMapper<QualityIndex> {

    /** 按域 + 排序号输出全部指标（页面主列表）。 */
    @Select("SELECT * FROM \"zing_doctor_db_prod\".\"quality_index\" "
            + "WHERE \"status\" = 1 ORDER BY \"domain_code\", \"sort_no\", \"index_code\"")
    List<QualityIndex> selectAllOrdered();

    @Select("SELECT * FROM \"zing_doctor_db_prod\".\"quality_index\" "
            + "WHERE \"index_code\" = #{indexCode} AND \"status\" = 1")
    QualityIndex selectByCode(@Param("indexCode") String indexCode);

    /** 字典表中当前处于启用状态的指标编号，用于比对配置里是否已被删除。 */
    @Select("SELECT \"index_code\" FROM \"zing_doctor_db_prod\".\"quality_index\" WHERE \"status\" = 1")
    List<String> selectEnabledCodes();

    /** 停用：配置里删除的指标不物理删除，保留字典以便历史结果回溯。 */
    @Update("UPDATE \"zing_doctor_db_prod\".\"quality_index\" SET \"status\" = 0, \"update_time\" = #{updateTime} "
            + "WHERE \"index_code\" = #{indexCode}")
    int disableByCode(@Param("indexCode") String indexCode, @Param("updateTime") LocalDateTime updateTime);
}
