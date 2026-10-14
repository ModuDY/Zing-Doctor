package com.zing.doctor.config;

import com.zing.doctor.external.ExternalLinkInterceptor;
import com.zing.doctor.quality.config.QualityAccessInterceptor;
import com.zing.doctor.quality.config.QualityConfigWriteInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：注册外链拦截器、质控数据访问控制、质控配置写保护、跨域（P0 开发期放开）。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ExternalLinkInterceptor externalLinkInterceptor;
    private final QualityAccessInterceptor qualityAccessInterceptor;
    private final QualityConfigWriteInterceptor qualityConfigWriteInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 放行：外链签发接口（它本身无法携带凭证）+ 登录接口（用账号密码换令牌）+
        // 存活探针与交付包信息（现场验收需要在未登录时也能确认服务版本）
        registry.addInterceptor(externalLinkInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/external/token", "/api/auth/login", "/api/health", "/api/system/build-info");
        // 顺序敏感：外链鉴权 → 访问控制（能否看）→ 写保护（能否改）。
        // 顺序反了会给出错误的原因（比如「配置写接口未启用」而真实原因是没通过访问控制），
        // 现场照提示排查只会越查越偏。
        registry.addInterceptor(qualityAccessInterceptor)
                .addPathPatterns("/api/quality/**");
        registry.addInterceptor(qualityConfigWriteInterceptor)
                .addPathPatterns("/api/quality/config/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
