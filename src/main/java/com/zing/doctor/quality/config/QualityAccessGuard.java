package com.zing.doctor.quality.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;

/**
 * 质控数据（看板 + 导出）的访问控制。
 *
 * <p>背景：质控数据是科室级敏感数据 —— 它包含全院患者级明细的汇总口径，
 * 也能逐患者下钻到姓名与住院号。此前的访问控制只有外链鉴权与登录态：
 * <b>凡是能打开 ICU 外链或登录本系统的人，都能查看并导出全部质控数据</b>，
 * 与「只有经授权人员才能访问和导出」的要求不符。
 *
 * <p>本类补上这一层，判定方式与配置写保护（{@link QualityConfigGuard}）同构：
 * <ol>
 *   <li>开关 {@code zing.quality.access-control-enabled} 默认 <b>false</b>：
 *       未启用时行为与改造前完全一致（放行），避免升级即把现场人员挡在门外；</li>
 *   <li>启用后只认 IP 白名单（{@code zing.quality.access-ip-whitelist}，逗号分隔、支持前缀匹配）——
 *       名单在服务端配置，浏览器端零改动、不必重建前端；</li>
 *   <li>启用但未配白名单时<b>一律拒绝</b>（fail-closed）：
 *       此时「配错了」和「没配」都表现为 403，宁可让部署方显式开一次口子，
 *       也不要默认放开一个能导出全院患者明细的入口。</li>
 * </ol>
 *
 * <p>本项目尚无 SSO，因此粒度是<b>工作站</b>而不是人；接入 SSO 后可把
 * {@link #denyReason(String)} 换成角色/权限位判定（如「质控查看」「质控导出」两个权限位），
 * 拦截器与页面无需再动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QualityAccessGuard {

    private final QualityProperties properties;

    /**
     * 启动时把访问控制状态打到日志。
     *
     * <p>「没启用」与「启用但没配白名单」在运行期都表现为「能访问」或「全 403」，
     * 若启动不留痕，现场排障时要靠猜。这里明确说出当前处于哪一档。
     */
    @PostConstruct
    public void reportState() {
        if (!properties.isAccessControlEnabled()) {
            log.warn("[质控访问] 数据访问控制未启用：能打开质控页面的用户均可查看并导出质控数据。"
                    + "如需限制，请配置 zing.quality.access-control-enabled=true "
                    + "与 zing.quality.access-ip-whitelist=10.0.0.,192.168.1.20");
            return;
        }
        if (!StringUtils.hasText(properties.getAccessIpWhitelist())) {
            log.error("[质控访问] 已启用访问控制但未配置白名单：/api/quality/** 将一律返回 403。"
                    + "请配置 zing.quality.access-ip-whitelist（逗号分隔，支持前缀匹配）。");
            return;
        }
        log.info("[质控访问] 访问控制已启用：授权白名单={}", properties.getAccessIpWhitelist());
    }

    /**
     * 访问判定。
     *
     * @param clientIp 客户端 IP（由 {@link QualityConfigGuard#clientIp} 解析，已优先取 XFF 首段）
     * @return {@code null} 表示放行；非空为<b>拒绝原因</b>。
     *         原因文案刻意写得短（&le;30 字）：前端的错误提示对短句原样展示，
     *         长句会被折叠成「操作失败，请稍后重试」，使用者便无从知道该找谁开通权限。
     */
    public String denyReason(String clientIp) {
        if (!properties.isAccessControlEnabled()) {
            return null;
        }
        // 安全修复（quality-config-write-bypass-via-sys-param 同族）：
        // 删除 sys_param QUALITY_ACCESS_OPEN 临时放行分支 —— 该参数可被任一已认证主体
        // 经 /api/sys-param/save 写入，使已启用的访问控制整体失效（原分支：
        // sysParamService.bool(PARAM_OPEN, false) 为真即 return null）。
        // 临时放开请改配置文件 zing.quality.access-control-enabled 并重启。
        String white = properties.getAccessIpWhitelist();
        if (!StringUtils.hasText(white)) {
            return "质控数据访问控制已启用，但未配置授权白名单";
        }
        if (ipAllowed(clientIp)) {
            return null;
        }
        return "质控数据需授权访问：当前工作站不在授权范围内";
    }

    /**
     * IP 是否命中白名单。元素支持前缀匹配，便于用 {@code 10.0.0.} 覆盖整段。
     *
     * <p>提示：前缀匹配是「字符串前缀」而不是「网段计算」，因此
     * {@code 10.0.0.} 不会命中 {@code 10.0.0.1} 之外的 {@code 10.0.100.1}（这是想要的行为），
     * 但 {@code 10.0.} 会命中 {@code 10.0.100.1}。写法上按需取粒度即可。
     */
    public boolean ipAllowed(String ip) {
        String white = properties.getAccessIpWhitelist();
        if (!StringUtils.hasText(ip) || !StringUtils.hasText(white)) {
            return false;
        }
        for (String raw : white.split(",")) {
            String rule = raw.trim();
            if (rule.isEmpty()) {
                continue;
            }
            if (ip.equals(rule) || ip.startsWith(rule)) {
                return true;
            }
        }
        return false;
    }
}
