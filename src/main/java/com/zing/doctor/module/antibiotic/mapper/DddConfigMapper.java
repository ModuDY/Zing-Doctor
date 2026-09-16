package com.zing.doctor.module.antibiotic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.antibiotic.entity.DddConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 抗菌药物 DDD 值配置 Mapper。
 */
@Mapper
public interface DddConfigMapper extends BaseMapper<DddConfig> {

    /** 查询所有启用的 DDD 配置（按药物分类排序） */
    @Select("SELECT * FROM \"zing_doctor_db_prod\".\"zing_ddd_config\" "
            + "WHERE status = 1 ORDER BY drug_class, drug_name")
    List<DddConfig> selectAllActive();

    /** 按药物分类查询 */
    @Select("SELECT * FROM \"zing_doctor_db_prod\".\"zing_ddd_config\" "
            + "WHERE status = 1 AND drug_class = #{drugClass} ORDER BY drug_name")
    List<DddConfig> selectByDrugClass(@Param("drugClass") String drugClass);

    /** 按管理级别查询 */
    @Select("SELECT * FROM \"zing_doctor_db_prod\".\"zing_ddd_config\" "
            + "WHERE status = 1 AND manage_level = #{manageLevel} ORDER BY drug_name")
    List<DddConfig> selectByManageLevel(@Param("manageLevel") String manageLevel);
}
