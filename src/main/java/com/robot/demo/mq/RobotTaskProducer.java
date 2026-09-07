package com.robot.demo.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.robot.demo.config.RabbitMqConfig;
import com.robot.demo.pojo.po.RobotTaskPO;
import com.robot.demo.service.RobotTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RobotTaskProducer {
    private final RabbitTemplate rabbitTemplate;


    public RobotTaskProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    // 参数1：交换机名称  参数2：路由键  参数3：消息内容
    public void sendTaskMessage(String taskNo){
        log.info("【MQ生产者】开始发送任务消息，任务单号：{}", taskNo);
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.ROBOT_TASK_EXCHANGE,
                RabbitMqConfig.ROBOT_TASK_ROUTING_KEY,
                taskNo
        );
        log.info("【MQ生产者】任务消息发送成功，任务单号：{}", taskNo);
    }



}
