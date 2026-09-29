package com.zing.doctor.external;

import cn.hutool.core.util.HexUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/**
 * 外链签名工具。
 *
 * <p>签名算法：对规范化后的参数串做 HmacSHA256，输出 hex。
 * 规范化规则：除 sign 本身外，其余参数按 key 字典序排序后以 {@code k=v} 用 {@code &} 连接，
 * 最前方固定拼接 {@code pageCode=xx&expire=xx}（这两个字段不参与字典序，始终在最前）。
 */
public final class SignatureUtil {

    private SignatureUtil() {
    }

    /**
     * 构造规范化参数串（用于签名与校验）。
     *
     * @param pageCode 页面编码
     * @param expire   过期时间戳（毫秒）
     * @param params   业务参数（可含 null，会被忽略）
     */
    public static String canonical(String pageCode, long expire, Map<String, String> params) {
        StringBuilder sb = new StringBuilder("pageCode=").append(pageCode)
                .append("&expire=").append(expire);
        if (params != null && !params.isEmpty()) {
            TreeMap<String, String> sorted = new TreeMap<>();
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                if ("sign".equals(e.getKey())) {
                    continue;
                }
                sorted.put(e.getKey(), e.getValue());
            }
            for (Map.Entry<String, String> e : sorted.entrySet()) {
                sb.append('&').append(e.getKey()).append('=').append(e.getValue());
            }
        }
        return sb.toString();
    }

    /**
     * HmacSHA256 签名，输出小写 hex。
     */
    public static String hmacSha256(String secret, String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return HexUtil.encodeHexStr(raw);
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 计算失败", e);
        }
    }

    /**
     * 常量时间字符串比较，用于防时序攻击。
     */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
