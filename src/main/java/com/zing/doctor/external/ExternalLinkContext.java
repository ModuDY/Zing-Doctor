package com.zing.doctor.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 外链校验通过后的上下文：页面编码 + 业务参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalLinkContext {

    /** 目标页面编码 */
    private String pageCode;

    /** 业务参数（不含 sign/expire/pageCode） */
    private Map<String, String> params = new HashMap<>();

    /** 过期时间戳（毫秒） */
    private long expire;

    /** 便捷取值 */
    public String param(String key) {
        return params.get(key);
    }
}
