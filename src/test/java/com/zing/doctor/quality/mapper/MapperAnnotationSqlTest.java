package com.zing.doctor.quality.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.builder.xml.XMLMapperEntityResolver;
import org.apache.ibatis.parsing.XPathParser;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注解 SQL 的 XML 合法性检查（全项目 Mapper，防「启动即 502」复发）。
 *
 * <p><b>为什么需要它</b>：以 {@code <script>} 开头的 MyBatis 注解会被当作 XML 文档解析，
 * 里面任何未转义的 {@code <}（典型是把不等号写成 {@code <>}）都会让 Mapper Bean 建不出来，
 * 整个应用启动失败 —— 而<b>编译期毫无征兆</b>，只有启动那一刻才炸。
 *
 * <p>现场已经因为一处 {@code <>} 导致全站 502，报错是
 * 「元素内容必须由格式正确的字符数据或标记组成」（columnNumber 292）。
 * 这个信息完全不提「哪个 Mapper、哪条语句」，而应用又起不来、页面全 502，
 * 排查时很反直觉 —— 所以把检查前移到构建期。
 *
 * <p><b>为什么只查 {@code <script>} 开头的语句</b>：不以它开头的 SQL 走纯文本解析，
 * {@code <} 完全合法 —— 项目里 {@code selectYear} 就写了 {@code period_start < #{yearEnd}}，
 * 一直正常工作。按「见到 {@code <} 就报错」去查会制造大量误报。
 *
 * <p>本测试不连数据库、不启动 Spring，纯解析，可随时执行：
 * <pre>mvn test -Dtest=MapperAnnotationSqlTest</pre>
 */
class MapperAnnotationSqlTest {

    /** 测试的工作目录是项目根（Maven 约定） */
    private static final Path SRC_ROOT = Paths.get("src", "main", "java");

    @Test
    void scriptWrappedAnnotationSqlMustBeValidXml() throws Exception {
        List<Path> files = mapperFiles();
        assertTrue(!files.isEmpty(),
                "未扫描到任何 Mapper 源文件，请确认测试工作目录是项目根：" + SRC_ROOT.toAbsolutePath());

        List<String> failures = new ArrayList<>();
        int checked = 0;
        int scanned = 0;
        for (Path p : files) {
            Class<?> type = load(p);
            if (type == null) {
                continue;
            }
            scanned++;
            for (Method m : type.getDeclaredMethods()) {
                for (Annotation a : m.getAnnotations()) {
                    String script = scriptOf(a);
                    if (script == null || !script.startsWith("<script>")) {
                        continue;
                    }
                    checked++;
                    try {
                        // 与 MyBatis 启动时同一套解析：未转义的 < 会在这里抛出
                        new XPathParser(script, false, new Properties(), new XMLMapperEntityResolver())
                                .evalNode("/script");
                    } catch (Exception e) {
                        failures.add(type.getName() + "#" + m.getName() + "\n    " + e.getMessage());
                    }
                }
            }
        }

        assertTrue(scanned > 0,
                "一个 Mapper 类都没加载成功，说明包名推导或工作目录不对，本测试会因此变成永远通过");
        assertTrue(checked > 0,
                "一条 <script> 语句都没扫到，说明扫描逻辑失效了（本测试会因此变成永远通过）");
        assertTrue(failures.isEmpty(),
                "以下注解 SQL 会被 MyBatis 当作 XML 解析但格式不合法。\n"
                        + "最常见原因：SQL 里写了未转义的 '<'，例如把不等号写成 <>。\n"
                        + "改法：用 NOT (x = ?) 代替 x <> ?（语义等价且不含 XML 特殊字符）。\n\n"
                        + String.join("\n", failures));
    }

    /**
     * 扫描全项目 {@code mapper} 包下的接口源文件。
     *
     * <p>按路径取而不是写死列表：新增 Mapper 时不需要记得回来改测试，
     * 否则这个保护会随着时间悄悄失效（那比没有更糟 —— 会让人以为已经防住了）。
     */
    private List<Path> mapperFiles() throws Exception {
        try (Stream<Path> s = Files.walk(SRC_ROOT)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> p.toString().contains(File.separator + "mapper" + File.separator))
                    .collect(Collectors.toList());
        }
    }

    /** 由源文件路径推导全限定类名并加载；加载不了（依赖缺失等）返回 null 跳过。 */
    private Class<?> load(Path file) {
        String rel = SRC_ROOT.relativize(file).toString();
        String className = rel.substring(0, rel.length() - ".java".length())
                .replace(File.separatorChar, '.')
                .replace('/', '.');
        try {
            return Class.forName(className);
        } catch (Throwable ignore) {
            return null;
        }
    }

    /** 取 @Select / @Insert / @Update / @Delete 的 value() 并拼成完整 SQL。 */
    private String scriptOf(Annotation a) {
        String[] value;
        if (a instanceof Select) {
            value = ((Select) a).value();
        } else if (a instanceof Insert) {
            value = ((Insert) a).value();
        } else if (a instanceof Update) {
            value = ((Update) a).value();
        } else if (a instanceof Delete) {
            value = ((Delete) a).value();
        } else {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String v : value) {
            sb.append(v);
        }
        return sb.toString();
    }
}
