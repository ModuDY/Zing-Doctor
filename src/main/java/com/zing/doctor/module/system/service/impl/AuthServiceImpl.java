package com.zing.doctor.module.system.service.impl;

import cn.hutool.jwt.JWT;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zing.doctor.common.BizException;
import com.zing.doctor.module.system.entity.SysUser;
import com.zing.doctor.module.system.mapper.SysUserMapper;
import com.zing.doctor.module.system.service.AuthService;
import com.zing.doctor.module.system.util.PasswordHasher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 直连登录服务实现。
 *
 * <p>令牌为 JWT（HS256），载荷只放 {@code username} 与过期时间戳 {@code exp}；
 * 过期判断自行读取 {@code exp} 完成，不依赖第三方校验器的默认行为。
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Value("${zing.doctor.auth.jwt-secret:zing-doctor-auth-secret-please-change}")
    private String jwtSecret;

    @Value("${zing.doctor.auth.expire-hours:8}")
    private long expireHours;

    private final SysUserMapper sysUserMapper;

    public AuthServiceImpl(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public Map<String, Object> login(String username, String password, String ip) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new BizException(400, "账号和密码不能为空");
        }
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username.trim()));
        if (user == null) {
            log.info("登录失败：账号不存在 username={}, ip={}", username, ip);
            throw new BizException(401, "账号或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(401, "该账号已停用，请联系系统管理员");
        }
        if (!PasswordHasher.matches(password, user.getPasswordHash())) {
            log.info("登录失败：口令错误 username={}, ip={}", username, ip);
            throw new BizException(401, "账号或密码错误");
        }

        // 仅回写登录时间：MyBatisPlus 默认忽略 null 字段，不会覆盖其它列
        SysUser upd = new SysUser();
        upd.setId(user.getId());
        upd.setLastLoginTime(LocalDateTime.now());
        upd.setUpdateTime(LocalDateTime.now());
        sysUserMapper.updateById(upd);

        long expireAt = System.currentTimeMillis() + expireHours * 3600 * 1000L;
        String token = JWT.create()
                .setPayload("username", user.getUsername())
                .setPayload("exp", expireAt)
                .setKey(jwtSecret.getBytes(StandardCharsets.UTF_8))
                .sign();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName() == null ? user.getUsername() : user.getRealName());
        data.put("expireAt", expireAt);
        log.info("登录成功 username={}, ip={}", username, ip);
        return data;
    }

    @Override
    public String resolveToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            JWT jwt = JWT.of(token.trim()).setKey(jwtSecret.getBytes(StandardCharsets.UTF_8));
            if (!jwt.verify()) {
                return null;
            }
            Object exp = jwt.getPayload("exp");
            if (!(exp instanceof Number)) {
                return null;
            }
            if (System.currentTimeMillis() > ((Number) exp).longValue()) {
                return null;
            }
            Object username = jwt.getPayload("username");
            return username == null ? null : String.valueOf(username);
        } catch (Exception e) {
            // 令牌被篡改、格式错误、密钥变更都落到这里，统一按未登录处理
            return null;
        }
    }

    /**
     * 真实姓名取自服务端库记录，与请求参数无关，因此调用方无法篡改。
     * 查库失败不阻断业务：退回账号本身，审计字段至少仍能追溯到账号。
     */
    @Override
    public String realNameOf(String username) {
        if (!StringUtils.hasText(username)) {
            return username;
        }
        String account = username.trim();
        try {
            SysUser user = sysUserMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, account));
            if (user == null || user.getRealName() == null || user.getRealName().trim().isEmpty()) {
                return account;
            }
            return user.getRealName().trim();
        } catch (Exception e) {
            log.warn("查询账号真实姓名失败，回退为账号名: username={}", account, e);
            return account;
        }
    }
}
