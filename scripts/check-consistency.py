# -*- coding: utf-8 -*-
"""
前后端一致性静态检查（无需 JDK / Maven 即可运行）

在没装 JDK 22 与 Maven 的环境下，用静态比对提前发现三类最容易出的对接错误：
  1. 前端调用了后端不存在的接口
  2. 路由 import 了不存在的页面文件
  3. 实体类字段与建表脚本列名不一致

用法：python check-consistency.py
"""
import os
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BACKEND = ROOT / 'backend' / 'src' / 'main' / 'java'
FRONTEND = ROOT / 'frontend' / 'src'
SCHEMA = ROOT / 'sql' / 'schema.sql'

problems = []
info = []


def read(p: Path) -> str:
    return p.read_text(encoding='utf-8')


# ---------- 1. 后端接口路径 ----------
def backend_paths() -> set:
    paths = set()
    for f in BACKEND.rglob('*Controller.java'):
        txt = read(f)
        # 类声明上 @RequestMapping 与 public class 之间可能存在其它注解（如 @RequireRole）
        m = re.search(r'@RequestMapping\("([^"]+)"\)(?:\s*@\w+(?:\([^)]*\))?)*\s*public class', txt)
        base = m.group(1) if m else ''
        for mm in re.finditer(r'@(Get|Post|Put|Delete)Mapping(?:\(\s*"([^"]*)"\s*\))?(?:\s*@\w+(?:\([^)]*\))?)*[ \t]*\n\s*public', txt):
            sub = mm.group(2) or ''
            paths.add(base + sub)
    return paths


# ---------- 2. 前端调用路径 ----------
def frontend_paths() -> dict:
    calls = {}
    for f in (FRONTEND / 'api').rglob('*.js'):
        txt = read(f)
        base_url = '/api'
        m = re.search(r"baseURL:\s*'([^']+)'", txt)
        if m:
            base_url = m.group(1)
        for mm in re.finditer(r"request\.(get|post|put|delete)\(\s*'([^']+)'", txt):
            calls[base_url + mm.group(2)] = str(f.relative_to(ROOT))
    return calls


def check_api_alignment():
    be = backend_paths()
    fe = frontend_paths()
    for path, src in fe.items():
        # 路径中含 :id 或模板变量的，去尾段后比对前缀
        candidate = path
        if '{' in path:
            candidate = path.split('/{')[0]
        ok = candidate in be
        if not ok and candidate.endswith('/'):
            # 前端为字符串拼接（如 '/trace/' + productId），按前缀匹配后端路径参数
            ok = any(b.startswith(candidate) and '{' in b for b in be)
        if not ok:
            # 若带路径参数（如 /api/trace/1），尝试匹配含 {xxx} 的后端路径
            for b in be:
                pat = '^' + re.sub(r'\{[^}]+\}', r'[^/]+', re.escape(b).replace(r'\{', '{').replace(r'\}', '}')) + '$'
                if re.match(pat, path):
                    ok = True
                    break
        if not ok:
            problems.append(f'[接口缺失] 前端 {src} 调用了 {path}，后端未提供该接口')
    info.append(f'后端接口 {len(be)} 个：' + ', '.join(sorted(be)))
    info.append(f'前端调用 {len(fe)} 个：' + ', '.join(sorted(fe)))


# ---------- 3. 路由页面文件存在性 ----------
def check_router_files():
    router = FRONTEND / 'router' / 'index.js'
    if not router.exists():
        return
    base = FRONTEND / 'router'
    txt = read(router)
    for mm in re.finditer(r"import\(['\"]([^'\"]+\.vue)['\"]\)", txt):
        rel = mm.group(1)
        p = (base / rel).resolve()
        if not p.exists():
            problems.append(f'[页面缺失] 路由引用了不存在的页面：{rel}')


# ---------- 4. 实体字段 vs 建表列名 ----------
def camel_to_snake(name: str) -> str:
    return re.sub(r'(?<!^)(?=[A-Z])', '_', name).lower()


def check_schema_fields():
    if not SCHEMA.exists():
        problems.append('[脚本缺失] 未找到 sql/schema.sql')
        return
    sql_txt = read(SCHEMA)
    tables = {}
    for m in re.finditer(r'CREATE TABLE (?:IF NOT EXISTS )?(\w+)\s*\((.*?)\n\) ENGINE', sql_txt, re.S):
        cols = set()
        body = m.group(2)
        for line in body.split('\n'):
            line = line.strip()
            if not line or line.startswith('--') or line.upper().startswith(('PRIMARY KEY', 'KEY ', 'UNIQUE KEY', 'INDEX', 'CONSTRAINT')):
                continue
            cm = re.match(r'`?([a-z_][a-z0-9_]*)`?\s+[A-Za-z]', line)
            if cm:
                cols.add(cm.group(1))
        tables[m.group(1)] = cols

    checked = 0
    for f in BACKEND.rglob('entity/*.java'):
        txt = read(f)
        tm = re.search(r'@TableName\("([^"]+)"\)', txt)
        if not tm:
            continue
        table = tm.group(1)
        body = txt.split('public class', 1)[-1]
        fields = re.findall(r'private\s+[\w<>\[\]]+\s+(\w+);', body)
        checked += 1
        if table not in tables:
            problems.append(f'[表缺失] 实体 {f.stem} 对应表 {table} 不在 schema.sql 中')
            continue
        for fd in fields:
            col = camel_to_snake(fd)
            if col not in tables[table]:
                problems.append(f'[列缺失] {table} 表缺少列 {col}（实体 {f.stem}.{fd}）')
    info.append(f'已校验 {checked} 个实体类与 {len(tables)} 张表')


# ---------- 5. 前端 multipart 提交字段 vs 后端 DTO 字段 ----------
def check_dto_fields():
    dto_fields = set()
    for f in BACKEND.rglob('dto/*.java'):
        body = read(f).split('public class', 1)[-1]
        dto_fields |= set(re.findall(r'private\s+[\w<>\[\]]+\s+(\w+);', body))

    checked = 0
    for f in (FRONTEND / 'views').rglob('*.vue'):
        txt = read(f)
        appended = set(re.findall(r"\.append\(\s*'([^']+)'", txt))
        if not appended:
            continue
        checked += 1
        for a in appended:
            if a not in dto_fields:
                problems.append(
                    f'[字段疑似不匹配] {f.relative_to(ROOT)} 提交字段 "{a}"，'
                    f'后端 DTO 无此字段（可能是名字写错或后端缺字段）')
    if checked:
        info.append(f'已校验 {checked} 个含表单提交的页面，DTO 字段共 {len(dto_fields)} 个')


if __name__ == '__main__':
    check_api_alignment()
    check_router_files()
    check_schema_fields()
    check_dto_fields()

    print('=' * 60)
    print('商品交易存证系统 · 前后端一致性检查')
    print('=' * 60)
    for line in info:
        print('· ' + line)
    print('-' * 60)
    if problems:
        print(f'发现 {len(problems)} 个问题：')
        for p in problems:
            print('  ✗ ' + p)
    else:
        print('✓ 全部通过：接口、路由、实体与建表脚本完全对齐')
    print('=' * 60)
