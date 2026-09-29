package com.zing.doctor.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValid(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().isEmpty()
                ? "参数校验失败"
                : e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return Result.fail(400, msg);
    }

    /**
     * 缺少必填请求参数（如定时探针/外部系统调用时漏传 pageCode）。
     *
     * <p>这是调用方的问题，不是系统故障：落到兜底 Exception 会被记成 ERROR 并打满堆栈，
     * 定时探针每隔几十秒来一次就把日志灌满、掩盖真实故障。这里降级为 WARN 并返回 400。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数 {}: {}", e.getParameterName(), e.getMessage());
        return Result.fail(400, "缺少必填参数：" + e.getParameterName());
    }

    /**
     * 请求体反序列化失败（如时间字段不是 yyyy-MM-dd HH:mm:ss）。
     * 该异常发生在进入 Controller 方法体之前，Controller 自身 try-catch 拦不到，
     * 若落到下面的兜底 Exception 会只返回“系统繁忙”，掩盖真实原因，这里单独给出可定位的提示。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        // 判断必须同时看「异常链顶层」和「根因」：
        // Jackson 顶层消息才带类型与字段名（Cannot deserialize value of type java.time.LocalDateTime ...），
        // 而根因是 DateTimeParseException，消息只有 "Text '2026-09-16 14:30:00' could not be parsed at index 10"，
        // 既没有类型名也没有类名 —— 只看根因会把时间格式错误误报成笼统的「数据格式有误」。
        String detail = (e.getMessage() == null ? "" : e.getMessage())
                + " | " + (root.getMessage() == null ? "" : root.getMessage());
        String hint = (detail.contains("LocalDateTime") || detail.contains("LocalDate") || detail.contains("yyyy-MM-dd")
                || detail.contains("DateTimeParse") || detail.contains("could not be parsed"))
                ? "提交的时间格式有误，应为 yyyy-MM-dd HH:mm:ss，请刷新页面后重试"
                : "提交的数据格式有误，请检查输入后重试";
        return Result.fail(400, hint);
    }

    /**
     * 数据库访问异常（SQL 语法/表字段不存在/类型转换失败/数据源连接失败等）。
     *
     * <p>这类异常的根因几乎总在 SQL 或表结构上（达梦尤其常见：列名不存在、
     * CAST 自由文本失败、CLOB 超长），但落到下面的兜底 Exception 只会返回
     * “系统繁忙，请稍后重试”，与代码 NPE 完全无法区分，排障必须翻服务端日志。
     * 这里单独给出可定位的提示，并把根因（含 SQL 概要）打到日志。
     */
    @ExceptionHandler(DataAccessException.class)
    public Result<Void> handleDataAccess(DataAccessException e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        log.error("数据库访问异常，根因: {}", root.getMessage(), e);
        return Result.fail(500, "数据查询失败，请稍后重试（已记录服务端日志）");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        log.error("系统异常，根因 {}: {}", root.getClass().getName(), root.getMessage(), e);
        return Result.fail(500, "系统繁忙，请稍后重试" + rootHint(root));
    }

    /**
     * 是否在 500 文案后附带根因摘要。
     *
     * <p>默认开启：兜底异常原先只有一句“系统繁忙”，无法与代码 NPE、SQL 异常区分，
     * 不翻服务端日志就完全无从定位。排查期先打开，定位完成后把
     * {@code zing.doctor.debug.expose-error-detail} 设为 false 即可关闭。
     * 附带内容仅为异常类名 + message（已截断），不含堆栈与患者数据。
     */
    @Value("${zing.doctor.debug.expose-error-detail:true}")
    private boolean exposeErrorDetail;

    private String rootHint(Throwable root) {
        if (!exposeErrorDetail) {
            return "";
        }
        String msg = root.getMessage();
        if (msg != null && msg.length() > 120) {
            msg = msg.substring(0, 120) + "…";
        }
        return "【" + root.getClass().getSimpleName()
                + (msg == null || msg.isEmpty() ? "" : ": " + msg) + "】";
    }
}
