package com.zing.doctor.module.sofa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.sofa.entity.SofaConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * SOFA 配置 Mapper（主库 doctor）。
 */
@Mapper
public interface SofaConfigMapper extends BaseMapper<SofaConfig> {
}
