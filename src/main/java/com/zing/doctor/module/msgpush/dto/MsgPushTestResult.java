package com.zing.doctor.module.msgpush.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试发送结果。
 *
 * <p>把「实际发出的报文」原样回给页面 —— 现场联调最需要看的就是这个：
 * 报文对不对、网关到底回了什么，对着调模板即可。</p>
 */
@Data
public class MsgPushTestResult {

    /** 是否演练（未真正发出） */
    private boolean dryRun;

    /** 实际调用的地址 */
    private String url;

    /** 逐号码结果 */
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private String mobile;
        private boolean success;
        private Integer httpStatus;
        /** 实际发出的完整报文 */
        private String requestBody;
        /** 网关返回内容（截断后） */
        private String response;
        private String error;
        private long durationMs;
    }
}
