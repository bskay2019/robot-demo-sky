-- =============================================
-- 机器人搬运系统建表脚本 MySQL8.0
-- 数据库名：robot_demo
-- =============================================

-- 创建数据库，如果不存在
CREATE DATABASE IF NOT EXISTS robot_demo DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 使用该数据库
USE robot_demo;

DROP TABLE IF EXISTS robot_task;
DROP TABLE IF EXISTS robot_device;

-- 机器人设备表
CREATE TABLE robot_device (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
                              robot_code VARCHAR(64) NOT NULL COMMENT '机器人编码 R001',
                              robot_name VARCHAR(128) NOT NULL COMMENT '机器人名称',
                              status TINYINT NOT NULL DEFAULT 0 COMMENT '0空闲，1忙碌',
                              position VARCHAR(128) COMMENT '当前所在位置',
                              battery INT COMMENT '电池电量0‑100',
                              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              UNIQUE KEY uk_robot_code (robot_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='机器人设备';

-- 机器人任务表
CREATE TABLE robot_task (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
                            task_no VARCHAR(128) NOT NULL COMMENT '任务业务单号',
                            robot_code VARCHAR(64) NOT NULL COMMENT '分配机器人编码',
                            task_type TINYINT NOT NULL COMMENT '任务类型 1搬运',
                            task_status TINYINT NOT NULL DEFAULT 1 COMMENT '1执行中，2完成',
                            target_position VARCHAR(128) COMMENT '目标货架位置',
                            remark VARCHAR(255) COMMENT '备注',
                            create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            UNIQUE KEY uk_task_no (task_no),
                            INDEX idx_robot_code (robot_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='机器人任务表';

-- 初始化测试数据：空闲机器人 R001
INSERT INTO robot_device(robot_code,robot_name,status,position,battery)
VALUES ('R001','一号搬运机器人',0,'A区‑01货架',85);
