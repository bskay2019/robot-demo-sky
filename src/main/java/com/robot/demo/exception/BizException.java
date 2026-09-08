package com.robot.demo.exception;

import lombok.Getter;
/**
 * 业务异常：可预期的业务错误（如没有空闲机器人）
 */
@Getter
public class BizException extends RuntimeException {
    private final Integer code;
    public BizException(String message) {
        super(message);
        this.code = 400;
    }
    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}