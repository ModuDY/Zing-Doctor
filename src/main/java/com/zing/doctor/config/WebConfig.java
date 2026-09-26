package com.zing.doctor.config;

import com.zing.doctor.external.ExternalLinkInterceptor;
import com.zing.doctor.quality.config.QualityConfigWriteInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：注册外链拦截器、质控配置写保护、跨域（P0 开发期放开）。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ExternalLinkInterceptor externalLinkInterceptor;
    private final QualityConfigWriteInterceptor qualityConfigWriteInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 放行：外链签发接口（它本身无法携带凭证）+ 登录接口（用账号密码换令牌）+
        // 存活探针与交付包信息（现场验收需要在未登录时也能确认服务版本）
        registry.addInterceptor(externalLinkInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/external/token", "/api/auth/login", "/api/health", "/api/system/build-info");
        // 顺序敏感：必须排在外链鉴权之后，先确认「能看」，再判定「能改」
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
