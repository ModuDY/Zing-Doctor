package com.zing.doctor.module.msgpush.service;

import com.zing.doctor.module.msgpush.dto.MsgPushTestRequest;
import com.zing.doctor.module.msgpush.dto.MsgPushTestResult;

/**
 * 高风险预警多通道推送（短信 / 院内通讯软件）。
 *
 * <p>本期只落地「配置 + 测试发送」：把报文按参数里的模板渲染出来、发出去、
 * 把网关响应原样返回给页面。自动推送（按 MSGPUSH_RULES 触发）留待通道联调通过后再接。</p>
 */
public interface MsgPushService {

    /**
     * 测试发送：按「参数设置 → 消息推送」的模板渲染并发送，逐号码留审计。
     *
     * <p>不受「启用预警推送」总开关限制 —— 通道联调必须先能发出去；
     * 但「演练模式」默认开启，只渲染不发出，直到现场主动勾选强制真发。</p>
     *
     * @param operator 操作人（记入审计）
     */
    MsgPushTestResult testSend(MsgPushTestRequest req, String operator);
}
