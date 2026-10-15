package com.zing.doctor.module.tube.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.external.ExternalLinkInterceptor;
import com.zing.doctor.icu.service.UserDepartScopeService;
import com.zing.doctor.module.tube.service.TubeStatsExportService;
import com.zing.doctor.module.tube.service.TubeStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 导管统计接口（只读）。
 *
 * <p>权限与驾驶舱一致：按 {@link UserDepartScopeService#resolveQueryDepart} 收敛科室，
 * 普通账号越权科室 403；管理员与外链免登录放行。
 */
@RestController
@RequestMapping("/api/tube")
public class TubeStatsController {

    private final TubeStatsService tubeStatsService;
    private final TubeStatsExportService tubeStatsExportService;
    private final UserDepartScopeService departScopeService;

    public TubeStatsController(TubeStatsService tubeStatsService,
                               TubeStatsExportService tubeStatsExportService,
                               UserDepartScopeService departScopeService) {
        this.tubeStatsService = tubeStatsService;
        this.tubeStatsExportService = tubeStatsExportService;
        this.departScopeService = departScopeService;
    }

    /**
     * 按月导管统计。
     *
     * @param yearMonth 形如 2026-09
     * @param departCode 科室编码；管理员可留空=全院
     */
    @GetMapping("/monthly")
    public Result<Map<String, Object>> monthly(@RequestParam String yearMonth,
                                               @RequestParam(required = false) String departCode,
                                               HttpServletRequest request) {
        String allowed = departScopeService.resolveQueryDepart(currentUsername(request), departCode);
        return Result.ok(tubeStatsService.monthly(yearMonth, allowed));
    }

    /**
     * 年度视图：12 个月 × 导管类型趋势，口径与月度完全一致。
     *
     * @param year 形如 2026
     */
    @GetMapping("/yearly")
    public Result<Map<String, Object>> yearly(@RequestParam String year,
                                              @RequestParam(required = false) String departCode,
                                              HttpServletRequest request) {
        String allowed = departScopeService.resolveQueryDepart(currentUsername(request), departCode);
        return Result.ok(tubeStatsService.yearly(year, allowed));
    }

    /** 月度明细导出（xlsx：明细 + 汇总两个 sheet）。 */
    @GetMapping("/export/monthly")
    public void exportMonthly(@RequestParam String yearMonth,
                              @RequestParam(required = false) String departCode,
                              HttpServletRequest request,
                              HttpServletResponse response) {
        String allowed = departScopeService.resolveQueryDepart(currentUsername(request), departCode);
        tubeStatsExportService.exportMonthly(response, yearMonth, allowed);
    }

    /** 年度趋势导出（xlsx：月×类型矩阵 + 全年汇总）。 */
    @GetMapping("/export/yearly")
    public void exportYearly(@RequestParam String year,
                             @RequestParam(required = false) String departCode,
                             HttpServletRequest request,
                             HttpServletResponse response) {
        String allowed = departScopeService.resolveQueryDepart(currentUsername(request), departCode);
        tubeStatsExportService.exportYearly(response, year, allowed);
    }

    private static String currentUsername(HttpServletRequest request) {
        Object u = request.getAttribute(ExternalLinkInterceptor.ATTR_LOGIN_USER);
        return u == null ? null : String.valueOf(u);
    }
}
