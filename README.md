# robot-demo-sky

机器人任务云端管理系统 Demo（Spring Boot）。面向学习与入职预习：覆盖北向 HTTP API、闲车分配、RabbitMQ 异步下发、南向 TCP/UDP 设备通信，以及任务完成回收全链路。

| 项 | 说明 |
|----|------|
| 工程名 | `robot-task-demo` |
| 技术栈 | Spring Boot 3.3 / Java 21、MyBatis-Plus、MySQL、Redis、RabbitMQ、JDK Socket |
| HTTP | `8080` |
| 南向 TCP | `9001`（登录 / 心跳 / 任务下发） |
| 南向 UDP | `9002`（遥测上报 / 任务完成） |

---

## 一、整体架构

```
┌─────────────┐  HTTP:8080   ┌──────────────────────────────────────┐
│ 调度端/脚本  │ ──────────► │  北向 Controller + Task/Device Service │
└─────────────┘              └──────────────┬───────────────────────┘
                                            │ 选空闲车(Redis+DB CAS)
                                            │ 落库任务
                                            ▼
                                     RabbitMQ 任务队列
                                            │
                                            ▼
                                   MQ Consumer（只负责下发）
                                            │
                     ┌──────────────────────┼──────────────────────┐
                     ▼                      ▼                      ▼
              TCP:9001 会话            UDP:9002 遥测           MySQL / Redis
           登录/心跳/任务下发        位置电量/任务完成          设备&任务状态
                     ▲                      │
                     └──────── 机器人(sim) ─┘
```

| 层次 | 职责 | 主要代码 |
|------|------|----------|
| 北向 | HTTP 创建/查询/取消任务、查设备 | `controller/` |
| 领域服务 | 选车、占车、任务状态机 | `service/impl/` |
| 异步通道 | 任务下发解耦、重试、死信 | `mq/` + `config/RabbitMqConfig` |
| 南向 | 设备长连接 + 高频遥测 | `southbound/` |
| 基础设施 | MySQL、Redis、配置 | `mapper/`、`application.yml` |

技术选型对入职学习够用；公司生产南向一般会换成 Netty / 自研网关，但业务骨架一致。

---

## 二、核心业务逻辑

### 1. 主链路（Happy Path）

1. **创建任务** `POST /api/task/create`
   - 生成业务单号 `taskNo`
   - `getIdleRobot()`：先查 Redis 闲车缓存，未命中再查 DB（空闲且电量高优先）
   - `occupyIdleRobot`：`status 0→1` 条件更新（CAS），防止并发双占同一台车
   - 任务落库为 **执行中**，绑定 `robotCode`
   - **事务提交后** 发 MQ（`afterCommit`，避免库未提交消费者先读）

2. **MQ 消费 → TCP 下发**
   - 仅处理状态为「执行中」的任务
   - `TcpMessageHandler.dispatchTask` 查找在线会话，发送 `MSG_TASK_DISPATCH`
   - 下发成功则 ACK；机器人不在线则重试，耗尽后进死信并释放车辆

3. **车端执行**（可用 `test_scripts/robot_sim.py` 模拟）
   - TCP 登录 / 心跳，收到任务后回 `TASK_ACK`
   - UDP 周期上报位置、电量、速度、`taskStatus`

4. **完成回收**
   - UDP 上报 `taskStatus=2` → 任务标为已完成、`setRobotIdle`、清空 Redis 闲车缓存
   - 下一单才能再次选到该车

### 2. 任务状态

| 码值 | 含义 |
|------|------|
| 0 | 待分配 |
| 1 | 执行中 |
| 2 | 已完成 |
| 3 | 失败 |
| 4 | 已取消 |

创建流程中 `PENDING` 多为中间变量，落库前通常已改为 `EXECUTING`。

### 3. 设备状态

| 码值 | 含义 |
|------|------|
| 0 | 空闲 |
| 1 | 工作中 |
| 2 | 故障（枚举已定义，业务中较少使用） |

占车依赖 DB CAS；空闲释放来自：任务取消、死信失败处理、或 UDP 上报完成。

### 4. 南向协议分工

| 通道 | 用途 |
|------|------|
| TCP `:9001` | 可靠通道：登录、心跳、任务下发 / 应答 |
| UDP `:9002` | 高频通道：遥测；任务完成态由此触发云端回收 |

统一二进制帧：

```
magic(4) + version(1) + msgType(2) + bodyLen(4) + UTF-8 JSON body
```

魔数 `0x524F4254`（ASCII `ROBT`），版本 `1`。

常见消息类型：

| msgType | 方向 | 含义 |
|---------|------|------|
| `0x0001` / `0x0002` | 车↔云 | 登录请求 / 响应 |
| `0x0003` / `0x0004` | 车↔云 | 心跳请求 / 响应 |
| `0x0010` / `0x0011` | 云↔车 | 任务下发 / 应答 |
| `0x0100` | 车→云 | UDP 遥测 |

---

## 三、模块与包结构

```
com.robot.demo
├── controller/          # 北向 REST、南向测试下发
├── service/             # 任务 / 设备业务
├── mapper/              # MyBatis-Plus + 自定义 SQL
├── mq/                  # 生产者、消费者、死信消费者
├── southbound/
│   ├── protocol/        # 常量、Packet、编解码
│   ├── session/         # 在线会话
│   ├── tcp/             # TCP Server
│   ├── udp/             # UDP Server
│   ├── handler/         # 登录/心跳/下发、遥测处理
│   └── config/          # 南向端口与 token
├── pojo/                # DTO / PO / VO
├── enums/               # 任务 / 设备 / 类型枚举
└── config/              # Redis、RabbitMQ、MyBatis 等
```

