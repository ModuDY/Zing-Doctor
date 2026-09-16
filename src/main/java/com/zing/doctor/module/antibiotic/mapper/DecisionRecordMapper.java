package com.zing.doctor.module.antibiotic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.antibiotic.entity.DecisionRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 抗感染决策记录 Mapper。
 */
@Mapper
public interface DecisionRecordMapper extends BaseMapper<DecisionRecord> {
}
