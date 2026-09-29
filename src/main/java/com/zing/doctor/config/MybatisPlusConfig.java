package com.zing.doctor.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：分页插件，方言按 zing.db-type（profile）自动选择。
 * <ul>
 *   <li>dm（默认）：达梦 DM8，DbType.DM</li>
 *   <li>mysql：MySQL 8.x，DbType.MYSQL</li>
 *   <li>mariadb：MariaDB 10.5，DbType.MARIADB（分页语法同 MySQL）</li>
 * </ul>
 */
@Configuration
public class MybatisPlusConfig {

    @Value("${zing.db-type:dm}")
    private String dbType;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        DbType type;
        String t = dbType == null ? "dm" : dbType.trim().toLowerCase();
        switch (t) {
            case "mysql":
                type = DbType.MYSQL;
                break;
            case "mariadb":
                type = DbType.MARIADB;
                break;
            default:
                type = DbType.DM;
        }
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(type);
        pagination.setMaxLimit(500L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
