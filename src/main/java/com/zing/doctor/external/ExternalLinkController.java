package com.zing.doctor.external;

import cn.hutool.json.JSONUtil;
import cn.hutool.core.util.StrUtil;
import com.zing.doctor.common.BizException;
import com.zing.doctor.module.antibiotic.entity.ExternalAccessLog;
import com.zing.doctor.module.antibiotic.entity.PageConfig;
import com.zing.doctor.module.antibiotic.mapper.ExternalAccessLogMapper;
import com.zing.doctor.module.antibiotic.mapper.PageConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 外链统一入口。
 *
 * <p>外部系统（如 ICU 信息系统）通过
 * {@code GET /entry/{pageCode}?expire=xx&sign=xx&业务参数...}
 * 免登录进入医生系统某个功能页面。本接口完成签名校验后 302 跳转到前端路由。
 */
@Slf4j
@RestController
@RequestMapping("/entry")
@RequiredArgsConstructor
public class ExternalLinkController {

    private final ExternalLinkService externalLinkService;
    private final ExternalLinkProperties properties;
    private final PageConfigMapper pageConfigMapper;
    private final ExternalAccessLogMapper externalAccessLogMapper;

    @GetMapping("/{pageCode}")
    public void entry(@PathVariable("pageCode") String pageCode,
                      HttpServletRequest request,
                      HttpServletResponse response) throws IOException {
        Map<String, String> raw = collectParams(request);
        ExternalLinkContext ctx = externalLinkService.verify(pageCode, raw);

        PageConfig cfg = pageConfigMapper.selectByPageCode(pageCode);
        if (cfg == null) {
            throw new BizException(404, "未注册的页面：" + pageCode);
        }

        recordAccessLog(pageCode, request, ctx);

        // 透传外链上下文：签名模式带 pageCode/expire/sign；ICU 明文模式带 pageCode/extToken。
        // 前端持之调用 /api 接口通过拦截器校验。
        String rawExpire = raw.get("expire");
        String rawSign = raw.get("sign");
        String rawExtToken = raw.get("extToken");
        boolean icuPlain = StrUtil.isBlank(rawSign) && StrUtil.isNotBlank(rawExtToken);

        StringBuilder redirect = new StringBuilder();
        // 跳转基础地址优先级：显式配置 EXTERNAL_LINK_BASE_URL（强制指定） > 外链请求自带的 Host（推荐，跟随外部系统访问地址）
        String baseUrl = properties.getBaseUrl();
        if (StrUtil.isNotBlank(baseUrl)) {
            redirect.append(baseUrl.replaceAll("/+$", ""));
        } else {
            String scheme = request.getScheme();
            String host = request.getHeader("Host");
            if (StrUtil.isBlank(host)) {
                host = request.getHeader("X-Forwarded-Host");
            }
            if (StrUtil.isNotBlank(host)) {
                redirect.append(scheme).append("://").append(host);
            }
        }
        redirect.append(cfg.getFrontendPath()).append('?')
                .append("pageCode=").append(pageCode);
        if (icuPlain) {
            redirect.append("&extToken=").append(URLEncoder.encode(rawExtToken, "UTF-8"));
        } else {
            redirect.append("&expire=").append(rawExpire == null ? "" : rawExpire)
                    .append("&sign=").append(URLEncoder.encode(rawSign == null ? "" : rawSign, "UTF-8"));
        }
        // 业务参数统一带 "&" 前缀（前面 pageCode/extToken/expire/sign 已自带分隔符）
        for (Map.Entry<String, String> e : ctx.getParams().entrySet()) {
            redirect.append('&').append(e.getKey()).append('=')
                    .append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), "UTF-8"));
        }
        response.sendRedirect(redirect.toString());
    }

    private void recordAccessLog(String pageCode, HttpServletRequest request, ExternalLinkContext ctx) {
        try {
            ExternalAccessLog logEntry = new ExternalAccessLog();
            logEntry.setPageCode(pageCode);
            logEntry.setSourceSystem("icu");
            logEntry.setIp(request.getRemoteAddr());
            logEntry.setBizParams(JSONUtil.toJsonStr(ctx.getParams()));
            logEntry.setAccessTime(LocalDateTime.now());
            externalAccessLogMapper.insert(logEntry);
        } catch (Exception e) {
            // 访问日志失败不影响外链跳转
            log.warn("记录外链访问日志失败", e);
        }
    }

    private Map<String, String> collectParams(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) {
                map.put(k, v[0]);
            }
        });
        return map;
    }
}
