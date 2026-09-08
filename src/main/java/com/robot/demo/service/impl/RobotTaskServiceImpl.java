package com.robot.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.robot.demo.enums.RobotStatusEnum;
import com.robot.demo.enums.TaskStatusEnum;
import com.robot.demo.exception.BizException;
import com.robot.demo.mapper.RobotDeviceMapper;
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
public class RobotTaskServiceImpl extends ServiceImpl<RobotTaskMapper, RobotTaskPO>
        implements RobotTaskService {

    private final RobotDeviceService robotDeviceService;
    private final RobotDeviceMapper robotDeviceMapper;
    private final RobotTaskProducer robotTaskProducer;

    public RobotTaskServiceImpl(RobotDeviceService robotDeviceService,
                                RobotDeviceMapper robotDeviceMapper,
                                RobotTaskProducer robotTaskProducer) {
        this.robotDeviceService = robotDeviceService;
        this.robotDeviceMapper = robotDeviceMapper;
        this.robotTaskProducer = robotTaskProducer;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RobotTaskPO createTask(RobotTaskPO task) {
        log.info("【创建任务】taskType={}, target={}", task.getTaskType(), task.getTargetPosition());

        // 1. 生成任务单号
        String taskNo = "TASK" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        task.setTaskNo(taskNo);
        task.setTaskStatus(TaskStatusEnum.PENDING.getCode());

        // 2. 找空闲机器人（可能来自缓存，仅作候选）
        RobotDevicePO idleRobot = robotDeviceService.getIdleRobot();
        if (idleRobot == null) {
            throw new BizException("没有空闲机器人可用，请稍后再试");
        }

        // 3. 原子占机：只有 status=0 才能改成 1
        int occupied = robotDeviceMapper.occupyIdleRobot(idleRobot.getRobotCode());
        if (occupied == 0) {
            // 缓存可能脏了，清掉后提示重试（也可以在这里循环重试 2~3 次）
            robotDeviceService.clearIdleRobotCache();
            throw new BizException("机器人刚刚被占用，请重试");
        }

        // 4. 绑定机器人，状态改为执行中
        task.setRobotCode(idleRobot.getRobotCode());
        task.setTaskStatus(TaskStatusEnum.EXECUTING.getCode());
        save(task);
        log.info("【创建任务】落库成功 taskNo={}, robotCode={}", taskNo, idleRobot.getRobotCode());

        // 5. 清缓存（这台机器人已不再空闲）
        robotDeviceService.clearIdleRobotCache();

        // 6. 事务提交后再发 MQ，避免回滚后消息已发出
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                robotTaskProducer.sendTaskMessage(taskNo);
            }
        });

        return task;
    }
}