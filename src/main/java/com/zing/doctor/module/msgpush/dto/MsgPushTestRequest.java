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

    /** 强制真发：勾选后即使「演练模式」开着也真发一条（现场联调最后一步用） */
    private Boolean forceReal;
}
