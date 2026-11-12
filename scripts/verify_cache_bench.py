# -*- coding: utf-8 -*-
"""
验真接口性能对照实验（开题报告"缓存优化前后验真响应时间"量化数据来源）

做两件事：
  1. 若商品数不足，调用登记接口补足到 N 件（默认 100，对齐开题"不少于 100 件商品"口径）
  2. 对这批商品的验真接口发起 rounds 轮请求（默认 10 轮 = 1000 次），逐次记录耗时，
     输出 mean / p50 / p95 / p99 / min / max / 吞吐，并按"首轮(未命中)"与"后续轮(命中)"分组统计

对照组做法：后端以 `--app.cache.enabled=false` 启动 = 缓存关闭（等价优化前直查），
          默认启动 = 缓存开启（优化后）。两次运行本脚本，对比输出即可。

用法：
    python scripts/verify_cache_bench.py --base http://localhost:8080 --products 100 --rounds 10
"""
import argparse
import json
import statistics
import time
import urllib.parse
import urllib.request

BASE = "http://localhost:8080"


def post_json(path, payload, token=None):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(BASE + path, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))


def get_json(path, token=None):
    req = urllib.request.Request(BASE + path)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))


def login():
    d = post_json("/api/auth/login", {"username": "ent", "password": "123456"})
    return d["data"]["token"]


def ensure_products(token, want):
    lst = get_json("/api/product/list", token)["data"]
    codes = [p["code"] for p in lst if p.get("code")]
    print(f"现有商品 {len(codes)} 件，目标 {want} 件")
    made = 0
    while len(codes) < want:
        made += 1
        name = f"压测样本-{int(time.time())}-{made}"
        r = post_json("/api/product/register",
                      {"name": name, "category": "数码", "description": "缓存对照实验样本",
                       "enterpriseId": 1}, token)
        if r.get("code") == 200:
            codes.append(r["data"]["code"])
        else:
            print("登记失败:", r)
            break
    print(f"补登记 {made} 件，样本总数 {len(codes)}")
    return codes[:want]


def pct(xs, p):
    if not xs:
        return 0.0
    s = sorted(xs)
    k = max(0, min(len(s) - 1, int(round((p / 100.0) * (len(s) - 1)))))
    return s[k]


def bench(codes, rounds):
    first, later = [], []
    total_start = time.perf_counter()
    for r in range(rounds):
        bucket = first if r == 0 else later
        for code in codes:
            t0 = time.perf_counter()
            req = urllib.request.Request(BASE + "/api/verify/scan?code=" + urllib.parse.quote(code))
            with urllib.request.urlopen(req, timeout=30) as resp:
                body = json.loads(resp.read().decode("utf-8"))
            dt = (time.perf_counter() - t0) * 1000.0
            bucket.append(dt)
            if not body.get("data"):
                print("异常响应:", body)
    total = time.perf_counter() - total_start
    return first, later, total


def report(title, first, later, total):
    allx = first + later
    n = len(allx)
    print("\n" + "=" * 62)
    print(f"{title}   请求数={n}  总耗时={total:.2f}s  吞吐={n / total:.1f} req/s")
    print("-" * 62)
    print(f"{'分组':<16}{'次数':>6}{'均值ms':>10}{'P50':>9}{'P95':>9}{'P99':>9}{'最小':>8}{'最大':>9}")
    for label, xs in (("首轮(缓存未命中)", first), ("后续(缓存命中)", later), ("合计", allx)):
        if not xs:
            continue
        print(f"{label:<16}{len(xs):>6}{statistics.mean(xs):>10.1f}{pct(xs, 50):>9.1f}"
              f"{pct(xs, 95):>9.1f}{pct(xs, 99):>9.1f}{min(xs):>8.1f}{max(xs):>9.1f}")
    print("=" * 62)
    return {"n": n, "total_s": round(total, 2), "qps": round(n / total, 1),
            "all_mean_ms": round(statistics.mean(allx), 1),
            "hit_mean_ms": round(statistics.mean(later), 1) if later else None,
            "miss_mean_ms": round(statistics.mean(first), 1) if first else None,
            "all_p95_ms": round(pct(allx, 95), 1)}


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default=BASE)
    ap.add_argument("--products", type=int, default=100)
    ap.add_argument("--rounds", type=int, default=10)
    ap.add_argument("--label", default="")
    a = ap.parse_args()
    BASE = a.base
    tk = login()
    codes = ensure_products(tk, a.products)
    first, later, total = bench(codes, a.rounds)
    summary = report(a.label or "验真接口对照实验", first, later, total)
    print("SUMMARY_JSON " + json.dumps(summary, ensure_ascii=False))
