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

@Slf4j
@Component
public class RobotTaskConsumer {

    private final RobotTaskService robotTaskService;
    private final RobotTaskMapper robotTaskMapper;
    private final RobotDeviceService robotDeviceService;
    private final RobotTaskProducer robotTaskProducer;

    public RobotTaskConsumer(RobotTaskService robotTaskService,
                             RobotTaskMapper robotTaskMapper,
                             RobotDeviceService robotDeviceService,
                             RobotTaskProducer robotTaskProducer) {
        this.robotTaskService = robotTaskService;
        this.robotTaskMapper = robotTaskMapper;
        this.robotDeviceService = robotDeviceService;
        this.robotTaskProducer = robotTaskProducer;
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
            RobotTaskPO before = robotTaskMapper.selectByTaskNo(taskNo);
            if (before == null) {
                log.error("【MQ消费者】任务不存在，直接 ACK 丢弃 taskNo={}", taskNo);
                channel.basicAck(deliveryTag, false);
                return;
            }
            if (!canExecute(before.getTaskStatus())) {
                log.warn("【MQ消费者】状态不可执行，直接 ACK 跳过 taskNo={}, status={}",
                        taskNo, TaskStatusEnum.getDescByCode(before.getTaskStatus()));
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 2. 模拟执行（测通后保持 3 秒即可）
            Thread.sleep(3000);

            // 测试死信/重试用：remark 包含 FAIL 就故意抛错
            // 创建任务时传 "FAIL-测试重试"，即可走失败分支
            if (StringUtils.hasText(before.getRemark()) && before.getRemark().contains("FAIL")) {
                throw new RuntimeException("模拟执行失败：remark=" + before.getRemark());
            }

            // 3. 执行后再查状态（期间可能已取消）
            RobotTaskPO task = robotTaskMapper.selectByTaskNo(taskNo);
            if (task == null || !canExecute(task.getTaskStatus())) {
                log.warn("【MQ消费者】执行后状态不可完成，ACK 跳过 taskNo={}", taskNo);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 4. 乐观更新为完成
            boolean updated = robotTaskService.lambdaUpdate()
                    .set(RobotTaskPO::getTaskStatus, TaskStatusEnum.COMPLETED.getCode())
                    .eq(RobotTaskPO::getId, task.getId())
                    .eq(RobotTaskPO::getTaskStatus, TaskStatusEnum.EXECUTING.getCode())
                    .update();

            if (!updated) {
                log.warn("【MQ消费者】完成更新失败，ACK 跳过 taskNo={}", taskNo);
                channel.basicAck(deliveryTag, false);
                return;
            }

            if (StringUtils.hasText(task.getRobotCode())) {
                robotTaskMapper.setRobotIdle(task.getRobotCode());
                robotDeviceService.clearIdleRobotCache();
            }

            // 5. 成功：手动 ACK
            channel.basicAck(deliveryTag, false);
            log.info("【MQ消费者】完成并 ACK taskNo={}, robotCode={}", taskNo, task.getRobotCode());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("【MQ消费者】被中断 taskNo={}", taskNo, e);
            // 中断场景：不重入队，进入死信（避免毒消息循环）
            channel.basicNack(deliveryTag, false, false);
        } catch (Exception e) {
            log.error("【MQ消费者】执行失败 taskNo={}, retryCount={}", taskNo, currentRetry, e);
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

            // 重新发一条（retry+1），然后 ACK 当前这条，避免同一条消息卡死
            robotTaskProducer.sendTaskMessage(taskNo, nextRetry);
            channel.basicAck(deliveryTag, false);
            return;
        }

        log.error("【MQ消费者】重试耗尽，进入死信 taskNo={}, retryCount={}", taskNo, currentRetry);
        // requeue=false → 触发队列 DLX → 进入死信队列
        channel.basicNack(deliveryTag, false, false);
    }

    private boolean canExecute(Integer status) {
        return TaskStatusEnum.EXECUTING.getCode().equals(status);
    }
}