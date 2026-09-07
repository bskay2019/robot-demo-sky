package com.robot.demo.config;

import com.robot.demo.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：所有 Controller 抛出的异常都在这里统一捕获
 * 小白解释：不用每个接口都 try-catch，出了异常自动走这里返回统一格式
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public Result<String> handleRuntimeException(RuntimeException e) {
        log.error("【全局异常】运行时异常：", e);
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        log.error("【全局异常】系统异常：", e);
        return Result.error("系统内部错误，请联系管理员");
    }
}
