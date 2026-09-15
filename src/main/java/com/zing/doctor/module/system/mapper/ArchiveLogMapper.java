package com.zing.doctor.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.system.entity.ArchiveLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 归档推送流水 Mapper（主库 doctor）。
 */
@Mapper
public interface ArchiveLogMapper extends BaseMapper<ArchiveLog> {
}
