package com.zing.doctor.module.system.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志查看一页数据。
 *
 * <p>翻页模型：每次从文件末尾（或上一页返回的 {@link #nextOffset}）向前读一页，
 * 本页返回的 {@code nextOffset} 即「更早一页」的起点，读到文件头为止（{@link #atHead}=true）。
 * 日志文件是外部 nohup 重定向追加写的，每次请求都重新打开文件，
 * 游标越界（文件被滚动/截断）时自动回退到文件末尾，保证永远能读到内容。
 */
@Data
public class LogPage {

    /** 状态：OK=正常；MISSING=文件不存在；UNREADABLE=存在但不可读/读取异常 */
    private String status = "OK";

    /** 状态补充说明（文件不存在时给完整路径，方便现场排查） */
    private String message;

    /** 配置的日志文件路径（展示用） */
    private String file;

    /** 文件当前字节数 */
    private long size;

    /** 文件最后修改时间 yyyy-MM-dd HH:mm:ss */
    private String modified;

    /** 本页最老一行的行首字节偏移；翻「更早」时作为 offset 传入。atHead 时无意义 */
    private long nextOffset;

    /** 是否已读到文件头（没有更早记录） */
    private boolean atHead;

    /** 文件末尾字节偏移（=size），前端据此判断「是否停在最新」 */
    private long tailOffset;

    /** 本页行（时间正序，即文件里从早到晚的顺序） */
    private List<LogLine> lines = new ArrayList<>();

    @Data
    public static class LogLine {
        /** 页内序号（1 起） */
        private int no;
        /** 行文本（超长行截断到 20000 字符） */
        private String text;
        /** 粗分级别：ERROR / WARN / INFO / DEBUG / TRACE / OTHER，用于前端着色 */
        private String level;
    }
}
