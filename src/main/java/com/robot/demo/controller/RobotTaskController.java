package com.robot.demo.controller;

import com.robot.demo.pojo.po.RobotTaskPO;
import com.robot.demo.service.RobotTaskService;
import com.robot.demo.util.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 机器人任务接口
 */
@RestController
@RequestMapping("/api/task")
public class RobotTaskController {

    private final RobotTaskService robotTaskService;

    public RobotTaskController(RobotTaskService robotTaskService) {
        this.robotTaskService = robotTaskService;
    }

    /**
     * 创建任务（核心接口：分配机器人 + 发MQ）
     */
    @PostMapping("/create")
    public Result<RobotTaskPO> create(@RequestBody RobotTaskPO task) {
        return Result.success(robotTaskService.createTask(task));
    }

    /**
     * 查询所有任务
     */
    @GetMapping("/list")
    public Result<List<RobotTaskPO>> list() {
        return Result.success(robotTaskService.list());
    }

    /**
     * 根据任务单号查询任务状态
     */
    @GetMapping("/{taskNo}")
    public Result<RobotTaskPO> getByTaskNo(@PathVariable String taskNo) {
        return Result.success(
                robotTaskService.lambdaQuery()
                        .eq(RobotTaskPO::getTaskNo, taskNo)
                        .one()
        );
    }
}
