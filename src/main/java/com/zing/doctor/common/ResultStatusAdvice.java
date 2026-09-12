package com.zing.doctor.common;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 业务失败码 → HTTP 状态码映射。
 *
 * <p>系统此前无论成功失败都返回 HTTP 200，失败信息只体现在 body 的 code/message 中，
 * 导致网关、Nginx 访问日志、前端监控都无法感知真实失败率。
 * 本 advice 在写入响应体前按 Result.code 设置对应的 HTTP 状态码：
 * <ul>
 *   <li>code = 0 → 200（不变）</li>
 *   <li>401/403/404 → 对应 HTTP 状态</li>
 *   <li>其余 4xx 业务码 → 400</li>
 *   <li>5xx 业务码 → 500</li>
 * </ul>
 * body 结构保持 {code, message, data} 不变，前端解析逻辑无需调整。
 */
@ControllerAdvice
public class ResultStatusAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class selectedConverterType, ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (!(body instanceof Result)) {
            return body;
        }
        int code = ((Result<?>) body).getCode();
        if (code == 0) {
            return body;
        }
        response.setStatusCode(HttpStatus.valueOf(toHttpStatus(code)));
        return body;
    }

    private int toHttpStatus(int bizCode) {
        if (bizCode == 401 || bizCode == 403 || bizCode == 404) {
            return bizCode;
        }
        if (bizCode >= 400 && bizCode < 500) {
            return 400;
        }
        return 500;
    }
}
