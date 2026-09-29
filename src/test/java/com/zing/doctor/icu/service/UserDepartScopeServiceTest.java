package com.zing.doctor.icu.service;

import com.zing.doctor.common.BizException;
import com.zing.doctor.icu.dto.DepartScope;
import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.module.system.service.SysParamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 患者工作台的科室边界。
 *
 * <p>这里钉死的是三条容易被"顺手改坏"的约定：
 * <ol>
 *   <li>管理员豁免；</li>
 *   <li>多科室必须显式指定，留空不退回全院；</li>
 *   <li>重症侧查无此人 = 无科室（fail-closed），不是全院。</li>
 * </ol>
 *
 * <p>第 3 条尤其要防：将来若有人为了"让新账号先能用起来"把空授权改成退回全院，
 * 就会把全科患者暴露给一个身份未确认的账号 —— 这是本测试存在的主要理由。
 */
class UserDepartScopeServiceTest {

    private IcuPatientMapper mapper;
    private SysParamService params;
    private UserDepartScopeService service;

    @BeforeEach
    void setUp() {
        mapper = mock(IcuPatientMapper.class);
        params = mock(SysParamService.class);
        // 参数未登记时返回 null —— 与 SysParamService.value 的真实契约一致
        when(params.value(anyString())).thenReturn(null);
        service = new UserDepartScopeService(mapper, params, "admin,zing");
    }

    @Test
    @DisplayName("管理员：豁免科室限制，留空即全院")
    void adminSeesAll() {
        when(params.value(UserDepartScopeService.KEY_SUPER_USERS)).thenReturn("admin");
        when(mapper.selectAllDepartments()).thenReturn(Arrays.asList(
                dept("20070131", "ICU-4U"), dept("20010231", "CCU-24U")));

        DepartScope scope = service.resolve("admin");
        assertTrue(scope.isAdmin(), "名单内账号应被识别为管理员");
        assertTrue(scope.isMatched());
        assertEquals(2, scope.getDeparts().size());

        assertEquals("20010231", service.resolveQueryDepart("admin", "20010231"));
        assertNull(service.resolveQueryDepart("admin", null), "管理员留空应放行（=全院）");
    }

    @Test
    @DisplayName("多科室账号：必须显式指定，留空不退回全院")
    void multiDeptNeedsExplicitPick() {
        when(mapper.selectAuthorizedDeparts("103032")).thenReturn(Arrays.asList(
                dept("20070131", "ICU-4U"), dept("20010231", "CCU-24U")));

        DepartScope scope = service.resolve("103032");
        assertFalse(scope.isAdmin());
        assertTrue(scope.isMatched());
        assertEquals(2, scope.getDeparts().size());

        assertEquals("20010231", service.resolveQueryDepart("103032", "20010231"));
        assertThrows(BizException.class, () -> service.resolveQueryDepart("103032", ""),
                "留空若被放宽成全院，等于绕过授权");
        assertThrows(BizException.class, () -> service.resolveQueryDepart("103032", "ALL"),
                "显式传 ALL 同样不能放行");
    }

    @Test
    @DisplayName("查无此人：按无科室处理，绝不退回全院")
    void unmatchedMeansNoDept() {
        when(mapper.selectAuthorizedDeparts("ghost")).thenReturn(new ArrayList<Map<String, Object>>());
        when(mapper.selectAllDepartments()).thenReturn(
                Collections.singletonList(dept("20070131", "ICU-4U")));

        DepartScope scope = service.resolve("ghost");
        assertFalse(scope.isAdmin());
        assertFalse(scope.isMatched(), "查无此人时必须标记为未匹配");
        assertTrue(scope.getDeparts().isEmpty());

        BizException e = assertThrows(BizException.class, () -> service.resolveQueryDepart("ghost", null),
                "未匹配账号不能被偷偷放宽成全院可见");
        assertTrue(e.getMessage().contains("绑定"), "错误信息里要说明原因，便于现场自查");
    }

    @Test
    @DisplayName("越权：请求未授权的科室一律拦截")
    void deniesDeptNotGranted() {
        when(mapper.selectAuthorizedDeparts("103032")).thenReturn(
                Collections.singletonList(dept("20070131", "ICU-4U")));

        assertEquals("20070131", service.resolveQueryDepart("103032", "20070131"));
        assertThrows(BizException.class, () -> service.resolveQueryDepart("103032", "20010231"));
    }

    @Test
    @DisplayName("外链免登录：不套直连账号的科室边界")
    void externalAccessKeepsLegacyBehavior() {
        when(mapper.selectAllDepartments()).thenReturn(
                Collections.singletonList(dept("20070131", "ICU-4U")));

        DepartScope scope = service.resolve(null);
        assertFalse(scope.isAdmin());
        assertEquals(1, scope.getDeparts().size(), "外链也要给字典，否则下拉显示不了科室名称");

        assertEquals("20070131", service.resolveQueryDepart(null, "20070131"),
                "外链的科室参数必须原样透传 —— 边界由来源系统说了算");
        assertNull(service.resolveQueryDepart(null, null), "外链没带科室则仍是原来的全院口径");
    }

    @Test
    @DisplayName("名单解析：容忍中英文逗号、分号与空格")
    void toleratesLooseSeparators() {
        when(params.value(UserDepartScopeService.KEY_SUPER_USERS)).thenReturn("admin， zing；bob");

        assertTrue(service.resolve("zing").isAdmin());
        assertTrue(service.resolve("bob").isAdmin());
        assertFalse(service.resolve("carol").isAdmin());
    }

    @Test
    @DisplayName("名单未入库时回退 application.yml 的默认值")
    void fallsBackToConfiguredDefault() {
        when(params.value(UserDepartScopeService.KEY_SUPER_USERS)).thenReturn(null);

        assertTrue(service.resolve("admin").isAdmin());
        assertTrue(service.resolve("zing").isAdmin());
        assertFalse(service.resolve("someone").isAdmin());
    }

    @Test
    @DisplayName("停用科室不进可见范围：只靠 SQL 过滤不够，空结果要在Java侧再判一次")
    void allDepartmentsRetiredMeansNoDept() {
        when(mapper.selectAuthorizedDeparts("103032")).thenReturn(new ArrayList<Map<String, Object>>());

        DepartScope scope = service.resolve("103032");
        assertFalse(scope.isMatched(), "授权科室全部停用时，不该被当成有权限");
    }

    private static Map<String, Object> dept(String code, String name) {
        Map<String, Object> m = new HashMap<>(4);
        m.put("org_code", code);
        m.put("depart_name", name);
        return m;
    }
}
