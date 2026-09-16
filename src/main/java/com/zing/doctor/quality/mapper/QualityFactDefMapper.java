package com.zing.doctor.quality.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.quality.entity.QualityFactDef;
import org.apache.ibatis.annotations.Mapper;

/**
 * 事实层定义 Mapper（配置真源，主库 doctor）。
 *
 * <p>包位置约定见 {@link QualityMetricDefMapper}。
 */
@DS("doctor")
@Mapper
public interface QualityFactDefMapper extends BaseMapper<QualityFactDef> {
}
