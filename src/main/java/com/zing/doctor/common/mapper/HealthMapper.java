package com.zing.doctor.common.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 部署就绪检查所需的最小数据库探针。
 *
 * <p>不要复用业务查询做健康检查：业务表结构或 ICU 数据量变化不应影响
 * 容器探针。这里仅检查连接是否可用，以及本版本必须存在的复评表是否已升级。</p>
 */
@Mapper
public interface HealthMapper {

    /** 医生库连通性。未标注 @DS 时使用动态数据源 primary=doctor。 */
    @Select("SELECT 1")
    Integer pingDoctor();

    /** ICU 库连通性；只在 ICU provider=sql 时执行。 */
    @DS("icu")
    @Select("SELECT 1")
    Integer pingIcu();

    /**
     * 复评表及当前版本必需列。WHERE 1=0 不扫描业务数据，但缺任一列会直接失败，
     * 避免只建了旧版表却被交付自检误报为 READY。
     */
    @Select("SELECT COUNT(\"treatment_start_time\") + COUNT(\"review_open_time\") "
            + "+ COUNT(\"review_due_time\") + COUNT(\"time_source\") "
            + "FROM \"zing_doctor_db_prod\".\"patient_doc_abx_reassessment\" WHERE 1 = 0")
    Integer checkReassessmentTable();
}
