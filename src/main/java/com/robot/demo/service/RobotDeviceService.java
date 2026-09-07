package com.robot.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.robot.demo.pojo.po.RobotDevicePO;
import org.springframework.stereotype.Service;

@Service
public interface RobotDeviceService  extends IService<RobotDevicePO> {
    // 查询空闲的机器人
    RobotDevicePO getIdleRobot();

    void clearIdleRobotCache();
}
