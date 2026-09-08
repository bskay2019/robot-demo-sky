package com.robot.demo.mq;

import com.robot.demo.config.RabbitMqConfig;
import com.robot.demo.enums.TaskStatusEnum;
import com.robot.demo.mapper.RobotTaskMapper;
import com.robot.demo.pojo.po.RobotTaskPO;
import com.robot.demo.service.RobotDeviceService;
import com.robot.demo.service.RobotTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RobotTaskConsumer {

    private final RobotTaskService robotTaskService;
    private final RobotTaskMapper robotTaskMapper;
    private final RobotDeviceService robotDeviceService;

    public RobotTaskConsumer(RobotTaskService robotTaskService,
                             RobotTaskMapper robotTaskMapper,
                             RobotDeviceService robotDeviceService) {
        this.robotTaskService = robotTaskService;
        this.robotTaskMapper = robotTaskMapper;
        this.robotDeviceService = robotDeviceService;
    }

    @RabbitListener(queues = RabbitMqConfig.ROBOT_TASK_QUEUE)
    public void handleTaskMessage(String taskNo) {
        log.info("【MQ消费者】收到任务，开始模拟执行 taskNo={}", taskNo);

        try {
            // 模拟机器人执行 3 秒
            Thread.sleep(20000);

            RobotTaskPO task = robotTaskMapper.selectByTaskNo(taskNo);
            if (task == null) {
                log.error("【MQ消费者】任务不存在 taskNo={}", taskNo);
                return;
            }

            // 更新任务为完成
            task.setTaskStatus(TaskStatusEnum.COMPLETED.getCode());
            robotTaskService.updateById(task);

            // 机器人改回空闲
            robotTaskMapper.setRobotIdle(task.getRobotCode());

            // 重要：机器人又空闲了，清掉旧缓存，下次重新查 DB
            robotDeviceService.clearIdleRobotCache();

            log.info("【MQ消费者】完成 taskNo={}, robotCode={}", taskNo, task.getRobotCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("【MQ消费者】被中断 taskNo={}", taskNo, e);
        } catch (Exception e) {
            log.error("【MQ消费者】执行失败 taskNo={}", taskNo, e);
            // 后面会教：失败重试 + 死信队列
        }
    }
}