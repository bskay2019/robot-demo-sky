package com.robot.demo.southbound.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "southbound")
public class SouthboundProperties {
    private int tcpPort=9001;

    private int udpPort=9002;

    private String loginToken="demo-token";

    private int heartbeatTimeoutSec=15;

    private int workerThreads=8;

}
