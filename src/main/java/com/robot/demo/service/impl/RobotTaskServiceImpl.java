package com.robot.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.robot.demo.mapper.RobotTaskMapper;
import com.robot.demo.mq.RobotTaskProducer;
import com.robot.demo.pojo.po.RobotDevicePO;
import com.robot.demo.pojo.po.RobotTaskPO;
import com.robot.demo.service.RobotDeviceService;
import com.robot.demo.service.RobotTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Slf4j
@Service
public class RobotTaskServiceImpl extends ServiceImpl<RobotTaskMapper, RobotTaskPO> implements RobotTaskService {
    private final RobotDeviceService robotDeviceService;
    private final RobotTaskProducer robotTaskProducer;
    private final RobotDeviceServiceImpl robotDeviceServiceImpl;

    // 构造器注入
    public RobotTaskServiceImpl(RobotDeviceService robotDeviceService,
                                RobotTaskProducer robotTaskProducer,
                                RobotDeviceServiceImpl robotDeviceServiceImpl) {
        this.robotDeviceService = robotDeviceService;
        this.robotTaskProducer = robotTaskProducer;
        this.robotDeviceServiceImpl = robotDeviceServiceImpl;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RobotTaskPO createTask(RobotTaskPO task) {
        log.info("【创建任务】开始创建任务，任务类型：{}，目标位置：{}",
                task.getTaskType(), task.getTargetPosition());

        // 1. 生成唯一任务单号（UUID去掉横杠）
        String taskNo = "TASK" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        task.setTaskNo(taskNo);

        // 2. 初始状态：待分配（0）
        task.setTaskStatus(0);

        // 3. 找一个空闲机器人
        RobotDevicePO idleRobot = robotDeviceService.getIdleRobot();
        if (idleRobot == null) {
            log.error("【创建任务】没有空闲机器人可用！");
            throw new RuntimeException("没有空闲机器人可用，请稍后再试");
        }

        // 4. 分配机器人，任务状态改为"执行中"（1）
        task.setRobotCode(idleRobot.getRobotCode());
        task.setTaskStatus(1);

        // 5. 保存任务到数据库
        save(task);
        log.info("【创建任务】任务已保存到数据库，任务单号：{}，分配机器人：{}",
                taskNo, idleRobot.getRobotCode());

        // 6. 更新机器人状态为"工作中"（1）
        idleRobot.setStatus(1);
        robotDeviceService.updateById(idleRobot);

        // 7. 清除 Redis 中的空闲机器人缓存（这个机器人不再空闲了）
        robotDeviceServiceImpl.clearIdleRobotCache();

        // 8. 发送 MQ 消息，通知机器人执行任务
        //✅注册事务回调：事务提交成功之后才执行发MQ
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                //事务提交成功后，再发送MQ消息
                robotTaskProducer.sendTaskMessage(taskNo);
            }
        });

        log.info("【创建任务】任务创建完成，任务单号：{}", taskNo);
        return task;
    }
}
