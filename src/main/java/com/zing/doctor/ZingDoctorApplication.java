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
@MapperScan("com.zing.doctor.**.mapper")
public class ZingDoctorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZingDoctorApplication.class, args);
    }
}
