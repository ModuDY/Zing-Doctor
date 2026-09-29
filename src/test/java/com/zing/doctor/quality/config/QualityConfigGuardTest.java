package com.zing.doctor.quality.config;

import com.zing.doctor.external.ExternalLinkContext;
import com.zing.doctor.external.ExternalLinkInterceptor;
import com.zing.doctor.module.system.service.SysParamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 质控配置写接口访问控制的单元测试（<b>不启动 Spring</b>）。
 *
 * <p><b>为什么值得单独测</b>：这层是「改生产口径」这个动作的准入判定，两种失效方向都很贵 ——
 * 判宽了等于没有保护（拿到外链就能改口径）；判严了会把信息科的正常运维挡在门外，
 * 而运维看到的现象只是「点保存没反应」，很难联想到是白名单配错。
 * 因此这里把「放行」与「拒绝」的每条分支都钉死，<b>尤其是默认不配置时必须拒绝</b>。
 */
class QualityConfigGuardTest {

    private QualityProperties props;
    private SysParamService sysParam;
    private QualityConfigGuard guard;

    @BeforeEach
    void setUp() {
        props = new QualityProperties();
        sysParam = mock(SysParamService.class);
        when(sysParam.bool(anyString(), anyBoolean())).thenAnswer(inv -> (Boolean) inv.getArgument(1));
        guard = new QualityConfigGuard(props, sysParam);
    }

    @Test
    @DisplayName("两种保护都没配时一律拒绝（fail-closed），并说清怎么开")
    void failClosedWhenNothingConfigured() {
        String reason = guard.denyReason("10.0.0.1", "whatever");

        assertNotNull(reason, "未配置写保护时必须拒绝，不能默认放行");
        assertTrue(reason.contains("未启用"), reason);
        assertTrue(reason.contains("config-write-ip-whitelist"), "拒绝原因应告诉部署方怎么配置：" + reason);
    }

    @Test
    @DisplayName("IP 白名单：精确命中与前缀命中放行，未命中拒绝并回显实际 IP")
    void ipWhitelist() {
        props.setConfigWriteIpWhitelist("100.120.1.104, 10.0.0.");

        assertNull(guard.denyReason("100.120.1.104", null), "精确命中应放行");
        assertNull(guard.denyReason("10.0.0.77", null), "前缀命中应放行（10.0.0. 覆盖整段）");

        String reason = guard.denyReason("10.9.9.9", null);
        assertNotNull(reason);
        assertTrue(reason.contains("10.9.9.9"), "拒绝原因应带上实际 IP，便于排障：" + reason);
    }

    @Test
    @DisplayName("IP 为空/空白一律判为未命中（不得因空串而放行）")
    void blankIpNeverAllowed() {
        props.setConfigWriteIpWhitelist("10.0.0.");

        assertFalse(guard.ipAllowed(null));
        assertFalse(guard.ipAllowed(""));
        assertFalse(guard.ipAllowed("   "));
    }

    @Test
    @DisplayName("令牌：正确放行，缺失/错误拒绝，且传 null 不抛异常")
    void tokenCheck() {
        props.setConfigWriteToken("s3cret");

        assertNull(guard.denyReason(null, "s3cret"));
        assertNotNull(guard.denyReason(null, "wrong"));
        assertNotNull(guard.denyReason(null, null));
        assertNotNull(guard.denyReason(null, ""));
    }

    @Test
    @DisplayName("两者都配时必须都通过：IP 对了但令牌错同样不放行")
    void bothMustPass() {
        props.setConfigWriteToken("s3cret");
        props.setConfigWriteIpWhitelist("10.0.0.");

        assertNull(guard.denyReason("10.0.0.9", "s3cret"));
        assertNotNull(guard.denyReason("10.0.0.9", "bad-token"));
        assertNotNull(guard.denyReason("8.8.8.8", "s3cret"));
    }

    @Test
    @DisplayName("客户端 IP 优先取 X-Forwarded-For 第一段（有反代时 remoteAddr 是代理地址）")
    void clientIpPrefersForwardedFor() {
        HttpServletRequest forwarded = mock(HttpServletRequest.class);
        when(forwarded.getHeader("X-Forwarded-For")).thenReturn("100.120.1.104, 10.0.0.1");
        assertEquals("100.120.1.104", QualityConfigGuard.clientIp(forwarded));

        HttpServletRequest realIp = mock(HttpServletRequest.class);
        when(realIp.getHeader("X-Real-IP")).thenReturn("100.120.1.105");
        assertEquals("100.120.1.105", QualityConfigGuard.clientIp(realIp));

        HttpServletRequest direct = mock(HttpServletRequest.class);
        when(direct.getRemoteAddr()).thenReturn("127.0.0.1");
        assertEquals("127.0.0.1", QualityConfigGuard.clientIp(direct));

        assertEquals("", QualityConfigGuard.clientIp(null));
    }

    @Test
    @DisplayName("操作人：请求头优先 → 外链 realname → username → 兜底 unknown")
    void operatorResolution() {
        HttpServletRequest fromHeader = mock(HttpServletRequest.class);
        when(fromHeader.getHeader(QualityConfigGuard.HEADER_OPERATOR)).thenReturn(" 张三 ");
        assertEquals("张三", guard.operator(fromHeader));

        Map<String, String> withRealName = new LinkedHashMap<>();
        withRealName.put("realname", "李四");
        withRealName.put("username", "lisi");
        HttpServletRequest link = mock(HttpServletRequest.class);
        when(link.getAttribute(ExternalLinkInterceptor.ATTR_CONTEXT))
                .thenReturn(new ExternalLinkContext("quality-config", withRealName, Long.MAX_VALUE));
        assertEquals("李四", guard.operator(link));

        Map<String, String> onlyUsername = new LinkedHashMap<>();
        onlyUsername.put("username", "wangwu");
        HttpServletRequest fallback = mock(HttpServletRequest.class);
        when(fallback.getAttribute(ExternalLinkInterceptor.ATTR_CONTEXT))
                .thenReturn(new ExternalLinkContext("quality-config", onlyUsername, Long.MAX_VALUE));
        assertEquals("wangwu", guard.operator(fallback));

        assertEquals(QualityConfigGuard.UNKNOWN_OPERATOR, guard.operator(mock(HttpServletRequest.class)));
        assertEquals(QualityConfigGuard.UNKNOWN_OPERATOR, guard.operator(null));
    }

    @Test
    @DisplayName("操作人超长截断到审计列长度，避免写历史时整条失败")
    void operatorTruncated() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 80; i++) {
            sb.append('x');
        }
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader(QualityConfigGuard.HEADER_OPERATOR)).thenReturn(sb.toString());

        assertEquals(64, guard.operator(req).length());
    }

    @Test
    @DisplayName("总开关 configWriteOpen=true 时跳过一切写保护检查（内网/开发环境用）")
    void openSwitchBypassesAll() {
        // 默认 fail-closed
        assertNotNull(guard.denyReason("1.2.3.4", "no-token"));

        // sys_param 里开关打开
        when(sysParam.bool(org.mockito.ArgumentMatchers.eq("QUALITY_CONFIG_WRITE_OPEN"), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(true);
        assertNull(guard.denyReason("1.2.3.4", "no-token"), "开关打开后任何 IP/无 token 都应放行");
    }

}
