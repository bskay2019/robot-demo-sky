package com.robot.demo.southbound.tcp;

import com.robot.demo.southbound.config.SouthboundProperties;
import com.robot.demo.southbound.handler.TcpMessageHandler;
import com.robot.demo.southbound.protocol.Packet;
import com.robot.demo.southbound.protocol.PacketCodec;
import com.robot.demo.southbound.protocol.ProtocolConstants;
import com.robot.demo.southbound.session.RobotSession;
import com.robot.demo.southbound.session.RobotSessionManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 启动tcp端口
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TcpSouthboundServer {

    private final SouthboundProperties props;
    private final TcpMessageHandler messageHandler;
    private final RobotSessionManager sessionManager;

    private ServerSocket serverSocket;
    private ExecutorService acceptExecutor;
    private ExecutorService workerExecutor;
    private ScheduledExecutorService watchdog;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @PostConstruct
    public void start() throws IOException {
        serverSocket = new ServerSocket(props.getTcpPort());
        acceptExecutor = Executors.newSingleThreadExecutor(r -> daemon("sb-tcp-accept", r));
        workerExecutor = Executors.newFixedThreadPool(
                props.getWorkerThreads(), r -> daemon("sb-tcp-worker", r));
        watchdog = Executors.newSingleThreadScheduledExecutor(r -> daemon("sb-tcp-watch", r));

        running.set(true);
        acceptExecutor.execute(this::acceptLoop);
        watchdog.scheduleAtFixedRate(this::kickIdle, 5, 5, TimeUnit.SECONDS);

        log.info("【南向TCP】监听端口 {}", props.getTcpPort());
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                // 读超时：长时间没数据会抛 SocketTimeoutException
                socket.setSoTimeout(props.getHeartbeatTimeoutSec() * 1000);
                workerExecutor.execute(() -> handleOneRobot(socket));
            } catch (IOException e) {
                if (running.get()) {
                    log.error("【南向TCP】accept 失败", e);
                }
            }
        }
    }

    private void handleOneRobot(Socket socket) {
        RobotSession session = null;
        String remote = String.valueOf(socket.getRemoteSocketAddress());
        try (socket;
             InputStream in = new BufferedInputStream(socket.getInputStream())) {

            // 规则：连接后第一包必须是登录
            Packet first = PacketCodec.decode(in);
            if (first.getMsgType() != ProtocolConstants.MSG_LOGIN_REQ) {
                log.warn("【南向TCP】首包非登录，关闭 remote={}", remote);
                return;
            }
            session = messageHandler.onLogin(socket, first);
            if (session == null) {
                return;
            }

            while (running.get() && !socket.isClosed()) {
                try {
                    Packet packet = PacketCodec.decode(in);
                    messageHandler.onMessage(session, packet);
                } catch (SocketTimeoutException te) {
                    long idle = System.currentTimeMillis() - session.getLastActiveMs().get();
                    if (idle > props.getHeartbeatTimeoutSec() * 1000L) {
                        log.warn("【南向TCP】心跳超时 robotCode={}", session.getRobotCode());
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("【南向TCP】连接结束 remote={} reason={}", remote, e.toString());
        } finally {
            if (session != null) {
                sessionManager.unregister(session.getRobotCode(), session);
                log.info("【南向TCP】离线 robotCode={}", session.getRobotCode());
            }
        }
    }

    private void kickIdle() {
        long now = System.currentTimeMillis();
        long limit = props.getHeartbeatTimeoutSec() * 1000L;
        sessionManager.all().forEach((code, s) -> {
            if (now - s.getLastActiveMs().get() > limit) {
                log.warn("【南向TCP】看门狗踢线 robotCode={}", code);
                try {
                    s.getSocket().close();
                } catch (IOException ignored) {
                }
                sessionManager.unregister(code, s);
            }
        });
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        shutdown(acceptExecutor);
        shutdown(workerExecutor);
        shutdown(watchdog);
        log.info("【南向TCP】已停止");
    }

    private static Thread daemon(String name, Runnable r) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    private static void shutdown(ExecutorService es) {
        if (es != null) {
            es.shutdownNow();
        }
    }
}