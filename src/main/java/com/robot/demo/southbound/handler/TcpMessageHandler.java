package com.robot.demo.southbound.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.robot.demo.southbound.config.SouthboundProperties;
import com.robot.demo.southbound.protocol.Packet;
import com.robot.demo.southbound.protocol.PacketCodec;
import com.robot.demo.southbound.protocol.ProtocolConstants;
import com.robot.demo.southbound.session.RobotSession;
import com.robot.demo.southbound.session.RobotSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.Socket;
import java.util.UUID;


/**
 * 消息处理（登录 / 心跳 / 任务应答 / 下发）
 */
@Slf4j
@Component
@RequiredArgsConstructor  // 自动生成构造器注入 final 字段
public class TcpMessageHandler {

    private final ObjectMapper objectMapper;
    private final SouthboundProperties props;
    private final RobotSessionManager sessionManager;

    /** 处理登录；成功返回 session，失败返回 null */
    public RobotSession onLogin(Socket socket, Packet packet) throws Exception {
        JsonNode body = objectMapper.readTree(packet.getBodyJson());
        String robotCode = asText(body, "robotCode");
        String token = asText(body, "token");

        ObjectNode resp = objectMapper.createObjectNode();

        if (robotCode == null || robotCode.isBlank()) {
            fail(socket, resp, 400, "robotCode required");
            return null;
        }
        if (!props.getLoginToken().equals(token)) {
            fail(socket, resp, 401, "invalid token");
            return null;
        }

        String sessionId = "sess-" + robotCode + "-" +
                UUID.randomUUID().toString().substring(0, 8);

        RobotSession session = new RobotSession(robotCode, sessionId, socket);
        sessionManager.register(session);

        resp.put("ok", true);
        resp.put("code", 0);
        resp.put("msg", "login ok");
        resp.put("heartbeatSec", 5);
        resp.put("udpPort", props.getUdpPort());
        resp.put("sessionId", sessionId);

        session.send(PacketCodec.encode(ProtocolConstants.MSG_LOGIN_RESP, resp.toString()));
        log.info("【南向TCP】登录成功 robotCode={} sessionId={}", robotCode, sessionId);
        return session;
    }

    /** 登录成功后的业务包 */
    public void onMessage(RobotSession session, Packet packet) throws Exception {
        session.touch();
        int type = packet.getMsgType();

        if (type == ProtocolConstants.MSG_HEARTBEAT_REQ) {
            ObjectNode resp = objectMapper.createObjectNode();
            resp.put("ok", true);
            resp.put("serverTs", System.currentTimeMillis());
            session.send(PacketCodec.encode(ProtocolConstants.MSG_HEARTBEAT_RESP, resp.toString()));
            return;
        }

        if (type == ProtocolConstants.MSG_TASK_ACK) {
            log.info("【南向TCP】TASK_ACK robotCode={} body={}",
                    session.getRobotCode(), packet.getBodyJson());
            // 入职后这里通常：更新任务状态、写日志、通知北向
            return;
        }

        log.warn("【南向TCP】未知消息 type=0x{} robot={}",
                Integer.toHexString(type), session.getRobotCode());
    }

    /**
     * 云端主动给某台车下发任务。
     * @return false 表示车不在线或发送失败
     */
    public boolean dispatchTask(String robotCode, String taskNo, int taskType,
                                String targetPosition, String remark) {
        return sessionManager.find(robotCode).map(session -> {
            try {
                ObjectNode body = objectMapper.createObjectNode();
                body.put("taskNo", taskNo);
                body.put("robotCode", robotCode);
                body.put("taskType", taskType);
                body.put("targetPosition", targetPosition == null ? "" : targetPosition);
                body.put("remark", remark == null ? "" : remark);
                session.send(PacketCodec.encode(
                        ProtocolConstants.MSG_TASK_DISPATCH, body.toString()));
                log.info("【南向TCP】已下发任务 robotCode={} taskNo={}", robotCode, taskNo);
                return true;
            } catch (Exception e) {
                log.error("【南向TCP】下发失败 robotCode={} taskNo={}", robotCode, taskNo, e);
                return false;
            }
        }).orElseGet(() -> {
            log.warn("【南向TCP】机器人不在线 robotCode={}", robotCode);
            return false;
        });
    }

    private void fail(Socket socket, ObjectNode resp, int code, String msg) throws Exception {
        resp.put("ok", false).put("code", code).put("msg", msg);
        byte[] bytes = PacketCodec.encode(ProtocolConstants.MSG_LOGIN_RESP, resp.toString());
        synchronized (socket) {
            socket.getOutputStream().write(bytes);
            socket.getOutputStream().flush();
        }
    }

    private static String asText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }
}