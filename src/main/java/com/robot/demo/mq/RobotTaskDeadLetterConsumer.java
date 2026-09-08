package com.robot.demo.mq;

import com.rabbitmq.client.Channel;
import com.robot.demo.config.RabbitMqConfig;
import com.robot.demo.enums.TaskStatusEnum;
import com.robot.demo.mapper.RobotTaskMapper;
import com.robot.demo.pojo.po.RobotTaskPO;
import com.robot.demo.service.RobotDeviceService;
import com.robot.demo.service.RobotTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 死信消费者：最终失败落库为 FAILED，并释放机器人
 */
@Slf4j
@Component
public class RobotTaskDeadLetterConsumer {

    private final RobotTaskService robotTaskService;
    private final RobotTaskMapper robotTaskMapper;
    private final RobotDeviceService robotDeviceService;

    public RobotTaskDeadLetterConsumer(RobotTaskService robotTaskService,
                                       RobotTaskMapper robotTaskMapper,
                                       RobotDeviceService robotDeviceService) {
        this.robotTaskService = robotTaskService;
        this.robotTaskMapper = robotTaskMapper;
        this.robotDeviceService = robotDeviceService;
    }

    @RabbitListener(queues = RabbitMqConfig.ROBOT_TASK_DEAD_QUEUE)
    public void handleDeadMessage(String taskNo,
                                  Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.error("【死信消费者】收到最终失败消息 taskNo={}", taskNo);

        try {
            RobotTaskPO task = robotTaskMapper.selectByTaskNo(taskNo);
            if (task == null) {
                log.error("【死信消费者】任务不存在，ACK taskNo={}", taskNo);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 已取消/已完成：不再改 FAILED
            if (TaskStatusEnum.CANCELLED.getCode().equals(task.getTaskStatus())
                    || TaskStatusEnum.COMPLETED.getCode().equals(task.getTaskStatus())) {
                log.warn("【死信消费者】任务已是终态，跳过 taskNo={}, status={}",
                        taskNo, TaskStatusEnum.getDescByCode(task.getTaskStatus()));
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 标记失败
            boolean updated = robotTaskService.lambdaUpdate()
                    .set(RobotTaskPO::getTaskStatus, TaskStatusEnum.FAILED.getCode())
                    .eq(RobotTaskPO::getId, task.getId())
                    .in(RobotTaskPO::getTaskStatus,
                            TaskStatusEnum.PENDING.getCode(),
                            TaskStatusEnum.EXECUTING.getCode())
                    .update();

            if (updated && StringUtils.hasText(task.getRobotCode())) {
                robotTaskMapper.setRobotIdle(task.getRobotCode());
                robotDeviceService.clearIdleRobotCache();
                log.error("【死信消费者】任务已标记失败并释放机器人 taskNo={}, robotCode={}",
                        taskNo, task.getRobotCode());
            } else {
                log.warn("【死信消费者】状态更新未生效 taskNo={}", taskNo);
            }

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("【死信消费者】处理异常 taskNo={}", taskNo, e);
            // 死信处理失败：先别 requeue 死循环，记录日志后 ACK（生产可告警/落库）
            channel.basicAck(deliveryTag, false);
        }
    }
}