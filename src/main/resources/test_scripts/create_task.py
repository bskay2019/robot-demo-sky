#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
批量创建云端任务脚本。

改配置：只改下面 CONFIG。
运行：python create_tasks.py

依赖：Python3 标准库（urllib，无需 pip install）
"""

from __future__ import annotations

import json
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

# ========================= CONFIG（只改这里）=========================
# 云端 HTTP 地址（不是 TCP 9001）
CLOUD_BASE_URL = "http://127.0.0.1:8080"

# 一次脚本要创建的多个任务（按顺序创建）
# taskType: 1取货 2送货 3盘点
TASKS = [
    {"taskType": 1, "targetPosition": "A-12", "remark": "sim-batch-1"},
    {"taskType": 2, "targetPosition": "B-03", "remark": "sim-batch-2"},
    {"taskType": 1, "targetPosition": "C-01", "remark": "sim-batch-3"},
]

# 每个任务创建间隔（秒）。太快时可能连续抢同一台空闲车失败
CREATE_INTERVAL_SEC = 0.5

# 创建失败是否继续后面的任务
CONTINUE_ON_ERROR = True

# 创建成功后，是否再调南向测试接口把任务 TCP 下发给对应机器人
# True  : 需要 robot_sim.py 已登录在线
# False : 只走 /api/task/create（当前 MQ 消费者仍是 sleep 模拟，车端收不到 TCP）
ALSO_TCP_DISPATCH = True
# ====================================================================


def http_json(
    method: str,
    url: str,
    body: dict[str, Any] | None = None,
    timeout: float = 10.0,
) -> dict[str, Any]:
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"

    req = urllib.request.Request(url=url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="ignore")
        try:
            return json.loads(raw) if raw else {"code": e.code, "message": str(e)}
        except json.JSONDecodeError:
            return {"code": e.code, "message": raw or str(e)}
    except urllib.error.URLError as e:
        return {"code": -1, "message": f"连接失败: {e}"}


def create_task(task: dict[str, Any]) -> dict[str, Any]:
    url = f"{CLOUD_BASE_URL.rstrip('/')}/api/task/create"
    payload = {
        "taskType": int(task["taskType"]),
        "targetPosition": str(task["targetPosition"]),
        "remark": task.get("remark") or "",
    }
    print(f"[CREATE] request -> {payload}")
    result = http_json("POST", url, payload)
    print(f"[CREATE] response <- {json.dumps(result, ensure_ascii=False)}")
    return result


def tcp_dispatch(robot_code: str, task_no: str, task_type: int, target_position: str) -> dict[str, Any]:
    qs = urllib.parse.urlencode(
        {
            "robotCode": robot_code,
            "taskNo": task_no,
            "taskType": task_type,
            "targetPosition": target_position,
        }
    )
    url = f"{CLOUD_BASE_URL.rstrip('/')}/api/southbound/test/dispatch?{qs}"
    print(f"[DISPATCH] request -> {url}")
    result = http_json("POST", url, body=None)
    print(f"[DISPATCH] response <- {json.dumps(result, ensure_ascii=False)}")
    return result


def main() -> None:
    print("==== batch create tasks ====")
    print(f"cloud={CLOUD_BASE_URL}")
    print(f"task_count={len(TASKS)} also_tcp_dispatch={ALSO_TCP_DISPATCH}")

    ok_count = 0
    fail_count = 0

    for i, task in enumerate(TASKS, start=1):
        print(f"\n----- task {i}/{len(TASKS)} -----")
        result = create_task(task)

        code = result.get("code")
        data = result.get("data") or {}
        if code != 200 or not data:
            fail_count += 1
            print(f"[FAIL] create failed: {result.get('message')}")
            if not CONTINUE_ON_ERROR:
                break
            continue

        ok_count += 1
        task_no = data.get("taskNo")
        robot_code = data.get("robotCode")
        print(f"[OK] taskNo={task_no} robotCode={robot_code}")

        if ALSO_TCP_DISPATCH:
            if not robot_code or not task_no:
                print("[WARN] missing robotCode/taskNo, skip TCP dispatch")
            else:
                disp = tcp_dispatch(
                    robot_code=str(robot_code),
                    task_no=str(task_no),
                    task_type=int(task["taskType"]),
                    target_position=str(task["targetPosition"]),
                )
                if disp.get("code") != 200:
                    print(f"[WARN] TCP dispatch failed: {disp.get('message')}")

        if i < len(TASKS) and CREATE_INTERVAL_SEC > 0:
            time.sleep(CREATE_INTERVAL_SEC)

    print("\n==== done ====")
    print(f"success={ok_count} fail={fail_count}")


if __name__ == "__main__":
    main()