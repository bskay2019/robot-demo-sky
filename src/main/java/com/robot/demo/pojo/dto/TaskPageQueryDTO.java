package com.robot.demo.pojo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class TaskPageQueryDTO {
    @Min(value = 1,message = "页码最少为1")
    private Integer pageNum=1;

    @Min(value = 1,message = "每页最少1条")
    @Max(value = 100,message = "每页最少100条")
    private Integer pageSize=10;

    /**
     * 可选：按状态过滤（0待分配 1执行中 2完成 3失败 4取消）
     */
    private Integer taskStatus;
    /**
     * 可选：按机器人编码过滤
     */
    private String robotCode;
    /**
     * 可选：任务单号模糊查询
     */
    private String taskNo;
}
