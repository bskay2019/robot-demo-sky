package com.robot.demo.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TaskTypeEnum {
    PICK(1, "取货"),
    DELIVER(2, "送货"),
    INVENTORY(3, "盘点");
    private final Integer code;
    private final String desc;

    public static String getDescByCode(Integer code) {
        if (code == null) {
            return "未知";
        }
        for (TaskTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e.desc;
            }
        }
        return "未知";
    }
}
