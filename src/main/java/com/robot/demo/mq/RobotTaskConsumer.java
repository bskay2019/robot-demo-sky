package com.robot.demo.mq;

import com.rabbitmq.client.Channel;
import com.robot.demo.config.RabbitMqConfig;
import com.robot.demo.enums.TaskStatusEnum;
import com.robot.demo.mapper.RobotTaskMapper;
import com.robot.demo.pojo.po.RobotTaskPO;
import com.robot.demo.southbound.handler.TcpMessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * MQ 消费者：收到任务后经南向 TCP 下发给在线机器人。
 * 任务完成与机器人空闲释放由 UDP 遥测（taskStatus=2）处理。
 */
@Slf4j
@Component
public class RobotTaskConsumer {

    private final RobotTaskMapper robotTaskMapper;
    private final RobotTaskProducer robotTaskProducer;
    private final TcpMessageHandler tcpMessageHandler;

    public RobotTaskConsumer(RobotTaskMapper robotTaskMapper,
                             RobotTaskProducer robotTaskProducer,
                             TcpMessageHandler tcpMessageHandler) {
        this.robotTaskMapper = robotTaskMapper;
        this.robotTaskProducer = robotTaskProducer;
        this.tcpMessageHandler = tcpMessageHandler;
    }

    @RabbitListener(queues = RabbitMqConfig.ROBOT_TASK_QUEUE)
    public void handleTaskMessage(String taskNo,
                                  Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                  @Header(value = RobotTaskProducer.HEADER_RETRY_COUNT, required = false) Integer retryCount)
            throws IOException {

        int currentRetry = retryCount == null ? 0 : retryCount;
        log.info("【MQ消费者】收到任务 taskNo={}, retryCount={}, deliveryTag={}",
                taskNo, currentRetry, deliveryTag);

        try {
            // 1. 幂等：只有执行中才处理
            RobotTaskPO task = robotTaskMapper.selectByTaskNo(taskNo);
            if (task == null) {
                log.error("【MQ消费者】任务不存在，直接 ACK 丢弃 taskNo={}", taskNo);
                channel.basicAck(deliveryTag, false);
                return;
            }
            if (!canExecute(task.getTaskStatus())) {
                log.warn("【MQ消费者】状态不可执行，直接 ACK 跳过 taskNo={}, status={}",
                        taskNo, TaskStatusEnum.getDescByCode(task.getTaskStatus()));
                channel.basicAck(deliveryTag, false);
                return;
            }

            if (!StringUtils.hasText(task.getRobotCode())) {
                throw new RuntimeException("任务未分配机器人 robotCode 为空, taskNo=" + taskNo);
            }

            // 测试死信/重试：remark 包含 FAIL 仍可故意失败
            if (StringUtils.hasText(task.getRemark()) && task.getRemark().contains("FAIL")) {
                throw new RuntimeException("模拟下发失败：remark=" + task.getRemark());
            }

            // 2. 通过南向 TCP 把任务发给机器人
            int taskType = task.getTaskType() == null ? 1 : task.getTaskType();
            boolean sent = tcpMessageHandler.dispatchTask(
                    task.getRobotCode(),
                    task.getTaskNo(),
                    taskType,
                    task.getTargetPosition(),
                    task.getRemark()
            );

            if (!sent) {
                throw new RuntimeException("TCP 下发失败，机器人可能不在线 robotCode="
                        + task.getRobotCode() + ", taskNo=" + taskNo);
            }

            // 3. 下发成功即可 ACK（完成态由 UDP 上报驱动）
            channel.basicAck(deliveryTag, false);
            log.info("【MQ消费者】已 TCP 下发并 ACK taskNo={}, robotCode={}",
                    taskNo, task.getRobotCode());

        } catch (Exception e) {
            log.error("【MQ消费者】下发失败 taskNo={}, retryCount={}", taskNo, currentRetry, e);
            handleFailure(taskNo, channel, deliveryTag, currentRetry);
        }
    }

    /**
     * 失败处理：未超限则重新投递；超限则 NACK 进死信
     */
    private void handleFailure(String taskNo, Channel channel, long deliveryTag, int currentRetry)
            throws IOException {

        if (currentRetry < RabbitMqConfig.MAX_RETRY_COUNT) {
            int nextRetry = currentRetry + 1;
            log.warn("【MQ消费者】准备重试 taskNo={}, nextRetry={}/{}",
                    taskNo, nextRetry, RabbitMqConfig.MAX_RETRY_COUNT);

            robotTaskProducer.sendTaskMessage(taskNo, nextRetry);
            channel.basicAck(deliveryTag, false);
            return;
        }

        log.error("【MQ消费者】重试耗尽，进入死信 taskNo={}, retryCount={}", taskNo, currentRetry);
        channel.basicNack(deliveryTag, false, false);
    }

    private boolean canExecute(Integer status) {
        return TaskStatusEnum.EXECUTING.getCode().equals(status);
    }
}
