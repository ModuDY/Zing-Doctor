package com.zing.doctor.module.antibiotic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.antibiotic.entity.AdviceLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 抗感染方案推荐日志 Mapper。
 */
@Mapper
public interface AdviceLogMapper extends BaseMapper<AdviceLog> {
}
