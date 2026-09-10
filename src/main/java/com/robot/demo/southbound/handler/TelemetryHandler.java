package com.robot.demo.southbound.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.demo.pojo.po.RobotDevicePO;
import com.robot.demo.service.RobotDeviceService;
import com.robot.demo.southbound.protocol.Packet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * UDP 收遥测，更新机器人状态
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryHandler {

    private final ObjectMapper objectMapper;
    private final RobotDeviceService robotDeviceService;

    public void onTelemetry(Packet packet, String from) {
        try {
            JsonNode body = objectMapper.readTree(packet.getBodyJson());
            String robotCode = body.path("robotCode").asText(null);
            if (robotCode == null || robotCode.isBlank()) {
                return;
            }

            // 约定 JSON 字段（和车端、海柔真实项目字段名可能不同，但思路一样）
            String position = body.hasNonNull("position") ? body.get("position").asText() : null;
            Integer battery = body.hasNonNull("battery") ? body.get("battery").asInt() : null;
            Integer status = body.hasNonNull("status") ? body.get("status").asInt() : null;
            // speed / taskNo / taskStatus 可先打日志，表结构有了再落库
            Double speed = body.hasNonNull("speed") ? body.get("speed").asDouble() : null;
            String taskNo = body.hasNonNull("taskNo") ? body.get("taskNo").asText() : null;

            LambdaUpdateWrapper<RobotDevicePO> uw = new LambdaUpdateWrapper<>();
            uw.eq(RobotDevicePO::getRobotCode, robotCode);
            if (position != null) {
                uw.set(RobotDevicePO::getPosition, position);
            }
            if (battery != null) {
                uw.set(RobotDevicePO::getBattery, battery);
            }
            if (status != null) {
                uw.set(RobotDevicePO::getStatus, status);
            }

            boolean updated = robotDeviceService.update(uw);
            if (updated) {
                // 电量/状态变了，清掉「空闲车」缓存，避免北向选到旧数据
                robotDeviceService.clearIdleRobotCache();
            }

            log.debug("【南向UDP】robot={} pos={} bat={} status={} speed={} task={} from={}",
                    robotCode, position, battery, status, speed, taskNo, from);
        } catch (Exception e) {
            log.warn("【南向UDP】解析失败 from={} err={}", from, e.toString());
        }
    }
}