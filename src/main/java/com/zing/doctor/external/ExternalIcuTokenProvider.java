package com.zing.doctor.external;

import cn.hutool.core.util.StrUtil;
import com.zing.doctor.module.system.service.SysParamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ICU 明文外链 token 的唯一取值入口。
 *
 * <p>安全修复（icu-static-token-no-expiry-no-replay）：此前同一凭证存在两个真源 ——
 * {@code /entry} 侧（ExternalLinkService.verifyIcuPlain）先读 sys_param 的
 * EXTERNAL_LINK_ICU_TOKEN、为空再回落配置文件；而 {@code /api} 侧
 * （ExternalLinkInterceptor.verifyIcuPlain）只读配置文件。结果是运维在「参数设置」页
 * 轮换 token 只能吊销页面入口，已泄露的旧 token 仍可继续调用全部 /api 接口，
 * 「轮换」形同虚设。
 *
 * <p>本类把取值收敛为一处：<b>sys_param 优先，配置文件兜底</b>，两条通道共用，
 * 使参数页「随机生成」新 token 后 API 通道立即生效（ParamStore 保存时清缓存）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalIcuTokenProvider {

    /** 参数表中登记外链 token 的键（与 ExternalLinkService/参数页约定一致） */
    public static final String PARAM_KEY = "EXTERNAL_LINK_ICU_TOKEN";

    private final SysParamService sysParamService;
    private final ExternalLinkProperties properties;

    /**
     * 当前生效的 ICU 外链 token；两处（/entry 与 /api）校验必须都走这里。
     */
    public String current() {
        String fromParam = sysParamService.value(PARAM_KEY);
        if (StrUtil.isNotBlank(fromParam)) {
            return fromParam.trim();
        }
        return properties.getIcuToken();
    }
}
