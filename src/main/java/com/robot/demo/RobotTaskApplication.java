package com.robot.demo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.robot.demo.mapper")
public class RobotTaskApplication {
    public static void main(String[] args) {
        SpringApplication.run(RobotTaskApplication.class,args);
    }
}
