package com.robot.demo.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    // ========== 业务交换机 / 队列 ==========
    // 机器人任务交换机
    public static final String ROBOT_TASK_EXCHANGE="robot.task.exchange";
    // 机器人任务队列名称
    public static final String ROBOT_TASK_QUEUE = "robot.task.queue";
    // 路由键：匹配哪些消息进这个队列
    public static final String ROBOT_TASK_ROUTING_KEY = "robot.task.create";


    // ========== 业务==========
    /**
     * 定义交换机：TopicExchange 支持通配符路由，企业最常用
     */
    @Bean
    public TopicExchange robotTaskExchange(){
        return ExchangeBuilder.topicExchange(ROBOT_TASK_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 定义队列：durable=true 表示持久化，MQ重启消息不丢
     */

    @Bean
    public Queue robotTaskQueue(){
        return QueueBuilder.durable(ROBOT_TASK_QUEUE)
                .withArgument("x-dead-letter-exchange",ROBOT_TASK_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key",ROBOT_TASK_DEAD_ROUTING_KEY)
                .build();
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



    // ========== 死信交换机 / 队列 ==========
    public static final String ROBOT_TASK_DLX_EXCHANGE="robot.task.dlx.exchange";
    public static final String ROBOT_TASK_DEAD_QUEUE="robot.task.dead.queue";
    public static final String ROBOT_TASK_DEAD_ROUTING_KEY="robot.task.dead";

    /**
     * 最大重试次数
     */
    public static final int MAX_RETRY_COUNT=3;


    // ========== 死信==========
    @Bean
    public TopicExchange robotTaskDlxBiding(){
        return ExchangeBuilder.topicExchange(ROBOT_TASK_DLX_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue robotTaskDeadQueue(){
        return QueueBuilder.durable(ROBOT_TASK_DEAD_QUEUE).build();
    }

    @Bean
    public Binding robotTaskDeadBinding(){
        return  BindingBuilder.bind(robotTaskDeadQueue())
                .to(robotTaskDlxBiding())
                .with(ROBOT_TASK_DEAD_ROUTING_KEY);
    }


}
