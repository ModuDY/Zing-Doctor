package com.zing.doctor.module.antibiotic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.antibiotic.entity.AntibioticReassessment;
import org.apache.ibatis.annotations.Mapper;

/** 抗感染 48～72 小时复评 Mapper。 */
@Mapper
public interface AntibioticReassessmentMapper extends BaseMapper<AntibioticReassessment> {
}
