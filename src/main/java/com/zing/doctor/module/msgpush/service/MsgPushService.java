package com.zing.doctor.module.msgpush.service;

import com.zing.doctor.module.msgpush.dto.MsgPushAlertRequest;
import com.zing.doctor.module.msgpush.dto.MsgPushTestRequest;
import com.zing.doctor.module.msgpush.dto.MsgPushTestResult;

/**
 * 高风险预警多通道推送（短信 / 院内通讯软件）。
 *
 * <p>两条入口：{@link #testSend} 供现场联调（看报文、看网关响应）；
 * {@link #pushAlert} 供自动推送（定时任务按工作台待办触发）。
 * 两者共用同一套报文模板与配置，区别只在「谁触发、内容从哪来」。</p>
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

    /**
     * 预警推送：按规则渲染文案并发送给「预警推送接收号码」。
     *
     * <p>与测试发送的三点差别：
     * <ol>
     *   <li>受「启用预警推送」总开关约束（关着就不发，也不写日志）；</li>
     *   <li>号码取 {@code MSGPUSH_ALERT_RECEIVERS}，留空才回退 {@code MSGPUSH_RECEIVERS}；</li>
     *   <li>场景记为 {@code alert} 并带上 ruleCode / 患者标识 —— 发送审计同时是去重依据。</li>
     * </ol>
     *
     * <p>本方法<b>不抛异常</b>：定时任务里一条预警发不出去，不该影响其它患者。
     * 配置缺失（地址/报文模板/号码为空）只记 warn 并返回 0。</p>
     *
     * @return 成功发出的号码数（演练模式也计 1，因为报文已按真实路径生成并留痕）
     */
    int pushAlert(MsgPushAlertRequest req);
}
