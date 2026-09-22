package com.zing.doctor.module.ards.prone.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.ards.prone.entity.ArdsProneCellLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * ARDS 俯卧位记录单元格更正留痕 Mapper。
 */
public interface ArdsProneCellLogMapper extends BaseMapper<ArdsProneCellLog> {

    @Select("SELECT \"id\",\"record_id\",\"tp_index\",\"param_key\",\"old_value\",\"new_value\","
            + "\"old_source\",\"new_source\",\"reason\",\"operator\",\"create_time\" "
            + "FROM \"zing_doctor_db_prod\".\"patient_doc_prone_cell_log\" "
            + "WHERE \"record_id\" = #{recordId} "
            + "ORDER BY \"create_time\" DESC")
    List<ArdsProneCellLog> selectByRecord(@Param("recordId") Long recordId);
}
