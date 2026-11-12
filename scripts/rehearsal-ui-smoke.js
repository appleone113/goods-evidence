/**
 * 答辩全流程演练 · 前端「真点按钮」冒烟（真链模式）
 *
 * 与 rehearsal-full-flow.py 的分工：
 *   - py 走 API + 穿透 DB/链上核对，验证数据与共识层
 *   - 本脚本走浏览器点按钮，验证答辩现场真正要操作的三端页面
 *
 * 复用 rehearsal-result.json 里刚演练出来的 code / 商品名，不新增商品数据。
 * 运行：
 *   cd code-design && NODE_PATH=<workspace>/node_modules <托管node> scripts/rehearsal-ui-smoke.js
 */
const puppeteer = require('puppeteer-core');
const path = require('path');
const fs = require('fs');

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const BASE = 'http://localhost:5173';
const SHOTS = path.join(__dirname, 'shots');
const RESULT = path.join(__dirname, 'rehearsal-result.json');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const problems = [];

if (!fs.existsSync(RESULT)) {
  console.error('缺少 rehearsal-result.json，请先跑 scripts/rehearsal-full-flow.py');
  process.exit(1);
}
const ctx = (JSON.parse(fs.readFileSync(RESULT, 'utf-8')).context || {});
const CODE = ctx.code;
const NAME = ctx.name;
if (!CODE) {
  console.error('rehearsal-result.json 里没有 code');
  process.exit(1);
}

async function login(page, keyword, user, pwd) {
  // 先清登录态再回登录页，避免路由守卫把 /login 重定向走
  await page.goto(BASE + '/login', { waitUntil: 'networkidle2' });
  await page.evaluate(() => localStorage.clear());
  await page.goto(BASE + '/login', { waitUntil: 'networkidle2' });
  await sleep(700);
  await page.click('.el-select');
  await sleep(400);
  await page.evaluate((kw) => {
    const items = [...document.querySelectorAll('.el-select-dropdown__item')];
    const t = items.find((i) => i.textContent.includes(kw));
    if (t) t.click();
  }, keyword);
  await sleep(400);
  const inputs = await page.$$('input.el-input__inner:not([readonly]):not([type=password])');
  const pwdEl = await page.$('input[type=password]');
  await inputs[0].type(user, { delay: 10 });
  await pwdEl.type(pwd, { delay: 10 });
  await page.evaluate(() => {
    const b = [...document.querySelectorAll('button')].find((x) => x.textContent.includes('登录'));
    if (b) b.click();
  });
  await page.waitForFunction(() => !location.pathname.startsWith('/login'), { timeout: 20000 });
  await sleep(700);
}

(async () => {
  const browser = await puppeteer.launch({
    executablePath: EDGE,
    headless: 'new',
    args: ['--no-sandbox', '--no-proxy-server']
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 950 });
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message.slice(0, 160)));

  // 1) 用户端：手输一物一码 → 真点「验真」按钮（真链模式下要走链上校验）
  await login(page, '用户', 'user', '123456');
  await page.goto(BASE + '/user/scan', { waitUntil: 'networkidle2' });
  await sleep(1200);
  const codeInput = await page.$('input.el-input__inner[placeholder*="粘贴"]');
  if (!codeInput) {
    problems.push('扫码页没有手输编码输入框');
  } else {
    await codeInput.type(CODE, { delay: 8 });
    await page.evaluate(() => {
      const b = [...document.querySelectorAll('button')].find((x) => x.textContent.trim() === '验真');
      if (b) b.click();
    });
    await sleep(4000);
    const verifyInfo = await page.evaluate(() => ({
      passed: document.body.innerText.includes('验真通过'),
      hasTraceBtn: [...document.querySelectorAll('button')].some((b) => b.textContent.includes('查看溯源履历'))
    }));
    // 商品名用 DOM 文本再查一次（避免 evaluate 传参）
    const hasName = await page.evaluate((n) => document.body.innerText.includes(n), NAME);
    if (!verifyInfo.passed) problems.push('点击「验真」后页面没有显示「验真通过」');
    if (!hasName) problems.push('验真结果里没有本次演练商品名');
    if (!verifyInfo.hasTraceBtn) problems.push('验真结果里没有「查看溯源履历」按钮');
    await page.screenshot({ path: path.join(SHOTS, 'rehearsal-ui-verify.png'), fullPage: true });
    console.log('用户端扫码验真：验真通过 =', verifyInfo.passed, '| 商品名命中 =', hasName,
      '| 履历按钮 =', verifyInfo.hasTraceBtn);
  }

  // 2) 企业端商品列表：本次演练商品 + 实物图缩略图
  await login(page, '企业', 'ent', '123456');
  await page.goto(BASE + '/enterprise/list', { waitUntil: 'networkidle2' });
  await sleep(1400);
  const listInfo = await page.evaluate((n) => ({
    hasImageCol: document.body.innerText.includes('图片'),
    hasImg: !!document.querySelector('.el-table__body img'),
    hasItem: document.body.innerText.includes(n)
  }), NAME);
  if (!listInfo.hasImageCol) problems.push('商品列表缺少「图片」列');
  if (!listInfo.hasImg) problems.push('商品列表没有渲染缩略图 img');
  if (!listInfo.hasItem) problems.push('商品列表里没有本次演练商品');
  await page.screenshot({ path: path.join(SHOTS, 'rehearsal-ui-list.png'), fullPage: true });
  console.log('企业端列表：图片列 =', listInfo.hasImageCol, '| 缩略图 =', listInfo.hasImg,
    '| 演练商品 =', listInfo.hasItem);

  // 3) 监管看板：趋势图渲染 + 真点「近 7 天」
  await login(page, '监管', 'admin', '123456');
  await page.goto(BASE + '/regulator/dashboard', { waitUntil: 'networkidle2' });
  await sleep(1700);
  const hasTrend = await page.evaluate(() => document.body.innerText.includes('权属转移趋势'));
  const bars14 = await page.evaluate(() => document.querySelectorAll('[title*="笔"]').length);
  if (!hasTrend) problems.push('看板缺少「权属转移趋势」卡片');
  if (bars14 !== 14) problems.push('默认趋势柱数=' + bars14 + '（应为 14）');
  await page.evaluate(() => {
    const b = [...document.querySelectorAll('button')].find((x) => x.textContent.includes('近 7 天'));
    if (b) b.click();
  });
  await sleep(1400);
  const bars7 = await page.evaluate(() => document.querySelectorAll('[title*="笔"]').length);
  if (bars7 !== 7) problems.push('点击「近 7 天」后柱数=' + bars7 + '（应为 7）');
  await page.screenshot({ path: path.join(SHOTS, 'rehearsal-ui-dashboard.png'), fullPage: true });
  console.log('监管看板：趋势柱数 14 →', bars14, '| 切 7 天后 →', bars7);

  await browser.close();
  console.log('\n===== 结果 =====');
  console.log('页面报错：', errors.length ? errors : '无');
  console.log('问题清单：', problems.length ? problems : '无');
  process.exit(problems.length || errors.length ? 1 : 0);
})().catch((e) => {
  console.error('冒烟异常：', e);
  process.exit(1);
});
