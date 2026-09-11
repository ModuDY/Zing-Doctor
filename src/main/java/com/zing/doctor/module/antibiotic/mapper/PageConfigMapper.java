package com.zing.doctor.module.antibiotic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.antibiotic.entity.PageConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 页面注册表 Mapper。
 */
@Mapper
public interface PageConfigMapper extends BaseMapper<PageConfig> {

    @Select("SELECT * FROM \"zing_doctor_db_prod\".\"zing_page_config\" "
            + "WHERE page_code = #{pageCode} AND status = 1 "
            + "AND ROWNUM <= 1")
    PageConfig selectByPageCode(@Param("pageCode") String pageCode);
}
