package com.zing.doctor.module.msgpush.dto;

import lombok.Data;

/**
 * 测试发送入参。
 *
 * <p>现场联调用它验证：通道是否通、报文对不对、返回怎么判成功。
 * 不改动任何业务数据，也不依赖总开关。</p>
 */
@Data
public class MsgPushTestRequest {

    /** 接收号码，多个用逗号分隔；留空则取参数 MSGPUSH_RECEIVERS */
    private String mobiles;

    /** 短信内容；留空则用系统默认联调文案 */
    private String content;

    /**
     * @deprecated 安全修复（msgpush-test-force-real-no-authz）：本字段已废弃且不再生效，
     * 真发与否只认服务端参数 MSGPUSH_ALLOW_REAL_TEST。保留字段仅为兼容旧前端请求体，
     * 防止未知字段反序列化失败；下一版本移除。
     */
    @Deprecated
    private Boolean forceReal;
}
