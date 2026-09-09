package com.robot.demo.southbound.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RobotSessionManager {

    /** robotCode -> 在线会话；ConcurrentHashMap 线程安全 */
    private final Map<String, RobotSession> online = new ConcurrentHashMap<>();

    public void register(RobotSession session) {
        RobotSession old = online.put(session.getRobotCode(), session);
        if (old != null && old != session) {
            try {
                old.getSocket().close();
            } catch (IOException ignored) {
            }
            log.warn("同车重连，已踢旧连接 robotCode={}", session.getRobotCode());
        }
    }

    public Optional<RobotSession> find(String robotCode) {
        return Optional.ofNullable(online.get(robotCode));
    }

    public void unregister(String robotCode, RobotSession expect) {
        // 只有仍是这个 session 才删，避免误删新连接
        online.computeIfPresent(robotCode, (k, v) -> v == expect ? null : v);
    }

    public Map<String, RobotSession> all() {
        return Map.copyOf(online);
    }
}