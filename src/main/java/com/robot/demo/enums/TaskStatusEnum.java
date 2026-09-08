package com.robot.demo.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务状态
 */
@Getter
@AllArgsConstructor
public enum TaskStatusEnum {
    PENDING(0, "待分配"),
    EXECUTING(1, "执行中"),
    COMPLETED(2, "已完成"),
    FAILED(3, "失败"),
    CANCELLED(4, "已取消");

    private final Integer code;
    private final String desc;
}