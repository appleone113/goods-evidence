#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
答辩全流程演练（真链模式 · 2026-11-11）

与 validate-real-chain.py 的区别：那个只验证「能不能连上真链」，
这个按答辩当天的讲解顺序把九个模块真走一遍，并且每一步都穿透到
MySQL / 链上核对（对应验收口径：别只看页面渲染，要看库里有没有数据）。

流程：
  0  环境自检（链上块高，双向 TLS）
  1  三角色登录 ENTERPRISE / USER / ADMIN
  2  上传商品实物图
  3  商品登记（一物一码）
  4  登记后立即验真（建立缓存，记录履历事件数）
  5  质检录入 → 6 维修录入 → 7 权属转移
  8  全流程后验真（事件数应增加，实证写操作缓存失效）
  9  全生命周期履历
 10  手机扫码 HTML 验真页
 11  监管看板 / 趋势图 / 一致性巡检 / 告警
 12  安全对抗：篡改 trade_record.buyer → 巡检点名 → 还原 → 复检
 13  链上核对：四类 biz_type 的真实 tx_hash / block_height / 块高增量

⚠️ 第 12 步会直接 UPDATE 业务表，脚本用 try/finally 保证无论中途是否异常都还原。

用法：C:/Users/Lenovo/.workbuddy/binaries/python/versions/3.13.12/python.exe scripts/rehearsal-full-flow.py
输出：控制台逐步结果 + scripts/rehearsal-result.json
"""

import base64
import json
import os
import re
import ssl
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

BASE = "http://127.0.0.1:8080"
MYSQL = r"D:\dev\MySQL\MySQL Server 8.0\bin\mysql.exe"
HERE = os.path.dirname(os.path.abspath(__file__))
FISCO_DIR = os.path.abspath(os.path.join(HERE, "..", "backend", "fisco"))
OUT_JSON = os.path.join(HERE, "rehearsal-result.json")

RESULTS = []
CONTEXT = {}


def step(name, ok, detail=""):
    RESULTS.append({"step": name, "ok": bool(ok), "detail": str(detail)})
    print(("  [OK]   " if ok else "  [FAIL] ") + name + "  " + str(detail)[:320])
    return bool(ok)


def j(raw):
    try:
        return json.loads(raw)
    except Exception:
        return {}


def http(method, path, body=None, token=None,
         ctype="application/json;charset=UTF-8", timeout=90):
    if isinstance(body, (dict, list)):
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    elif isinstance(body, str):
        data = body.encode("utf-8")
    elif isinstance(body, bytes):
        data = body
    else:
        data = None
    headers = {"Content-Type": ctype}
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


def form(fields):
    """@ModelAttribute 接口用 urlencoded 表单提交，中文按 UTF-8 编码。"""
    return urllib.parse.urlencode(fields), "application/x-www-form-urlencoded; charset=UTF-8"


def sql(query):
    p = subprocess.run(
        [MYSQL, "-uroot", "-proot", "-D", "second_hand_chain", "-N", "-B",
         "--default-character-set=utf8mb4", "-e", query],
        capture_output=True,
    )
    return p.stdout.decode("utf-8", "ignore").strip()


def chain_height():
    cfg = open(os.path.join(FISCO_DIR, "config.toml"), encoding="utf-8").read()
    m = re.search(r"peers\s*=\s*\[(.*?)\]", cfg, re.S)
    if not m:
        return None
    peer = [x.strip().strip('"') for x in m.group(1).split(",") if x.strip()][0]
    host, port = peer.rsplit(":", 1)
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    ctx.load_cert_chain(os.path.join(FISCO_DIR, "sdk.crt"), os.path.join(FISCO_DIR, "sdk.key"))
    body = json.dumps({"jsonrpc": "2.0", "method": "getBlockNumber", "params": [], "id": 1}).encode()
    req = urllib.request.Request("https://%s:%s" % (host, port), data=body,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=8, context=ctx) as r:
        return int(json.loads(r.read().decode())["result"])


def login(username, password):
    st, raw = http("POST", "/api/auth/login", {"username": username, "password": password})
    d = j(raw)
    if st != 200 or d.get("code") != 200:
        raise RuntimeError("登录 %s 失败: %s" % (username, raw[:200]))
    return d["data"]["token"], d["data"]


def ok_result(st, raw):
    d = j(raw)
    return st == 200 and d.get("code") == 200, d


def upload_png(token):
    """手写 multipart/form-data，避免为演练额外装第三方库。"""
    png = base64.b64decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    )
    boundary = "----rehearsal" + uuid.uuid4().hex
    b = boundary.encode("ascii")
    body = (b"--" + b + b"\r\n"
            + b'Content-Disposition: form-data; name="file"; filename="rehearsal.png"\r\n'
            + b"Content-Type: image/png\r\n\r\n"
            + png + b"\r\n"
            + b"--" + b + b"--\r\n")
    return http("POST", "/api/product/upload-image", body, token,
                ctype="multipart/form-data; boundary=" + boundary)


def run():
    h0 = chain_height()
    if h0 is None:
        raise RuntimeError("连不上链节点，确认真链模式与 config.toml 的 peers")
    step("0 环境自检", True, "链上块高=%s（双向 TLS getBlockNumber）" % h0)

    ent, ent_data = login("ent", "123456")
    user, _ = login("user", "123456")
    admin, _ = login("admin", "123456")
    step("1 三角色登录", True, "ENTERPRISE / USER / ADMIN 均 code=200，enterpriseId=%s" % ent_data.get("enterpriseId"))

    st, raw = upload_png(ent)
    ok, d = ok_result(st, raw)
    image_url = d.get("data")
    step("2 上传商品实物图", ok and bool(image_url), image_url or raw[:200])

    name = "答辩全流程演练-%s" % time.strftime("%m%d%H%M%S")
    payload = {
        "name": name,
        "category": "数码产品",
        "description": "真链模式下按答辩顺序走完九个模块的演练样品",
        "imageUrl": image_url,
        "enterpriseId": ent_data.get("enterpriseId"),
    }
    st, raw = http("POST", "/api/product/register", payload, ent)
    ok, d = ok_result(st, raw)
    p = d.get("data") or {}
    pid, code = p.get("productId"), p.get("code")
    CONTEXT.update(pid=pid, code=code, name=name)
    step("3 商品登记（一物一码）", ok and bool(pid and code),
         "productId=%s code=%s qr=%s" % (pid, code, p.get("qrContent")))
    if not pid:
        raise RuntimeError("登记失败，后续步骤无法进行：%s" % raw[:300])

    # 4 登记后立刻验真：建立缓存 + 记录事件数基线
    st, raw = http("GET", "/api/verify/scan?code=%s" % code, token=user)
    ok, d = ok_result(st, raw)
    v0 = d.get("data") or {}
    ev0 = len((v0.get("lifecycle") or {}).get("events") or [])
    step("4 登记后立即验真", ok and v0.get("authentic") is True,
         "authentic=%s 履历事件数=%s" % (v0.get("authentic"), ev0))

    # 5 质检
    body, ct = form({"productId": pid, "org": "国家质检中心", "result": "合格"})
    st, raw = http("POST", "/api/quality/add", body, ent, ctype=ct)
    ok, d = ok_result(st, raw)
    qid = d.get("data")
    qrow = sql("SELECT quality_id,org,result FROM quality_record WHERE product_id=%s ORDER BY quality_id DESC LIMIT 1" % pid)
    step("5 质检录入", ok and bool(qid) and "国家质检中心" in qrow and "合格" in qrow,
         "qualityId=%s DB=%s" % (qid, qrow))

    # 6 维修
    body, ct = form({"productId": pid, "repairer": "官方维修点", "content": "更换电池，功能复检正常"})
    st, raw = http("POST", "/api/repair/add", body, ent, ctype=ct)
    ok, d = ok_result(st, raw)
    rid = d.get("data")
    rrow = sql("SELECT repair_id,repairer,content FROM repair_record WHERE product_id=%s ORDER BY repair_id DESC LIMIT 1" % pid)
    step("6 维修录入", ok and bool(rid) and "官方维修点" in rrow,
         "repairId=%s DB=%s" % (rid, rrow))

    # 7 权属转移
    buyer = "答辩演练买家"
    qs = urllib.parse.urlencode({"productId": pid, "buyer": buyer})
    st, raw = http("POST", "/api/trade/transfer?" + qs, None, ent)
    ok, d = ok_result(st, raw)
    tid = d.get("data")
    prod = sql("SELECT current_owner,status FROM product WHERE product_id=%s" % pid)
    step("7 权属转移", ok and bool(tid) and buyer in prod and prod.endswith("\t3"),
         "tradeId=%s DB(current_owner,status)=%s" % (tid, prod))

    # 8 全流程后验真：事件数必须增加（实证写操作后缓存失效）
    st, raw = http("GET", "/api/verify/scan?code=%s" % code, token=user)
    ok, d = ok_result(st, raw)
    v1 = d.get("data") or {}
    ev1 = len((v1.get("lifecycle") or {}).get("events") or [])
    step("8 全流程后验真（缓存失效实证）", ok and v1.get("authentic") is True and ev1 > ev0,
         "authentic=%s 事件数 %s → %s（增加=%s）" % (v1.get("authentic"), ev0, ev1, ev1 - ev0))

    # 9 履历
    st, raw = http("GET", "/api/trace/%s" % pid, token=user)
    ok, d = ok_result(st, raw)
    life = d.get("data") or {}
    events = life.get("events") or []
    step("9 全生命周期履历", ok and len(events) >= 4,
         "事件数=%s 状态=%s" % (len(events), life.get("status")))

    # 10 手机 HTML 验真页
    st, page = http("GET", "/api/verify/view?code=%s" % code)
    page_ok = st == 200 and "验真通过" in page and "<img" in page
    step("10 手机扫码 HTML 验真页", page_ok,
         "HTTP=%s bytes=%s 含验真通过=%s 含商品图=%s" %
         (st, len(page), "验真通过" in page, "<img" in page))

    # 11 监管看板四个接口
    st, raw = http("GET", "/api/admin/dashboard", token=admin)
    ok1, dash = ok_result(st, raw)
    st, raw = http("GET", "/api/admin/trade-trend?days=7", token=admin)
    ok2, trend = ok_result(st, raw)
    st, raw = http("GET", "/api/admin/integrity/check", token=admin)
    ok3, base_integrity = ok_result(st, raw)
    st, raw = http("GET", "/api/admin/alerts", token=admin)
    ok4, alerts = ok_result(st, raw)
    step("11 监管看板 / 趋势图 / 巡检 / 告警",
         ok1 and ok2 and ok3 and ok4,
         "dashboard=%s 趋势点数=%s 巡检=%s 告警字段=%s" %
         (list((dash.get("data") or {}).keys())[:6],
          len((trend.get("data") or [])),
          {k: (base_integrity.get("data") or {}).get(k) for k in ("checkedCount", "passCount", "mismatchCount", "missingCount")},
          list((alerts.get("data") or {}).keys())[:6]))

    # 12 安全对抗：篡改 buyer → 巡检点名 → 还原 → 复检
    trade_row = sql("SELECT trade_id,buyer FROM trade_record WHERE product_id=%s ORDER BY trade_id DESC LIMIT 1" % pid)
    if not trade_row:
        raise RuntimeError("找不到本次转移的 trade_record")
    parts = trade_row.split("\t")
    tamper_tid, orig_buyer = parts[0], parts[1]
    CONTEXT.update(tamper_tid=tamper_tid, orig_buyer=orig_buyer)
    base_mismatch = (base_integrity.get("data") or {}).get("mismatchCount", 0)
    sql("UPDATE trade_record SET buyer='ATTACKER-演练' WHERE trade_id=%s" % tamper_tid)
    st, raw = http("GET", "/api/admin/integrity/check", token=admin)
    _, tampered = ok_result(st, raw)
    td = tampered.get("data") or {}
    hit = any(str(it.get("productId")) == str(pid) for it in (td.get("items") or []))
    step("12 链下篡改检测（TC-07）", td.get("mismatchCount", 0) > base_mismatch and hit,
         "mismatch %s → %s，点名到本次商品=%s" % (base_mismatch, td.get("mismatchCount"), hit))
    sql("UPDATE trade_record SET buyer='%s' WHERE trade_id=%s" % (orig_buyer, tamper_tid))
    CONTEXT["tamper_restored"] = True
    st, raw = http("GET", "/api/admin/integrity/check", token=admin)
    _, restored = ok_result(st, raw)
    rd = restored.get("data") or {}
    step("12b 还原后复检", rd.get("mismatchCount", -1) == base_mismatch,
         "mismatch 回到 %s" % rd.get("mismatchCount"))
    st, raw = http("GET", "/api/verify/scan?code=%s" % code, token=user)
    ok, d = ok_result(st, raw)
    step("12c 还原后验真", ok and (d.get("data") or {}).get("authentic") is True,
         "authentic=%s" % (d.get("data") or {}).get("authentic"))

    # 13 链上凭证核对
    rows = sql("SELECT biz_type,tx_hash,block_height FROM chain_record WHERE biz_id=%s ORDER BY chain_time" % pid)
    parsed = [l.split("\t") for l in rows.splitlines() if l]
    types = {r[0] for r in parsed}
    hashes_ok = all(re.fullmatch(r"0x[0-9a-fA-F]{64}", r[1] or "") for r in parsed)
    h1 = chain_height()
    heights = [int(r[2]) for r in parsed if str(r[2]).isdigit()]
    # 真链块高不会落在 Mock 的 200000+ 模拟段里
    heights_ok = bool(heights) and max(heights) <= h1 and min(heights) < 200000
    step("13 链上凭证核对", {"product", "quality", "repair", "trade"} <= types and hashes_ok and heights_ok and h1 > h0,
         "链高 %s→%s，本次 %s 条存证 %s，块高=%s" %
         (h0, h1, len(parsed), sorted(types), heights))
    CONTEXT["chain_records"] = parsed
    CONTEXT["height_before"] = h0
    CONTEXT["height_after"] = h1

    # 商品图链路 DB 核对
    img_db = sql("SELECT image_url FROM product WHERE product_id=%s" % pid)
    step("14 商品图落库核对", bool(image_url) and image_url in img_db, "DB image_url=%s" % img_db)


def main():
    print("=" * 72)
    print(" 答辩全流程演练 · 真链模式")
    print("=" * 72)
    try:
        run()
    except Exception as e:
        step("异常中断", False, "%s: %s" % (type(e).__name__, e))
    finally:
        # 无论中途是否异常，一定把篡改过的 buyer 还原
        tid, orig = CONTEXT.get("tamper_tid"), CONTEXT.get("orig_buyer")
        if tid and orig and not CONTEXT.get("tamper_restored"):
            sql("UPDATE trade_record SET buyer='%s' WHERE trade_id=%s" % (orig, tid))
            print("  [还原] 已把 trade_record.buyer 恢复为 %s" % orig)

    passed = sum(1 for r in RESULTS if r["ok"])
    total = len(RESULTS)
    payload = {
        "runAt": time.strftime("%Y-%m-%d %H:%M:%S"),
        "mode": "FISCO BCOS 真链（fisco.enabled=true）",
        "passed": passed,
        "total": total,
        "allPass": passed == total,
        "context": {k: v for k, v in CONTEXT.items() if k not in ("orig_buyer",)},
        "steps": RESULTS,
    }
    with open(OUT_JSON, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    print("=" * 72)
    print(" 结论：%s / %s 步通过，结果已写入 %s" % (passed, total, OUT_JSON))
    print("=" * 72)
    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())
