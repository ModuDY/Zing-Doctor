package com.zing.doctor.config;

import com.zing.doctor.external.ExternalIcuTokenProvider;
import com.zing.doctor.external.ExternalLinkProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全凭据出厂默认值门禁（security-audit 2026-10：production-credentials-in-repo /
 * jwt-secret-and-admin-defaults / icu-static-token-no-expiry-no-replay）。
 *
 * <p>仓库历史交付的默认密钥（jwt-secret、外链 HMAC secret、ICU 静态 token）随源码
 * 公开，任何拿到仓库或交付包的人都可据此伪造合法凭证。本检查在启动期做 fail-fast：
 * 检测到仍为出厂默认值时直接终止启动，把「忘了改密钥」从静默风险变成部署即失败。
 *
 * <p>豁免方式（仅限本地开发）：设置环境变量 {@code ZING_ALLOW_DEFAULT_SECRETS=true}，
 * 此时只打 WARN 不阻断 —— 避免开发环境无法起服务；生产环境严禁设置该变量。
 *
 * <p>注意：管理员口令（ADMIN_PASSWORD）不在此检查内 —— 它仅在首次建号时生效且可
 * 登录后修改，误伤面大；数据库口令属于部署侧配置，应用启动期无法校验其正确性。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecretDefaultGuard implements ApplicationRunner {

    /** 仓库内出现过的出厂默认值（application.yml / docker-compose / install.sh 同源） */
    static final String DEFAULT_JWT_SECRET = "zing-doctor-auth-secret-please-change";
    static final String DEFAULT_EXTERNAL_SECRET = "zing-doctor-dev-secret-change-me";
    static final String DEFAULT_EXTERNAL_SECRET_PROD = "zing-doctor-prod-secret-change-me";
    static final String DEFAULT_ICU_TOKEN = "zing-icu-link-token-2026";

    private final ExternalLinkProperties externalLinkProperties;
    private final ExternalIcuTokenProvider icuTokenProvider;

    @Value("${zing.doctor.auth.jwt-secret:}")
    private String jwtSecret;

    @Value("${ZING_ALLOW_DEFAULT_SECRETS:false}")
    private boolean allowDefaults;

    @Override
    public void run(ApplicationArguments args) {
        List<String> defaults = new ArrayList<>();
        if (isBlankOr(jwtSecret, DEFAULT_JWT_SECRET)) {
            defaults.add("zing.doctor.auth.jwt-secret（AUTH_JWT_SECRET）仍为出厂默认值");
        }
        if (isBlankOr(externalLinkProperties.getSecret(), DEFAULT_EXTERNAL_SECRET, DEFAULT_EXTERNAL_SECRET_PROD)) {
            defaults.add("zing.doctor.external-link.secret（EXTERNAL_LINK_SECRET）仍为出厂默认值");
        }
        if (isBlankOr(icuTokenProvider.current(), DEFAULT_ICU_TOKEN)) {
            defaults.add("zing.doctor.external-link.icu-token（ICU_LINK_TOKEN）仍为出厂默认值");
        }

        if (defaults.isEmpty()) {
            return;
        }

        String summary = String.join("；", defaults);
        if (allowDefaults) {
            log.warn("[安全门禁] 检测到出厂默认密钥（ZING_ALLOW_DEFAULT_SECRETS=true 已豁免，仅限本地开发）：{}", summary);
            return;
        }
        log.error("[安全门禁] 拒绝启动 —— 出厂默认密钥不得用于真实部署：{}。"
                        + "请通过环境变量注入强随机密钥后重启；本地开发可设置 ZING_ALLOW_DEFAULT_SECRETS=true 豁免。",
                summary);
        throw new IllegalStateException("检测到出厂默认密钥，拒绝启动：" + summary
                + "（本地开发可设 ZING_ALLOW_DEFAULT_SECRETS=true 豁免）");
    }

    /** 空白视同未配置（放行由「空 token 校验必失败」兜底）；命中任一默认值即报告 */
    private static boolean isBlankOr(String value, String... defaults) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        for (String d : defaults) {
            if (d.equals(value.trim())) {
                return true;
            }
        }
        return false;
    }
}
