package com.zing.doctor.quality.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.quality.entity.QualityMetricResult;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 指标结果 Mapper（血缘第 2 层）。
 *
 * <p>幂等策略：同一 (周期, 部门) 重算前先删后插，不依赖数据库特有的 UPSERT 语法，
 * 达梦 / MySQL 均可运行。
 */
@DS("doctor")
@Mapper
public interface QualityMetricResultMapper extends BaseMapper<QualityMetricResult> {

    /**
     * 重算删除时的「人工录入值保护」条件，拼在每条删除语句末尾。
     *
     * <p>人工录入且有值的行不参与删除，因此重算不会覆盖它们。集中成一个常量而不是在五条
     * 语句里各写一遍：漏写任何一条，那条路径上的人工值就会被静默抹掉，而症状是
     * 「偶尔丢数」—— 事后极难追查（现场已踩：每月 1 日定时批算抹掉上月手工录入值）。
     *
     * <p>判据用 {@code value_source} 显式列而不是 {@code calc_status='MANUAL' AND metric_value IS NOT NULL}：
     * 后者是把业务含义藏在两个字段的组合里，后人调整状态写法就会失效。
     *
     * <p><b>为什么写成 {@code NOT (x = 'MANUAL')} 而不是 {@code x <> 'MANUAL'}</b>：
     * MyBatis 会把注解里的 SQL 当作 XML 文档解析，{@code <>} 中的 {@code <} 会被当成标签起始，
     * 启动时直接抛「元素内容必须由格式正确的字符数据或标记组成」，Mapper 建不出来、
     * 整个应用起不来（现场已踩）。这类错误编译期毫无征兆，只有启动才暴露。
     * {@code NOT (x = ...)} 语义等价且不含 XML 特殊字符；{@code !=} 虽然也可行，
     * 但它不是标准 SQL，不如前者稳妥。
     *
     * <p>{@code IS NULL} 分支保留着：尽管 DDL 给了 DEFAULT 'AUTO'，历史行或人工补数仍可能为 NULL，
     * 而 {@code NOT (NULL = 'MANUAL')} 求值为 UNKNOWN，行会被静默排除在删除范围之外。
     */
    String MANUAL_GUARD = " AND (\"value_source\" IS NULL OR NOT (\"value_source\" = 'MANUAL'))";

    @Select("SELECT * FROM \"zing_doctor_db_prod\".\"quality_metric_result\" "
            + "WHERE \"period_start\" = #{periodStart} AND \"depart_code\" = #{departCode} "
            + "ORDER BY \"domain_code\", \"metric_code\"")
    List<QualityMetricResult> selectByPeriod(@Param("periodStart") LocalDateTime periodStart,
                                             @Param("departCode") String departCode);

    @Delete("DELETE FROM \"zing_doctor_db_prod\".\"quality_metric_result\" "
            + "WHERE \"period_type\" = #{periodType} AND \"period_start\" = #{periodStart} "
            + "AND \"depart_code\" = #{departCode}" + MANUAL_GUARD)
    int deleteByPeriod(@Param("periodType") String periodType,
                       @Param("periodStart") LocalDateTime periodStart,
                       @Param("departCode") String departCode);

    /** 删除单条指标结果（人工录入覆盖用）。 */
    @Delete("DELETE FROM \"zing_doctor_db_prod\".\"quality_metric_result\" "
            + "WHERE \"metric_code\" = #{metricCode} AND \"period_type\" = #{periodType} "
            + "AND \"period_start\" = #{periodStart} AND \"depart_code\" = #{departCode}")
    int deleteOne(@Param("metricCode") String metricCode,
                  @Param("periodType") String periodType,
                  @Param("periodStart") LocalDateTime periodStart,
                  @Param("departCode") String departCode);

    /**
     * 删除某指标在某周期下的全部科室结果（单指标重算用）。
     *
     * <p>不带 depart_code 是刻意的：一条指标算完会产出「逐科室 + 全院汇总」多行，
     * 只删当前筛选科室会让其余科室的旧行残留，新旧混在同一周期里。
     */
    @Delete("DELETE FROM \"zing_doctor_db_prod\".\"quality_metric_result\" "
            + "WHERE \"metric_code\" = #{metricCode} AND \"period_type\" = #{periodType} "
            + "AND \"period_start\" = #{periodStart}" + MANUAL_GUARD)
    int deleteByMetricPeriod(@Param("metricCode") String metricCode,
                             @Param("periodType") String periodType,
                             @Param("periodStart") LocalDateTime periodStart);

    /**
     * 删除某周期下**所有科室**的结果（整批全院计算用）。
     *
     * <p>不能复用只删一行的那条：批次是「全院 + 各科室」一次写入多行，
     * 若只删掉当前筛选科室那一行，其余科室的旧行会和本次插入撞唯一键
     * {@code uk_quality_result}，整批可能直接失败。
     */
    @Delete("DELETE FROM \"zing_doctor_db_prod\".\"quality_metric_result\" "
            + "WHERE \"period_type\" = #{periodType} AND \"period_start\" = #{periodStart}"
            + MANUAL_GUARD)
    int deleteByPeriodAllDept(@Param("periodType") String periodType,
                              @Param("periodStart") LocalDateTime periodStart);

