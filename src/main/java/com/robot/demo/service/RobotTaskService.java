package com.robot.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.robot.demo.pojo.po.RobotDevicePO;
import com.robot.demo.pojo.po.RobotTaskPO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public interface RobotTaskService  extends IService<RobotTaskPO> {

    RobotTaskPO createTask(RobotTaskPO task);
}
