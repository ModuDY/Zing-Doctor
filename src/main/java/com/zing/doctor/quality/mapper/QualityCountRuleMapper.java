package com.zing.doctor.quality.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.quality.entity.QualityCountRule;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
     * <p><b>del_flag 必须带 NULL 兜底</b>：源表该列允许 NULL，而 NULL 行是「未删除」的正常数据。
     * 只写 {@code del_flag = '0'} 会把整批 NULL 行静默漏掉 —— 的表现就是
     * 「点完同步，ICU 侧有的指标这边查不到」，且不会报任何错。
     *
     * <p><b>不去重</b>：ICU 侧「ICU医师床位比」「ICU护士床位比」各有 2 条
     * （id 不同、分子分母相同），按业务要求全部保留。
     *
     * <p><b>不同步 target/warning 四列</b>：源端全 NULL，同步会把本院已配置的值覆盖掉。
     */
    @Insert("INSERT INTO \"zing_doctor_db_prod\".\"quality_count_rule\" "
            + "(\"rule_id\",\"count_name\",\"quality_type_code\",\"is_visible\",\"is_show_page\",\"sort_no\",\"remark\","
            + "\"numerator_code\",\"denominator_code\",\"percent_unit\",\"percent_rate\",\"percent_precision\","
            + "\"depart_code\",\"source_status\",\"sync_time\",\"status\",\"origin\",\"local_override\") "
            + "SELECT src.\"id\", src.\"count_name\", src.\"quality_type_code\", "
            + "CAST(src.\"is_visible\" AS INT), CAST(src.\"is_show_page\" AS INT), CAST(src.\"sort_no\" AS INT), "
            + "src.\"remark\", src.\"numerator_code\", src.\"denominator_code\", src.\"percent_unit\", "
            + "CAST(src.\"percent_rate\" AS INT), CAST(src.\"percent_precision\" AS INT), "
            + "src.\"depart_code\", CAST(src.\"status\" AS INT), CURRENT_TIMESTAMP, 1, 'ICU', 0 "
            + "FROM \"zing_icu_db_prod\".\"quality_count_rule\" src "
            + "WHERE (src.\"del_flag\" = '0' OR src.\"del_flag\" IS NULL) "
            + "AND src.\"id\" NOT IN (SELECT \"rule_id\" FROM \"zing_doctor_db_prod\".\"quality_count_rule\")")
    int syncFromIcu();

    /**
     * 本院自建规则条数，用于生成下一条 {@code LOCAL_n} 编号。
     *
     * <p>用「现存条数 + 1」而非独立序列：本地规则可被物理删除，
     * 计数法即使编号出现空洞也无害（rule_id 只要求唯一与隔离，不要求连续）。
     */
    @Select("SELECT COUNT(*) FROM \"zing_doctor_db_prod\".\"quality_count_rule\" "
            + "WHERE \"origin\" = 'LOCAL'")
    int countLocalRules();

    /** 本院自建规则的 ruleId 是否已被占用（防并发下重复分配同一编号）。 */
    @Select("SELECT COUNT(*) FROM \"zing_doctor_db_prod\".\"quality_count_rule\" "
            + "WHERE \"rule_id\" = #{ruleId}")
    int countByRuleId(@Param("ruleId") String ruleId);

    /**
     * 写规则口径（分子 / 分母 / 名称 / 系数等）。
     *
     * <p>刻意不用 MyBatis-Plus 的 {@code updateById}：它的默认策略是「只更新非 null 字段」，
     * 想清空放大系数或备注时会被整列跳过，配置就再也删不掉。
     * 这里显式写全量业务列，null 即清空。
     *
     * <p>{@code target_value} / {@code warning_value} 不在此列 —— 它们由
     * {@link #updateTarget} 单独维护，避免编辑口径时把已配好的目标值抹掉。
     */
    @Update("UPDATE \"zing_doctor_db_prod\".\"quality_count_rule\" SET "
            + "\"count_name\" = #{r.countName}, \"quality_type_code\" = #{r.qualityTypeCode}, "
            + "\"is_show_page\" = #{r.isShowPage}, \"sort_no\" = #{r.sortNo}, \"remark\" = #{r.remark}, "
            + "\"numerator_code\" = #{r.numeratorCode}, \"denominator_code\" = #{r.denominatorCode}, "
            + "\"percent_unit\" = #{r.percentUnit}, \"percent_rate\" = #{r.percentRate}, "
            + "\"percent_precision\" = #{r.percentPrecision}, \"depart_code\" = #{r.departCode}, "
            + "\"local_override\" = #{r.localOverride}, \"update_time\" = CURRENT_TIMESTAMP "
            + "WHERE \"rule_id\" = #{r.ruleId}")
    int updateRuleCaliber(@Param("r") QualityCountRule rule);

    /** 启用 / 停用（停用不物理删除，历史结果仍可按 ruleId 回溯）。 */
    @Update("UPDATE \"zing_doctor_db_prod\".\"quality_count_rule\" "
            + "SET \"status\" = #{status}, \"update_time\" = CURRENT_TIMESTAMP "
            + "WHERE \"rule_id\" = #{ruleId}")
    int updateRuleStatus(@Param("ruleId") String ruleId, @Param("status") Integer status);

    /**
     * 读 ICU 侧规则全量（未删除），用于「刷新已存在规则」。
     *
     * <p>用 {@code Map} 接收而非实体：源表列名带引号小写，不同驱动返回的 key 大小写不确定，
     * 取值统一走 {@code QualityCountRuleService#val} 的大小写无关查找。
     *
     * <p>这是只读查询，失败不影响主流程 —— 由调用方 try/catch 后降级为「只新增不刷新」。
     */
    @Select("SELECT src.\"id\" AS \"rule_id\", src.\"count_name\", src.\"quality_type_code\", "
            + "src.\"is_visible\", src.\"is_show_page\", src.\"sort_no\", src.\"remark\", "
            + "src.\"numerator_code\", src.\"denominator_code\", src.\"percent_unit\", "
            + "src.\"percent_rate\", src.\"percent_precision\", src.\"depart_code\", src.\"status\" "
            + "FROM \"zing_icu_db_prod\".\"quality_count_rule\" src "
            + "WHERE (src.\"del_flag\" = '0' OR src.\"del_flag\" IS NULL)")
    List<Map<String, Object>> selectIcuSource();

    /**
     * 按 ICU 侧规则 id 定位本表规则。
     *
     * <p>配目标值、单条计算都靠它定位 —— rule_id 是业务主键（源端 id），
     * 比自增 id 稳：同步重建行也不会变。
     */
    @Select("SELECT * FROM \"zing_doctor_db_prod\".\"quality_count_rule\" WHERE \"rule_id\" = #{ruleId}")
    QualityCountRule selectByRuleId(@Param("ruleId") String ruleId);

    /**
     * 写本院自管的目标值 / 预警值。
     *
     * <p>刻意不用 MyBatis-Plus 的 updateById：它的默认策略是「只更新非 null 字段」，
     * 传 null 想清空目标值时会被整列跳过，配置就再也删不掉。
     * 这里显式写两列，null 即清空。
     */
    @Update("UPDATE \"zing_doctor_db_prod\".\"quality_count_rule\" "
            + "SET \"target_value\" = #{targetValue}, \"warning_value\" = #{warningValue}, "
            + "\"target_direction\" = #{targetDirection}, "
            + "\"update_time\" = CURRENT_TIMESTAMP "
            + "WHERE \"rule_id\" = #{ruleId}")
    int updateTarget(@Param("ruleId") String ruleId,
                     @Param("targetValue") BigDecimal targetValue,
                     @Param("warningValue") BigDecimal warningValue,
                     @Param("targetDirection") String targetDirection);

    /** 看板取数：仅 is_show_page=1 且未停用，按 sort_no 排序。 */
    @Select("SELECT * FROM \"zing_doctor_db_prod\".\"quality_count_rule\" "
            + "WHERE \"status\" = 1 AND \"is_show_page\" = 1 "
            + "ORDER BY \"sort_no\", \"id\"")
    List<QualityCountRule> selectForBoard();

    /** 全部规则（含不上看板的），供配置与排查。 */
    @Select("SELECT * FROM \"zing_doctor_db_prod\".\"quality_count_rule\" "
            + "WHERE \"status\" = 1 ORDER BY \"sort_no\", \"id\"")
    List<QualityCountRule> selectAll();

    /**
     * 配置页全量：含未上板与已停用的规则。
     *
     * <p>与 {@link #selectAll()} 的区别是不限 status —— 配置页要能看到并重新启用
     * 停用过的规则，否则停用一次就再也找不回来。本院自建规则排在最前，
     * 便于「我刚加的那条」一眼可见。
     */
    @Select("SELECT * FROM \"zing_doctor_db_prod\".\"quality_count_rule\" "
            + "ORDER BY CASE WHEN \"origin\" = 'LOCAL' THEN 0 ELSE 1 END, \"sort_no\", \"id\"")
    List<QualityCountRule> selectAllForConfig();
}
