package com.robot.demo.controller;

import com.robot.demo.pojo.dto.TaskCreateDTO;
import com.robot.demo.pojo.dto.TaskPageQueryDTO;
import com.robot.demo.pojo.po.RobotTaskPO;
import com.robot.demo.pojo.vo.PageResult;
import com.robot.demo.pojo.vo.TaskVO;
import com.robot.demo.service.RobotTaskService;
import com.robot.demo.util.Result;
import com.robot.demo.util.TaskConvert;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/task")
public class RobotTaskController {

    private final RobotTaskService robotTaskService;

    public RobotTaskController(RobotTaskService robotTaskService) {
        this.robotTaskService = robotTaskService;
    }

    /**
     * 创建任务
     */
    @PostMapping("/create")
    public Result<TaskVO> create(@Valid @RequestBody TaskCreateDTO dto) {
        return Result.success(robotTaskService.createTask(dto));
    }

    /**
     * 查询所有任务
     */
    @GetMapping("/list")
    public Result<List<TaskVO>> list() {
        List<TaskVO> list = robotTaskService.list().stream()
                .map(TaskConvert::pOtoVO)
                .toList();
        return Result.success(list);
    }

    /**
     * 根据任务单号查询
     */
    @GetMapping("/{taskNo}")
    public Result<TaskVO> getByTaskNo(@PathVariable String taskNo) {
        return Result.success(robotTaskService.getByTaskNo(taskNo));
    }

    /**
     * 取消任务
     */
    @PostMapping("/{taskNo}/cancel")
    public Result<TaskVO> cancel(@PathVariable String taskNo) {
        return Result.success(robotTaskService.cancelTask(taskNo));
    }

    /**
     * 分页查询
     * 例：/api/task/page?pageNum=1&pageSize=10&taskStatus=1
     */
    @GetMapping("/page")
    public Result<PageResult<TaskVO>> page(@Valid TaskPageQueryDTO query) {
        return Result.success(robotTaskService.pageTasks(query));
    }
}