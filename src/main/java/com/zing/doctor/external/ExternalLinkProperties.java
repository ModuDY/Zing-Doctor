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
}
