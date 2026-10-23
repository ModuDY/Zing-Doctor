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
 * Web 配置：注册外链拦截器、管理员写保护、质控数据访问控制、质控配置写保护、跨域（P0 开发期放开）。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ExternalLinkInterceptor externalLinkInterceptor;
    private final QualityAccessInterceptor qualityAccessInterceptor;
    private final QualityConfigWriteInterceptor qualityConfigWriteInterceptor;
    private final AdminWriteInterceptor adminWriteInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 放行：外链签发接口（它本身无法携带凭证）+ 登录接口（用账号密码换令牌）+
        // 存活探针与交付包信息（现场验收需要在未登录时也能确认服务版本）+
        // 登录页品牌信息（院徽 / 院名）：登录页没有令牌，不放行就只能显示内置 /logo.png
        registry.addInterceptor(externalLinkInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/external/token", "/api/auth/login", "/api/health",
                        "/api/system/build-info", "/api/sys-param/public/doc-header");
        // 顺序敏感：外链鉴权 → 访问控制（能否看）→ 写保护（能否改）。
        // 顺序反了会给出错误的原因（比如「配置写接口未启用」而真实原因是没通过访问控制），
        // 现场照提示排查只会越查越偏。
        registry.addInterceptor(qualityAccessInterceptor)
                .addPathPatterns("/api/quality/**");
        registry.addInterceptor(qualityConfigWriteInterceptor)
                // 安全修复（quality-write-endpoints-bypass-write-gate）：写闸从
                // /api/quality/config/** 扩到 /api/quality/** —— QualityController 下
                // 8 个写端点（/rules/config、/manual、/recalc、/monthly/rebuild 等）
                // 此前在闸外，任意外链/登录主体可改质控上报口径与结果。
                // 拦截器本身只拦非 GET/HEAD/OPTIONS，看板与配置回显不受影响。
                .addPathPatterns("/api/quality/**");
        // 管理员写保护（security-audit 2026-10：clinical-config-write-no-authz / sys-param-save-missing-admin-authz）：
        // 覆盖全部「改配置即改临床/系统行为」的写端点；GET 放行，写请求要求直连登录管理员。
        // 拦截顺序在外链鉴权之后（写请求先通过通道鉴权，再判定管理员）。
        registry.addInterceptor(adminWriteInterceptor)
                .addPathPatterns(
                        // 系统参数与参数分组（自我提权 / 关闭质控开关的入口）
                        "/api/sys-param/**",
                        "/api/param-group/**",
                        // 外链签发（未认证方可铸造全 API 凭证的入口）
                        "/api/external/token",
                        // SOFA 取数映射与阈值（篡改即改评分逻辑）
                        "/api/sofa/config/**",
                        "/api/sofa/auto-generate",
                        // 抗感染四维配置（词库 / DDD / MDRO / PKPD / 字典同步）
                        "/api/antibiotic/word-config/**",
                        "/api/antibiotic/ddd/config/**",
                        "/api/antibiotic/mdro/config/**",
                        "/api/antibiotic/pkpd-knowledge",
                        "/api/antibiotic/pkpd-knowledge/**",
                        "/api/antibiotic/drug-dict/**",
                        // ARDS 俯卧位采集映射 / 时点模板 / 导管分组
                        "/api/ards-prone/map/**",
                        "/api/ards-prone/tpl/**",
                        "/api/ards-prone/tp/**",
                        "/api/tube/groups/**");
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
