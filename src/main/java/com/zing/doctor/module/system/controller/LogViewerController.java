package com.zing.doctor.module.system.controller;

import cn.hutool.core.util.StrUtil;
import com.zing.doctor.common.BizException;
import com.zing.doctor.common.Result;
import com.zing.doctor.external.ExternalLinkInterceptor;
import com.zing.doctor.icu.service.UserDepartScopeService;
import com.zing.doctor.module.system.dto.LogPage;
import com.zing.doctor.module.system.service.LogViewerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 后端日志查看（系统设置 → 日志查看）。
 *
 * <p><b>安全模型（fail-closed）</b>：日志含 SQL、患者数据、部署路径等敏感信息，只允许
 * <b>直连登录的管理员账号</b>查看：
 * <ul>
 *   <li>外链通道（ICU 第三方系统免登录跳转）一律 403 —— 该通道不写 {@code ATTR_LOGIN_USER}；</li>
 *   <li>登录但非管理员（不在 {@code WORKBENCH_SUPER_USERS} 名单）一律 403；</li>
 *   <li>接口挂 {@code /api/system/**}，未登录请求先被 {@link ExternalLinkInterceptor} 拦截（401）。</li>
 * </ul>
 * 页面路由不进 sys_page_config 外链白名单，双重保险下外链无法打开该页。
 */
@RestController
@RequestMapping("/api/system/logs")
@RequiredArgsConstructor
public class LogViewerController {

    private final LogViewerService logViewerService;
    private final UserDepartScopeService departScopeService;

    /**
     * 读取一页日志。
     *
     * @param lines  行数（1~2000，默认 200）
     * @param offset 翻页游标：0=最新一页；非 0=该字节偏移向前的更早一页
     */
    @GetMapping("/tail")
    public Result<LogPage> tail(@RequestParam(defaultValue = "200") int lines,
                                @RequestParam(defaultValue = "0") long offset,
                                HttpServletRequest request) {
        requireAdmin(request);
        return Result.ok(logViewerService.tail(lines, offset));
    }

    private void requireAdmin(HttpServletRequest request) {
        Object user = request.getAttribute(ExternalLinkInterceptor.ATTR_LOGIN_USER);
        String username = user == null ? null : String.valueOf(user);
        if (StrUtil.isBlank(username)) {
            throw new BizException(403, "日志查看仅支持登录访问");
        }
        if (!departScopeService.resolve(username).isAdmin()) {
            throw new BizException(403, "仅系统管理员可查看日志");
        }
    }
}
