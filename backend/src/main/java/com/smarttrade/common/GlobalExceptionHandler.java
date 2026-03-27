package com.smarttrade.common;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

/**
 * 全局统一异常处理拦截
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 自定义业务异常 (可建立单独的 BusinessException)
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<String> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常: ", e);
        return Result.error(e.getMessage());
    }

    /**
     * `@Valid` 参数检验拦截
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidationException(MethodArgumentNotValidException e) {
        // 拿到其中一个错误信息反馈给前端
        String msg = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return Result.error(400, "参数校验失败: " + msg);
    }
}