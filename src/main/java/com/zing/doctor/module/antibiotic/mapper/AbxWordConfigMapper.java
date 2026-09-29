package com.zing.doctor.module.antibiotic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.antibiotic.entity.AbxWordConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 抗菌药物识别词库配置 Mapper
 */
@Mapper
public interface AbxWordConfigMapper extends BaseMapper<AbxWordConfig> {
}
