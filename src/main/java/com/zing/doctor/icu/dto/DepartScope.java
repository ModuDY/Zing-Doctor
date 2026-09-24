package com.zing.doctor.icu.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前访问者在患者工作台上的<b>科室可见范围</b>。
 *
 * <p>工作台读的是重症系统多个科室的数据，但本系统此前没有任何依据去判断
 * 「这条数据该不该让这个人看到」—— 本库没有角色表（不存在 sys_role /
 * sys_user_role），所以长期处于不受控状态。本对象把这个结论显式化，
 * 交由前端据此决定「能不能看、要不要先选科室」。
 *
 * <p>字段名的下划线写法（org_code / depart_name）是为了让前端科室下拉
 * 与其它页面（质控看板、DDD、MDRO）保持完全一致，省一层转换。
 */
@Data
public class DepartScope {

    /** 当前账号名；外链免登录访问时为 {@code null} —— 此时不套直连账号的科室边界 */
    private String username;

    /** 是否管理员：豁免科室限制，可查看全部科室 */
    private boolean admin;

    /**
     * 是否在重症侧匹配到该账号。
     *
     * <p>{@code false} 表示「查无此人」或「其科室全部已停用」，按约定按<b>无科室</b>
     * 处理。这是刻意的选择：宁可让使用者看到一个明确的提示并报过来，也不能让一个
     * 未在重症侧登记的账号看全科数据。
     */
    private boolean matched;

    /** 可选科室列表；管理员为重症侧全部在用科室 */
    private List<DepartOption> departs = new ArrayList<>();

    /** 面向使用者的说明：为什么是空的、该怎么处理 */
    private String message;

    @Data
    public static class DepartOption {
        @JsonProperty("org_code")
        private String orgCode;

        @JsonProperty("depart_name")
        private String departName;
    }
}
