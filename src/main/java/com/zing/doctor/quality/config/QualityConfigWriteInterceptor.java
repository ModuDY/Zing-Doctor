package com.zing.doctor.quality.config;

import com.zing.doctor.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 质控配置写接口拦截器：只拦写请求，读请求原样放行。
 *
 * <p>由 {@code WebConfig} 注册在 {@code /api/quality/config/**} 上，执行顺序排在外链鉴权之后，
 * 因此进入本拦截器的请求已通过外链校验，这里只做「能否改」的二次判定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QualityConfigWriteInterceptor implements HandlerInterceptor {

    private final QualityConfigGuard guard;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        // 预检请求与只读请求不拦：写保护不应影响看板与配置回显
        if (HttpMethod.OPTIONS.matches(method) || HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method)) {
            return true;
        }

        String ip = QualityConfigGuard.clientIp(request);
        String reason = guard.denyReason(ip, request.getHeader(QualityConfigGuard.HEADER_TOKEN));
        if (reason == null) {
            return true;
        }

        log.warn("[质控配置] 写请求被拒绝: {} {} ip={}, 原因={}", method, request.getRequestURI(), ip, reason);
        // 抛 BizException 交由 GlobalExceptionHandler 统一成 {code:403,message:reason}
        throw new BizException(403, reason);
    }
}
