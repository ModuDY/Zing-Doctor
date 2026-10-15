package com.zing.doctor.module.department.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 驾驶舱 Mapper 的 SQL 静态自检。
 *
 * <p>背景：{@code @Select} 里的 SQL 是几十个字符串片段拼出来的，<b>括号不闭合编译期看不出来、
 * 启动也不报错</b>，只有真跑到那条查询才抛
 * {@code BadSqlGrammarException}（达梦的报错还只给「第 N 列 [AS] 附近出现错误」，看不出是少括号）。
 * 现场表现就是页面上某个区块永远显示「查询失败」，非常难定位 —— 已发生过一次
 * （{@code selectBedOverview} 的标量子查询少一个右括号，导致「当前床位使用率」长期不可用）。
 *
 * <p>这里只做一件很笨但很值的事：把注解里的 SQL 片段拼起来、去掉动态标签与 {@code #{}} 占位符，
 * 验证括号配平。它不校验语义，但能挡住「写 SQL 时漏个括号」这一类只在运行时暴露的故障。
 */
class DepartmentCockpitMapperSqlTest {

    @Test
    @DisplayName("驾驶舱所有 @Select 的 SQL 括号配平")
    void selectSqlParenthesesAreBalanced() {
        int checked = 0;
        for (Method method : DepartmentCockpitMapper.class.getDeclaredMethods()) {
            Select select = method.getAnnotation(Select.class);
            if (select == null) {
                continue;
            }
            checked++;
            assertBalanced(method.getName(), String.join(" ", select.value()));
        }
        assertTrue(checked > 0, "没有扫到任何 @Select，测试本身失效了");
    }

    private void assertBalanced(String methodName, String rawSql) {
        // 占位符与动态标签本身不含括号，先抹掉，避免它们的写法影响计数
        String sql = rawSql.replaceAll("#\\{[^}]*}", "?").replaceAll("<[^>]+>", " ");
        int depth = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                assertTrue(depth >= 0, methodName + " 出现多余的右括号：" + sql);
            }
        }
        assertEquals(0, depth, methodName + " 的 SQL 括号不闭合，少 " + depth + " 个右括号：" + sql);
    }
}
