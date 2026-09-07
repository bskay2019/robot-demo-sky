package com.robot.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.robot.demo.pojo.po.RobotTaskPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RobotTaskMapper extends BaseMapper<RobotTaskPO> {
    /**
     * 根据任务业务单号查询
     */
    RobotTaskPO selectByTaskNo(@Param("taskNo") String taskNo);

    /**
     * 更新任务状态为完成
     * @param id 主键id
     */
    void updateTaskStatusComplete(@Param("id") Long id);

    void setRobotIdle(@Param("robotCode") String robotCode);

}
