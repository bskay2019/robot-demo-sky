package com.robot.demo.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.robot.demo.config.RabbitMqConfig;
import com.robot.demo.mapper.RobotTaskMapper;
import com.robot.demo.pojo.po.RobotTaskPO;
import com.robot.demo.service.RobotTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 消息消费者：监听队列，收到消息后模拟机器人执行任务
 * 小白解释：消费者就是"收信人"，一直盯着邮箱，有信就取出来处理
 */
@Slf4j
@Component
public class RobotTaskConsumer {
    private final RobotTaskService robotTaskService;

    @Autowired
    private RobotTaskMapper robotTaskMapper;

    public RobotTaskConsumer(RobotTaskService robotTaskService) {
        this.robotTaskService = robotTaskService;
    }

    /**
     * 监听 robot.task.queue 队列
     * 收到消息后：把任务状态从"执行中"改成"完成"
     */
    @RabbitListener(queues = RabbitMqConfig.ROBOT_TASK_QUEUE)
    public void handleTaskMessage(String taskNo){
        log.info("【MQ消费者】收到任务消息，任务单号：{}，开始模拟机器人执行...", taskNo);

        try {
            Thread.sleep(3000);
            RobotTaskPO one = robotTaskMapper.selectByTaskNo(taskNo);
            if (one ==null){
                log.error("【MQ消费者】任务不存在，任务单号：{}", taskNo);
                return;
            }

            one.setTaskStatus(2);
            robotTaskService.updateById(one);
            robotTaskMapper.setRobotIdle(one.getRobotCode());
            log.info("【MQ消费者】机器人{}已重置为空闲",one.getRobotCode());
        } catch (InterruptedException e) {
            log.error("【MQ消费者】任务执行异常，任务单号：{}", taskNo, e);
            return;
        }

        log.info("【MQ消费者】任务执行完成，任务单号：{}，状态已更新为完成", taskNo);

    }

}
