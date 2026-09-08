package com.robot.demo.pojo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建任务请求体（只收前端该传的字段，不暴露 id/taskNo 等）
 */
@Data
public class TaskCreateDTO {

    /**
     * 任务类型：1取货 2送货 3盘点
     */
    @NotNull(message = "任务类型不能为空")
    @Min(value = 1, message = "任务类型只能是 1/2/3")
    @Max(value = 3, message = "任务类型只能是 1/2/3")
    private Integer taskType;

    /**
     * 目标位置
     */
    @NotBlank(message = "目标位置不能为空")
    private String targetPosition;

    /**
     * 备注（可选）
     */
    private String remark;
}