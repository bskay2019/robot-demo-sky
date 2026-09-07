package com.robot.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.robot.demo.pojo.po.RobotDevicePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RobotDeviceMapper extends BaseMapper<RobotDevicePO> {
}
