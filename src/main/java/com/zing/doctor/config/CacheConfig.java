package com.zing.doctor.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 缓存配置。
 *
 * <p>当前是单实例部署，用进程内 Caffeine 即可，不引入 Redis：少一个中间件就少一个故障点。
 * 业务代码统一走 Spring Cache 抽象（{@code @Cacheable}），将来若横向扩容需要共享缓存，
 * 把这里换成 RedisCacheManager 即可，调用方不用改。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 系统参数值缓存：key = paramKey */
    public static final String CACHE_PARAM = "param";

    /** 系统参数布尔值缓存：key = paramKey + 默认值 */
    public static final String CACHE_PARAM_BOOL = "paramBool";

    /** 已确认存在的登录工号：外链自动注册用，避免每个接口都查一次用户表 */
    public static final String CACHE_AUTO_REG = "autoRegKnown";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(CACHE_PARAM, CACHE_PARAM_BOOL, CACHE_AUTO_REG);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                // 参数变更极少，10 分钟足以扛住高频读取；保存接口会主动清缓存，不会有脏读窗口
                .expireAfterWrite(10, TimeUnit.MINUTES));
        return manager;
    }
}
