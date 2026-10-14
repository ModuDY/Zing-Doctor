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
 * 质控数据访问拦截器：把 {@code /api/quality/**} 的每个请求交给
 * {@link QualityAccessGuard} 判定「这个工作站是否有权看质控数据」。
 *
 * <p>由 {@code WebConfig} 注册，执行顺序排在外链鉴权之后、配置写保护之前：
 * 先确认「能看」，再确认「能改」—— 顺序反了会出现「没通过访问控制的人先被写保护拒绝」，
 * 返回的原因与真实原因不符，现场按提示排查会越查越偏。
 *
 * <p>只读请求同样拦截：本次要管的正是「谁<b>看</b>得到、谁<b>导</b>得出」，
 * 与写保护只拦写请求的目标不同。因此导出接口（GET 流式响应）也在覆盖范围内 ——
 * 否则「看板看不见、导出却拿得到」会成为一个更难发现的漏洞。
 *
 * <p>启用了独立的前端页面（质控看板/月度汇总/指标配置）不受影响：
 * 它们在接口层被拒时页面会给出 403 提示，而不是白屏。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QualityAccessInterceptor implements HandlerInterceptor {

    private final QualityAccessGuard guard;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS 预检不带业务意图，拦掉会让跨域场景下的正常请求也失败
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String ip = QualityConfigGuard.clientIp(request);
        String reason = guard.denyReason(ip);
        if (reason == null) {
            return true;
        }
        log.warn("[质控访问] 请求被拒绝: {} {} ip={}, 原因={}",
                request.getMethod(), request.getRequestURI(), ip, reason);
        // 交由 GlobalExceptionHandler 统一成 {code:403,message:reason}
        throw new BizException(403, reason);
    }
}
