package com.robot.demo.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TaskType {
    PICK(1, "取货"),
    DELIVER(2, "送货"),
    INVENTORY(3, "盘点");
    private final Integer code;
    private final String desc;
}
