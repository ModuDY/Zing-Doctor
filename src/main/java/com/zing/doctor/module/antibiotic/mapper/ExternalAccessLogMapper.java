package com.zing.doctor.module.antibiotic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.antibiotic.entity.ExternalAccessLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 外链访问日志 Mapper。
 */
@Mapper
public interface ExternalAccessLogMapper extends BaseMapper<ExternalAccessLog> {
}
