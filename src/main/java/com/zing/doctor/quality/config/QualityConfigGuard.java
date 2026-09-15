package com.zing.doctor.quality.config;

import com.zing.doctor.common.OperatorContext;
import com.zing.doctor.external.ExternalLinkContext;
import com.zing.doctor.external.ExternalLinkInterceptor;
import com.zing.doctor.external.SignatureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

/**
 * 质控配置<b>写接口</b>的访问控制与操作人解析。
 *
 * <p>背景：质控配置页改一行条件就能改全院质控口径，属于高危写操作；而它与此前的只读看板
 * 共用同一套外链鉴权 —— 拿到外链（ICU 模板里写死的 {@code extToken}）即可改生产口径，
 * 风险等级明显不匹配。本类补上这一层，把「能看」和「能改」分开：
 *
 * <ol>
 *   <li><b>IP 白名单</b>（推荐）：只有信息科/质控科的工作站能提交，浏览器端零改动；</li>
 *   <li><b>写接口令牌</b>：写请求需带 {@code X-Quality-Config-Token}，适合白名单不好维护的场景；</li>
 *   <li>两者都配 → 都必须通过；<b>两者都不配 → 写接口一律拒绝</b>（fail-closed）。</li>
 * </ol>
 *
 * <p>兜底策略是刻意选择的：宁可让部署方显式开一次口子，也不要默认放开一个能改生产口径的入口。
 * 本项目尚无 SSO，本类是「待接 SSO」之前的过渡手段，接入 SSO 后可整体替换为角色判定。
 *
 * <p>注意：写保护只作用于写请求，只读看板接口不受影响，避免配置失误把看板一起打挂。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QualityConfigGuard {

    /** 写接口令牌请求头 */
    public static final String HEADER_TOKEN = "X-Quality-Config-Token";

    /** 操作人请求头（网关/SSO 接入后可由此注入真实身份） */
    public static final String HEADER_OPERATOR = "X-Operator";

    /** 无法解析操作人时的兜底值（保证审计字段非空，且与真实人名一眼可区分） */
    public static final String UNKNOWN_OPERATOR = "unknown";

    /** 审计表中 operator 字段长度上限，超长会直接导致写历史失败 */
    private static final int OPERATOR_MAX_LEN = 64;

    private final QualityProperties properties;

    /**
     * 启动时把写保护状态打到日志。
     *
     * <p>写保护「没配」和「配错」在运行期的表现都是 403，若启动不留痕，
     * 排障时要翻配置猜半天。这里明确说出当前生效的是哪种方式。
     */
    @PostConstruct
    public void reportState() {
        boolean hasToken = StringUtils.hasText(properties.getConfigWriteToken());
        boolean hasIp = StringUtils.hasText(properties.getConfigWriteIpWhitelist());
        if (!hasToken && !hasIp) {
            log.warn("[质控配置] 写保护未配置：POST /api/quality/config/** 将一律返回 403。"
                    + "如需开放配置编辑，请设置 zing.quality.config-write-ip-whitelist（推荐）"
                    + "或 zing.quality.config-write-token。");
        } else {
            log.info("[质控配置] 写保护已启用：IP 白名单={}，令牌={}",
                    hasIp ? properties.getConfigWriteIpWhitelist() : "未启用",
                    hasToken ? "已配置" : "未启用");
        }
    }

    /**
     * 写权限判定。
     *
     * @param clientIp 客户端 IP（可为空，由 X-Forwarded-For 优先解析）
     * @param token    请求头 {@code X-Quality-Config-Token}
     * @return {@code null} 表示放行；非空为<b>拒绝原因</b>（可直接回给前端展示）
     */
    public String denyReason(String clientIp, String token) {
        boolean hasToken = StringUtils.hasText(properties.getConfigWriteToken());
        boolean hasIp = StringUtils.hasText(properties.getConfigWriteIpWhitelist());

        if (!hasToken && !hasIp) {
            return "质控配置写接口未启用：请在服务端配置 zing.quality.config-write-ip-whitelist"
                    + "（推荐，浏览器端无需改动）或 zing.quality.config-write-token 后重试。";
        }
        if (hasToken && !SignatureUtil.constantTimeEquals(properties.getConfigWriteToken(), token)) {
            return "质控配置写接口令牌缺失或错误（请求头 " + HEADER_TOKEN + "）。";
        }
        if (hasIp && !ipAllowed(clientIp)) {
            return "当前客户端 IP（" + (StringUtils.hasText(clientIp) ? clientIp : "未知")
                    + "）不在质控配置写接口白名单内。";
        }
        return null;
    }

    /**
     * IP 是否命中白名单。元素支持前缀匹配，便于用 {@code 10.0.0.} 覆盖整段。
     */
    public boolean ipAllowed(String ip) {
        if (!StringUtils.hasText(ip) || !StringUtils.hasText(properties.getConfigWriteIpWhitelist())) {
            return false;
        }
        for (String raw : properties.getConfigWriteIpWhitelist().split(",")) {
            String rule = raw.trim();
            if (rule.isEmpty()) {
                continue;
            }
            if (ip.equals(rule) || ip.startsWith(rule)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析真实操作人，用于变更历史与审计留痕。
     *
     * <p>取值优先级：{@code X-Operator} 请求头（SSO/网关注入）→ 直连登录账号的真实姓名
     * （由服务端签发的令牌反解后查库得到，不可伪造）→ 外链业务参数 {@code realname} →
     * {@code username} → {@code userId} → 服务端配置的
     * {@code zing.quality.config-default-operator} → {@code unknown}。
     *
     * <p><b>这是审计归属，不是身份认证</b>：外链参数与请求头都可由调用方伪造，
     * 真正兜底的是上面的写接口访问控制。接入 SSO 后应改为从服务端会话取值。
     */
    public String operator(HttpServletRequest request) {
        if (request == null) {
            return fallbackOperator();
        }
        String fromHeader = request.getHeader(HEADER_OPERATOR);
        if (StringUtils.hasText(fromHeader)) {
            return normalize(fromHeader);
        }
        // 直连登录：姓名由服务端签发令牌反解出的账号查库得到，不接受请求参数覆盖。
        // 排在 X-Operator 之后——网关注入的真实身份仍是最可信的一档。
        String fromLogin = OperatorContext.currentOrNull(request);
        if (StringUtils.hasText(fromLogin)) {
            return normalize(fromLogin);
        }
        Object ctx = request.getAttribute(ExternalLinkInterceptor.ATTR_CONTEXT);
        if (ctx instanceof ExternalLinkContext) {
            ExternalLinkContext linkCtx = (ExternalLinkContext) ctx;
            String[] keys = {"realname", "userName", "username", "operator", "userId"};
            for (String key : keys) {
                String value = linkCtx.param(key);
                if (StringUtils.hasText(value)) {
                    return normalize(value);
                }
            }
        }
        return fallbackOperator();
    }

    /**
     * 兜底操作人：请求头与外链参数都没有时的最后取值。
     *
     * <p>典型场景是信息科在自己工作站上直接打开配置页（内网直连、不经过 ICU 外链，
     * 自然没有 realname），此时系统无从得知操作者，审计字段会记成 {@code unknown}，
     * 事后无法追溯是谁改的口径。配置 {@code zing.quality.config-default-operator}
     * 后可落到具体名字，例如「质控科-信息科工作站」。
     *
     * <p>值取自<b>服务端配置文件</b>而非请求，因此使用方无法自行伪造，
     * 与「不接受前端传 operator」的约定不冲突。
     */
    private String fallbackOperator() {
        if (StringUtils.hasText(properties.getConfigDefaultOperator())) {
            return normalize(properties.getConfigDefaultOperator());
        }
        return UNKNOWN_OPERATOR;
    }

    /** 去掉空白与换行并截断，避免越界写库失败或把控制字符带进审计表 */
    private String normalize(String raw) {
        String value = raw.replaceAll("[\\r\\n\\t]", " ").trim();
        if (value.isEmpty()) {
            return UNKNOWN_OPERATOR;
        }
        return value.length() > OPERATOR_MAX_LEN ? value.substring(0, OPERATOR_MAX_LEN) : value;
    }

    /**
     * 取客户端 IP。存在反向代理（Nginx/网关）时 {@code getRemoteAddr()} 是代理地址，
     * 直接按它做白名单会「全部放行」或「全部拒绝」，因此优先取 XFF 的第一段。
     */
    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            String first = (comma > 0 ? xff.substring(0, comma) : xff).trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }
}
