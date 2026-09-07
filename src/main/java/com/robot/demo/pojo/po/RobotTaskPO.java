package com.robot.demo.pojo.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("robot_task")
public class RobotTaskPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private String robotCode;
    private Integer taskType;
    private Integer taskStatus;
    private String targetPosition;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
