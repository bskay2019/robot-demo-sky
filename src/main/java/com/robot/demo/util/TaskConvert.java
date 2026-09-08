package com.robot.demo.util;

import com.robot.demo.enums.TaskStatusEnum;
import com.robot.demo.enums.TaskTypeEnum;
import com.robot.demo.pojo.po.RobotTaskPO;
import com.robot.demo.pojo.vo.TaskVO;

public final class TaskConvert {

    private TaskConvert() {
    }

    public static TaskVO pOtoVO(RobotTaskPO po) {
        if (po == null) {
            return null;
        }
        TaskVO vo = new TaskVO();
        vo.setId(po.getId());
        vo.setTaskNo(po.getTaskNo());
        vo.setRobotCode(po.getRobotCode());
        vo.setTaskType(po.getTaskType());
        vo.setTaskTypeName(TaskTypeEnum.getDescByCode(po.getTaskType()));
        vo.setTaskStatus(po.getTaskStatus());
        vo.setTaskStatusName(TaskStatusEnum.getDescByCode(po.getTaskStatus()));
        vo.setTargetPosition(po.getTargetPosition());
        vo.setRemark(po.getRemark());
        vo.setCreateTime(po.getCreateTime());
        vo.setUpdateTime(po.getUpdateTime());
        return vo;
    }
}