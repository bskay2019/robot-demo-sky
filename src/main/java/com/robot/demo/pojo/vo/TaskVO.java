package com.robot.demo.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务对外返回对象（可带状态中文名，方便前端展示）
 */
@Data
public class TaskVO {
    private Long id;
    private String taskNo;
    private String robotCode;
    private Integer taskType;
    private String taskTypeName;
    private Integer taskStatus;
    private String taskStatusName;
    private String targetPosition;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}