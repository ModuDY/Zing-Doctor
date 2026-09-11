package com.zing.doctor.external;

import cn.hutool.core.util.StrUtil;
import com.zing.doctor.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * 内部 API 外链校验拦截器。
 *
 * <p>前端在拿到外链 URL 后，调用 {@code /api/**} 时把 pageCode 放在请求头
 * （X-External-PageCode），并按外链模式携带：
 * <ul>
 *   <li>签名模式：X-External-Expire + X-External-Sign，业务参数放 query，
 *       用与 {@link SignatureUtil#canonical} 完全一致的规则重算签名并比对；</li>
 *   <li>ICU 明文模式：X-External-Token（= 配置的 icu-token），业务参数放 query。</li>
 * </ul>
 * 校验通过后将上下文写入 request attribute 供下游使用。
 */
@Component
@RequiredArgsConstructor
public class ExternalLinkInterceptor implements HandlerInterceptor {

    public static final String ATTR_CONTEXT = "externalCtx";

    private final ExternalLinkProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String pageCode = request.getHeader("X-External-PageCode");
        String expireStr = request.getHeader("X-External-Expire");
        String sign = request.getHeader("X-External-Sign");
        String token = request.getHeader("X-External-Token");
        if (StrUtil.isBlank(pageCode)) {
            throw new BizException(401, "缺少外链 pageCode 请求头");
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
        return true;
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
}
