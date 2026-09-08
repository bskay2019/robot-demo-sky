package com.robot.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.robot.demo.pojo.dto.TaskCreateDTO;
import com.robot.demo.pojo.dto.TaskPageQueryDTO;
import com.robot.demo.pojo.po.RobotTaskPO;
import com.robot.demo.pojo.vo.PageResult;
import com.robot.demo.pojo.vo.TaskVO;

public interface RobotTaskService extends IService<RobotTaskPO> {

    /**
     * 创建任务
     */
    TaskVO createTask(TaskCreateDTO dto);

    /**
     * 按任务单号查询
     */
    TaskVO getByTaskNo(String taskNo);

    /**
     * 取消任务（仅待分配/执行中可取消）
     */
    TaskVO cancelTask(String taskNo);

    /**
     * 分页查询任务
     */
    PageResult<TaskVO> pageTasks(TaskPageQueryDTO query);
}