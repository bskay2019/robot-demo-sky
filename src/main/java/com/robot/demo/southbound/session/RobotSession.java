package com.robot.demo.southbound.session;

import lombok.Getter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;

@Getter
public class RobotSession{
    private final String robotCode;

    private final String sessionId;

    private final Socket socket;

    private final OutputStream outputStream;

    /** 最后活跃时间（心跳/收包时刷新） */
    private final AtomicLong  lastActiveMs=new AtomicLong(System.currentTimeMillis());

    public RobotSession(String robotCode, String sessionId, Socket socket) throws IOException {
        this.robotCode = robotCode;
        this.sessionId = sessionId;
        this.socket = socket;
        this.outputStream = socket.getOutputStream();
    }

    public void touch(){
        lastActiveMs.set(System.currentTimeMillis());
    }

    public synchronized void send(byte[]packetBytes) throws IOException {
        outputStream.write(packetBytes);
        outputStream.flush();
    }
}
