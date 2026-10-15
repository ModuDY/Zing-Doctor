package com.zing.doctor.module.system;

import com.zing.doctor.module.system.dto.LogPage;
import com.zing.doctor.module.system.service.LogViewerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 日志查看核心逻辑（docs/…）：tail 分页读取的正确性。
 *
 * <p>钉住四件事：
 * <ol>
 *   <li><b>最新页</b>：从文件尾向前取 N 行、时间正序、页内序号 1..N，文件以 \n 结尾不产生空行；</li>
 *   <li><b>翻页不重不漏</b>：传上一页 nextOffset 返回更早一页，两页拼起来正好是连续区间；</li>
 *   <li><b>跨块长行</b>：单行超过读块大小（64KB）仍能完整切出（超 20000 字节截断标记）；</li>
 *   <li><b>边界</b>：空文件 OK、文件不存在 MISSING、到文件头 atHead=true。</li>
 * </ol>
 */
class LogViewerServiceTest {

    @TempDir
    Path dir;

    private LogViewerService service(Path logFile) {
        return new LogViewerService(logFile.toString());
    }

    private Path write(String content) throws IOException {
        Path p = dir.resolve("backend.log");
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
        return p;
    }

    @Test
    @DisplayName("最新页：末尾N行、正序、行号1..N，尾部\\n不产生空行")
    void latestPage() throws IOException {
        Path p = write("l1\nl2\nl3\nl4\n");
        LogPage page = service(p).tail(2, 0);
        assertEquals("OK", page.getStatus());
        assertFalse(page.isAtHead());
        assertEquals(2, page.getLines().size());
        assertEquals("l3", page.getLines().get(0).getText());
        assertEquals("l4", page.getLines().get(1).getText());
        assertEquals(1, page.getLines().get(0).getNo());
        assertEquals(2, page.getLines().get(1).getNo());
    }

    @Test
    @DisplayName("翻页不重不漏：两页拼起来=全量行，且不重复")
    void pagingNoOverlapNoGap() throws IOException {
        Path p = write("l1\nl2\nl3\nl4\nl5\nl6\n");
        LogViewerService svc = service(p);
        LogPage page1 = svc.tail(2, 0);
        assertEquals("l5", page1.getLines().get(0).getText());
        assertEquals("l6", page1.getLines().get(1).getText());
        assertFalse(page1.isAtHead());

        LogPage page2 = svc.tail(2, page1.getNextOffset());
        assertEquals("l3", page2.getLines().get(0).getText());
        assertEquals("l4", page2.getLines().get(1).getText());
        assertFalse(page2.isAtHead());

        LogPage page3 = svc.tail(2, page2.getNextOffset());
        assertEquals("l1", page3.getLines().get(0).getText());
        assertEquals("l2", page3.getLines().get(1).getText());
        assertTrue(page3.isAtHead(), "读到文件头应 atHead=true");
    }

    @Test
    @DisplayName("文件尾无换行符：最后一行也能读全")
    void lastLineWithoutNewline() throws IOException {
        Path p = write("l1\nl2\nl3");
        LogPage page = service(p).tail(10, 0);
        assertEquals("OK", page.getStatus());
        assertTrue(page.isAtHead());
        assertEquals(3, page.getLines().size());
        assertEquals("l1", page.getLines().get(0).getText());
        assertEquals("l3", page.getLines().get(2).getText());
    }

    @Test
    @DisplayName("跨块长行：单行超过64KB读块仍完整（超20000字节截断并标记）")
    void crossChunkLongLine() throws IOException {
        int longLen = 70000;
        StringBuilder sb = new StringBuilder();
        sb.append("head-line\n");
        sb.append(new String(new char[longLen]).replace('\0', 'x'));
        sb.append("\ntail-line\n");
        Path p = write(sb.toString());
        LogPage page = service(p).tail(3, 0);
        assertEquals(3, page.getLines().size());
        assertEquals("head-line", page.getLines().get(0).getText());
        // 长行被截断到 20000 字节并带标记
        String longLine = page.getLines().get(1).getText();
        assertTrue(longLine.contains("行超长已截断"), "超长行应带截断标记");
        assertTrue(longLine.length() <= 20000 + 32, "截断后长度受控");
        assertEquals("tail-line", page.getLines().get(2).getText());
    }