    /**
     * 删除某指标在某周期下**指定科室**的结果（单指标按科室重算用）。
     *
     * <p>与 {@link #deleteByMetricPeriod} 的区别：那条清掉全部科室，
     * 而按科室重算只会重新写入该科室一行 —— 清掉别的科室等于把数据删没了。
     */
    @Delete("DELETE FROM \"zing_doctor_db_prod\".\"quality_metric_result\" "
            + "WHERE \"metric_code\" = #{metricCode} AND \"period_type\" = #{periodType} "
            + "AND \"period_start\" = #{periodStart} AND \"depart_code\" = #{departCode}"
            + MANUAL_GUARD)
    int deleteByMetricPeriodDept(@Param("metricCode") String metricCode,
                                 @Param("periodType") String periodType,
                                 @Param("periodStart") LocalDateTime periodStart,
                                 @Param("departCode") String departCode);

    /**
     * 删除若干指标在整个周期下的结果（科室批次里「会整周期重写」的那些指标）。
     *
     * <p>科室批次不是所有指标都只写一行：没有科室维度的指标（占位类、dims 为空的、
     * 按 depart_name 分维的）本批次仍按全院口径算，会写出 ALL 行乃至各科室行。
     * 它们的删除范围必须跟着扩到整周期，否则本次要插入的 ALL 行会与库里旧行撞唯一键。
     */
    @Delete("<script>DELETE FROM \"zing_doctor_db_prod\".\"quality_metric_result\" "
            + "WHERE \"period_type\" = #{periodType} AND \"period_start\" = #{periodStart} "
            + "AND \"metric_code\" IN "
            + "<foreach collection='codes' item='c' open='(' separator=',' close=')'>#{c}</foreach>"
            + MANUAL_GUARD
            + "</script>")
    int deleteByMetricCodes(@Param("periodType") String periodType,
                            @Param("periodStart") LocalDateTime periodStart,
                            @Param("codes") List<String> codes);

    /**
     * 取某周期下已存在「全院 ALL 行」的指标编码。
     *
     * <p>供科室批次判断「占位类指标能否整条跳过」：占位指标的值与科室无关，
     * 若该周期的全院行已在库里（上一次全院计算写下的），科室批次就不必再动它。
     * 查不到就当没有，调用方会退回「照常计算」，不会因此缺行。
     */
    @Select("SELECT DISTINCT \"metric_code\" FROM \"zing_doctor_db_prod\".\"quality_metric_result\" "
            + "WHERE \"period_type\" = #{periodType} AND \"period_start\" = #{periodStart} "
            + "AND \"depart_code\" = 'ALL'")
    List<String> selectCodesWithAll(@Param("periodType") String periodType,
                                    @Param("periodStart") LocalDateTime periodStart);

    /**
     * 取某周期下「受保护的人工录入行」的指标 × 科室组合（{@code value_source='MANUAL'}）。
     *
     * <p>供批算落库前过滤：这些行在删除阶段被有意保留，本批次就不能再写同 key 的占位行。
     * 虽然撞唯一键后逐条插入的降级逻辑也会跳过它们，但那是「意外保住」——
     * 降级逻辑一旦调整，丢掉的正是人工录入的真值。所以这里显式判断并跳过。
     */
    @Select("SELECT \"metric_code\", \"depart_code\" FROM \"zing_doctor_db_prod\".\"quality_metric_result\" "
            + "WHERE \"period_type\" = #{periodType} AND \"period_start\" = #{periodStart} "
            + "AND \"value_source\" = 'MANUAL'")
    List<QualityMetricResult> selectManualProtected(@Param("periodType") String periodType,
                                                    @Param("periodStart") LocalDateTime periodStart);

    /** 取某年全部月度结果，供月度宽表透视。 */
    @Select("SELECT * FROM \"zing_doctor_db_prod\".\"quality_metric_result\" "
            + "WHERE \"period_type\" = 'MONTH' AND \"depart_code\" = #{departCode} "
            + "AND \"period_start\" >= #{yearStart} AND \"period_start\" < #{yearEnd} "
            + "ORDER BY \"metric_code\", \"period_start\"")
    List<QualityMetricResult> selectYear(@Param("yearStart") LocalDateTime yearStart,
                                         @Param("yearEnd") LocalDateTime yearEnd,
                                         @Param("departCode") String departCode);

    /** 批量落库（一次计算几百条结果，逐条 insert 太慢）。 */
    @Insert("<script>"
            + "INSERT INTO \"zing_doctor_db_prod\".\"quality_metric_result\" "
            + "(\"run_id\",\"metric_code\",\"metric_name\",\"domain_code\",\"period_type\",\"period_start\",\"period_end\","
            + "\"depart_code\",\"numerator\",\"denominator\",\"metric_value\",\"unit\",\"calc_status\",\"error_msg\","
            + "\"expression_version\",\"calc_time\",\"value_source\",\"operator\",\"manual_note\") VALUES "
            + "<foreach collection='list' item='it' separator=','>"
            + "(#{it.runId},#{it.metricCode},#{it.metricName},#{it.domainCode},#{it.periodType},#{it.periodStart},#{it.periodEnd},"
            + "#{it.departCode},#{it.numerator},#{it.denominator},#{it.metricValue},#{it.unit},#{it.calcStatus},#{it.errorMsg},"
            + "#{it.expressionVersion},#{it.calcTime},#{it.valueSource},#{it.operator},#{it.manualNote})"
            + "</foreach>"
            + "</script>")
    int batchInsert(@Param("list") List<QualityMetricResult> list);
}
