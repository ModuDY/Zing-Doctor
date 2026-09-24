package com.zing.doctor.icu.service;

import cn.hutool.core.util.StrUtil;
import com.zing.doctor.common.BizException;
import com.zing.doctor.icu.dto.DepartScope;
import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.module.system.service.SysParamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 患者工作台的科室边界：按账号推导「这个人能看哪些科室」。
 *
 * <p>三条规则，按顺序判定：
 *
 * <ol>
 *   <li><b>管理员</b>：名单（系统参数 {@code WORKBENCH_SUPER_USERS}）内的账号豁免
 *       一切限制，全院可见可切；</li>
 *   <li><b>授权科室</b>：其余账号按重症侧 {@code sys_user_depart} 的授权收敛。
 *       授权多个科室时不替使用者猜，由前端让他自选；</li>
 *   <li><b>查无此人</b>：重症侧查不到该账号（或其科室全部停用）时视为<b>无科室</b>，
 *       <b>绝不退回全院</b>。</li>
 * </ol>
 *
 * <p>第 3 条是这里最关键的一条。此前工作台对直连账号不做任何限制，等于写链接的人
 * 能看到全科患者；改成「查不到就是无权限」之后，配置遗漏会以「看不到数据」的形式
 * 暴露出来，而不是默默把数据给出去 —— 这是默认拒绝（fail-closed）。
 *
 * <p><b>校验必须在服务端独立完成</b>：{@link #resolveQueryDepart} 是实际放行逻辑，
 * 前端是否传了科室、下拉选了什么都不影响这里的判定。前端只负责交互，
 * 不能构成任何安全性。
 *
 * <p>本服务全程只读，不写任何表。
 */
@Service
@Slf4j
public class UserDepartScopeService {

    /** 管理员名单所在的系统参数键 */
    public static final String KEY_SUPER_USERS = "WORKBENCH_SUPER_USERS";

    private final IcuPatientMapper icuPatientMapper;
    private final SysParamService sysParamService;

    /** 参数未登记时的兜底名单，来自 application.yml（不给就会走到 null，下面统一处理） */
    private final String superUsersFallback;

    public UserDepartScopeService(IcuPatientMapper icuPatientMapper,
                                  SysParamService sysParamService,
                                  @Value("${zing.workbench.super-users:admin,zing}") String superUsersFallback) {
        this.icuPatientMapper = icuPatientMapper;
        this.sysParamService = sysParamService;
        this.superUsersFallback = superUsersFallback;
    }

    /**
     * 解析当前账号的科室范围。
     *
     * @param username 直连登录账号；{@code null} / 空白表示外链免登录访问
     */
    public DepartScope resolve(String username) {
        DepartScope scope = new DepartScope();
        scope.setUsername(username);

        if (StrUtil.isBlank(username)) {
            // 外链免登录：没有直连账号可依据，科室边界按原有行为交给来源系统的 URL 参数。
            // 字典仍返回全部在用科室 —— 只为让下拉能把「20070131」显示成「ICU-4U」，
            // 切换后实际查什么依旧由外链参数说了算，不构成新的可见范围。
            scope.setAdmin(false);
            scope.setMatched(false);
            scope.setDeparts(toOptions(icuPatientMapper.selectAllDepartments()));
            scope.setMessage("外链访问，科室范围由来源系统指定");
            return scope;
        }

        boolean admin = isSuperUser(username);
        scope.setAdmin(admin);

        List<DepartScope.DepartOption> departs = admin
                ? toOptions(icuPatientMapper.selectAllDepartments())
                : toOptions(icuPatientMapper.selectAuthorizedDeparts(username));
        scope.setDeparts(departs);
        scope.setMatched(admin || !departs.isEmpty());
        scope.setMessage(describe(admin, departs));
        return scope;
    }

    /**
     * 把「请求要查的科室」收敛成「这个账号实际能查的科室」，越权一律抛错。
     *
     * <p>这是真正的安全边界：管理员和外链维持原行为，普通账号必须显式指定一个
     * 自己有权限的科室。刻意不提供「没传就按全院」的宽松分支 —— 否则绕过前端
     * 直接调接口就能拿到全科数据。
     *
     * @param requested 请求的科室编码（{@code null} / 空 / {@code ALL} 表示全院）
     * @return 允许查询的科室编码（普通账号下必为其授权科室之一）
     */
    public String resolveQueryDepart(String username, String requested) {
        DepartScope scope = resolve(username);

        if (StrUtil.isBlank(username)) {
            return requested;
        }
        if (scope.isAdmin()) {
            return requested;
        }
        if (scope.getDeparts().isEmpty()) {
            throw new BizException(403, "当前账号未在重症系统绑定在用科室，无法查看患者，请联系管理员配置科室权限");
        }
        if (StrUtil.isBlank(requested) || "ALL".equals(requested)) {
            throw new BizException(400, "当前账号有多个科室权限，请先选择科室后再查看");
        }
        boolean allowed = scope.getDeparts().stream()
                .anyMatch(d -> Objects.equals(d.getOrgCode(), requested));
        if (!allowed) {
            log.warn("越权访问被拦截 username={} requestedDepart={} allowed={}", username, requested,
                    scope.getDeparts().stream().map(DepartScope.DepartOption::getOrgCode).collect(Collectors.toList()));
            throw new BizException(403, "当前账号无权查看该科室");
        }
        return requested;
    }

    /**
     * 是否管理员名单成员。
     *
     * <p>名单用系统参数维护（页面可改），读不到时回退 {@code application.yml} 的默认值。
     * 之所以用名单而不是角色表：本库没有 sys_role / sys_user_role，短期也不打算为此
     * 引入一整套权限体系；名单写在这里有据可查，将来换成按角色判定不影响调用方。
     */
    private boolean isSuperUser(String username) {
        String cfg = sysParamService.value(KEY_SUPER_USERS);
        if (StrUtil.isBlank(cfg)) {
            cfg = superUsersFallback;
        }
        if (StrUtil.isBlank(cfg)) {
            return false;
        }
        // 同时容忍中英文逗号与分号，避免页面上随手写错分隔符导致名单整条失效
        return Arrays.stream(cfg.split("[，,;；]"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .anyMatch(u -> u.equalsIgnoreCase(username));
    }

    private static String describe(boolean admin, List<DepartScope.DepartOption> departs) {
        if (admin) {
            return "管理员账号，可查看全部科室";
        }
        if (departs.isEmpty()) {
            return "当前账号未在重症系统绑定到任何在用科室，无法查看患者，请联系管理员配置科室权限";
        }
        return departs.size() == 1 ? null : "当前账号有多个科室权限，请选择科室后查看";
    }

    private static List<DepartScope.DepartOption> toOptions(List<Map<String, Object>> rows) {
        if (rows == null) {
            return new ArrayList<>();
        }
        List<DepartScope.DepartOption> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            if (r == null || r.get("org_code") == null) {
                continue;
            }
            DepartScope.DepartOption o = new DepartScope.DepartOption();
            String code = String.valueOf(r.get("org_code"));
            o.setOrgCode(code);
            Object name = r.get("depart_name");
            // 名称缺失时宁可显示编码也不显示空白 —— 下拉里出现空选项比多一个编号更难排查
            o.setDepartName(name == null ? code : String.valueOf(name));
            out.add(o);
        }
        return out;
    }
}
