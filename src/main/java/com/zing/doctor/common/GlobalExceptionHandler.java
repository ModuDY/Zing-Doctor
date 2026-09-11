package com.zing.doctor.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
        String detail = root.getMessage() == null ? "" : root.getMessage();
        String hint = (detail.contains("LocalDateTime") || detail.contains("yyyy-MM-dd") || detail.contains("DateTimeParse"))
                ? "提交的时间格式有误，应为 yyyy-MM-dd HH:mm:ss，请刷新页面后重试"
                : "提交的数据格式有误，请检查输入后重试";
        return Result.fail(400, hint);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(500, "系统繁忙，请稍后重试");
    }
}
