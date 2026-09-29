package com.zing.doctor;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring MVC 路由重复检查（防「启动即 Ambiguous mapping」复发）。
 *
 * <p><b>为什么需要它</b>：两个方法映射到同一个 {@code HTTP 方法 + 路径} 时，
 * Spring 在 {@code requestMappingHandlerMapping} 初始化阶段抛
 * {@code Ambiguous mapping} 并中止启动 —— 而<b>编译期毫无征兆</b>。
 * 现场踩过一次：新增 {@code GET /api/quality/metric/patients} 时，
 * 该路径其实早已存在，应用直接起不来、前端全站 502，
 * 报错信息只出现在服务器日志里。
 *
 * <p><b>误报防护</b>：Spring 允许同一路径靠 {@code params / headers / consumes / produces}
 * 区分多个映射。为了让本检查不阻断正常开发，只要参与冲突的任一方带上了这类条件，
 * 就不判为冲突（宁可漏报，也不要因为误报让人把检查关掉）。
 *
 * <p>本测试不启动 Spring、不连数据库，纯反射：
 * <pre>mvn test -Dtest=SpringMvcMappingTest</pre>
 */
class SpringMvcMappingTest {

    /** 测试的工作目录是项目根（Maven 约定） */
    private static final Path SRC_ROOT = Paths.get("src", "main", "java");

    @Test
    void noDuplicateRequestMapping() throws Exception {
        Map<String, String> seen = new LinkedHashMap<>();
        List<String> duplicates = new ArrayList<>();
        int controllers = 0;
        int endpoints = 0;

        for (Path file : javaFiles()) {
            Class<?> type = load(file);
            if (type == null || !isController(type)) {
                continue;
            }
            controllers++;
            RequestMapping classMapping = type.getAnnotation(RequestMapping.class);
            String base = classMapping == null ? "" : first(classMapping.value(), classMapping.path());

            for (Method m : type.getDeclaredMethods()) {
                for (MappingSpec spec : specsOf(m, classMapping)) {
                    endpoints++;
                    String key = spec.method + " " + join(base, spec.path);
                    String where = type.getSimpleName() + "#" + m.getName();
                    String prev = seen.put(key, where);
                    if (prev != null && !spec.conditional) {
                        duplicates.add(key + "\n      已存在: " + prev + "\n      重复写: " + where);
                    }
                }
            }
        }

        assertTrue(controllers > 0,
                "一个 Controller 都没扫到，说明扫描逻辑失效了（本测试会因此变成永远通过）");
        assertTrue(endpoints > 0, "一个端点都没扫到，说明注解解析失效了");
        assertTrue(duplicates.isEmpty(),
                "以下路由重复，会导致 Spring 启动失败（Ambiguous mapping）并让全站 502：\n\n"
                        + String.join("\n\n", duplicates)
                        + "\n\n改法：复用已有的方法（在其入参上扩展），而不是新增一个同路径的方法。");
    }

    // ------------------------------------------------------------------

    private List<Path> javaFiles() throws Exception {
        try (Stream<Path> s = Files.walk(SRC_ROOT)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }
    }

    private Class<?> load(Path file) {
        String rel = SRC_ROOT.relativize(file).toString();
        String className = rel.substring(0, rel.length() - ".java".length())
                .replace(File.separatorChar, '.').replace('/', '.');
        try {
            return Class.forName(className);
        } catch (Throwable ignore) {
            return null;
        }
    }

    private boolean isController(Class<?> t) {
        return t.isAnnotationPresent(RestController.class) || t.isAnnotationPresent(Controller.class);
    }

    /** 一个方法可能同时挂多个 mapping 注解（少见但合法），逐个收集。 */
    private List<MappingSpec> specsOf(Method m, RequestMapping classMapping) {
        List<MappingSpec> out = new ArrayList<>();
        for (Annotation a : m.getAnnotations()) {
            if (a instanceof GetMapping) {
                GetMapping g = (GetMapping) a;
                addSpecs(out, "GET", g.value(), g.path(), g.params(), g.headers(), g.consumes(), g.produces());
            } else if (a instanceof PostMapping) {
                PostMapping g = (PostMapping) a;
                addSpecs(out, "POST", g.value(), g.path(), g.params(), g.headers(), g.consumes(), g.produces());
            } else if (a instanceof PutMapping) {
                PutMapping g = (PutMapping) a;
                addSpecs(out, "PUT", g.value(), g.path(), g.params(), g.headers(), g.consumes(), g.produces());
            } else if (a instanceof DeleteMapping) {
                DeleteMapping g = (DeleteMapping) a;
                addSpecs(out, "DELETE", g.value(), g.path(), g.params(), g.headers(), g.consumes(), g.produces());
            } else if (a instanceof PatchMapping) {
                PatchMapping g = (PatchMapping) a;
                addSpecs(out, "PATCH", g.value(), g.path(), g.params(), g.headers(), g.consumes(), g.produces());
            } else if (a instanceof RequestMapping) {
                RequestMapping g = (RequestMapping) a;
                // 类级 @RequestMapping 的 method 作为方法级的默认（两者都没写时按 GET 计）
                RequestMethod[] methods = g.method().length > 0 ? g.method()
                        : (classMapping != null && classMapping.method().length > 0
                        ? classMapping.method() : new RequestMethod[]{RequestMethod.GET});
                for (RequestMethod rm : methods) {
                    addSpecs(out, rm.name(), g.value(), g.path(), g.params(), g.headers(),
                            g.consumes(), g.produces());
                }
            }
        }
        return out;
    }

    private void addSpecs(List<MappingSpec> out, String httpMethod, String[] value, String[] path,
                          String[] params, String[] headers, String[] consumes, String[] produces) {
        String[] paths = value.length > 0 ? value : path;
        boolean conditional = params.length > 0 || headers.length > 0
                || consumes.length > 0 || produces.length > 0;
        if (paths.length == 0) {
            out.add(new MappingSpec(httpMethod, "", conditional));
            return;
        }
        for (String p : paths) {
            out.add(new MappingSpec(httpMethod, p, conditional));
        }
    }

    private String first(String[] value, String[] path) {
        String[] arr = value.length > 0 ? value : path;
        return arr.length == 0 ? "" : arr[0];
    }

    /** 拼接类级与方法级路径，规整多余的斜杠。 */
    private String join(String base, String path) {
        String b = base == null ? "" : base.trim();
        String p = path == null ? "" : path.trim();
        String full = b + "/" + p;
        full = full.replaceAll("/{2,}", "/");
        if (full.length() > 1 && full.endsWith("/")) {
            full = full.substring(0, full.length() - 1);
        }
        return full.isEmpty() ? "/" : full;
    }

    private static final class MappingSpec {
        final String method;
        final String path;
        /** 带 params/headers 等条件，可与同路径的其他映射共存，不参与冲突判定 */
        final boolean conditional;

        MappingSpec(String method, String path, boolean conditional) {
            this.method = method;
            this.path = path;
            this.conditional = conditional;
        }
    }
}
