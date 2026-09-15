package com.zing.doctor.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper（主库 doctor）。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
