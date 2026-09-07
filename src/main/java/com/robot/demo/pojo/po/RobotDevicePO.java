package com.robot.demo.pojo.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("robot_device")
public class RobotDevicePO {
    @JsonProperty("id")
    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonProperty("robotCode")
    private String robotCode;

    @JsonProperty("robotName")
    private String robotName;

    @JsonProperty("status")
    private Integer status;
    private String position;
    private Integer battery;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
