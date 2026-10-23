package com.zing.doctor.module.config.dataitem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.config.dataitem.entity.DataItemConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 数据项映射配置 Mapper（读多写少，结果由 Service 缓存）。
 */
@Mapper
public interface DataItemConfigMapper extends BaseMapper<DataItemConfig> {

    String COLS = "\"id\",\"domain\",\"group_code\",\"group_name\",\"item_key\",\"item_name\",\"unit\",\"source\","
            + "\"match_type\",\"match_value\",\"exclude_value\",\"sort_no\",\"status\",\"remark\","
            + "\"create_by\",\"create_time\",\"update_by\",\"update_time\"";

    /** 后台列表（含停用项）：domain 可空 = 全部 */
    @Select("<script>SELECT " + COLS + " FROM \"zing_doctor_db_prod\".\"config_data_item\" WHERE 1 = 1 "
            + "<if test='domain != null and domain != \"\"'> AND \"domain\" = #{domain}</if> "
            + "ORDER BY \"domain\" ASC, \"group_code\" ASC, \"sort_no\" ASC, \"id\" ASC</script>")
    List<DataItemConfig> selectForAdmin(@Param("domain") String domain);

    /** 全部启用项，供 Service 构建缓存 */
    @Select("SELECT " + COLS + " FROM \"zing_doctor_db_prod\".\"config_data_item\" WHERE \"status\" = 1 "
            + "ORDER BY \"domain\" ASC, \"group_code\" ASC, \"sort_no\" ASC, \"id\" ASC")
    List<DataItemConfig> selectEnabled();

    /** 判重：同域 + 同分组 + 同键 + 同匹配值视为同一条 */
    @Select("SELECT COUNT(*) FROM \"zing_doctor_db_prod\".\"config_data_item\" "
            + "WHERE \"domain\" = #{domain} AND \"item_key\" = #{itemKey} AND \"match_value\" = #{matchValue} "
            + "AND COALESCE(\"group_code\", '') = COALESCE(#{groupCode}, '')")
    int countSame(@Param("domain") String domain,
                  @Param("groupCode") String groupCode,
                  @Param("itemKey") String itemKey,
                  @Param("matchValue") String matchValue);
}
