package com.zing.doctor.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.system.entity.SysParam;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统参数 Mapper（主库 doctor）。
 */
@Mapper
public interface SysParamMapper extends BaseMapper<SysParam> {
}
