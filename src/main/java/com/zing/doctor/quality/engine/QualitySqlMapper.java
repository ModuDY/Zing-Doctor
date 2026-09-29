package com.zing.doctor.quality.engine;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 质控引擎动态 SQL 执行入口。
 *
 * <p>统一走 {@code doctor} 数据源：在本项目部署中 doctor 与 icu 指向同一达梦实例
 * （见 application.yml，二者 host/port 相同），因此单条 SQL 内可跨 schema 引用
 * {@code "zing_icu_db_prod"} 与 {@code "zing_doctor_db_prod"}，事实层才能一次物化、
 * 多指标复用（这是性能优于「逐指标各自取数」的根因）。
 *
 * <p>SQL 全部由 {@link SqlCompiler} 依据仓库内 YAML 生成，不接受外部用户输入；
 * 仍在此做一次危险语句拦截，防止配置笔误造成误删。
 */
@DS("doctor")
@Mapper
public interface QualitySqlMapper {

    @Select("${sql}")
    List<Map<String, Object>> query(@Param("sql") String sql);

    @Update("${sql}")
    int execute(@Param("sql") String sql);

    @Select("SELECT COUNT(1) FROM \"zing_doctor_db_prod\".\"quality_index\"")
    Integer countIndexRows();
}
