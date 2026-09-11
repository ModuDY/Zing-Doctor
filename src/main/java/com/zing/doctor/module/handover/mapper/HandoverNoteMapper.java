package com.zing.doctor.module.handover.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.handover.entity.HandoverNote;
import org.apache.ibatis.annotations.Mapper;

/**
 * 医生交班手工记录 Mapper（默认数据源 zing_doctor_db_prod）。
 */
@Mapper
public interface HandoverNoteMapper extends BaseMapper<HandoverNote> {
}
