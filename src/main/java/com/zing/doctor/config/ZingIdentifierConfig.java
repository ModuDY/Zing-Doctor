package com.zing.doctor.config;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.zing.doctor.common.ZingIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 接入 MyBatis-Plus 的主键生成。
 *
 * <p>本库所有业务表都已去掉数据库自增，主键统一走这里生成 15 位 ID；
 * ICU 只读库的表结构由第三方系统维护，本系统只查不写，不受影响。
 *
 * <p>机器号用 {@code zing.worker-id} 配置：将来横向扩多副本时各实例配不同值，
 * 避免同一秒生成相同 ID。单实例部署保持默认 0 即可。
 */
@Slf4j
@Component
public class ZingIdentifierConfig implements IdentifierGenerator {

    public ZingIdentifierConfig(@Value("${zing.worker-id:0}") long workerId) {
        ZingIdGenerator.init(workerId);
        log.info("ID 生成器已就绪：15 位（秒级时间戳10 + 机器号2 + 序列号3），机器号={}", workerId);
    }

    @Override
    public Long nextId(Object entity) {
        return ZingIdGenerator.nextId();
    }
}
