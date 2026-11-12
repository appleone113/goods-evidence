/**
 * 前端页面冒烟：用系统 Edge（headless）遍历三种角色的全部页面，
 * 收集 console 报错、失败请求，并逐页截图。
 *
 * 运行：
 *   NODE_PATH=<workspace>/node_modules node uitest.js
 */
const puppeteer = require('puppeteer-core');
const path = require('path');
const fs = require('fs');

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const BASE = 'http://localhost:5173';
const SHOTS = path.join(__dirname, 'shots');

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const issues = [];
const visited = [];

/** 登录：role 为 ENTERPRISE / USER / ADMIN */
async function login(page, role, username, password) {
  await page.goto(BASE + '/login', { waitUntil: 'networkidle2' });
  await page.evaluate(() => localStorage.clear());
  await page.reload({ waitUntil: 'networkidle2' });
  await sleep(900);

  if (role !== 'ENTERPRISE') {
    await page.click('.el-select');
    await sleep(600);
    const label = role === 'USER' ? '用户端' : '监管端';
    const ok = await page.evaluate((label) => {
      const items = [...document.querySelectorAll('.el-select-dropdown__item')];
      const t = items.find((i) => i.textContent.includes(label));
      if (t) { t.click(); return true; }
      return false;
    }, label);
    if (!ok) issues.push(`[${role}] 角色下拉里没找到「${label}」`);
    await sleep(500);
  }

  const accs = await page.$$('input.el-input__inner:not([readonly]):not([type=password])');
  const pwd = await page.$('input[type=password]');
  if (!accs.length || !pwd) {
    issues.push(`[${role}] 登录页未找到账号/密码输入框`);
    return false;
  }
  await accs[0].type(username, { delay: 20 });
  await pwd.type(password, { delay: 20 });
  await sleep(200);

  await page.evaluate(() => {
    const b = [...document.querySelectorAll('button')].find((x) => x.textContent.includes('登录'));
    if (b) b.click();
  });

  try {
    await page.waitForFunction(() => !location.pathname.startsWith('/login'), { timeout: 20000 });
  } catch (e) {
    issues.push(`[${role}] 登录后未跳转（可能登录失败）`);
    return false;
  }
  await sleep(1200);
  return true;
}

/** 访问单个页面并截图 */
async function visit(page, url, name, tag) {
  const before = issues.length;
  await page.goto(BASE + url, { waitUntil: 'networkidle2', timeout: 25000 }).catch((e) => {
    issues.push(`[${tag}${name}] 页面加载超时/失败：${e.message.slice(0, 80)}`);
  });
  await sleep(1600);
  await page.screenshot({ path: path.join(SHOTS, `${tag}-${name}.png`) });
  // 页面主体是否渲染出内容
  const len = await page.evaluate(() => (document.querySelector('#app')?.innerText || '').trim().length);
  if (len < 10) issues.push(`[${tag}${name}] 页面主体为空（可能白屏）`);
  visited.push({ tag, name, url, textLen: len, newIssues: issues.length - before });
}

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });

  const browser = await puppeteer.launch({
    executablePath: EDGE,
    headless: 'new',
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--window-size=1440,900'],
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 900 });

  page.on('console', (m) => {
    if (m.type() === 'error') issues.push(`[console] ${m.text().slice(0, 180)}`);
  });
  page.on('pageerror', (e) => issues.push(`[pageerror] ${String(e.message).slice(0, 180)}`));
  page.on('requestfailed', (r) =>
    issues.push(`[reqfail] ${r.url().replace(BASE, '')} :: ${r.failure()?.errorText}`));
  page.on('response', (r) => {
    if (r.status() >= 400) issues.push(`[http ${r.status()}] ${r.url().replace(BASE, '')}`);
  });

  // ---------- 企业端 ----------
  if (await login(page, 'ENTERPRISE', 'ent', '123456')) {
    for (const [n, u] of Object.entries({
      register: '/enterprise/register',
      list: '/enterprise/list',
      quality: '/enterprise/quality',
      repair: '/enterprise/repair',
      trade: '/enterprise/trade',
      trace: '/enterprise/trace',
    })) await visit(page, u, n, 'ent');

    // 带商品ID查履历：模拟列表页「查看溯源履历」的跳转（验证 Long 精度修复）
    if (process.env.PID) {
      await visit(page, `/enterprise/trace?productId=${process.env.PID}`, 'trace-query', 'ent');
      const txt = await page.evaluate(() => document.querySelector('#app')?.innerText || '');
      if (!/真链验证|单反/.test(txt)) {
        issues.push('[ent/trace-query] 带 productId 查询未渲染出商品履历');
      }
      console.log(`\n履历页片段：${txt.replace(/\s+/g, ' ').slice(0, 200)}`);
    }
  }

  // ---------- 用户端（含扫码验真交互） ----------
  if (await login(page, 'USER', 'user', '123456')) {
    await visit(page, '/user/scan', 'scan', 'user');
    await visit(page, '/user/trace', 'trace', 'user');

    // 扫码验真：填入二维码内容后点查询
    const pid = process.env.PID;
    if (pid) {
      await page.goto(BASE + '/user/scan', { waitUntil: 'networkidle2' });
      await sleep(1200);
      const typed = await page.evaluate((code) => {
        const inp = document.querySelector('input.el-input__inner:not([readonly]):not([type=password])');
        if (!inp) return false;
        inp.value = code;
        inp.dispatchEvent(new Event('input', { bubbles: true }));
        return true;
      }, `P${pid}`);
      if (!typed) {
        issues.push('[user/scan] 未找到验真输入框');
      } else {
        await sleep(300);
        await page.evaluate(() => {
          const b = [...document.querySelectorAll('button')].find((x) =>
            /验真|查询|验证/.test(x.textContent));
          if (b) b.click();
        });
        await sleep(3500);
        await page.screenshot({ path: path.join(SHOTS, 'user-scan-result.png') });
        const txt = await page.evaluate(() => document.querySelector('#app')?.innerText || '');
        const ok = /验真通过|authentic/.test(txt) || /通过/.test(txt);
        console.log(`\n扫码验真结果片段：${txt.replace(/\s+/g, ' ').slice(0, 260)}`);
        if (!ok) issues.push('[user/scan] 验真动作未看到预期结果');
      }
    }
  }

  // ---------- 监管端 ----------
  if (await login(page, 'ADMIN', 'admin', '123456')) {
    await visit(page, '/regulator/dashboard', 'dashboard', 'adm');
    await visit(page, '/regulator/logs', 'logs', 'adm');
  }

  await browser.close();

  console.log('\n================ 页面遍历结果 ================');
  for (const v of visited) {
    console.log(`  ${v.tag}/${v.name.padEnd(10)} 文本长度=${String(v.textLen).padStart(5)}  新增问题=${v.newIssues}`);
  }
  console.log(`\n共访问 ${visited.length} 个页面，截图目录：${SHOTS}`);
  console.log(`\n================ 问题清单 (${issues.length}) ================`);
  if (!issues.length) console.log('  无');
  [...new Set(issues)].forEach((i) => console.log('  - ' + i));
})();
