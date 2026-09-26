package com.zing.doctor.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 交付包信息接口。
 *
 * <p>现场只需要访问这个接口，就能确认当前运行的 jar、profile、ICU 数据源模式和
 * 构建提交，不再靠「服务器上到底替换没替换成功」猜版本。构建信息通过环境变量注入，
 * 未注入时明确返回 unknown，不伪造版本。</p>
 */
@RestController
public class BuildInfoController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    @Value("${zing.doctor.icu-data-provider:sql}")
    private String icuDataProvider;

    @Value("${zing.build.version:1.0.0}")
    private String buildVersion;

    @Value("${GIT_COMMIT:unknown}")
    private String gitCommit;

    @Value("${BUILD_TIME:unknown}")
    private String buildTime;

    @GetMapping("/api/system/build-info")
    public Result<Map<String, Object>> buildInfo() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("application", "zing-doctor");
        data.put("version", buildVersion);
        data.put("gitCommit", gitCommit);
        data.put("buildTime", buildTime);
        data.put("activeProfile", activeProfile == null || activeProfile.trim().isEmpty()
                ? "default" : activeProfile);
        data.put("icuDataProvider", icuDataProvider);
        data.put("serverTime", LocalDateTime.now().format(FMT));
        return Result.ok(data);
    }
}
