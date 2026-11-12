#!/usr/bin/env bash
# 真链联调前置检查：链节点 / SDK 证书 / config.toml / 当前运行模式
# 用法：bash scripts/check-real-chain.sh
# 说明：只做只读检查，不会停服务或改配置。全部 OK 后即可切到真链模式。
# 2026-11-11：IP 不再硬编码，直接从运行时 config.toml 的 peers 读取。
#            虚机换网段后只改 config.toml 即可，本脚本无需跟着改。

PY="C:/Users/Lenovo/.workbuddy/binaries/python/versions/3.13.12/python.exe"
BASE="$(cd "$(dirname "$0")/.." && pwd)"
FISCO="$BASE/backend/fisco"

echo "=============================================="
echo " FISCO BCOS 真链联调前置检查"
echo "=============================================="

# 1) 链节点端口（自动读 config.toml 的 peers，避免 IP 硬编码漂移）
echo "[1/3] 链节点端口（自动读取 config.toml 的 peers）"
# 用 stdin 读 config.toml，避开 Git Bash 的 /d/... 路径传给 Windows Python 会丢盘符的问题
"$PY" -c '
import socket, re, sys
cfg = sys.stdin.read()
m = re.search(r"peers\s*=\s*\[(.*?)\]", cfg, re.S)
if not m:
    print("  config.toml 未解析到 peers"); sys.exit(1)
peers = [x.strip().strip(chr(34)) for x in m.group(1).split(",") if x.strip()]
ok = 0
for addr in peers:
    host, port = addr.rsplit(":", 1)
    s = socket.socket(); s.settimeout(1.5)
    try:
        s.connect((host, int(port))); print("  %s  OK" % addr); ok += 1
    except Exception as e:
        print("  %s  FAIL (%s)" % (addr, type(e).__name__))
    finally:
        s.close()
sys.exit(0 if ok == len(peers) else 1)
' < "$FISCO/config.toml"
PORTS=$?

# 2) SDK 证书（必须保持 SDK 默认文件名 ca.crt / sdk.crt / sdk.key）
echo "[2/3] SDK 证书（$FISCO）"
CERT_OK=1
for f in ca.crt sdk.crt sdk.key ssl.crt ssl.key config.toml; do
  if [ -f "$FISCO/$f" ]; then
    printf '  %-12s OK\n' "$f"
  else
    printf '  %-12s MISSING\n' "$f"
    CERT_OK=0
  fi
done

# 3) 运行时配置指向
echo "[3/3] 运行时 config.toml 的 peers"
grep -n "^peers" "$FISCO/config.toml" 2>/dev/null | sed 's/^/  /'

echo "=============================================="
if [ "$PORTS" = "0" ] && [ "$CERT_OK" = "1" ]; then
  echo "结论：全部就绪，可以切真链。"
  echo ""
  echo "切换步骤（在 backend 目录下，先停掉现有 Mock 后端）："
  echo "  1) 停掉占用 8080 的 java 进程"
  echo "  2) 启动（注意：不要带 --fisco.enabled=false）"
  echo "     C:\\Users\\Lenovo\\.jdks\\openjdk-22\\bin\\java.exe -jar target\\goods-evidence-1.0.0.jar \\"
  echo "       --server.port=8080 --app.public-base-url=http://192.168.106.221:8080"
  echo "  3) 登记一件商品，确认 chain_record.tx_hash 为真实 0x 长哈希、block_height > 0"
  echo "  4) 调 /api/verify/scan?code=P... 确认为 authentic=true"
  exit 0
else
  echo "结论：链节点不可达或证书缺失，暂不能切真链。"
  echo "排查顺序：虚机是否开机 → bash nodes/127.0.0.1/start_all.sh → IP 是否漂移（改 config.toml 的 peers）。"
  echo "当前保持 Mock 链模式即可完整演示业务流程；修好后重跑本脚本再切。"
  exit 1
fi
