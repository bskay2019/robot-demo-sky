package com.robot.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.demo.enums.RobotStatusEnum;
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
        // 1. 先查 Redis
        Object cachedRobot = redisTemplate.opsForValue().get(IDLE_ROBOT_KEY);
        if (cachedRobot != null) {
            log.info("【Redis缓存命中】空闲机器人：{}", cachedRobot);
            return objectMapper.convertValue(cachedRobot, RobotDevicePO.class);
        }
        // 2. 再查 DB：空闲可能有多台，必须 LIMIT 1
        log.info("【Redis缓存未命中】查询数据库找空闲机器人...");
        LambdaQueryWrapper<RobotDevicePO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RobotDevicePO::getStatus, RobotStatusEnum.IDLE.getCode())
                .orderByDesc(RobotDevicePO::getBattery) // 可选：优先电量高的
                .last("LIMIT 1");
        // 第二个参数 false：即使万一查出多条也不抛 TooManyResultsException
        RobotDevicePO robot = getOne(wrapper, false);
        // 3. 写入缓存（5 分钟）
        if (robot != null) {
            redisTemplate.opsForValue().set(IDLE_ROBOT_KEY, robot, 5, TimeUnit.MINUTES);
            log.info("【Redis缓存写入】robotCode={}", robot.getRobotCode());
        }
        return robot;
    }

    @Override
    public void clearIdleRobotCache() {
       boolean deleted= redisTemplate.delete(IDLE_ROBOT_KEY);
        log.info("【Redis缓存清除】key={}, deleted={}", IDLE_ROBOT_KEY, deleted);
    }
}
