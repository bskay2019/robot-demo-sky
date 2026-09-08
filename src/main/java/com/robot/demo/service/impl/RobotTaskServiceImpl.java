package com.robot.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.robot.demo.enums.TaskStatusEnum;
import com.robot.demo.exception.BizException;
import com.robot.demo.mapper.RobotDeviceMapper;
import com.robot.demo.mapper.RobotTaskMapper;
import com.robot.demo.mq.RobotTaskProducer;
import com.robot.demo.pojo.dto.TaskCreateDTO;
import com.robot.demo.pojo.dto.TaskPageQueryDTO;
import com.robot.demo.pojo.po.RobotDevicePO;
import com.robot.demo.pojo.po.RobotTaskPO;
import com.robot.demo.pojo.vo.PageResult;
import com.robot.demo.pojo.vo.TaskVO;
import com.robot.demo.service.RobotDeviceService;
import com.robot.demo.service.RobotTaskService;
import com.robot.demo.util.TaskConvert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class RobotTaskServiceImpl extends ServiceImpl<RobotTaskMapper, RobotTaskPO>
        implements RobotTaskService {

    private final RobotDeviceService robotDeviceService;
    private final RobotDeviceMapper robotDeviceMapper;
    private final RobotTaskProducer robotTaskProducer;

    public RobotTaskServiceImpl(RobotDeviceService robotDeviceService,
                                RobotDeviceMapper robotDeviceMapper,
                                RobotTaskProducer robotTaskProducer) {
        this.robotDeviceService = robotDeviceService;
        this.robotDeviceMapper = robotDeviceMapper;
        this.robotTaskProducer = robotTaskProducer;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public TaskVO createTask(TaskCreateDTO dto) {
        log.info("【创建任务】taskType={}, target={}", dto.getTaskType(), dto.getTargetPosition());

        RobotTaskPO task = new RobotTaskPO();
        task.setTaskType(dto.getTaskType());
        task.setTargetPosition(dto.getTargetPosition());
        task.setRemark(dto.getRemark());

        String taskNo = "TASK" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        task.setTaskNo(taskNo);
        task.setTaskStatus(TaskStatusEnum.PENDING.getCode());

        RobotDevicePO idleRobot = robotDeviceService.getIdleRobot();
        if (idleRobot == null) {
            throw new BizException("没有空闲机器人可用，请稍后再试");
        }

        int occupied = robotDeviceMapper.occupyIdleRobot(idleRobot.getRobotCode());
        if (occupied == 0) {
            robotDeviceService.clearIdleRobotCache();
            throw new BizException("机器人刚刚被占用，请重试");
        }

        task.setRobotCode(idleRobot.getRobotCode());
        task.setTaskStatus(TaskStatusEnum.EXECUTING.getCode());
        save(task);
        log.info("【创建任务】落库成功 taskNo={}, robotCode={}", taskNo, idleRobot.getRobotCode());

        robotDeviceService.clearIdleRobotCache();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                robotTaskProducer.sendTaskMessage(taskNo);
            }
        });

        return TaskConvert.pOtoVO(task);
    }

    @Override
    public TaskVO getByTaskNo(String taskNo) {
        RobotTaskPO task = getBaseMapper().selectByTaskNo(taskNo);
        if (task == null) {
            throw new BizException("任务不存在：" + taskNo);
        }
        return TaskConvert.pOtoVO(task);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public TaskVO cancelTask(String taskNo) {
        log.info("【取消任务】taskNo={}", taskNo);

        RobotTaskPO task = getBaseMapper().selectByTaskNo(taskNo);
        if (task == null) {
            throw new BizException("任务不存在：" + taskNo);
        }

        Integer status = task.getTaskStatus();
        // 只有待分配、执行中可以取消
        if (!TaskStatusEnum.PENDING.getCode().equals(status)
                && !TaskStatusEnum.EXECUTING.getCode().equals(status)) {
            throw new BizException("当前状态不允许取消：" + TaskStatusEnum.getDescByCode(status));
        }

        // 乐观更新：防止并发下状态已变仍被取消
        boolean updated = lambdaUpdate()
                .set(RobotTaskPO::getTaskStatus, TaskStatusEnum.CANCELLED.getCode())
                .eq(RobotTaskPO::getId, task.getId())
                .in(RobotTaskPO::getTaskStatus,
                        TaskStatusEnum.PENDING.getCode(),
                        TaskStatusEnum.EXECUTING.getCode())
                .update();

        if (!updated) {
            throw new BizException("取消失败，任务状态已变更，请刷新后重试");
        }

        // 已占用机器人则释放
        if (StringUtils.hasText(task.getRobotCode())) {
            getBaseMapper().setRobotIdle(task.getRobotCode());
            robotDeviceService.clearIdleRobotCache();
            log.info("【取消任务】已释放机器人 robotCode={}", task.getRobotCode());
        }

        task.setTaskStatus(TaskStatusEnum.CANCELLED.getCode());
        log.info("【取消任务】成功 taskNo={}", taskNo);
        return TaskConvert.pOtoVO(task);
    }

    @Override
    public PageResult<TaskVO> pageTasks(TaskPageQueryDTO query) {
        int pageNum = query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : query.getPageSize();

        LambdaQueryWrapper<RobotTaskPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getTaskStatus() != null, RobotTaskPO::getTaskStatus, query.getTaskStatus())
                .eq(StringUtils.hasText(query.getRobotCode()), RobotTaskPO::getRobotCode, query.getRobotCode())
                .like(StringUtils.hasText(query.getTaskNo()), RobotTaskPO::getTaskNo, query.getTaskNo())
                .orderByDesc(RobotTaskPO::getCreateTime);

        Page<RobotTaskPO> page = page(new Page<>(pageNum, pageSize), wrapper);

        List<TaskVO> records = page.getRecords().stream()
                .map(TaskConvert::pOtoVO)
                .toList();

        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }
}