package com.zing.doctor.icu.service;

import com.zing.doctor.external.ExternalLinkInterceptor;
import com.zing.doctor.icu.dto.WorkbenchPatient;

import javax.servlet.http.HttpServletRequest;

/**
 * 科室收敛辅助（security-audit 2026-10：patient-endpoint-no-depart-scope）。
 *
 * <p>此前 SOFA / APACHE II / 脓毒症 / 俯卧位 / 血管活性 / MDRO / DDD / 交班等模块的
 * 患者级与总览端点未接入 {@link UserDepartScopeService#resolveQueryDepart}，
 * 任一已鉴权主体（外链持有人或普通登录账号）可跨科室读取全院患者数据。
 * 本类把两个既有正控范式收拢成一行调用，供各控制器复用：
 *
 * <ul>
 *   <li><b>列表/总览类</b>：{@link #scopeDepart(HttpServletRequest, String)} ——
 *       把请求的 departCode 收敛成「该账号实际能查的科室」（TimelineController 范式）；</li>
 *   <li><b>患者级（按 patientId / inHospitalNo）</b>：{@link #assertPatientDepart(HttpServletRequest, WorkbenchPatient)}
 *       —— 先取患者所属科室，再按其科室收敛（AntibioticController.assertPatientAccess 范式）。</li>
 * </ul>
 *
 * <p>收敛失败（无科室授权 / 越权 / 普通账号未选科室）抛出 {@code BizException}，
 * 由 GlobalExceptionHandler 统一转为 400/403。外链通道（username=null）维持
 * resolveQueryDepart 的既有语义（按来源系统 URL 参数放行）。
 */
public final class DepartScopeAsserts {

    private DepartScopeAsserts() {
    }

    /** 当前登录账号；外链免登录时为 null */
    public static String currentUsername(HttpServletRequest request) {
        Object u = request.getAttribute(ExternalLinkInterceptor.ATTR_LOGIN_USER);
        return u == null ? null : u.toString();
    }

    /**
     * 列表/总览类：收敛请求的 departCode。
     *
     * @return 允许查询的科室编码（管理员/外链为调用方原值；普通账号为其授权科室）
     */
    public static String scopeDepart(UserDepartScopeService scope, HttpServletRequest request, String requested) {
        return scope.resolveQueryDepart(currentUsername(request), requested);
    }

    /**
     * 患者级：按「患者所属科室」收敛。患者不存在时由调用方先行判空返回。
     * 普通账号越权访问他科患者时抛 403。
     */
    public static void assertPatientDepart(UserDepartScopeService scope, HttpServletRequest request,
                                           WorkbenchPatient patient) {
        scope.resolveQueryDepart(currentUsername(request), patient.getDepartCode());
    }
}