测试脚本（可选）：

- `src/main/resources/test_scripts/robot_sim.py`：多车 TCP/UDP 模拟器
- 可用 HTTP 脚本批量调用 `/api/task/create` 创建任务

---

## 四、主要北向接口

### 任务 `/api/task`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/create` | 创建任务（自动选闲车并发 MQ） |
| GET | `/list` | 任务列表 |
| GET | `/{taskNo}` | 按单号查询 |
| POST | `/{taskNo}/cancel` | 取消任务 |
| GET | `/page` | 分页查询 |

创建请求体示例：

```json
{
  "taskType": 1,
  "targetPosition": "A-12",
  "remark": "demo"
}
```

`taskType`：`1` 取货 / `2` 送货 / `3` 盘点。

### 设备 `/api/robot`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list` | 设备列表 |
| POST | `/add` | 新增设备 |
| GET | `/idle` | 查询一台空闲设备 |

### 南向测试 `/api/southbound/test`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/dispatch` | 绕过 MQ，直接 TCP 下发（联调用） |

---

## 五、中间件与配置要点

配置见 `src/main/resources/application.yml`：

| 组件 | 默认 |
|------|------|
| MySQL | `127.0.0.1:3306` / 库 `robot_demo` |
| Redis | `127.0.0.1:6379` |
| RabbitMQ | `127.0.0.1:5672` guest/guest，手动 ACK |
| 南向 | TCP `9001`，UDP `9002`，`login-token: demo-token` |

建表与种子数据：`src/main/resources/sql/init.sql`（默认种子车 `R001`~`R003`）。

RabbitMQ 概要：

- 交换机 / 队列：任务创建 → 消费下发
- 失败重试（带重试次数头），耗尽进入死信队列
- 死信消费：任务标失败并释放机器人

---

## 六、项目难点（学习 / 面试可展开）

### 1. 并发选车

多人同时建单不能分到同一台车。  
做法：**Redis 加速读 + DB 条件更新 CAS**。  
延伸点：缓存与 DB 不一致、占车失败清缓存重试——「缓存旁路 + 乐观锁」缩影。

### 2. 事务与 MQ 时序

若先发 MQ 再提交事务，消费者可能读不到任务。  
本项目使用 **`TransactionSynchronization.afterCommit`** 在提交后再投递。

### 3. 「下发」与「执行完成」解耦

- MQ：**只负责把任务下发到车**
- UDP：**上报完成才改任务状态并释放车辆**

状态机跨进程（云端 DB ↔ 车端行为），需接受最终一致性，不能把「TCP 发送成功」当成业务完成。

### 4. TCP 长连接会话

粘包 / 半包（定长头 + bodyLen）、心跳超时踢线、同车重连踢旧连接、多线程写 socket 同步。  
Demo 使用线程池 + `ServerSocket`；生产常见 Netty、鉴权、多协议版本。

### 5. MQ 至少一次 vs 重复下发

失败重试可能导致车端收到两次任务。  
Demo **尚无下发幂等键**（如按 `taskNo` 去重）——真实系统必须补齐。

### 6. UDP 不可靠

完成依赖 UDP，丢包时可能出现「车已完成、云端仍占用」。  
生产可改为：完成走 TCP、UDP + 补传、或超时巡检。

### 7. 取消与南向不同步

北向取消会释放 DB 空闲，但**当前无 TCP 取消帧**，车可能仍在执行。  
「云端状态」与「车端真实行为」不一致，是设备云常见难题。

---

## 七、与真实机器人云端的对照

| Demo 已覆盖 | 公司项目通常还会有 |
|-------------|-------------------|
| 任务创建 / 分配 / 下发 / 完成 | 多仓、地图、路径规划、交通管制 |
| TCP/UDP 南向雏形 | 设备网关集群、TLS、协议多版本 |
| Redis 闲车、MQ 重试死信 | 更细状态机、告警、运维大盘 |
| 单机会话 Map | 会话存 Redis / 网关，水平扩展 |

吃透本 Demo，有助于对齐真实架构关键词：**北向 API、设备在线会话、指令下行、遥测上行、任务状态机、资源占用与释放**。

---

## 八、本地快速联调

1. 启动 MySQL / Redis / RabbitMQ，执行 `sql/init.sql`
2. 启动 Spring Boot 应用 `RobotTaskApplication`
3. 启动模拟车：`python src/main/resources/test_scripts/robot_sim.py`（按需改顶部 IP / token / 车列表）
4. 创建任务：

```bash
curl -X POST "http://127.0.0.1:8080/api/task/create" \
  -H "Content-Type: application/json" \
  -d "{\"taskType\":1,\"targetPosition\":\"A-12\",\"remark\":\"demo\"}"
```

预期日志链：创建落库 → MQ 收到 → TCP 下发 → 模拟车 ACK → UDP 完成 → 释放空闲车。

---

## 九、一句话总结

- **架构**：北向 REST + 领域服务 + MQ 异步下发 + 南向 TCP/UDP，数据落 MySQL，闲车用 Redis。  
- **核心业务**：选空闲车 → 占车建单 → MQ 触发 TCP 下发 → UDP 报完成再释放。  
- **难点**：并发占车、事务后发 MQ、下发与完成分离、长连接与粘包、MQ 重试幂等、UDP 不可靠、取消与车端一致性。
