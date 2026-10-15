package com.zing.doctor.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapper 方法名唯一性自检。
 *
 * <p>MyBatis 的语句 id 是「接口全名 + 方法名」，<b>不含参数类型</b>，因此 Mapper 接口
 * <b>不支持方法重载</b>：同名重载会撞 id，后注册的那条被静默丢弃。症状分两种，都很难查：
 * <ul>
 *   <li>参数名对不上：SQL 里的 {@code #{x}} 在另一个方法的参数里找不到，MyBatis 把 Map 取不到
 *       的键绑成 NULL —— 查询不报错、结果恒为空（已发生：{@code IcuPatientMapper}
 *       的两个 {@code selectDischargedPatients} 重载，启动日志只有一行
 *       {@code ERROR ... mapper[...] is ignored, because it exists, maybe from xml file}）；</li>
 *   <li>参数名恰好重名：静默执行了另一条查询，数据完全不对。</li>
 * </ul>
 *
 * <p>这里用反射扫一遍所有 {@code *Mapper} 接口，任何同名方法直接失败，把问题拦在编译后的单测阶段，
 * 而不是等现场从一行 ERROR 日志里去猜。
 */
class MapperMethodOverloadTest {

    @Test
    @DisplayName("所有 *Mapper 接口内不得有同名重载方法（MyBatis 语句 id 会撞）")
    void mapperInterfacesHaveNoOverloadedMethods() throws Exception {
        List<Class<?>> mappers = scanMapperInterfaces();
        assertTrue(mappers.size() > 20, "只扫到 " + mappers.size() + " 个 Mapper，扫描规则可能失效了");

        Map<String, List<String>> conflicts = new LinkedHashMap<>();
        for (Class<?> mapper : mappers) {
            Map<String, Integer> seen = new LinkedHashMap<>();
            for (Method m : mapper.getDeclaredMethods()) {
                if (canHaveStatement(m)) {
                    seen.merge(m.getName(), 1, Integer::sum);
                }
            }
            seen.forEach((name, count) -> {
                if (count > 1) {
                    conflicts.computeIfAbsent(mapper.getName(), k -> new ArrayList<>()).add(name + " x" + count);
                }
            });
        }
        assertTrue(conflicts.isEmpty(),
                "以下 Mapper 存在同名重载方法，MyBatis 会把后注册的语句静默丢弃：" + conflicts);
    }

    /**
     * 是否会注册成 MyBatis 语句，判定与 {@code MapperAnnotationBuilder#canHaveStatement} 一致：
     * {@code default} 方法不注册语句 id，因此「default 便捷重载 + 一个 @Select 方法」是安全写法
     * （本项目里 {@code selectAntibioticAdvice} / {@code selectVasopressorAdvice} 就是这种），
     * 不能算冲突；bridge 方法同理跳过。
     */
    private static boolean canHaveStatement(Method m) {
        return !m.isBridge() && !m.isDefault();
    }

    /** 扫 classpath 上所有以 Mapper 结尾的接口（不初始化类，避免副作用）。 */
    private List<Class<?>> scanMapperInterfaces() throws Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Resource[] resources = new PathMatchingResourcePatternResolver(cl)
                .getResources("classpath*:com/zing/doctor/**/*Mapper.class");
        List<Class<?>> out = new ArrayList<>();
        for (Resource r : resources) {
            String path = r.getURL().getPath();
            int i = path.indexOf("com/zing/doctor");
            if (i < 0) {
                continue;
            }
            String name = path.substring(i, path.length() - ".class".length()).replace('/', '.');
            Class<?> c = Class.forName(name, false, cl);
            if (c.isInterface() && c.getSimpleName().endsWith("Mapper")) {
                out.add(c);
            }
        }
        out.sort((a, b) -> a.getName().compareTo(b.getName()));
        return out;
    }
}
