package com.robot.demo.southbound.protocol;

public final class ProtocolConstants {
    private ProtocolConstants() {
    }


    //防脏数据
    public static final int MAGIC= 0x524F4254;

    //版本协议
    public static final byte VERSION=1;

    /** 固定头长度：4+1+2+4 = 11 */
    public static final int  HEADER_LENGTH=11;

    //车→云
    //TCP 登录请求
    public static final int  MSG_LOGIN_REQ= 0x0001;

    //云→车
    //TCP 登录响应
    public static final int MSG_LOGIN_RESP=0x0002;

    //车→云
    //TCP 心跳
    public static final int MSG_HEARTBEAT_REQ=0x0003;

    //云→车
    //TCP 心跳应答
    public static final int MSG_HEARTBEAT_RESP=0x0004;

    //云→车
    //TCP 任务下发
    public static final int MSG_TASK_DISPATCH=0x0010;

    //车→云
    //TCP 任务应答
    public static final int MSG_TASK_ACK=0x0011;

    //车→云
    //UDP 实时上报
    public static final int MSG_TELEMETRY=0x0100;



}