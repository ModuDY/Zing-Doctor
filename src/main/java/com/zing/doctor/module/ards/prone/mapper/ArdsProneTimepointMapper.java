package com.zing.doctor.module.ards.prone.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.ards.prone.entity.ArdsProneTimepoint;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * ARDS 俯卧位记录时点 Mapper。
 */
public interface ArdsProneTimepointMapper extends BaseMapper<ArdsProneTimepoint> {

    @Select("SELECT \"id\",\"record_id\",\"tp_index\",\"tp_label\",\"offset_minutes\",\"plan_time\","
            + "\"collect_status\",\"status\",\"create_by\",\"create_time\",\"update_by\",\"update_time\" "
            + "FROM \"zing_doctor_db_prod\".\"patient_doc_prone_timepoint\" "
            + "WHERE \"record_id\" = #{recordId} AND \"status\" = 1 "
            + "ORDER BY \"tp_index\" ASC")
    List<ArdsProneTimepoint> selectByRecord(@Param("recordId") Long recordId);
}
