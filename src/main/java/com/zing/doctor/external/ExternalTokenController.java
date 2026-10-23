package com.zing.doctor.external;

import cn.hutool.core.util.StrUtil;
import com.zing.doctor.common.BizException;
import com.zing.doctor.common.Result;
import com.zing.doctor.icu.service.UserDepartScopeService;
import com.zing.doctor.module.antibiotic.entity.PageConfig;
import com.zing.doctor.module.antibiotic.mapper.PageConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 外链令牌签发接口。
 *
 * <p>安全修复（external-token-unauthenticated-issuance）：本端点此前被
 * {@code WebConfig} 排除鉴权、且签出的 HMAC 凭证不含路径/方法绑定，等于向未认证方
 * 开放「全 API 凭证铸造厂」。现收紧为三层防线：
 *
 * <ol>
 *   <li><b>拦截器层</b>：/api/external/token 已纳入 {@code AdminWriteInterceptor}
 *       写保护（GET 放行例外——注意签发本身是 GET，因此本控制器内再做第二层判定，
 *       防止拦截器按「读」放行）；</li>
 *   <li><b>控制器层</b>：仅直连登录的管理员可签发，外链通道一律 403
 *       （与 LogViewerController.requireAdmin 口径一致）；</li>
 *   <li><b>参数层</b>：删除调用方可控的 baseUrl 参数（跳转基址一律取服务端配置，
 *       防开放重定向/凭证导向），并拒绝注入身份类参数（realname/userName 等，
 *       身份只能由 ICU 侧模板带出，不得经签发接口伪造审计署名）。</li>
 * </ol>
 *
 * <p>服务端到服务端的自动签发需求，应另行提供持独立管理密钥（非 EXTERNAL_LINK_SECRET）
 * 的接口，而非向浏览器开放本端点。
 */
@RestController
@RequestMapping("/api/external")
@RequiredArgsConstructor
public class ExternalTokenController {

    /** 禁止调用方通过签发接口注入的身份类参数（会被 OperatorContext 当作审计署名） */
    static final String[] IDENTITY_PARAMS =
            {"realname", "userName", "username", "userId", "operator", "operatorId", "workNo", "empNo"};

    private final ExternalLinkService externalLinkService;
    private final PageConfigMapper pageConfigMapper;
    private final ExternalLinkProperties properties;
    private final UserDepartScopeService departScopeService;

    /**
     * 按页面编码与业务参数签发外链 URL。
     * 示例：GET /api/external/token?pageCode=abx-decision&patientId=1001
     */
    @GetMapping("/token")
    public Result<String> issueToken(@RequestParam("pageCode") String pageCode,
                                     HttpServletRequest request,
                                     @RequestParam Map<String, String> allParams) {
        // 管理员判定：ATTR_LOGIN_USER 仅直连登录通道写入，外链持有人到此即 403
        Object user = request.getAttribute(ExternalLinkInterceptor.ATTR_LOGIN_USER);
        String username = user == null ? null : String.valueOf(user);
        if (StrUtil.isBlank(username) || !departScopeService.isAdmin(username)) {
            throw new BizException(403, "外链签发仅限直连登录的管理员账号");
        }

        PageConfig cfg = pageConfigMapper.selectByPageCode(pageCode);
        if (cfg == null) {
            return Result.fail(404, "未注册的页面：" + pageCode);
        }

        Map<String, String> params = new HashMap<>(allParams);
        params.remove("pageCode");
        for (String key : IDENTITY_PARAMS) {
            if (params.remove(key) != null) {
                throw new BizException(400, "外链签发不接受身份类参数：" + key);
            }
        }

        // 跳转基址只认服务端配置（EXTRA_LINK_BASE_URL），杜绝凭证被导向任意主机；
        // 配置为空时回退相对路径（由 /entry 按请求 Host 决定，不在此处接受调用方指定）
        String url = externalLinkService.buildEntryUrl(
                StrUtil.nullToEmpty(properties.getBaseUrl()), pageCode, params);
        return Result.ok(url);
    }
}
