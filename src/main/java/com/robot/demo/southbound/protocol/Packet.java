package com.robot.demo.southbound.protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Packet {
    private int msgType;
    private String bodyJson;
}
