package com.zing.doctor;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 医生系统（ICU 抗生素分析 - P0 框架）启动类。
 */
@SpringBootApplication
@EnableScheduling
// 说明：一旦显式使用 @MapperScan，MyBatis 的 AutoConfiguredMapperScannerRegistrar（兜底扫描所有 @Mapper 接口）
// 会因 @ConditionalOnMissingBean(MapperFactoryBean.class) 而失效，因此扫描范围必须覆盖全部 Mapper 接口所在包。
// 质控引擎的 QualitySqlMapper 位于 quality.engine（非 *.mapper），若不显式纳入，启动时会因
// NoSuchBeanDefinitionException: QualitySqlMapper 直接 APPLICATION FAILED TO START。
@MapperScan({"com.zing.doctor.**.mapper", "com.zing.doctor.quality.engine"})
public class ZingDoctorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZingDoctorApplication.class, args);
    }
}
