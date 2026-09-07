package com.robot.demo.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    // 机器人任务交换机
    public static final String ROBOT_TASK_EXCHANGE="robot.task.exchange";

    // 机器人任务队列名称
    public static final String ROBOT_TASK_QUEUE = "robot.task.queue";

    // 路由键：匹配哪些消息进这个队列
    public static final String ROBOT_TASK_ROUTING_KEY = "robot.task.create";


    /**
     * 定义交换机：TopicExchange 支持通配符路由，企业最常用
     */
    @Bean
    public TopicExchange robotTaskExchange(){
        return new TopicExchange(ROBOT_TASK_EXCHANGE);
    }

    /**
     * 定义队列：durable=true 表示持久化，MQ重启消息不丢
     */

    @Bean
    public Queue robotTaskQueue(){
        return QueueBuilder.durable(ROBOT_TASK_QUEUE).build();
    }

    /**
     * 绑定：把队列绑到交换机上，并指定路由键
     */
    @Bean
    public Binding robotTaskBinding(){
       return BindingBuilder.bind(robotTaskQueue())
               .to(robotTaskExchange())
               .with(ROBOT_TASK_ROUTING_KEY);
    }
}
