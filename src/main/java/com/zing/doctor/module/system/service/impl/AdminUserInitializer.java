package com.zing.doctor.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zing.doctor.module.system.entity.SysUser;
import com.zing.doctor.module.system.mapper.SysUserMapper;
import com.zing.doctor.module.system.util.PasswordHasher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 初始化管理员账号。
 *
 * <p>首次启动自动写入 {@code admin / zing@123}（口令散列为运行时生成，不写在 SQL 里），
 * 已存在同名账号时跳过，不会覆盖院内已改过的口令。
 * 表尚未创建（漏执行 {@code sql/13_auth.sql}）时只告警不阻断启动 ——
 * 否则会让整个系统起不来，反而更难定位。
 */
@Slf4j
@Component
public class AdminUserInitializer implements ApplicationRunner {

    private final SysUserMapper sysUserMapper;

    @Value("${zing.doctor.auth.admin-username:admin}")
    private String adminUsername;

    @Value("${zing.doctor.auth.admin-password:zing@123}")
    private String adminPassword;

    @Value("${zing.doctor.auth.admin-real-name:系统管理员}")
    private String adminRealName;

    public AdminUserInitializer(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, adminUsername);
            if (sysUserMapper.selectCount(q) > 0) {
                return;
            }
            SysUser user = new SysUser();
            user.setUsername(adminUsername);
            user.setRealName(adminRealName);
            user.setPasswordHash(PasswordHasher.hash(adminPassword));
            user.setStatus(1);
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());
            sysUserMapper.insert(user);
            log.info("已初始化管理员账号：{} / {}（建议首次登录后修改口令）", adminUsername, adminPassword);
        } catch (Exception e) {
            log.warn("初始化管理员账号失败，请先执行 sql/13_auth.sql 建表；当前无法直连登录，外链访问不受影响。原因：{}",
                    e.getMessage());
        }
    }
}
