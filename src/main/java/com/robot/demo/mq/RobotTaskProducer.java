package com.robot.demo.mq;

import com.robot.demo.config.RabbitMqConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RobotTaskProducer {

    public final static String HEADER_RETRY_COUNT="x-retry-count";

    private final RabbitTemplate rabbitTemplate;

    public RobotTaskProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendTaskMessage(String taskNo) {
        sendTaskMessage(taskNo,0);
    }

    /**
     * 发送任务消息（可带当前已重试次数）
     */
    public void sendTaskMessage(String taskNo, int retryCount) {
        log.info("【MQ生产者】发送任务消息 taskNo={}, retryCount={}", taskNo, retryCount);
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.ROBOT_TASK_EXCHANGE,
                RabbitMqConfig.ROBOT_TASK_ROUTING_KEY,
                taskNo,
                message -> {
                    message.getMessageProperties().setHeader(HEADER_RETRY_COUNT, retryCount);
                    return message;
                }
        );
        log.info("【MQ生产者】发送成功 taskNo={}, retryCount={}", taskNo, retryCount);
    }
}