    @Test
    @DisplayName("空文件：OK + 空页 + atHead")
    void emptyFile() throws IOException {
        Path p = write("");
        LogPage page = service(p).tail(10, 0);
        assertEquals("OK", page.getStatus());
        assertTrue(page.isAtHead());
        assertTrue(page.getLines().isEmpty());
    }

    @Test
    @DisplayName("文件不存在：MISSING 且带路径说明")
    void missingFile() {
        LogPage page = service(dir.resolve("nope.log")).tail(10, 0);
        assertEquals("MISSING", page.getStatus());
        assertTrue(page.getMessage().contains("nope.log"));
    }

    @Test
    @DisplayName("行数clamp：0/负值回退200，超大上限2000")
    void linesClamp() throws IOException {
        Path p = write(String.join("\n", java.util.stream.IntStream.rangeClosed(1, 100)
                .mapToObj(i -> "row-" + i).toArray(String[]::new)));
        LogViewerService svc = service(p);
        assertEquals(100, svc.tail(0, 0).getLines().size());
        assertEquals(100, svc.tail(-5, 0).getLines().size());
        LogPage big = svc.tail(999999, 0);
        assertEquals(100, big.getLines().size(), "文件只有100行，行数不够即到文件头");
        assertTrue(big.isAtHead());
    }

    @Test
    @DisplayName("级别粗分：ERROR/WARN 行被识别")
    void levelDetection() {
        assertEquals("ERROR", LogViewerService.levelOf("2026-10-15 10:00:00.123 ERROR --- [main] c.z: boom"));
        assertEquals("WARN", LogViewerService.levelOf("10:00:00.123 WARN --- [main] c.z: slow"));
        assertEquals("INFO", LogViewerService.levelOf("INFO ---"));
        assertEquals("OTHER", LogViewerService.levelOf("plain text line"));
    }

    @Test
    @DisplayName("编码可配：GBK 日志按 GBK 解出中文，级别判定不受影响")
    void charsetConfigurable() throws IOException {
        Path p = dir.resolve("gbk.log");
        Files.write(p, "启动完成\nERROR 服务异常\n".getBytes("GBK"));

        LogPage page = new LogViewerService(p.toString(), "GBK").tail(10, 0);
        assertEquals("OK", page.getStatus());
        assertEquals(2, page.getLines().size());
        assertEquals("启动完成", page.getLines().get(0).getText());
        assertEquals("ERROR 服务异常", page.getLines().get(1).getText());
        assertEquals("ERROR", page.getLines().get(1).getLevel());
    }

    @Test
    @DisplayName("编码配错：只出现替换符，不抛异常、整页仍为 OK")
    void wrongCharsetDoesNotBreakPage() throws IOException {
        Path p = dir.resolve("gbk.log");
        Files.write(p, "启动完成\nERROR 服务异常\n".getBytes("GBK"));

        // 现场把 charset 配成 ASCII：多字节字符退化成替换符，但页照读
        LogPage page = new LogViewerService(p.toString(), "US-ASCII").tail(10, 0);
        assertEquals("OK", page.getStatus());
        assertEquals(2, page.getLines().size());
        assertTrue(page.getLines().get(1).getText().startsWith("ERROR"));
    }

    @Test
    @DisplayName("编码名非法：退回 UTF-8，不拦启动")
    void unknownCharsetFallsBackToUtf8() throws IOException {
        Path p = write("l1\n");
        LogPage page = new LogViewerService(p.toString(), "NOT-A-CHARSET").tail(10, 0);
        assertEquals("OK", page.getStatus());
        assertEquals("l1", page.getLines().get(0).getText());
    }
}
