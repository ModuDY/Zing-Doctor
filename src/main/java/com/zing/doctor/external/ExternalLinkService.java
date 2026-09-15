package com.zing.doctor.external;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.zing.doctor.common.BizException;
import com.zing.doctor.module.antibiotic.mapper.PageConfigMapper;
import com.zing.doctor.module.antibiotic.entity.PageConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 外链签名签发与校验。
 */
@Service
@RequiredArgsConstructor
public class ExternalLinkService {

    private final ExternalLinkProperties properties;
    private final PageConfigMapper pageConfigMapper;

    /**
     * 签发一个可直接外链访问的 URL。
     *
     * @param baseUrl  医生决策系统对外访问基础地址，如 https://doctor.example.com
     * @param pageCode 页面编码（须已在 zing_page_config 注册）
     * @param params   业务参数（如 patientId）
     * @return 完整外链 URL
     */
    public String buildEntryUrl(String baseUrl, String pageCode, Map<String, String> params) {
        requirePageRegistered(pageCode);
        long expire = System.currentTimeMillis() + properties.getExpireMs();
        String sign = SignatureUtil.hmacSha256(
                properties.getSecret(), SignatureUtil.canonical(pageCode, expire, params));

        StringBuilder url = new StringBuilder(baseUrl)
                .append("/entry/").append(pageCode)
                .append("?expire=").append(expire)
                .append("&sign=").append(sign);
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                url.append('&').append(e.getKey()).append('=').append(urlEncode(e.getValue()));
            }
        }
        return url.toString();
    }

    /**
     * 校验外链参数并返回上下文。失败抛出 {@link BizException}。
     *
     * <p>支持两种免登录校验模式：
     * <ol>
     *   <li><b>签名模式</b>：URL 携带 expire + sign（HMAC-SHA256），用于可动态签名的调用方；</li>
     *   <li><b>ICU 明文模式</b>：URL 携带静态 extToken 且等于配置的 icu-token。ICU 系统外链
     *       只做 {@code ${参数}} 占位替换、不会计算 HMAC 签名，故在 URL 模板中写死 extToken 即可。</li>
     * </ol>
     *
     * @param pageCode 页面编码
     * @param rawParams 请求携带的全部参数（含 sign/expire/extToken）
     */
    public ExternalLinkContext verify(String pageCode, Map<String, String> rawParams) {
        requirePageRegistered(pageCode);

        String expireStr = rawParams.get("expire");
        String sign = rawParams.get("sign");
        String extToken = rawParams.get("extToken");
        boolean signed = StrUtil.isNotBlank(expireStr) && StrUtil.isNotBlank(sign);
        boolean icuPlain = StrUtil.isNotBlank(extToken);

        ExternalLinkContext ctx;
        if (signed) {
            ctx = verifySigned(pageCode, rawParams, expireStr, sign);
        } else if (icuPlain) {
            ctx = verifyIcuPlain(pageCode, rawParams, extToken);
        } else {
            throw new BizException(401, "外链参数缺失：需提供 expire/sign（签名模式）或 extToken（ICU 外链模式）");
        }

        // 白名单（可选）
        String allow = properties.getAllowPageCodes();
        if (StrUtil.isNotBlank(allow)
                && !CollUtil.newHashSet(allow.split(",")).contains(pageCode)) {
            throw new BizException(403, "页面未开放外链访问：" + pageCode);
        }
        return ctx;
    }

    /** 签名模式校验（expire + sign） */
    private ExternalLinkContext verifySigned(String pageCode, Map<String, String> rawParams,
                                             String expireStr, String sign) {
        long expire;
        try {
            expire = Long.parseLong(expireStr);
        } catch (NumberFormatException e) {
            throw new BizException(401, "外链参数非法：expire");
        }
        if (System.currentTimeMillis() > expire) {
            throw new BizException(401, "外链已过期，请从 ICU 系统重新进入");
        }

        Map<String, String> params = new HashMap<>(rawParams);
        params.remove("sign");
        params.remove("expire");
        params.remove("pageCode");
        params.remove("extToken");

        String expected = SignatureUtil.hmacSha256(
                properties.getSecret(), SignatureUtil.canonical(pageCode, expire, params));
        if (!SignatureUtil.constantTimeEquals(expected, sign)) {
            throw new BizException(401, "外链签名校验失败");
        }
        return new ExternalLinkContext(pageCode, params, expire);
    }

    /** ICU 明文模式校验（静态 extToken == icu-token；P0 内网使用，token 固定写死在 ICU 外链模板） */
    private ExternalLinkContext verifyIcuPlain(String pageCode, Map<String, String> rawParams, String extToken) {
        String expected = properties.getIcuToken();
        if (StrUtil.isBlank(expected) || !SignatureUtil.constantTimeEquals(expected, extToken)) {
            throw new BizException(401, "ICU 外链 token 校验失败");
        }
        Map<String, String> params = new HashMap<>(rawParams);
        params.remove("sign");
        params.remove("expire");
        params.remove("pageCode");
        params.remove("extToken");
        // ICU 明文模式无过期时间，取一个远期值避免过期判断
        return new ExternalLinkContext(pageCode, params, Long.MAX_VALUE);
    }

    private void requirePageRegistered(String pageCode) {
        PageConfig cfg = pageConfigMapper.selectByPageCode(pageCode);
        if (cfg == null) {
            throw new BizException(404, "未注册的页面：" + pageCode);
        }
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }
}
