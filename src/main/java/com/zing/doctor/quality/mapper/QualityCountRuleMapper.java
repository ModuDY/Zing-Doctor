package com.zing.doctor.quality.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.quality.entity.QualityCountRule;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 质控指标规则 Mapper（主库 doctor）。
 *
 * <p><b>包位置约定</b>：必须在 {@code com.zing.doctor.quality.mapper}。
 * 应用启动类显式声明了 {@code @MapperScan({"com.zing.doctor.**.mapper", "com.zing.doctor.quality.engine"})}，
 * 一旦显式声明，MyBatis 兜底扫描即失效 —— 放在其它包里不会注册为 Bean，启动直接失败。
 */
@DS("doctor")
@Mapper
public interface QualityCountRuleMapper extends BaseMapper<QualityCountRule> {

    /**
     * 从 ICU 侧同步规则（跨 schema，幂等：只插入 rule_id 尚不存在的）。
     *
     * <p>能跨 schema 是因为 doctor 与 icu 指向同一达梦实例（host/port 相同），
     * 单条 SQL 内可直接引用 {@code "zing_icu_db_prod"}。
     *
     * <p>CAST 是兜住源列可能为字符型（源表 percent_rate 取值形如 '100'）；
     * 若某天源端改了列类型导致 CAST 报错，去掉对应 CAST 直接取列即可。
     *
     * <p><b>不去重</b>：ICU 侧「ICU医师床位比」「ICU护士床位比」各有 2 条
     * （id 不同、分子分母相同），按业务要求全部保留。
     *
     * <p><b>不同步 target/warning 四列</b>：源端全 NULL，同步会把本院已配置的值覆盖掉。
     */
    @Insert("INSERT INTO \"zing_doctor_db_prod\".\"quality_count_rule\" "
            + "(\"rule_id\",\"count_name\",\"quality_type_code\",\"is_visible\",\"is_show_page\",\"sort_no\",\"remark\","
            + "\"numerator_code\",\"denominator_code\",\"percent_unit\",\"percent_rate\",\"percent_precision\","
            + "\"depart_code\",\"source_status\",\"sync_time\",\"status\") "
            + "SELECT src.\"id\", src.\"count_name\", src.\"quality_type_code\", "
            + "CAST(src.\"is_visible\" AS INT), CAST(src.\"is_show_page\" AS INT), CAST(src.\"sort_no\" AS INT), "
            + "src.\"remark\", src.\"numerator_code\", src.\"denominator_code\", src.\"percent_unit\", "
            + "CAST(src.\"percent_rate\" AS INT), CAST(src.\"percent_precision\" AS INT), "
            + "src.\"depart_code\", CAST(src.\"status\" AS INT), CURRENT_TIMESTAMP, 1 "
            + "FROM \"zing_icu_db_prod\".\"quality_count_rule\" src "
            + "WHERE src.\"del_flag\" = '0' "
            + "AND src.\"id\" NOT IN (SELECT \"rule_id\" FROM \"zing_doctor_db_prod\".\"quality_count_rule\")")
    int syncFromIcu();

    /** 看板取数：仅 is_show_page=1 且未停用，按 sort_no 排序。 */
    @Select("SELECT * FROM \"zing_doctor_db_prod\".\"quality_count_rule\" "
            + "WHERE \"status\" = 1 AND \"is_show_page\" = 1 "
            + "ORDER BY \"sort_no\", \"id\"")
    List<QualityCountRule> selectForBoard();

    /** 全部规则（含不上看板的），供配置与排查。 */
    @Select("SELECT * FROM \"zing_doctor_db_prod\".\"quality_count_rule\" "
            + "WHERE \"status\" = 1 ORDER BY \"sort_no\", \"id\"")
    List<QualityCountRule> selectAll();
}
