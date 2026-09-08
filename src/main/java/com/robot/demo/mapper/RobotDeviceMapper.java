package com.robot.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.robot.demo.pojo.po.RobotDevicePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RobotDeviceMapper extends BaseMapper<RobotDevicePO> {
    /**
     * 仅当机器人当前为空闲时，才改为工作中。
     * 返回影响行数：1=抢占成功，0=已被别人抢走。
     */
    int occupyIdleRobot(@Param("robotCode") String robotCode);
}
