package com.zing.doctor.config;

import cn.hutool.core.util.StrUtil;
import com.zing.doctor.common.BizException;
import com.zing.doctor.external.ExternalLinkInterceptor;
import com.zing.doctor.icu.service.UserDepartScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 管理员写保护拦截器：只拦「写请求」，要求直连登录 + 超管名单。
 *
 * <p>安全模型与 {@code LogViewerController#requireAdmin} 完全一致（fail-closed）：
 * <ul>
 *   <li>外链通道（含 ICU 静态 token / HMAC 签名）不写 {@code ATTR_LOGIN_USER} —— 一律 403；</li>
 *   <li>直连登录但不在 WORKBENCH_SUPER_USERS 名单 —— 403；</li>
 *   <li>GET/HEAD/OPTIONS 一律放行，避免误伤查询类页面。</li>
 * </ul>
 *
 * <p>背景（security-audit 2026-10）：全仓无角色体系，管理员名单本身存于 sys_param，
 * 此前 40+ 个配置写端点对任一通过通道鉴权的主体开放（含临床决策配置、系统参数、
 * 外链签发）。本拦截器是「待接 SSO」之前的统一收口点；接入 SSO 后可整体替换为角色判定。
 */
@Component
@RequiredArgsConstructor
public class AdminWriteInterceptor implements HandlerInterceptor {

    private final UserDepartScopeService departScopeService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        Object user = request.getAttribute(ExternalLinkInterceptor.ATTR_LOGIN_USER);
        String username = user == null ? null : String.valueOf(user);
        if (StrUtil.isBlank(username)) {
            throw new BizException(403, "配置写操作仅支持直连登录访问");
        }
        if (!departScopeService.isAdmin(username)) {
            throw new BizException(403, "仅系统管理员可执行配置写操作");
        }
        return true;
    }
}
