package com.robot.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.demo.mapper.RobotDeviceMapper;
import com.robot.demo.pojo.po.RobotDevicePO;
import com.robot.demo.service.RobotDeviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RobotDeviceServiceImpl extends ServiceImpl<RobotDeviceMapper, RobotDevicePO>  implements RobotDeviceService {

    // Redis 中缓存空闲机器人的 key
    private static final String IDLE_ROBOT_KEY = "robot:idle:one";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;



    @Override
    public RobotDevicePO getIdleRobot() {
        // 第一步：先查 Redis 缓存
        Object cachedRobot = redisTemplate.opsForValue().get(IDLE_ROBOT_KEY);
        if (cachedRobot != null) {
            log.info("【Redis缓存命中】从缓存取到空闲机器人：{}", cachedRobot);
            // 修复：LinkedHashMap → RobotDevicePO，禁止直接 (RobotDevicePO) 强转
            return objectMapper.convertValue(cachedRobot, RobotDevicePO.class);
        }

        // 第二步：缓存没有，查数据库
        log.info("【Redis缓存未命中】查询数据库找空闲机器人...");
        LambdaQueryWrapper<RobotDevicePO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RobotDevicePO::getStatus, 0); // 0=空闲
        RobotDevicePO robot = getOne(wrapper);

        // 第三步：查到后写入 Redis，缓存 5 分钟（防止脏数据）
        if (robot != null) {
            redisTemplate.opsForValue().set(IDLE_ROBOT_KEY, robot, 5, TimeUnit.MINUTES);
            log.info("【Redis缓存写入】空闲机器人已写入缓存，5分钟过期");
        }

        return robot;
    }

    @Override
    public void clearIdleRobotCache() {

    }
}
