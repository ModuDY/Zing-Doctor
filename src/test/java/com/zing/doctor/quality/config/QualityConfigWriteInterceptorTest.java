package com.zing.doctor.quality.config;

import com.zing.doctor.common.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 写接口拦截器的边界测试。
 *
 * <p>重点验证两件事：<b>只拦写、不拦读</b>（避免配置失误把只读看板一起打挂），
 * 以及未配置写保护时 POST 返回 403 而不是静默放行。
 */
class QualityConfigWriteInterceptorTest {

    private static final Object HANDLER = new Object();

    @Test
    @DisplayName("GET 请求直接放行：写保护不应影响只读接口")
    void getPassesThrough() {
        QualityConfigWriteInterceptor interceptor = interceptor(new QualityProperties());

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("GET");

        assertTrue(interceptor.preHandle(req, mock(HttpServletResponse.class), HANDLER));
    }

    @Test
    @DisplayName("OPTIONS 预检放行，避免跨域预检被 403 挡掉")
    void optionsPassesThrough() {
        QualityConfigWriteInterceptor interceptor = interceptor(new QualityProperties());

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("OPTIONS");

        assertTrue(interceptor.preHandle(req, mock(HttpServletResponse.class), HANDLER));
    }

    @Test
    @DisplayName("未配置写保护时 POST 返回 403，并带上可读原因")
    void postDeniedWhenUnconfigured() {
        QualityConfigWriteInterceptor interceptor = interceptor(new QualityProperties());

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("POST");
        when(req.getRequestURI()).thenReturn("/api/quality/config/metric");

        BizException e = assertThrows(BizException.class,
                () -> interceptor.preHandle(req, mock(HttpServletResponse.class), HANDLER));

        assertEquals(403, e.getCode());
        assertTrue(e.getMessage().contains("未启用"), e.getMessage());
    }

    @Test
    @DisplayName("命中 IP 白名单时 POST 放行")
    void postAllowedWhenIpWhitelisted() {
        QualityProperties props = new QualityProperties();
        props.setConfigWriteIpWhitelist("10.0.0.");
        QualityConfigWriteInterceptor interceptor = interceptor(props);

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("POST");
        when(req.getRemoteAddr()).thenReturn("10.0.0.31");

        assertTrue(interceptor.preHandle(req, mock(HttpServletResponse.class), HANDLER));
    }

    @Test
    @DisplayName("令牌保护下：带对令牌放行，带错令牌 403")
    void postTokenChecked() {
        QualityProperties props = new QualityProperties();
        props.setConfigWriteToken("s3cret");
        QualityConfigWriteInterceptor interceptor = interceptor(props);

        HttpServletRequest ok = mock(HttpServletRequest.class);
        when(ok.getMethod()).thenReturn("POST");
        when(ok.getHeader(QualityConfigGuard.HEADER_TOKEN)).thenReturn("s3cret");
        assertTrue(interceptor.preHandle(ok, mock(HttpServletResponse.class), HANDLER));

        HttpServletRequest bad = mock(HttpServletRequest.class);
        when(bad.getMethod()).thenReturn("POST");
        when(bad.getRequestURI()).thenReturn("/api/quality/config/metric");
        when(bad.getHeader(QualityConfigGuard.HEADER_TOKEN)).thenReturn("bad");
        assertThrows(BizException.class,
                () -> interceptor.preHandle(bad, mock(HttpServletResponse.class), HANDLER));
    }

    private static QualityConfigWriteInterceptor interceptor(QualityProperties props) {
        return new QualityConfigWriteInterceptor(new QualityConfigGuard(props));
    }
}
