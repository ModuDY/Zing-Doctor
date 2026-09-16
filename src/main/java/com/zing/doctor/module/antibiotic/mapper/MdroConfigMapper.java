package com.zing.doctor.module.antibiotic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.antibiotic.entity.MdroConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 细菌培养监测配置表 Mapper（第四维度）。
 */
@Mapper
public interface MdroConfigMapper extends BaseMapper<MdroConfig> {
}
