package com.zing.doctor.common;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

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

    /**
     * 第三方系统「没有真的传身份」时常见的占位值。
     *
     * <p>现场真出过：外链模板把 realname 写成 {@code unkonw}（unknown 拼错）且一直没人发现，
     * 结果配置变更历史、评分记录的 create_by 全记成这个词——页面看着像有操作人，
     * 实际一样追溯不到人。模板变量没被替换时会原样传来 {@code ${realname}}，同理不能当人名收下。
     */
    private static final Set<String> PLACEHOLDER_NAMES = new HashSet<>(Arrays.asList(
            "unknown", "unkonw", "unkown", "unknow",
            "null", "undefined", "none", "nil", "n/a", "na",
            "匿名", "未知", "无", "-", "--", "."));

    private OperatorContext() {
    }

    /**
     * 该值是否只是「占位」而非真实人名（含空、未替换的模板变量、unknown 及其常见拼错）。
     *
     * <p>占位值一律按「没传」处理，让取值链继续往下走，最终落到服务端配置的默认操作人或
     * {@link #UNKNOWN}——总比把一个不是人名的词写进审计字段强。
     */
    public static boolean isPlaceholder(String name) {
        if (name == null) {
            return true;
        }
        String s = name.trim();
        if (s.isEmpty() || s.contains("${")) {
            return true;
        }
        return PLACEHOLDER_NAMES.contains(s.toLowerCase(Locale.ROOT));
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
