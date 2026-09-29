package com.zing.doctor.quality.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.quality.entity.QualityDefHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 配置变更历史 Mapper（主库 doctor）。
 *
 * <p>包位置约定见 {@link QualityMetricDefMapper}。
 */
@DS("doctor")
@Mapper
public interface QualityDefHistoryMapper extends BaseMapper<QualityDefHistory> {
}
