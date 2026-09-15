package com.zing.doctor.external;

import com.zing.doctor.common.Result;
import com.zing.doctor.module.antibiotic.entity.PageConfig;
import com.zing.doctor.module.antibiotic.mapper.PageConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 外链令牌签发接口（P0 供集成调试与联调用）。
 *
 * <p>生产环境中应由 ICU 系统或医生决策系统管理端持密钥签发，避免暴露给浏览器端调用方。
 */
@RestController
@RequestMapping("/api/external")
@RequiredArgsConstructor
public class ExternalTokenController {

    private final ExternalLinkService externalLinkService;
    private final PageConfigMapper pageConfigMapper;

    /**
     * 按页面编码与业务参数签发外链 URL。
     * 示例：GET /api/external/token?pageCode=abx-decision&patientId=1001
     */
    @GetMapping("/token")
    public Result<String> issueToken(@RequestParam("pageCode") String pageCode,
                                     @RequestParam(value = "baseUrl", required = false) String baseUrl,
                                     @RequestParam Map<String, String> allParams) {
        PageConfig cfg = pageConfigMapper.selectByPageCode(pageCode);
        if (cfg == null) {
            return Result.fail(404, "未注册的页面：" + pageCode);
        }
        Map<String, String> params = new HashMap<>(allParams);
        params.remove("pageCode");
        params.remove("baseUrl");
        String url = externalLinkService.buildEntryUrl(
                baseUrl == null || baseUrl.isEmpty() ? "http://localhost:8081" : baseUrl,
                pageCode, params);
        return Result.ok(url);
    }
}
