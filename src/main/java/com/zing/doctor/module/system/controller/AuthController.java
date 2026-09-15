package com.zing.doctor.module.system.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.external.ExternalLinkInterceptor;
import com.zing.doctor.module.system.entity.SysUser;
import com.zing.doctor.module.system.mapper.SysUserMapper;
import com.zing.doctor.module.system.service.AuthService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 直连登录接口。
 *
 * <p>{@code /api/auth/login} 在 {@code WebConfig} 中放行（它本身不可能携带令牌），
 * 其余接口由拦截器按「JWT 或 外链凭证」双通道校验。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SysUserMapper sysUserMapper;

    @Value("${zing.doctor.auth.expire-hours:8}")
    private long expireHours;

    /** 账号密码登录 */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest body, HttpServletRequest request) {
        Map<String, Object> data = authService.login(
                body == null ? null : body.getUsername(),
                body == null ? null : body.getPassword(),
                request == null ? null : request.getRemoteAddr());
        return Result.ok(data);
    }

    /** 当前登录者信息（令牌失效应当跳回登录页，前端据此恢复会话） */
    @GetMapping("/info")
    public Result<Map<String, Object>> info(HttpServletRequest request) {
        Object username = request.getAttribute(ExternalLinkInterceptor.ATTR_LOGIN_USER);
        if (username == null) {
            return Result.fail(401, "未登录或登录已失效");
        }
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, String.valueOf(username)));
        if (user == null) {
            return Result.fail(401, "账号不存在或已被删除");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName() == null ? user.getUsername() : user.getRealName());
        return Result.ok(data);
    }

    /** 退出登录。令牌无状态，服务端不维护会话，前端清除本地令牌即可 */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        Object username = request.getAttribute(ExternalLinkInterceptor.ATTR_LOGIN_USER);
        log.info("退出登录 username={}", username);
        return Result.ok();
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}
