package com.zing.doctor.quality.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.quality.entity.QualityMetricDef;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 指标定义 Mapper（配置真源，主库 doctor）。
 *
 * <p><b>包位置约定</b>：必须位于 {@code com.zing.doctor.quality.mapper}。
 * 应用启动类用的是 {@code @MapperScan({"com.zing.doctor.**.mapper", "com.zing.doctor.quality.engine"})}，
 * 一旦显式声明 @MapperScan，MyBatis 的兜底扫描就会失效 —— 放在其它包里的 Mapper 不会注册为 Bean，
 * 启动时直接 APPLICATION FAILED TO START（此前 QualitySqlMapper 就是这样踩过一次）。
 */
@DS("doctor")
@Mapper
public interface QualityMetricDefMapper extends BaseMapper<QualityMetricDef> {

    /**
     * 列是否存在（启动自检用，只读元数据）。
     *
     * <p>用元数据查询而不是 {@code SELECT 列 FROM 表}：表不存在或列不存在都只会返回 0 行，
     * 不会抛异常，自检因此永远不会妨碍启动。
     */
    @Select("SELECT COUNT(1) FROM ALL_TAB_COLUMNS "
            + "WHERE UPPER(OWNER) = UPPER(#{schema}) AND UPPER(TABLE_NAME) = UPPER(#{table}) "
            + "AND UPPER(COLUMN_NAME) = UPPER(#{column})")
    int countColumn(@Param("schema") String schema,
                    @Param("table") String table,
                    @Param("column") String column);
}
