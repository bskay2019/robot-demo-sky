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
import org.springframework.util.StringUtils;

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
        log.info("【MQ消费者】收到任务 taskNo={}", taskNo);

        try {
            // ---- 执行前检查：已取消/已完成就直接跳过（幂等）----
            RobotTaskPO before = robotTaskMapper.selectByTaskNo(taskNo);
            if (before == null) {
                log.error("【MQ消费者】任务不存在，跳过 taskNo={}", taskNo);
                return;
            }
            if (!canExecute(before.getTaskStatus())) {
                log.warn("【MQ消费者】当前状态不可执行，跳过 taskNo={}, status={}",
                        taskNo, TaskStatusEnum.getDescByCode(before.getTaskStatus()));
                return;
            }

            // 模拟机器人执行（你现在是 20 秒，方便测取消；测完可改回 3000）
            Thread.sleep(30000);

            // ---- 执行后再查一次：sleep 期间可能已被取消 ----
            RobotTaskPO task = robotTaskMapper.selectByTaskNo(taskNo);
            if (task == null) {
                log.error("【MQ消费者】任务不存在，跳过 taskNo={}", taskNo);
                return;
            }
            if (!canExecute(task.getTaskStatus())) {
                log.warn("【MQ消费者】执行期间状态已变，跳过完成逻辑 taskNo={}, status={}",
                        taskNo, TaskStatusEnum.getDescByCode(task.getTaskStatus()));
                return;
            }

            // ---- 乐观更新：只有「执行中」才能改成「完成」----
            boolean updated = robotTaskService.lambdaUpdate()
                    .set(RobotTaskPO::getTaskStatus, TaskStatusEnum.COMPLETED.getCode())
                    .eq(RobotTaskPO::getId, task.getId())
                    .eq(RobotTaskPO::getTaskStatus, TaskStatusEnum.EXECUTING.getCode())
                    .update();

            if (!updated) {
                log.warn("【MQ消费者】完成更新失败（可能已被取消）taskNo={}", taskNo);
                return;
            }

            // 释放机器人（取消接口可能已释放，这里再放一次也安全：幂等把 status 设为 0）
            if (StringUtils.hasText(task.getRobotCode())) {
                robotTaskMapper.setRobotIdle(task.getRobotCode());
                robotDeviceService.clearIdleRobotCache();
            }

            log.info("【MQ消费者】完成 taskNo={}, robotCode={}", taskNo, task.getRobotCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("【MQ消费者】被中断 taskNo={}", taskNo, e);
        } catch (Exception e) {
            log.error("【MQ消费者】执行失败 taskNo={}", taskNo, e);
        }
    }

    /**
     * 只有「执行中」才允许继续执行并完成
     */
    private boolean canExecute(Integer status) {
        return TaskStatusEnum.EXECUTING.getCode().equals(status);
    }
}