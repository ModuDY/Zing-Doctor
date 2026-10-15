package com.zing.doctor.module.msgpush.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.msgpush.entity.MsgPushLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息推送审计 Mapper。
 */
@Mapper
public interface MsgPushLogMapper extends BaseMapper<MsgPushLog> {
}
