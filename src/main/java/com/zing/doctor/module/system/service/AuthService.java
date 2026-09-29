package com.zing.doctor.module.system.service;

import java.util.Map;

/**
 * 直连登录服务。
 *
 * <p>与外链免登录互不干扰：本服务只负责「账号密码换 JWT」，
 * 外链校验仍在 {@code ExternalLinkService} 中完成。
 */
public interface AuthService {

    /**
     * 账号密码登录，成功返回 {@code {token, username, realName}}。
     *
     * @param username 登录账号
     * @param password 明文口令
     * @param ip       来源 IP（写入日志便于追溯），可为 null
     */
    Map<String, Object> login(String username, String password, String ip);

    /**
     * 解析 JWT。
     *
     * @param token 请求携带的令牌
     * @return 令牌有效时返回登录账号，签名不符/已过期/格式错误一律返回 null
     */
    String resolveToken(String token);

    /**
     * 查登录账号的真实姓名，用于记录归属与审计。
     *
     * <p>这是「审计归属」而非「身份认证」：调用方本身无法伪造该值——它取自由服务端
     * 签发的令牌反解出的账号、再查库得到，与请求参数无关。
     *
     * @param username {@link #resolveToken(String)} 解析出的登录账号
     * @return 配了真实姓名则返回姓名，否则返回账号本身（账号已被删除时同样返回账号本身）
     */
    String realNameOf(String username);
}
