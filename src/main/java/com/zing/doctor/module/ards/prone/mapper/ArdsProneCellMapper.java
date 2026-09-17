package com.zing.doctor.module.ards.prone.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.ards.prone.entity.ArdsProneCell;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * ARDS 俯卧位记录单元格值 Mapper。
 */
public interface ArdsProneCellMapper extends BaseMapper<ArdsProneCell> {

    @Select("SELECT \"id\",\"record_id\",\"tp_index\",\"param_key\",\"value_text\",\"value_num\",\"source\","
            + "\"collect_time\",\"manual_reason\",\"status\",\"create_by\",\"create_time\",\"update_by\",\"update_time\" "
            + "FROM \"zing_doctor_db_prod\".\"ards_prone_cell\" "
            + "WHERE \"record_id\" = #{recordId} AND \"status\" = 1 "
            + "ORDER BY \"tp_index\" ASC, \"id\" ASC")
    List<ArdsProneCell> selectByRecord(@Param("recordId") Long recordId);

    @Select("SELECT \"id\",\"record_id\",\"tp_index\",\"param_key\",\"value_text\",\"value_num\",\"source\","
            + "\"collect_time\",\"manual_reason\",\"status\" "
            + "FROM \"zing_doctor_db_prod\".\"ards_prone_cell\" "
            + "WHERE \"record_id\" = #{recordId} AND \"tp_index\" = #{tpIndex} AND \"param_key\" = #{paramKey} "
            + "AND \"status\" = 1")
    List<ArdsProneCell> selectOne(@Param("recordId") Long recordId,
                                  @Param("tpIndex") Integer tpIndex,
                                  @Param("paramKey") String paramKey);

    /** 批量取多条记录的单元格值：列表页统计 P/F 与 ΔP，避免逐条查询 */
    @Select("<script>SELECT \"id\",\"record_id\",\"tp_index\",\"param_key\",\"value_text\",\"value_num\",\"source\","
            + "\"collect_time\",\"manual_reason\",\"status\" "
            + "FROM \"zing_doctor_db_prod\".\"ards_prone_cell\" "
            + "WHERE \"record_id\" IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "AND \"status\" = 1 "
            + "ORDER BY \"record_id\" ASC, \"tp_index\" ASC, \"id\" ASC</script>")
    List<ArdsProneCell> selectByRecordIds(@Param("ids") List<Long> ids);
}
