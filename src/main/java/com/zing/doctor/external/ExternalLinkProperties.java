package com.zing.doctor.external;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 外链免登录配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "zing.doctor.external-link")
public class ExternalLinkProperties {

    /** HMAC 签名密钥 */
    private String secret;

    /** ICU 明文外链静态密钥（URL 参数名 extToken）。ICU 系统不生成 HMAC 签名，配外链模板时写死该值即可 */
    private String icuToken;

    /** 签名有效期（毫秒） */
    private long expireMs = 600000L;

    /** 允许外链访问的页面 code 白名单（逗号分隔，空表示放行已注册页面） */
    private String allowPageCodes = "";

    /**
     * 医生决策系统对外访问基础地址（含端口），如 http://100.120.1.104:2001。
     * 用于外链校验通过后的 302 跳转目标：配置后强制跳到本地址 + 页面路径，
     * 避免外部系统（如 ICU）从 80 端口或其他代理访问时相对路径跳转丢端口导致 404。
     * 留空则使用相对路径（依赖发起请求方的 host:port）。
     */
    private String baseUrl = "";

    /**
     * 外链未携带医生身份时的默认操作人（可选）。
     *
     * <p><b>为什么需要它</b>：外链模式下操作人只有一个来源 —— 第三方系统随外链带来的
     * {@code realname}/{@code username} 等参数。ICU 侧外链模板一旦没配这些参数（或配了未替换的
     * {@code ${realname}}），后端解析结果就是空，评分记录的 {@code create_by}、配置变更历史
     * 全被写成 {@code unknown}，事后无法追溯是谁做的操作。而直连登录通道有兜底
     * （{@code realNameOf} 查不到姓名时退回归属账号），外链通道此前没有任何兜底，
     * 两侧行为不对称。
     *
     * <p>配了本项后，拿不到身份时落到这个值（如「ICU-外链未传身份」）。它<b>不是真人姓名</b>，
     * 粒度是「一类来源」而不是人，只用于让审计字段不至于完全不可读；要精确到人，
     * 仍须 ICU 侧在模板里带上 {@code realname}。
     *
     * <p>值取自服务端配置文件，不是请求参数，调用方无法伪造，
     * 与「不接受前端传 operator」的约定不冲突。
     */
    private String defaultOperator = "";
}
