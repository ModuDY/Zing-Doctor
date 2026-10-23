package com.zing.doctor.module.system.service.impl;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.zing.doctor.module.system.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * resolveToken 算法固定回归测试（security-audit: jwt-alg-none-accepted）。
 *
 * <p>核心断言：alg=none / 非法算法 / 垃圾签名三类令牌一律拒绝，
 * 正常 HS256 令牌解析出用户名，过期令牌拒绝 —— 与修复前唯一的差异是
 * alg=none 从「通过」变为「拒绝」。
 */
class AuthServiceImplJwtTest {

    private static final String SECRET = "unit-test-secret";

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(mock(SysUserMapper.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "jwtSecret", SECRET);
    }

    private String hs256(String username, long exp) {
        return JWT.create()
                .setPayload("username", username)
                .setPayload("exp", exp)
                .setSigner(JWTSignerUtil.hs256(SECRET.getBytes(StandardCharsets.UTF_8)))
                .sign();
    }

    private String hs512(String username, long exp) {
        return JWT.create()
                .setPayload("username", username)
                .setPayload("exp", exp)
                .setSigner(JWTSignerUtil.hs512(SECRET.getBytes(StandardCharsets.UTF_8)))
                .sign();
    }

    private static final long FUTURE_EXP = 4102444800000L; // 2100-01-01

    @Test
    @DisplayName("正常 HS256 令牌解析出用户名")
    void validTokenResolves() {
        assertEquals("admin", service.resolveToken(hs256("admin", FUTURE_EXP)));
    }

    @Test
    @DisplayName("过期令牌拒绝")
    void expiredTokenRejected() {
        assertNull(service.resolveToken(hs256("admin", 1000L)));
    }

    @Test
    @DisplayName("错误密钥签发的令牌拒绝")
    void wrongKeyRejected() {
        String forged = JWT.create()
                .setPayload("username", "admin")
                .setPayload("exp", FUTURE_EXP)
                .setSigner(JWTSignerUtil.hs256("another-secret".getBytes(StandardCharsets.UTF_8)))
                .sign();
        assertNull(service.resolveToken(forged));
    }

    @Test
    @DisplayName("alg=none 空签名令牌拒绝（修复点：修复前解析为 admin）")
    void algNoneRejected() {
        String header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"username\":\"admin\",\"exp\":" + FUTURE_EXP + "}")
                        .getBytes(StandardCharsets.UTF_8));
        assertNull(service.resolveToken(header + "." + payload + "."));
    }

    @Test
    @DisplayName("alg 缺失令牌拒绝（缺省回退 HS256 同样不再放行）")
    void missingAlgRejected() {
        String header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"username\":\"admin\",\"exp\":" + FUTURE_EXP + "}")
                        .getBytes(StandardCharsets.UTF_8));
        assertNull(service.resolveToken(header + "." + payload + "."));
    }

    @Test
    @DisplayName("alg=HS512 拒绝（禁止算法混淆）")
    void hs512Rejected() {
        assertNull(service.resolveToken(hs512("admin", FUTURE_EXP)));
    }

    @Test
    @DisplayName("垃圾签名拒绝")
    void garbageSignatureRejected() {
        String header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"username\":\"admin\",\"exp\":" + FUTURE_EXP + "}")
                        .getBytes(StandardCharsets.UTF_8));
        assertNull(service.resolveToken(header + "." + payload + ".AAAA"));
    }

    @Test
    @DisplayName("exp 为字符串拒绝")
    void stringExpRejected() {
        String token = JWT.create()
                .setPayload("username", "admin")
                .setPayload("exp", "4102444800000")
                .setSigner(JWTSignerUtil.hs256(SECRET.getBytes(StandardCharsets.UTF_8)))
                .sign();
        assertNull(service.resolveToken(token));
    }
}
