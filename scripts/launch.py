#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
商品交易存证系统 · 一键打开

给不熟悉命令行的人用：双击桌面 bat 就会调到这里。
特点：
  1) 幂等 —— 端口已经在跑就直接用，不会重复启动导致端口冲突
  2) 自动选链 —— 链节点可达就走真链，不可达自动降级 Mock 并明确提示
  3) 后台常驻 —— 用 DETACHED_PROCESS 拉起，关掉黑窗口服务也不会停
  4) 自动开浏览器
  5) 跨平台找 java / node —— 优先 JAVA_HOME，其次作者机器已知路径，最后 PATH，
     不写死 C:\\Users\\Lenovo，别人下载后也能用

参数：
  --no-browser   只启动/检查，不弹浏览器（自动化验证用）
  --check        只打印将要执行的命令，不真的启动（排错用）
  --mock         强制走 Mock 链
  --stop         停止 8080 / 5173 两个服务

用法（一般由 bat 调用，不用手敲）：
  python scripts/launch.py
"""

import os
import re
import shutil
import signal
import socket
import subprocess
import sys
import time
import webbrowser

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, ".."))
BACKEND = os.path.join(ROOT, "backend")
FRONTEND = os.path.join(ROOT, "frontend")
JAR = os.path.join(BACKEND, "target", "goods-evidence-1.0.0.jar")
VITE = os.path.join(FRONTEND, "node_modules", "vite", "bin", "vite.js")
CONFIG = os.path.join(BACKEND, "fisco", "config.toml")

# 作者机器上的已知位置，仅兜底；别人机器上这些路径不存在会自动走 JAVA_HOME / PATH
LOCAL_JAVA = r"C:\Users\Lenovo\.jdks\openjdk-22\bin\java.exe"
LOCAL_NODE = r"C:\Users\Lenovo\.workbuddy\binaries\node\versions\22.22.2-2\node.exe"

URL = "http://localhost:5173"
DETACHED = 0x00000008 | 0x00000200  # DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP


def resolve_java():
    """JAVA_HOME → 作者机器已知 JDK22 → PATH。
    注意作者机器 PATH 里的 java 是 1.8，跑不了 Spring Boot 3，不能优先用 PATH。"""
    jh = os.environ.get("JAVA_HOME")
    if jh:
        for name in ("java.exe", "java"):
            p = os.path.join(jh, "bin", name)
            if os.path.isfile(p):
                return p
    if os.path.isfile(LOCAL_JAVA):
        return LOCAL_JAVA
    return shutil.which("java") or "java"


def resolve_node():
    if os.path.isfile(LOCAL_NODE):
        return LOCAL_NODE
    return shutil.which("node") or "node"


JAVA = resolve_java()
NODE = resolve_node()


def port_open(host, port, timeout=1.0):
    try:
        s = socket.create_connection((host, port), timeout)
        s.close()
        return True
    except Exception:
        return False


def wait_port(port, seconds):
    end = time.time() + seconds
    while time.time() < end:
        if port_open("127.0.0.1", port):
            return True
        time.sleep(1)
    return False


def lan_ip():
    """用默认路由出口拿局域网 IP，比解析 ipconfig 稳（不受多块虚拟网卡干扰）。"""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("223.5.5.5", 80))
        return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"
    finally:
        s.close()


def chain_reachable():
    """从运行时 config.toml 读 peers，探测第一个节点是否在线。"""
    if not os.path.isfile(CONFIG):
        return False, "未配置 FISCO 证书（backend/fisco/config.toml），按 Mock 模式演示"
    try:
        cfg = open(CONFIG, encoding="utf-8").read()
        m = re.search(r"peers\s*=\s*\[(.*?)\]", cfg, re.S)
        if not m:
            return False, "config.toml 没解析到 peers"
        peers = [x.strip().strip('"') for x in m.group(1).split(",") if x.strip()]
        for addr in peers:
            host, port = addr.rsplit(":", 1)
            if port_open(host, int(port), 1.5):
                return True, addr
        return False, "节点 " + ", ".join(peers) + " 均不可达"
    except Exception as e:
        return False, "%s: %s" % (type(e).__name__, e)


def start_detached(cmd, cwd):
    return subprocess.Popen(
        cmd, cwd=cwd, creationflags=DETACHED,
        stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )


def listening_pid(port):
    """从 netstat 找监听指定端口的 PID。"""
    try:
        out = subprocess.run(["netstat", "-ano", "-p", "TCP"], capture_output=True).stdout.decode("gbk", "ignore")
        for line in out.splitlines():
            if re.search(r":%d\s" % port, line) and "LISTENING" in line.upper():
                m = re.search(r"(\d+)\s*$", line.strip())
                if m:
                    return int(m.group(1))
    except Exception:
        pass
    return None


def do_stop():
    for port in (8080, 5173):
        pid = listening_pid(port)
        if not pid:
            print("  %d 本来就没在运行" % port)
            continue
        try:
            os.kill(pid, signal.SIGTERM)
            print("  已停止 %d 端口的进程 PID=%s" % (port, pid))
        except Exception as e:
            print("  停止 %d 失败：%s" % (port, e))


def main():
    args = sys.argv[1:]
    if "--stop" in args:
        do_stop()
        return 0

    check = "--check" in args
    no_browser = "--no-browser" in args
    force_mock = "--mock" in args

    print("=" * 58)
    print("  商品交易存证系统 · 一键打开")
    print("=" * 58)
    print("  工程目录：%s" % ROOT)

    # 1) MySQL
    if port_open("127.0.0.1", 3306):
        print("  [1/4] MySQL 3306  OK")
    else:
        print("  [1/4] MySQL 3306 不可用！请先启动 MySQL 服务，否则系统连不上库")
        if not check:
            input("  按回车继续（或关掉窗口去启动 MySQL）...")

    # 2) 后端
    ip = lan_ip()
    base_url = "http://%s:8080" % ip
    chain_ok, chain_msg = chain_reachable()
    if force_mock:
        chain_ok, chain_msg = False, "启动参数指定 --mock"
    mode = "真实 FISCO BCOS 链" if chain_ok else "Mock 模拟链"

    if port_open("127.0.0.1", 8080):
        print("  [2/4] 后端 8080  已在运行，直接复用（不重复启动）")
    else:
        # application.yml 默认 fisco.enabled=false（方便别人下载后直接跑）；
        # 这里显式写死，避免环境变化导致本机答辩悄悄降级成 Mock
        cmd = [JAVA, "-jar", JAR, "--server.port=8080",
               "--app.public-base-url=" + base_url,
               "--fisco.enabled=" + ("true" if chain_ok else "false")]
        print("  [2/4] 后端未启动 → 模式：%s（%s）" % (mode, chain_msg))
        print("        %s" % " ".join(cmd))
        if not check:
            start_detached(cmd, BACKEND)
            if not wait_port(8080, 60):
                print("        X 60 秒内没等到 8080，请看后端日志或改用 --mock 重试")
                return 1
            print("        后端已就绪")

    # 3) 前端
    if port_open("127.0.0.1", 5173):
        print("  [3/4] 前端 5173  已在运行，直接复用")
    else:
        cmd = [NODE, VITE, "--host", "0.0.0.0", "--port", "5173", "--strictPort"]
        print("  [3/4] 前端未启动 → %s" % " ".join(cmd))
        if not check:
            start_detached(cmd, FRONTEND)
            if not wait_port(5173, 30):
                print("        X 30 秒内没等到 5173，检查 node_modules 是否完整")
                return 1
            print("        前端已就绪")

    # 4) 打开浏览器
    print("  [4/4] 访问地址")
    print("        本机浏览器：%s" % URL)
    print("        手机/别的电脑：http://%s:5173" % ip)
    print("        账号：admin / ent / user，密码都是 123456")
    if not chain_ok and not port_open("127.0.0.1", 8080):
        print("  提示：当前是 Mock 模拟链。要给老师看真实 tx_hash，先开 VMware 虚机再重新双击。")
    print("=" * 58)
    if not check and not no_browser:
        webbrowser.open(URL)
        print("  已帮你打开浏览器。这个黑窗口可以直接关掉，服务在后台继续跑。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
