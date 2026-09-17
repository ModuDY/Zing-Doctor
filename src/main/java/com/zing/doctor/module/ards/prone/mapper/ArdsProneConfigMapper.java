package com.zing.doctor.module.ards.prone.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.ards.prone.entity.ArdsProneConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * ARDS 俯卧位采集映射配置 Mapper。
 *
 * <p>配置读多写少：查询走主库（本表在 zing_doctor_db_prod），取数时由
 * {@code ArdsProneMappingResolver} 缓存，保存/删除/启停后失效重载。
 */
@Mapper
public interface ArdsProneConfigMapper extends BaseMapper<ArdsProneConfig> {

    /** 全部启用规则（按参数键、优先级、通道排序），供解析器构建缓存 */
    @Select("SELECT \"id\",\"config_type\",\"config_key\",\"config_value\",\"match_type\",\"priority\","
            + "\"window_min\",\"unit_scale\",\"unit_offset\",\"item_name\",\"remark\",\"sort_no\",\"status\" "
            + "FROM \"zing_doctor_db_prod\".\"ards_prone_config\" "
            + "WHERE \"status\" = 1 "
            + "ORDER BY \"config_key\" ASC, \"priority\" ASC, \"config_type\" DESC, \"sort_no\" ASC, \"id\" ASC")
    List<ArdsProneConfig> selectEnabled();

    /** 后台列表（含停用项，供配置页管理） */
    @Select("<script>SELECT \"id\",\"config_type\",\"config_key\",\"config_value\",\"match_type\",\"priority\","
            + "\"window_min\",\"unit_scale\",\"unit_offset\",\"item_name\",\"remark\",\"sort_no\",\"status\","
            + "\"create_by\",\"create_time\",\"update_by\",\"update_time\" "
            + "FROM \"zing_doctor_db_prod\".\"ards_prone_config\" WHERE 1 = 1 "
            + "<if test='configType != null and configType != \"\"'> AND \"config_type\" = #{configType}</if> "
            + "<if test='configKey != null and configKey != \"\"'> AND \"config_key\" = #{configKey}</if> "
            + "ORDER BY \"config_key\" ASC, \"priority\" ASC, \"sort_no\" ASC, \"id\" ASC</script>")
    List<ArdsProneConfig> selectForAdmin(@Param("configType") String configType,
                                        @Param("configKey") String configKey);

    /** 判重：同参数 + 同通道 + 同匹配值视为同一条（一键生成时幂等） */
    @Select("SELECT COUNT(*) FROM \"zing_doctor_db_prod\".\"ards_prone_config\" "
            + "WHERE \"config_key\" = #{configKey} AND \"config_type\" = #{configType} "
            + "AND \"config_value\" = #{configValue}")
    int countSame(@Param("configKey") String configKey,
                  @Param("configType") String configType,
                  @Param("configValue") String configValue);
}
