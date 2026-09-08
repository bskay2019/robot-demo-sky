package com.robot.demo.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 机器人状态
 */
@Getter
@AllArgsConstructor
public enum RobotStatusEnum {
    IDLE(0, "空闲"),
    WORKING(1, "工作中"),
    FAULT(2, "故障");

    private final Integer code;
    private final String desc;
}