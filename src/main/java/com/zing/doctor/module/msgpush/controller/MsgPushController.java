package com.zing.doctor.module.msgpush.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.external.ExternalLinkInterceptor;
import com.zing.doctor.module.msgpush.dto.MsgPushTestRequest;
import com.zing.doctor.module.msgpush.dto.MsgPushTestResult;
import com.zing.doctor.module.msgpush.service.MsgPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 高风险预警多通道推送接口。
 *
 * <p>本期只提供「测试发送」：现场联调时把渲染好的报文发出去，并把网关原样返回给页面。
 * 不写任何业务数据；也不受「启用预警推送」总开关限制 —— 通道没通之前，
 * 总开关关着也必须能试发。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/msgpush")
@RequiredArgsConstructor
public class MsgPushController {

    private final MsgPushService service;

    /** 测试发送：按「参数设置 → 消息推送」的模板渲染并发送，返回实际报文与网关响应。 */
    @PostMapping("/test")
    public Result<MsgPushTestResult> test(@RequestBody(required = false) MsgPushTestRequest req,
                                         HttpServletRequest request) {
        try {
            MsgPushTestRequest body = req == null ? new MsgPushTestRequest() : req;
            return Result.ok(service.testSend(body, username(request)));
        } catch (IllegalStateException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("消息推送测试发送失败", e);
            return Result.fail("测试发送失败：" + e.getMessage());
        }
    }

    private static String username(HttpServletRequest request) {
        Object u = request.getAttribute(ExternalLinkInterceptor.ATTR_LOGIN_USER);
        return u == null ? null : u.toString();
    }
}
