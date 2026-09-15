package com.zing.doctor.common;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * 当前请求的真实操作人，用于评分记录等业务的审计字段（create_by / update_by）。
 *
 * <p>身份由服务端解析，统一在 ExternalLinkInterceptor 鉴权通过后写入 request attribute：
 * <ol>
 *   <li>直连登录：按登录账号从 zing_sys_user 查出真实姓名。令牌由服务端签发，
 *       因此这一步的结果不可能被调用方影响；</li>
 *   <li>外链：取第三方系统随外链带来的身份参数（realname 优先）。</li>
 * </ol>
 *
 * <p>业务层只读这里的值，不接受前端传入的同名字段：保存时会无条件覆盖请求体里的
 * createBy。因此改 URL 参数或伪造请求体都无法把一条记录署成别人的名字。
 */
public final class OperatorContext {

    /** request attribute 名：本次请求已解析出的操作人姓名 */
    public static final String ATTR_OPERATOR = "operatorName";

    /** 两条通道都识别不出身份时的兜底值（一眼可与真实姓名区分） */
    public static final String UNKNOWN = "unknown";

    private OperatorContext() {
    }

    /** 当前操作人；识别不出时返回 UNKNOWN */
    public static String current() {
        return currentOrDefault(UNKNOWN);
    }

    /** 当前操作人；识别不出时返回调用方指定的兜底值 */
    public static String currentOrDefault(String fallback) {
        String operator = currentOrNull();
        return operator == null ? fallback : operator;
    }

    /** 当前操作人；未处于请求上下文或身份不明时返回 null */
    public static String currentOrNull() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes)) {
            // 定时任务 / 自动评分等非 HTTP 触发的场景
            return null;
        }
        return currentOrNull(((ServletRequestAttributes) attrs).getRequest());
    }

    /**
     * 当前操作人。
     *
     * @param request 当前请求，可为 null
     * @return 操作人姓名；拿不到时返回 null，由调用方决定兜底
     */
    public static String currentOrNull(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(ATTR_OPERATOR);
        if (value == null) {
            return null;
        }
        String name = String.valueOf(value).trim();
        return name.isEmpty() ? null : name;
    }
}
