package com.zing.doctor.quality.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.quality.entity.QualityMetricDef;
import org.apache.ibatis.annotations.Mapper;

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
}
