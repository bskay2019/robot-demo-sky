package com.robot.demo.southbound.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.demo.enums.RobotStatusEnum;
import com.robot.demo.enums.TaskStatusEnum;
import com.robot.demo.mapper.RobotTaskMapper;
import com.robot.demo.pojo.po.RobotDevicePO;
import com.robot.demo.pojo.po.RobotTaskPO;
import com.robot.demo.service.RobotDeviceService;
import com.robot.demo.service.RobotTaskService;
import com.robot.demo.southbound.protocol.Packet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * UDP 收遥测：更新位置/电量；任务完成时释放机器人为空闲
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryHandler {

    private final ObjectMapper objectMapper;
    private final RobotDeviceService robotDeviceService;
    private final RobotTaskService robotTaskService;
    private final RobotTaskMapper robotTaskMapper;

    public void onTelemetry(Packet packet, String from) {
        try {
            JsonNode body = objectMapper.readTree(packet.getBodyJson());
            String robotCode = body.path("robotCode").asText(null);
            if (robotCode == null || robotCode.isBlank()) {
                return;
            }

            String position = body.hasNonNull("position") ? body.get("position").asText() : null;
            Integer battery = body.hasNonNull("battery") ? body.get("battery").asInt() : null;
            Integer status = body.hasNonNull("status") ? body.get("status").asInt() : null;
            Double speed = body.hasNonNull("speed") ? body.get("speed").asDouble() : null;
            String taskNo = body.hasNonNull("taskNo") ? body.get("taskNo").asText() : null;
            Integer taskStatus = body.hasNonNull("taskStatus") ? body.get("taskStatus").asInt() : null;

            // 任务完成上报：标任务完成 + 机器人空闲（幂等）
            if (TaskStatusEnum.COMPLETED.getCode().equals(taskStatus)) {
                onTaskCompleted(robotCode, taskNo);
                status = RobotStatusEnum.IDLE.getCode();
            }

            LambdaUpdateWrapper<RobotDevicePO> uw = new LambdaUpdateWrapper<>();
            uw.eq(RobotDevicePO::getRobotCode, robotCode);
            boolean needUpdate = false;
            if (position != null) {
                uw.set(RobotDevicePO::getPosition, position);
                needUpdate = true;
            }
            if (battery != null) {
                uw.set(RobotDevicePO::getBattery, battery);
                needUpdate = true;
            }
            if (status != null) {
                uw.set(RobotDevicePO::getStatus, status);
                needUpdate = true;
            }

            if (needUpdate && robotDeviceService.update(uw)) {
                robotDeviceService.clearIdleRobotCache();
            }

            log.debug("【南向UDP】robot={} pos={} bat={} status={} speed={} task={} taskStatus={} from={}",
                    robotCode, position, battery, status, speed, taskNo, taskStatus, from);
        } catch (Exception e) {
            log.warn("【南向UDP】解析失败 from={} err={}", from, e.toString());
        }
    }

    /**
     * 车端/测试脚本上报 taskStatus=2 时调用。
     * 多次 UDP 重复上报也安全：只有仍是「执行中」的任务会更新成功一次。
     */
    private void onTaskCompleted(String robotCode, String taskNo) {
        boolean taskUpdated = false;

        if (StringUtils.hasText(taskNo)) {
            taskUpdated = robotTaskService.lambdaUpdate()
                    .set(RobotTaskPO::getTaskStatus, TaskStatusEnum.COMPLETED.getCode())
                    .eq(RobotTaskPO::getTaskNo, taskNo)
                    .eq(RobotTaskPO::getTaskStatus, TaskStatusEnum.EXECUTING.getCode())
                    .update();
        }

        robotTaskMapper.setRobotIdle(robotCode);
        robotDeviceService.clearIdleRobotCache();

        if (taskUpdated) {
            log.info("【南向UDP】任务完成，已释放机器人 taskNo={}, robotCode={}", taskNo, robotCode);
        } else {
            log.debug("【南向UDP】收到完成态（任务可能已完成或不存在）taskNo={}, robotCode={}",
                    taskNo, robotCode);
        }
    }
}
