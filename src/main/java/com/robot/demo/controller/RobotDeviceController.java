package com.robot.demo.controller;

import com.robot.demo.pojo.po.RobotDevicePO;
import com.robot.demo.service.RobotDeviceService;
import com.robot.demo.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/robot")
public class RobotDeviceController {
    @Autowired
    private RobotDeviceService robotDeviceService;

    /**
     * 查询所有机器人
     * @return
     */
    @GetMapping("/list")
    public Result<List<RobotDevicePO>> getRobotResultList(){
        log.info("查询机器人");
        return Result.success(robotDeviceService.list());
    }

    /**
     * 新增机器人
     */
    @PostMapping("/add")
    public Result<String> add(@RequestBody RobotDevicePO robot) {
        robotDeviceService.save(robot);
        return Result.success("新增成功");
    }

    /**
     * 查询空闲机器人（走Redis缓存）
     */
    @GetMapping("/idle")
    public Result<RobotDevicePO> getIdleRobot() {
        return Result.success(robotDeviceService.getIdleRobot());
    }
}
