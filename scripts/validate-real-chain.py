#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
真链端到端验证（只读检查 + 一笔真实登记）

链路：链上块高 -> 登录 -> 登记商品（发两笔链上交易）-> 查 chain_record
      -> 验真 JSON -> 手机 HTML 验真页

⚠️ 重要判定口径（踩过坑）：
   Mock 链模式也会生成 0x + 64 位十六进制的「伪交易哈希」，
   所以 **tx_hash 格式不能单独作为真链判据**。真正的判据是块高：
     - Mock   ：block_height 固定从 200000+ 起递增（内存模拟值，链上查不到）
     - 真链   ：block_height 是链上真实块高，可用 getBlockNumber 交叉核对
   本脚本用 SDK 证书走双向 TLS 直接调节点 JSON-RPC 拿链上块高做交叉验证。

前置条件：
  - 链节点已启动：bash scripts/check-real-chain.sh 全绿
  - 后端以真链模式启动（启动参数不要带 --fisco.enabled=false）
用法：C:/Users/Lenovo/.workbuddy/binaries/python/versions/3.13.12/python.exe scripts/validate-real-chain.py
"""

import json
import os
import re
import ssl
import subprocess
import sys
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080"
MYSQL = r"D:\dev\MySQL\MySQL Server 8.0\bin\mysql.exe"
FISCO_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "backend", "fisco"))


def http(method, path, body=None, token=None, timeout=90):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json;charset=UTF-8"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read().decode("utf-8", "ignore")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "ignore")
    except Exception as e:
        return -1, "%s: %s" % (type(e).__name__, e)


def sql(query):
    p = subprocess.run(
        [MYSQL, "-uroot", "-proot", "-D", "second_hand_chain", "-N", "-B",
         "--default-character-set=utf8mb4", "-e", query],
        capture_output=True,
    )
    return p.stdout.decode("utf-8", "ignore").strip()


def chain_block_number():
    """用 SDK 证书走双向 TLS，直接问节点当前块高。连不上返回 None。"""
    cfg = open(os.path.join(FISCO_DIR, "config.toml"), encoding="utf-8").read()
    m = re.search(r"peers\s*=\s*\[(.*?)\]", cfg, re.S)
    if not m:
        return None, "config.toml 未解析到 peers"
    peer = [x.strip().strip('"') for x in m.group(1).split(",") if x.strip()][0]
    host, port = peer.rsplit(":", 1)
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    ctx.load_cert_chain(os.path.join(FISCO_DIR, "sdk.crt"), os.path.join(FISCO_DIR, "sdk.key"))
    body = json.dumps({"jsonrpc": "2.0", "method": "getBlockNumber", "params": [], "id": 1}).encode()
    req = urllib.request.Request("https://%s:%s" % (host, port), data=body,
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=8, context=ctx) as r:
            res = json.loads(r.read().decode("utf-8", "ignore"))
            return int(res["result"]), "%s:%s" % (host, port)
    except Exception as e:
        return None, "%s (%s)" % (type(e).__name__, e)


def jbody(raw):
    try:
        return json.loads(raw)
    except Exception:
        return {}


def main():
    print("=" * 66)
    print(" 真链端到端验证")
    print("=" * 66)

    # 0) 直接问链：当前块高（双向 TLS + SDK 客户端证书）
    chain_h, rpc_info = chain_block_number()
    print("[0] 链上块高 = %s  来源 = %s" % (chain_h, rpc_info))
    if chain_h is None:
        print("    连不上链节点，请检查虚机/节点/IP；本次不做后续判定。")
        return 1

    # 1) 登录企业账号
    st, raw = http("POST", "/api/auth/login", {"username": "ent", "password": "123456"})
    login = jbody(raw)
    if st != 200 or login.get("code") != 200:
        print("[1] 登录失败 HTTP=%s body=%s" % (st, raw[:300]))
        return 1
    data = login.get("data") or {}
    token, ent_id = data.get("token"), data.get("enterpriseId")
    print("[1] 登录 OK  role=%s enterpriseId=%s" % (data.get("role"), ent_id))

    # 2) 登记一件商品（真链下会发 registerOwnership + saveEvidence 两笔交易）
    name = "真链联调验证-%s" % time.strftime("%m%d%H%M%S")
    payload = {
        "name": name,
        "category": "数码产品",
        "description": "真链模式自动验证用，用于核对 tx_hash / block_height / 验真结果",
        "enterpriseId": ent_id,
    }
    t0 = time.time()
    st, raw = http("POST", "/api/product/register", payload, token)
    cost = time.time() - t0
    reg = jbody(raw)
    print("[2] 登记 HTTP=%s code=%s 耗时=%.1fs" % (st, reg.get("code"), cost))
    if st != 200 or reg.get("code") != 200:
        print("    失败原因：%s" % raw[:500])
        return 1
    p = reg.get("data") or {}
    pid, code = p.get("productId"), p.get("code")
    print("    productId=%s code=%s qrContent=%s" % (pid, code, p.get("qrContent")))
    if not pid or not code:
        print("    登记返回缺少 productId / code，无法继续")
        return 1

    # 3) 查 chain_record，并把 DB 块高与链上块高交叉核对
    rows = sql(
        "SELECT evidence_id, biz_type, tx_hash, block_height, origin_hash "
        "FROM chain_record WHERE biz_id=%s ORDER BY chain_time DESC;" % pid
    )
    print("[3] chain_record（biz_id=%s）:" % pid)
    for line in rows.splitlines():
        print("    " + line)
    if not rows:
        print("    没有链上存证记录，判定失败")
        return 1
    fields = rows.splitlines()[0].split("\t")
    tx_hash = fields[2] if len(fields) > 2 else ""
    block_height = fields[3] if len(fields) > 3 else ""
    real_hash = bool(re.fullmatch(r"0x[0-9a-fA-F]{64}", tx_hash or ""))
    # ⚠️ 必须在登记之后再读一次链高：[0] 读到的是登记前的值，登记发两笔交易会把链高顶上去，
    #    拿登记前的链高去比登记后的 block_height 会误判失败。
    chain_h_after, _ = chain_block_number()
    latest = chain_h_after if chain_h_after is not None else chain_h
    real_block = str(block_height).isdigit() and 0 < int(block_height) <= latest
    # Mock 的 200000+ 模拟块高会在这里被判 false；真链块高与链上当前块高只差几块
    chain_ok = real_block and (latest - int(block_height)) <= 20
    print("    登记前链高=%s → 登记后链高=%s（登记发 2 笔交易，通常 +2）" % (chain_h, chain_h_after))
    print("    tx_hash 格式合法=%s (len=%d, prefix=%s)  ← Mock 也会伪造，不能单独判定" %
          (real_hash, len(tx_hash), tx_hash[:10]))
    print("    DB block_height=%s / 链上块高=%s  一致性=%s" % (block_height, latest, chain_ok))

    # 4) 验真 JSON（前端 axios 走这个）
    st, raw = http("GET", "/api/verify/scan?code=%s" % code)
    v = jbody(raw)
    vd = v.get("data") or {}
    authentic = vd.get("authentic")
    print("[4] /api/verify/scan  HTTP=%s code=%s authentic=%s" % (st, v.get("code"), authentic))
    print("    productName=%s" % vd.get("productName"))

    # 5) 手机扫码落地页
    st, page = http("GET", "/api/verify/view?code=%s" % code)
    page_ok = "验真通过" in page
    print("[5] /api/verify/view   HTTP=%s 含「验真通过」=%s bytes=%d" % (st, page_ok, len(page)))

    # 6) 结论
    print("=" * 66)
    ok = real_hash and real_block and chain_ok and authentic is True and page_ok
    if ok:
        print(" 结论：真链联调通过 —— DB 块高与链上块高一致、交易哈希为 SDK 回执真实值、验真通过。")
        print(" 本次验证商品：productId=%s  code=%s  name=%s" % (pid, code, name))
    else:
        print(" 结论：真链联调未通过，请逐项检查上面标 false 的项。")
        print(" 常见原因：后端仍带 --fisco.enabled=false / peers 指向旧 IP / 合约地址与链不一致。")
    print("=" * 66)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
