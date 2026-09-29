package com.zing.doctor.module.ards.prone.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.ards.prone.entity.ArdsProneTpTpl;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * ARDS 俯卧位时点模板 Mapper。
 */
public interface ArdsProneTpTplMapper extends BaseMapper<ArdsProneTpTpl> {

    /**
     * 取科室模板；科室无模板时回退全院默认（depart_code = ''）。
     */
    @Select("SELECT \"id\",\"depart_code\",\"tp_index\",\"tp_label\",\"offset_minutes\",\"status\" "
            + "FROM \"zing_doctor_db_prod\".\"config_prone_timepoint_tpl\" "
            + "WHERE \"depart_code\" = #{departCode} AND \"status\" = 1 "
            + "ORDER BY \"tp_index\" ASC")
    List<ArdsProneTpTpl> selectByDepart(@Param("departCode") String departCode);
}
