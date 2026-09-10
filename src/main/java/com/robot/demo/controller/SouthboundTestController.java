package com.robot.demo.controller;

import com.robot.demo.southbound.handler.TcpMessageHandler;
import com.robot.demo.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/southbound/test")
@RequiredArgsConstructor
public class SouthboundTestController {

    private final TcpMessageHandler tcpMessageHandler;

    @PostMapping("/dispatch")
    public Result<String> dispatch(@RequestParam String robotCode,
                                   @RequestParam String taskNo,
                                   @RequestParam(defaultValue = "1") int taskType,
                                   @RequestParam(defaultValue = "A-12") String targetPosition) {
        boolean ok = tcpMessageHandler.dispatchTask(
                robotCode, taskNo, taskType, targetPosition, "manual-test");
        return ok ? Result.success("已下发") : Result.error("机器人不在线或发送失败");
    }
}