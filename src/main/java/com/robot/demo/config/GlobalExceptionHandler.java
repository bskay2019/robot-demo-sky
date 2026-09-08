package com.robot.demo.config;

import com.robot.demo.exception.BizException;
import com.robot.demo.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BizException.class)
    public Result<String> handleBizException(BizException e) {
        log.warn("【业务异常】{}", e.getMessage());
        Result<String> result = Result.error(e.getMessage());
        result.setCode(e.getCode());
        return result;
    }
    @ExceptionHandler(RuntimeException.class)
    public Result<String> handleRuntimeException(RuntimeException e) {
        log.error("【运行时异常】", e);
        return Result.error(e.getMessage());
    }
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        log.error("【系统异常】", e);
        return Result.error("系统内部错误，请联系管理员");
    }

    /**
     * @RequestBody + @Valid 校验失败
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        log.warn("【参数校验失败】{}", msg);
        Result<String> result = Result.error(msg);
        result.setCode(400);
        return result;
    }
    /**
     * 表单/Query 参数校验失败（备用）
     */
    @ExceptionHandler(BindException.class)
    public Result<String> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        log.warn("【参数绑定失败】{}", msg);
        Result<String> result = Result.error(msg);
        result.setCode(400);
        return result;
    }
}
