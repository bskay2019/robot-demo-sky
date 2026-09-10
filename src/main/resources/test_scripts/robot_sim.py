#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
多机器人模拟器：TCP 登录/心跳/接任务 + UDP 遥测上报
- 每台车可配置不同速度、初始电量、耗电速率
- 电量按「车辆已运行时间」持续衰减（空闲/作业速率不同）

改配置：只改 CONFIG。
运行：python robot_sim.py
"""

from __future__ import annotations

import json
import math
import socket
import struct
import threading
import time
from typing import Any, Optional

# ========================= CONFIG（只改这里）=========================
CLOUD_HOST = "127.0.0.1"
TCP_PORT = 9001
UDP_PORT = 9002
LOGIN_TOKEN = "demo-token"

# 多台机器人：速度、电量、耗电都可不同
# speed_mps          : 作业时巡航速度 (m/s)，会反映到 UDP 的 speed 字段
# idle_speed_mps     : 空闲时速度（一般 0）
# battery            : 初始电量 0~100
# drain_idle_per_min : 空闲时每分钟掉电（按真实运行时间换算）
# drain_work_per_min : 作业时每分钟掉电（按真实运行时间换算）
ROBOTS = [
    {
        "robotCode": "R001",
        "position": "A-01",
        "battery": 95,
        "speed_mps": 1.2,
        "idle_speed_mps": 0.0,
        "drain_idle_per_min": 3.0,    # 空闲约每秒 -0.05%
        "drain_work_per_min": 30.0,   # 作业约每秒 -0.5%，8 秒任务约 -4%
    },
    {
        "robotCode": "R002",
        "position": "A-02",
        "battery": 88,
        "speed_mps": 0.9,
        "idle_speed_mps": 0.0,
        "drain_idle_per_min": 2.4,
        "drain_work_per_min": 24.0,
    },
    {
        "robotCode": "R003",
        "position": "B-01",
        "battery": 76,
        "speed_mps": 1.5,
        "idle_speed_mps": 0.0,
        "drain_idle_per_min": 3.6,
        "drain_work_per_min": 36.0,   # 最快车最费电
    },
]

TELEMETRY_INTERVAL_SEC = 1.0
TASK_DURATION_SEC = 8.0
HEARTBEAT_FALLBACK_SEC = 5

# 电量低于该值：速度降为巡航速度的一半（模拟低电限速）
LOW_BATTERY_THRESHOLD = 20
LOW_BATTERY_SPEED_FACTOR = 0.5
# ====================================================================

MAGIC = 0x524F4254
VERSION = 1
HEADER_LEN = 11

MSG_LOGIN_REQ = 0x0001
MSG_LOGIN_RESP = 0x0002
MSG_HEARTBEAT_REQ = 0x0003
MSG_HEARTBEAT_RESP = 0x0004
MSG_TASK_DISPATCH = 0x0010
MSG_TASK_ACK = 0x0011
MSG_TELEMETRY = 0x0100

STATUS_IDLE = 0
STATUS_WORKING = 1

TASK_EXECUTING = 1
TASK_COMPLETED = 2


def encode(msg_type: int, body: Optional[dict[str, Any]]) -> bytes:
    raw = b""
    if body is not None:
        raw = json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    return struct.pack(">IBHI", MAGIC, VERSION, msg_type & 0xFFFF, len(raw)) + raw


def recv_exact(sock: socket.socket, n: int) -> bytes:
    buf = bytearray()
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ConnectionError("TCP closed by peer")
        buf.extend(chunk)
    return bytes(buf)


def recv_packet(sock: socket.socket) -> tuple[int, dict[str, Any]]:
    header = recv_exact(sock, HEADER_LEN)
    magic, ver, msg_type, body_len = struct.unpack(">IBHI", header)
    if magic != MAGIC:
        raise ValueError(f"bad magic: 0x{magic:08x}")
    if ver != VERSION:
        raise ValueError(f"bad version: {ver}")
    if body_len < 0 or body_len > 1_000_000:
        raise ValueError(f"bad body_len: {body_len}")
    body_raw = recv_exact(sock, body_len) if body_len else b"{}"
    return msg_type, json.loads(body_raw.decode("utf-8") or "{}")


class RobotSimulator:
    def __init__(self, cfg: dict[str, Any]) -> None:
        self.robot_code = cfg["robotCode"]
        self.tcp: Optional[socket.socket] = None
        self.udp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.running = False

        self.session_id = ""
        self.udp_port = UDP_PORT
        self.hb_sec = HEARTBEAT_FALLBACK_SEC

        # 速度/电量参数（每车不同）
        self.cruise_speed = float(cfg.get("speed_mps", 1.0))
        self.idle_speed = float(cfg.get("idle_speed_mps", 0.0))
        self.drain_idle_per_min = float(cfg.get("drain_idle_per_min", 0.05))
        self.drain_work_per_min = float(cfg.get("drain_work_per_min", 0.8))

        self.initial_battery = float(cfg.get("battery", 90))
        self.battery = self.initial_battery
        self.position = cfg.get("position", "A-01")
        self.status = STATUS_IDLE
        self.task_no: Optional[str] = None
        self.task_status: Optional[int] = None
        self.speed = self.idle_speed
        self.x = 0.0
        self.y = 0.0
        self.heading = 0.0

        # 运行时间计量：电量按真实流逝时间衰减
        self._boot_ts = time.monotonic()
        self._last_drain_ts = self._boot_ts
        self._lock = threading.Lock()

    def _log(self, msg: str) -> None:
        print(f"[{self.robot_code}] {msg}", flush=True)

    def _current_speed(self) -> float:
        """根据状态 + 电量计算当前速度（每车巡航速度不同）。"""
        if self.status != STATUS_WORKING:
            return self.idle_speed
        spd = self.cruise_speed
        if self.battery < LOW_BATTERY_THRESHOLD:
            spd *= LOW_BATTERY_SPEED_FACTOR
        return spd

    def _drain_battery_by_runtime(self) -> None:
        """
        核心：按车辆运行时间衰减电量。
        每次调用用「距离上次结算」的真实秒数 * 对应耗电速率。
        """
        now = time.monotonic()
        dt = now - self._last_drain_ts
        if dt <= 0:
            return
        self._last_drain_ts = now

        # 每分钟耗电 -> 换算成每秒
        if self.status == STATUS_WORKING:
            rate_per_sec = self.drain_work_per_min / 60.0
        else:
            rate_per_sec = self.drain_idle_per_min / 60.0

        self.battery = max(0.0, self.battery - rate_per_sec * dt)

    def _uptime_sec(self) -> float:
        return time.monotonic() - self._boot_ts

    def connect_and_login(self) -> None:
        self._log(f"TCP connect {CLOUD_HOST}:{TCP_PORT}")
        self.tcp = socket.create_connection((CLOUD_HOST, TCP_PORT), timeout=10)
        self.tcp.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)

        req = {
            "robotCode": self.robot_code,
            "token": LOGIN_TOKEN,
            "firmware": "py-sim-1.1.0",
            "ts": int(time.time() * 1000),
        }
        self.tcp.sendall(encode(MSG_LOGIN_REQ, req))

        msg_type, body = recv_packet(self.tcp)
        if msg_type != MSG_LOGIN_RESP or not body.get("ok"):
            raise RuntimeError(f"login failed: type=0x{msg_type:04x} body={body}")

        self.session_id = body.get("sessionId") or ""
        self.hb_sec = int(body.get("heartbeatSec") or HEARTBEAT_FALLBACK_SEC)
        self.udp_port = int(body.get("udpPort") or UDP_PORT)
        self._log(
            f"login ok session={self.session_id} hb={self.hb_sec}s udp={self.udp_port} "
            f"cruise={self.cruise_speed}m/s bat0={self.initial_battery}"
        )

    def _send_tcp(self, msg_type: int, body: dict[str, Any]) -> None:
        assert self.tcp is not None
        self.tcp.sendall(encode(msg_type, body))

    def heartbeat_loop(self) -> None:
        while self.running:
            try:
                self._send_tcp(
                    MSG_HEARTBEAT_REQ,
                    {"robotCode": self.robot_code, "ts": int(time.time() * 1000)},
                )
            except OSError as e:
                self._log(f"heartbeat error: {e}")
                self.running = False
                break
            time.sleep(max(1, self.hb_sec))

    def telemetry_loop(self) -> None:
        while self.running:
            with self._lock:
                # 1) 先按运行时间结算电量
                self._drain_battery_by_runtime()

                # 2) 再算当前速度，并做简单位移
                self.speed = self._current_speed()
                # 用本拍间隔近似积分位移（也可用更细的物理循环）
                self.x += self.speed * TELEMETRY_INTERVAL_SEC
                if self.status == STATUS_WORKING and self.speed > 0:
                    self.heading = (self.heading + 3.0) % 360.0

                body = {
                    "robotCode": self.robot_code,
                    "sessionId": self.session_id,
                    "ts": int(time.time() * 1000),
                    "position": self.position,
                    "battery": int(math.floor(self.battery)),
                    "status": self.status,
                    "taskNo": self.task_no,
                    "taskStatus": self.task_status,
                    "speed": round(self.speed, 3),
                    "x": round(self.x, 2),
                    "y": round(self.y, 2),
                    "heading": round(self.heading, 1),
                    # 额外字段：便于你在日志里观察「按时间衰减」
                    "uptimeSec": int(self._uptime_sec()),
                    "batteryExact": round(self.battery, 3),
                }

            try:
                self.udp.sendto(encode(MSG_TELEMETRY, body), (CLOUD_HOST, self.udp_port))
                self._log(
                    f"UDP pos={body['position']} bat={body['battery']} "
                    f"({body['batteryExact']}%) speed={body['speed']}m/s "
                    f"uptime={body['uptimeSec']}s "
                    f"status={body['status']} task={body['taskNo']}"
                )
            except OSError as e:
                self._log(f"udp error: {e}")
                self.running = False
                break

            time.sleep(TELEMETRY_INTERVAL_SEC)

    def tcp_reader_loop(self) -> None:
        assert self.tcp is not None
        while self.running:
            try:
                msg_type, body = recv_packet(self.tcp)
            except Exception as e:
                self._log(f"tcp read end: {e}")
                self.running = False
                break

            if msg_type == MSG_HEARTBEAT_RESP:
                continue

            if msg_type == MSG_TASK_DISPATCH:
                self._log(f"TASK_DISPATCH {body}")
                self._on_task_dispatch(body)
                continue

            self._log(f"unknown msg 0x{msg_type:04x} {body}")

    def _on_task_dispatch(self, body: dict[str, Any]) -> None:
        task_no = body.get("taskNo")
        target = body.get("targetPosition") or self.position
        accepted = body.get("robotCode") == self.robot_code

        ack = {
            "taskNo": task_no,
            "robotCode": self.robot_code,
            "accepted": accepted,
            "reason": "" if accepted else "robotCode mismatch",
        }
        try:
            self._send_tcp(MSG_TASK_ACK, ack)
            self._log(f"TASK_ACK {ack}")
        except OSError as e:
            self._log(f"TASK_ACK failed: {e}")
            return

        if not accepted or not task_no:
            return

        with self._lock:
            # 切到作业态前先结算一段空闲耗电，避免状态切换瞬间少扣/多扣
            self._drain_battery_by_runtime()
            self.task_no = str(task_no)
            self.task_status = TASK_EXECUTING
            self.status = STATUS_WORKING
            self.position = str(target)
            self.speed = self._current_speed()

        threading.Thread(
            target=self._finish_task_later,
            args=(str(task_no),),
            name=f"{self.robot_code}-task",
            daemon=True,
        ).start()

    def _finish_task_later(self, task_no: str) -> None:
        # 任务时长也可随车速略有差异：更快完成稍快（可选）
        duration = TASK_DURATION_SEC * (1.0 / max(self.cruise_speed, 0.3))
        # 上面会让慢车更久；若想所有车固定时长，改回：duration = TASK_DURATION_SEC
        duration = max(3.0, min(20.0, duration))

        self._log(f"executing {task_no} for {duration:.1f}s (cruise={self.cruise_speed})")
        time.sleep(duration)

        with self._lock:
            if self.task_no != task_no:
                return
            self._drain_battery_by_runtime()
            self.task_status = TASK_COMPLETED
            self.status = STATUS_IDLE
            self.speed = self.idle_speed
            self._log(f"completed {task_no}, battery={self.battery:.2f}%")

        time.sleep(TELEMETRY_INTERVAL_SEC + 0.2)
        with self._lock:
            if self.task_no == task_no:
                self.task_no = None
                self.task_status = None

    def run(self) -> None:
        try:
            self.connect_and_login()
        except Exception as e:
            self._log(f"startup failed: {e}")
            self._cleanup()
            return

        # 登录成功后重新对齐耗电计时，避免登录等待被算进去太多
        now = time.monotonic()
        self._boot_ts = now
        self._last_drain_ts = now

        self.running = True
        threading.Thread(
            target=self.heartbeat_loop, name=f"{self.robot_code}-hb", daemon=True
        ).start()
        threading.Thread(
            target=self.telemetry_loop, name=f"{self.robot_code}-udp", daemon=True
        ).start()

        try:
            self.tcp_reader_loop()
        finally:
            self._cleanup()

    def _cleanup(self) -> None:
        self.running = False
        if self.tcp is not None:
            try:
                self.tcp.close()
            except OSError:
                pass
            self.tcp = None
        try:
            self.udp.close()
        except OSError:
            pass
        self._log("stopped")


def main() -> None:
    print("==== robot multi-sim start ====")
    print(f"cloud={CLOUD_HOST} tcp={TCP_PORT} udp={UDP_PORT} token={LOGIN_TOKEN}")
    for r in ROBOTS:
        print(
            f"  - {r['robotCode']}: bat={r.get('battery')} "
            f"speed={r.get('speed_mps')}m/s "
            f"drain_idle={r.get('drain_idle_per_min')}/min "
            f"drain_work={r.get('drain_work_per_min')}/min"
        )

    threads: list[threading.Thread] = []
    for cfg in ROBOTS:
        sim = RobotSimulator(cfg)
        t = threading.Thread(target=sim.run, name=f"robot-{cfg['robotCode']}", daemon=True)
        t.start()
        threads.append(t)
        time.sleep(0.2)

    try:
        while any(t.is_alive() for t in threads):
            time.sleep(0.5)
    except KeyboardInterrupt:
        print("\n==== interrupted, exiting ====")


if __name__ == "__main__":
    main()