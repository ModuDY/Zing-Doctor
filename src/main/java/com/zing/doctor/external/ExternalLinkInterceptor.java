package com.zing.doctor.external;

import cn.hutool.core.util.StrUtil;
import com.zing.doctor.common.BizException;
import com.zing.doctor.common.OperatorContext;
import com.zing.doctor.module.system.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code /api/**} 统一鉴权拦截器：以下两条通道任选其一放行。
 *
 * <p><b>通道一 · 直连登录</b>：请求头携带 {@code Authorization: Bearer <JWT>}
 * （或 {@code X-Auth-Token}），令牌由 {@code /api/auth/login} 签发。
 * 通过后把登录账号写入 request attribute {@value #ATTR_LOGIN_USER}。
 *
 * <p><b>通道二 · 外链免登录</b>：第三方系统（ICU）带凭证直接打开页面，不需要登录。
 * 前端调用 {@code /api/**} 时把 pageCode 放在请求头（X-External-PageCode），并按模式携带：
 * <ul>
 *   <li>签名模式：X-External-Expire + X-External-Sign，业务参数放 query，
 *       用与 {@link SignatureUtil#canonical} 完全一致的规则重算签名并比对；</li>
 *   <li>ICU 明文模式：X-External-Token（= 配置的 icu-token），业务参数放 query。</li>
 * </ul>
 *
 * <p>背景：此前只实现通道二，直接用 IP+端口打开系统时所有接口都以
 * 「缺少外链 pageCode 请求头」被拒，页面全空；加入通道一后两种访问方式并存。
 */
@Component
@RequiredArgsConstructor
public class ExternalLinkInterceptor implements HandlerInterceptor {

    public static final String ATTR_CONTEXT = "externalCtx";

    /** 直连登录成功后写入的登录账号 */
    public static final String ATTR_LOGIN_USER = "loginUser";

    private static final String BEARER_PREFIX = "Bearer ";

    private final ExternalLinkProperties properties;

    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 通道一优先：登录令牌存在即按其校验。这样「已登录 + 带着过期外链参数」
        // 的浏览器仍能正常使用，不会被外链通道的过期判断挡在门外。
        String bearer = resolveBearer(request);
        if (StrUtil.isNotBlank(bearer)) {
            String username = authService.resolveToken(bearer);
            if (StrUtil.isBlank(username)) {
                throw new BizException(401, "登录已失效，请重新登录");
            }
            request.setAttribute(ATTR_LOGIN_USER, username);
            // 登录通道：姓名由「服务端签发令牌反解出的账号」查库得到，不接受任何请求参数覆盖
            request.setAttribute(OperatorContext.ATTR_OPERATOR, authService.realNameOf(username));
            return true;
        }

        // 通道二：外链免登录（第三方系统）
        String pageCode = request.getHeader("X-External-PageCode");
        String expireStr = request.getHeader("X-External-Expire");
        String sign = request.getHeader("X-External-Sign");
        String token = request.getHeader("X-External-Token");
        if (StrUtil.isBlank(pageCode)) {
            throw new BizException(401, "未登录，请先登录后访问");
        }

        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) {
                params.put(k, v[0]);
            }
        });
        params.remove("sign");

        ExternalLinkContext ctx;
        boolean signed = StrUtil.isNotBlank(expireStr) && StrUtil.isNotBlank(sign);
        boolean icuPlain = StrUtil.isNotBlank(token);
        if (signed) {
            ctx = verifySigned(request, pageCode, expireStr, sign, params);
        } else if (icuPlain) {
            ctx = verifyIcuPlain(pageCode, token, params);
        } else {
            throw new BizException(401, "缺少外链签名/ICU token 请求头");
        }

        request.setAttribute(ATTR_CONTEXT, ctx);
        request.setAttribute(OperatorContext.ATTR_OPERATOR, operatorFromExternal(request, ctx));
        return true;
    }

    /**
     * 外链通道的操作人：第三方系统随外链带来的医生身份。
     *
     * <p>realname 原先只存在于 /entry 302 后的页面 URL 上，调用 /api 时并不会自动带过去，
     * 故前端统一注入 {@code X-External-Operator} 请求头；请求 query 仅作为兼容项保留
     * （个别接口可能正好带着同名参数）。
     *
     * <p>这些值来自调用方，单看这一层是可被伪造的——真正保证不可篡改的是
     * 「保存时由服务端读取 OperatorContext 覆盖 createBy」这一步，而非这里的传递。
     */
    private String operatorFromExternal(HttpServletRequest request, ExternalLinkContext ctx) {
        String fromHeader = request.getHeader("X-External-Operator");
        if (StrUtil.isNotBlank(fromHeader)) {
            return fromHeader.trim();
        }
        String[] keys = {"realname", "userName", "username", "userId", "operator"};
        for (String key : keys) {
            String value = ctx.param(key);
            if (StrUtil.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private ExternalLinkContext verifySigned(HttpServletRequest request, String pageCode,
                                             String expireStr, String sign, Map<String, String> params) {
        long expire;
        try {
            expire = Long.parseLong(expireStr);
        } catch (NumberFormatException e) {
            throw new BizException(401, "外链参数非法：expire");
        }
        if (System.currentTimeMillis() > expire) {
            throw new BizException(401, "外链已过期，请从 ICU 系统重新进入");
        }
        String expected = SignatureUtil.hmacSha256(
                properties.getSecret(), SignatureUtil.canonical(pageCode, expire, params));
        if (!SignatureUtil.constantTimeEquals(expected, sign)) {
            throw new BizException(401, "外链签名校验失败");
        }
        return new ExternalLinkContext(pageCode, params, expire);
    }

    private ExternalLinkContext verifyIcuPlain(String pageCode, String token, Map<String, String> params) {
        String expected = properties.getIcuToken();
        if (StrUtil.isBlank(expected) || !SignatureUtil.constantTimeEquals(expected, token)) {
            throw new BizException(401, "ICU 外链 token 校验失败");
        }
        return new ExternalLinkContext(pageCode, params, Long.MAX_VALUE);
    }

    /** 取出请求里的登录令牌：优先 Authorization: Bearer，其次 X-Auth-Token 头 */
    private String resolveBearer(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StrUtil.isNotBlank(authorization)) {
            String v = authorization.trim();
            if (v.length() > BEARER_PREFIX.length()
                    && v.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
                String candidate = v.substring(BEARER_PREFIX.length()).trim();
                if (StrUtil.isNotBlank(candidate)) {
                    return candidate;
                }
            }
        }
        String xToken = request.getHeader("X-Auth-Token");
        return StrUtil.isBlank(xToken) ? null : xToken.trim();
    }
}
