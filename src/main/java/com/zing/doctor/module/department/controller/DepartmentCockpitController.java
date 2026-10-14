package com.zing.doctor.module.department.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.external.ExternalLinkInterceptor;
import com.zing.doctor.icu.service.UserDepartScopeService;
import com.zing.doctor.module.department.dto.CockpitQualityExtra;
import com.zing.doctor.module.department.dto.CockpitSnapshot;
import com.zing.doctor.module.department.service.CockpitQualityExtraService;
import com.zing.doctor.module.department.service.DepartmentCockpitService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 科室驾驶舱接口（只读）。
 *
 * <p>权限与患者工作台 / 神经监测一致：先按账号的科室授权收敛 departCode
 * （见 {@link UserDepartScopeService#resolveQueryDepart}），<b>不信任前端传入的科室编码</b> ——
 * 普通账号越权科室会直接 403，留空也不会退回全院，满足 docs/23 验收项
 * 「伪造未授权 departCode 被后端拒绝」。
 *
 * <p>外链免登录（username=null）时不套科室限制，由 extToken 体系控制，与其它页面一致。
 */
@RestController
@RequestMapping("/api/department-cockpit")
@RequiredArgsConstructor
public class DepartmentCockpitController {

    private final UserDepartScopeService departScopeService;
    private final DepartmentCockpitService cockpitService;
    private final CockpitQualityExtraService qualityExtraService;

    /**
     * 今日快照 + 近 N 天患者流转（阶段1 MVP）。
     *
     * @param departCode 科室编码（sys_depart.org_code）；管理员可留空 = 全院
     * @param days       流转趋势天数，1~90，默认 30
     */
    @GetMapping("/summary")
    public Result<CockpitSnapshot> summary(
            @RequestParam(required = false) String departCode,
            @RequestParam(required = false, defaultValue = "30") int days,
            HttpServletRequest request) {
        String allowed = departScopeService.resolveQueryDepart(currentUsername(request), departCode);
        return Result.ok(cockpitService.snapshot(allowed, days));
    }

    /**
     * 补充质控块：近 7 天转出后 48h 重返率 + 近 12 个月 sepsis bundle 完成率趋势。
     *
     * <p>单独成接口而不是并进 summary：一块要按任意窗口现算、一块要读 12 个月结果，
     * 耗时和「今日快照」不是一个量级，分开挂墙大屏可以先出首屏、补充块后到，
     * 任一块取数失败也只影响自己（服务内已降级，不抛 500）。
     */
    @GetMapping("/quality-extra")
    public Result<CockpitQualityExtra> qualityExtra(
            @RequestParam(required = false) String departCode,
            HttpServletRequest request) {
        String allowed = departScopeService.resolveQueryDepart(currentUsername(request), departCode);
        return Result.ok(qualityExtraService.extra(allowed));
    }

    /** 当前登录账号；外链免登录时返回 null（与其它控制器同一取法）。 */
    private static String currentUsername(HttpServletRequest request) {
        Object u = request.getAttribute(ExternalLinkInterceptor.ATTR_LOGIN_USER);
        return u == null ? null : String.valueOf(u);
    }
}
