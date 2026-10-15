package com.zing.doctor.module.system.service;

import com.zing.doctor.module.system.dto.LogPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 后端日志查看：从部署目录 {@code logs/backend.log}（nohup 重定向追加）按页向前读。
 *
 * <p><b>性能设计</b>：日志文件可能上百 MB，绝不整文件读取。
 * 用 {@link RandomAccessFile} 从游标位置向前读 64KB 块、在块内按 {@code \n} 切行，
 * 单页只触碰「末尾 N 行」对应的那一小段字节；翻页同理，只再向前读一页。
 * 游标是字节偏移，天然支持日志滚动（每次请求重新打开文件，游标越界自动回退文件末尾）。
 *
 * <p><b>翻页模型</b>：最新页从文件末尾向前读 N 行；本页最老行的行首偏移记为
 * {@code nextOffset}（= 从文件尾向前数 N 个行尾换行后的下一个换行 +1，推演见
 * {@link #oldestLineStart}）。翻「更早」时传该偏移，内部从 {@code offset-1} 继续向前，
 * 保证与上一页不重叠、不遗漏。
 *
 * <p><b>正确性细节</b>：
 * <ul>
 *   <li>跨块长行：行可能横跨相邻读块，用 {@code pendingSuffix} 把「更晚块读到的行尾片段」
 *       拼到「更早块切出的行头」之后，保证长行完整（如异常堆栈）；</li>
 *   <li>文件尾部若以 {@code \n} 结尾（logback 追加的常态），跳过末尾空段，不产生空行；</li>
 *   <li>单行超过 20000 字节截断并标记，避免个别超长 SQL 把页面撑爆。</li>
 * </ul>
 */
@Slf4j
@Service
public class LogViewerService {

    /** 每次向后（文件头方向）读取的块大小 */
    static final int CHUNK = 64 * 1024;

    /** 单行最大字节数，超过截断 */
    static final int MAX_LINE_BYTES = 20000;

    /** 单页行数上限（controller 也会 clamp，这里兜底） */
    static final int MAX_LINES = 2000;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final Pattern LEVEL_PATTERN = Pattern.compile("\\b(ERROR|WARN|INFO|DEBUG|TRACE)\\b");

    private final String logFile;

    /**
     * 日志文件的字符编码。
     *
     * <p>日志是 shell / systemd 重定向出来的 JVM stdout 字节，实际编码由服务器 locale 决定
     * （Java 8 的 stdout 用平台编码）。所以这里默认 UTF-8 但必须可配：现场若是 GBK，
     * 或 locale 退化成了 ASCII，硬编 UTF-8 会<b>静默乱码</b> —— 页面看着「有日志」，中文全是问号。
     */
    private final Charset charset;

    /** 生产注入用：两个构造器并存，必须显式指明 Spring 走这一个。 */
    @Autowired
    public LogViewerService(@Value("${zing.log-viewer.file:logs/backend.log}") String logFile,
                            @Value("${zing.log-viewer.charset:UTF-8}") String charsetName) {
        this.logFile = logFile;
        this.charset = resolveCharset(charsetName);
    }

    /** 便捷构造：不关心编码时用默认 UTF-8（单测与现场排查脚本用）。 */
    public LogViewerService(String logFile) {
        this(logFile, null);
    }

    private static Charset resolveCharset(String name) {
        if (name == null || name.trim().isEmpty()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(name.trim());
        } catch (Exception e) {
            // 配错编码不能拦启动：退回 UTF-8，页面照样能用
            log.warn("zing.log-viewer.charset 无法识别，已回退 UTF-8: {}", name);
            return StandardCharsets.UTF_8;
        }
    }

    /**
     * 从日志尾部（或游标）向前读一页。
     *
     * @param lines  本页行数，clamp 到 [1, 2000]，默认 200
     * @param offset 翻页游标：0 表示从文件末尾（最新页）；否则为该页最老行的行首偏移
     */
    public LogPage tail(int lines, long offset) {
        int want = Math.max(1, Math.min(lines <= 0 ? 200 : lines, MAX_LINES));
        Path path = Paths.get(logFile);
        LogPage page = new LogPage();
        page.setFile(path.toAbsolutePath().toString());
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            page.setStatus("MISSING");
            page.setMessage("日志文件不存在：" + path.toAbsolutePath());
            return page;
        }
        if (!Files.isReadable(path)) {
            page.setStatus("UNREADABLE");
            page.setMessage("日志文件不可读：" + path.toAbsolutePath());
            return page;
        }
        try {
            long size = Files.size(path);
            page.setSize(size);
            page.setTailOffset(size);
            page.setModified(formatTime(Files.getLastModifiedTime(path)));
            if (size == 0) {
                page.setStatus("OK");
                page.setAtHead(true);
                return page;
            }
            long cursor = (offset > 0 && offset < size) ? offset - 1 : size;
            BackReadResult result = readTailBackwards(path, cursor, want);
            page.setStatus("OK");
            page.setLines(result.rows);
            if (!result.rows.isEmpty()) {
                // 最老行行首 == 0 说明它从文件头开始，没有更早的页
                page.setAtHead(result.oldestStart == 0);
                if (result.oldestStart > 0) {
                    page.setNextOffset(result.oldestStart);
                }
            } else {
                page.setAtHead(true);
            }
            return page;
        } catch (IOException e) {
            log.warn("读取日志失败: file={}", path.toAbsolutePath(), e);
            page.setStatus("UNREADABLE");
            page.setMessage("日志读取失败：" + e.getMessage());
            return page;
        }
    }

    /** 一次反向扫描的结果：行（倒序收集后已转正序）+ 最老行的行首偏移。 */
    private static class BackReadResult {
        final List<LogPage.LogLine> rows;
        /** 最老行（本页第一行）在文件中的行首字节偏移；0 表示该行从文件头开始（已到顶） */
        final long oldestStart;

        BackReadResult(List<LogPage.LogLine> rows, long oldestStart) {
            this.rows = rows;
            this.oldestStart = oldestStart;
        }
    }

    /**
     * 从 {@code endOffset} 向前收集至多 {@code want} 行（倒序收集，最后反转成正序），
     * 并记录本页最老行的行首偏移（= 最后 add 的那一行的行首）。
     *
     * <p>块内切行规则：从块尾向前找 {@code \n}，每找到一个即切出
     * {@code [\n+1, lineEnd)} 一段；切出的段与更晚块遗留的 {@code pendingSuffix}
     * 拼接成完整行，行首 = 该 {@code \n} + 1。块头到最后一个 {@code \n} 之间的未闭合段
     * （一行只有尾部的部分）并入 {@code pendingSuffix}，留给更早的块补全其头部；
     * 补头行必然位于文件头（只有扫描到文件头行数仍不够时才会出现），行首 = 0。
     */
    private BackReadResult readTailBackwards(Path path, long endOffset, int want) throws IOException {
        List<LogPage.LogLine> reversed = new ArrayList<>();
        long oldestStart = 0;
        long pos = endOffset;
        byte[] pendingSuffix = new byte[0];
        boolean firstBlock = true;
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            while (reversed.size() < want && pos > 0) {
                long start = Math.max(0, pos - CHUNK);
                int len = (int) (pos - start);
                byte[] buf = new byte[len];
                raf.seek(start);
                raf.readFully(buf);

                int lineEnd = len;
                int i = len - 1;
                while (i >= 0 && reversed.size() < want) {
                    if (buf[i] == '\n') {
                        byte[] seg = Arrays.copyOfRange(buf, i + 1, lineEnd);
                        boolean trailingEmpty = firstBlock && lineEnd == len && seg.length == 0;
                        lineEnd = i;
                        if (!trailingEmpty) {
                            byte[] full = concat(seg, pendingSuffix);
                            pendingSuffix = new byte[0];
                            reversed.add(line(decoded(full)));
                            // 行首绝对偏移为 0 的那个换行 = 文件第一个字节就是换行（Boot banner 前的
                            // 前导空行），它前面只有一个空行、没有更早的内容。此时若仍记 oldestStart=1，
                            // 整文件已在一页里读完却会报 atHead=false、nextOffset=1，前端一直提示
                            // 「还有更早的日志」，点一次才拿到一个空页 —— 必须按「已到顶」处理。
                            oldestStart = (start + i == 0) ? 0 : start + i + 1;
                        } else {
                            pendingSuffix = new byte[0];
                        }
                    }
                    i--;
                }
                // 块头到最后一个 \n 之间的未闭合段：它是某个完整行的尾部，头部在更早的块里
                if (reversed.size() < want && lineEnd > 0) {
                    pendingSuffix = concat(Arrays.copyOfRange(buf, 0, lineEnd), pendingSuffix);
                }
                firstBlock = false;
                pos = start;
            }
            if (pendingSuffix.length > 0) {
                // 文件头第一行（文件头可能不是 \n 开头）；行首即文件头 = 0
                reversed.add(line(decoded(pendingSuffix)));
                oldestStart = 0;
            }
        }
        Collections.reverse(reversed);
        for (int n = 0; n < reversed.size(); n++) {
            reversed.get(n).setNo(n + 1);
        }
        return new BackReadResult(reversed, oldestStart);
    }

    /** 非 static：解码要按实例上配置的 {@link #charset} 走。 */
    private LogPage.LogLine line(String text) {
        LogPage.LogLine l = new LogPage.LogLine();
        l.setText(text);
        l.setLevel(levelOf(text));
        return l;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        if (b.length == 0) {
            return a;
        }
        if (a.length + b.length > MAX_LINE_BYTES) {
            int keep = MAX_LINE_BYTES - b.length;
            if (keep <= 0) {
                return Arrays.copyOf(b, MAX_LINE_BYTES);
            }
            byte[] out = new byte[keep + b.length];
            System.arraycopy(a, 0, out, 0, keep);
            System.arraycopy(b, 0, out, keep, b.length);
            return out;
        }
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /**
     * 按配置的编码解码一行。
     *
     * <p>{@code new String(bytes, charset)} 对非法字节用替换符处理，不会抛异常 ——
     * 截断处正好落在多字节字符中间、或现场编码配错了，都只会让个别字符变成 {@code ?}，
     * 不会把整页日志变成一条错误信息。
     */
    private String decoded(byte[] bytes) {
        if (bytes.length >= MAX_LINE_BYTES) {
            return new String(bytes, 0, MAX_LINE_BYTES, charset) + " …[行超长已截断]";
        }
        return new String(bytes, charset);
    }

    /** 粗分级别的行文本。 */
    public static String levelOf(String text) {
        if (text == null) {
            return "OTHER";
        }
        Matcher m = LEVEL_PATTERN.matcher(text);
        return m.find() ? m.group(1) : "OTHER";
    }

    private static String formatTime(FileTime fileTime) {
        return TIME_FMT.format(fileTime.toInstant());
    }
}
