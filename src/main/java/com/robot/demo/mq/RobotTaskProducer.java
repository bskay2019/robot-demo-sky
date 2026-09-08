package com.robot.demo.mq;

import com.robot.demo.config.RabbitMqConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RobotTaskProducer {

    private final RabbitTemplate rabbitTemplate;

    public RobotTaskProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendTaskMessage(String taskNo) {
        log.info("【MQ生产者】发送任务消息 taskNo={}", taskNo);
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.ROBOT_TASK_EXCHANGE,
                RabbitMqConfig.ROBOT_TASK_ROUTING_KEY,
                taskNo
        );
        log.info("【MQ生产者】发送成功 taskNo={}", taskNo);
    }
}