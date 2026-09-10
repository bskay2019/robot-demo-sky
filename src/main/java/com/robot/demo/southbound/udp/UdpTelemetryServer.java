package com.robot.demo.southbound.udp;

import com.robot.demo.southbound.config.SouthboundProperties;
import com.robot.demo.southbound.handler.TelemetryHandler;
import com.robot.demo.southbound.protocol.Packet;
import com.robot.demo.southbound.protocol.PacketCodec;
import com.robot.demo.southbound.protocol.ProtocolConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UDP服务器 接受南向机器人上传的udp状态包
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UdpTelemetryServer {

    private final SouthboundProperties props;
    private final TelemetryHandler telemetryHandler;

    private DatagramSocket datagramSocket;
    private ExecutorService loop;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @PostConstruct
    public void start() throws Exception {
        datagramSocket = new DatagramSocket(props.getUdpPort());
        loop = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sb-udp");
            t.setDaemon(true);
            return t;
        });
        running.set(true);
        loop.execute(this::receiveLoop);
        log.info("【南向UDP】监听端口 {}", props.getUdpPort());
    }

    private void receiveLoop() {
        byte[] buf = new byte[2048];
        while (running.get()) {
            try {
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                datagramSocket.receive(dp); // 阻塞等到一包

                Packet packet = PacketCodec.decode(dp.getData(), dp.getOffset(), dp.getLength());
                if (packet.getMsgType() != ProtocolConstants.MSG_TELEMETRY) {
                    continue;
                }
                String from = dp.getAddress().getHostAddress() + ":" + dp.getPort();
                telemetryHandler.onTelemetry(packet, from);
            } catch (Exception e) {
                if (running.get()) {
                    // 坏包直接丢，UDP 常见
                    log.trace("【南向UDP】坏包 {}", e.toString());
                }
            }
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (datagramSocket != null) {
            datagramSocket.close();
        }
        if (loop != null) {
            loop.shutdownNow();
        }
        log.info("【南向UDP】已停止");
    }
}