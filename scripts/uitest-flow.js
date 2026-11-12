/**
 * 业务流程端到端验证（答辩演示路径）
 *
 * 商品登记 → 质检 → 维修 → 权属转移 → 查看履历 → 扫码验真
 * 全程通过前端页面真实操作，每一步都真实上链。
 * 质检/维修含文件上传，用来验证 el-upload + multipart 提交链路。
 *
 * 运行：
 *   NODE_PATH=<workspace>/node_modules node uitest-flow.js
 */
const puppeteer = require('puppeteer-core');
const path = require('path');
const fs = require('fs');
const os = require('os');

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const BASE = 'http://localhost:5173';
const SHOTS = path.join(__dirname, 'shots');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const results = [];

function step(name, ok, detail) {
  results.push({ name, ok });
  console.log(`  ${ok ? 'OK  ' : 'FAIL'} ${name}${detail ? ' - ' + detail : ''}`);
}

async function login(page, role, username, password) {
  await page.goto(BASE + '/login', { waitUntil: 'networkidle2' });
  await page.evaluate(() => localStorage.clear());
  await page.reload({ waitUntil: 'networkidle2' });
  await sleep(900);
  if (role !== 'ENTERPRISE') {
    await page.click('.el-select');
    await sleep(600);
    const label = role === 'USER' ? '用户端' : '监管端';
    await page.evaluate((l) => {
      const it = [...document.querySelectorAll('.el-select-dropdown__item')]
        .find((i) => i.textContent.includes(l));
      if (it) it.click();
    }, label);
    await sleep(500);
  }
  const accs = await page.$$('input.el-input__inner:not([readonly]):not([type=password])');
  const pwd = await page.$('input[type=password]');
  await accs[0].type(username, { delay: 20 });
  await pwd.type(password, { delay: 20 });
  await sleep(200);
  await page.evaluate(() => {
    const b = [...document.querySelectorAll('button')].find((x) => x.textContent.includes('登录'));
    if (b) b.click();
  });
  await page.waitForFunction(() => !location.pathname.startsWith('/login'), { timeout: 20000 });
  await sleep(1200);
}

function clickByText(page, text) {
  return page.evaluate((t) => {
    const b = [...document.querySelectorAll('button')].find((x) => x.textContent.includes(t));
    if (b) { b.click(); return true; }
    return false;
  }, text);
}

async function pickOption(page, matchText) {
  await page.click('.el-select');
  await sleep(700);
  const ok = await page.evaluate((t) => {
    const items = [...document.querySelectorAll('.el-select-dropdown__item')];
    const hit = items.find((i) => i.textContent.includes(t));
    if (hit) { hit.click(); return true; }
    if (items.length === 1) { items[0].click(); return true; }
    return false;
  }, matchText);
  await sleep(500);
  return ok;
}

/** 等待页面上出现成功提示文本（ElMessage 或结果区） */
async function waitToast(page, text, timeout = 40000) {
  try {
    await page.waitForFunction((t) => document.body.innerText.includes(t), { timeout }, text);
    return true;
  } catch (e) {
    return false;
  }
}

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });

  // 供 el-upload 使用的测试文件
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'qe-'));
  const reportFile = path.join(tmp, 'quality-report.txt');
  const voucherFile = path.join(tmp, 'repair-voucher.txt');
  fs.writeFileSync(reportFile, '质检报告-测试内容');
  fs.writeFileSync(voucherFile, '维修凭证-测试内容');

  const browser = await puppeteer.launch({
    executablePath: EDGE, headless: 'new',
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 900 });
  page.on('pageerror', (e) => console.log('  [pageerror] ' + String(e.message).slice(0, 120)));

  const apiCalls = [];
  page.on('response', async (r) => {
    const u = r.url();
    for (const api of ['/product/register', '/quality/add', '/repair/add', '/trade/transfer']) {
      if (u.includes(api)) {
        let body = '';
        try { body = await r.text(); } catch (e) { body = '<读取失败>'; }
        const code = (body.match(/"code":(\d+)/) || [])[1];
        apiCalls.push({ api, code });
        const ok = code === '200';
        console.log(`  [${ok ? '200' : '业务异常 ' + code}] ${api}  ${ok ? '' : (body.match(/"message":"([^"]*)"/) || [])[1] || ''}`);
      }
    }
  });

  const tag = Date.now();
  const goodsName = `UI流程验证-${tag}`;
  console.log(`\n测试商品：${goodsName}\n`);

  // ---------- 1 登录 ----------
  await login(page, 'ENTERPRISE', 'ent', '123456');
  step('企业端登录', !page.url().includes('/login'));

  // ---------- 2 商品登记（模块二） ----------
  await page.goto(BASE + '/enterprise/register', { waitUntil: 'networkidle2' });
  await sleep(1200);
  const inputs = await page.$$('input.el-input__inner');
  await inputs[0].type(goodsName, { delay: 15 });
  await inputs[1].type('数码', { delay: 15 });
  const ta = await page.$('textarea.el-textarea__inner');
  if (ta) await ta.type('界面端到端验证', { delay: 5 });
  await sleep(200);
  await clickByText(page, '提交登记');
  const r1 = await waitToast(page, '登记成功');
  await sleep(1600);
  await page.screenshot({ path: path.join(SHOTS, 'flow-1-register.png') });
  const info = await page.evaluate(() => {
    const txt = document.body.innerText.replace(/\s+/g, ' ');
    return {
      id: (txt.match(/商品ID\s*(\d{15,25})/) || [])[1],
      code: (txt.match(/一物一码\s*(P\d{15,25})/) || [])[1],
    };
  });
  step('商品登记并同步链上权属', r1 && !!info.id, info.id ? `id=${info.id}` : '未拿到商品ID');
  step('页面渲染出一物一码与二维码', !!info.code, info.code || '');

  // ---------- 3 质检（模块五 · 含文件上传） ----------
  if (info.id) {
    await page.goto(BASE + '/enterprise/quality', { waitUntil: 'networkidle2' });
    await sleep(1500);
    const picked = await pickOption(page, goodsName);
    const fi = await page.$('input[type=file]');
    if (fi) await fi.uploadFile(reportFile);
    await sleep(500);
    await clickByText(page, '提交并上链');
    const ok = await waitToast(page, '质检已存证上链');
    await sleep(1000);
    await page.screenshot({ path: path.join(SHOTS, 'flow-2-quality.png') });
    step('质检录入并上链（含报告上传）', ok && picked, picked ? '' : '下拉未选中商品');
  }

  // ---------- 4 维修（模块五 · 含文件上传） ----------
  if (info.id) {
    await page.goto(BASE + '/enterprise/repair', { waitUntil: 'networkidle2' });
    await sleep(1500);
    const picked = await pickOption(page, goodsName);
    // 维修内容是必填项且没有默认值，必须显式填写，否则前端直接拦下不发请求
    const cta = await page.$('textarea.el-textarea__inner');
    if (cta) await cta.type('更换快门组件', { delay: 10 });
    const fi = await page.$('input[type=file]');
    if (fi) await fi.uploadFile(voucherFile);
    await sleep(500);
    await clickByText(page, '提交并上链');
    const ok = await waitToast(page, '维修记录已存证上链');
    await sleep(1000);
    await page.screenshot({ path: path.join(SHOTS, 'flow-3-repair.png') });
    step('维修录入并上链（含凭证上传）', ok && picked, picked ? '' : '下拉未选中商品');
  }

  // ---------- 5 权属转移（模块四 · 合约校验） ----------
  if (info.id) {
    await page.goto(BASE + '/enterprise/trade', { waitUntil: 'networkidle2' });
    await sleep(1500);
    const picked = await pickOption(page, goodsName);
    const buyer = await page.$('input.el-input__inner:not([readonly])');
    if (buyer) await buyer.type('ui-buyer-001', { delay: 15 });
    await sleep(200);
    await clickByText(page, '发起转移');
    const ok = await waitToast(page, '权属转移成功');
    await sleep(1200);
    await page.screenshot({ path: path.join(SHOTS, 'flow-4-transfer.png') });
    step('权属转移（合约前置校验）', ok && picked, picked ? '转给 ui-buyer-001' : '下拉未选中商品');
  }

  // ---------- 6 查看履历（模块三） ----------
  if (info.id) {
    await page.goto(`${BASE}/enterprise/trace?productId=${info.id}`, { waitUntil: 'networkidle2' });
    await sleep(2200);
    const txt = await page.evaluate(() => document.body.innerText);
    const kinds = ['REGISTER', 'QUALITY', 'REPAIR', 'TRADE'].filter((k) => txt.includes(k));
    await page.screenshot({ path: path.join(SHOTS, 'flow-5-trace.png') });
    step(`商品履历（${kinds.length}/4 类事件）`, kinds.length === 4, kinds.join('→'));
  }

  // ---------- 7 扫码验真（模块七） ----------
  if (info.code) {
    await login(page, 'USER', 'user', '123456');
    await page.goto(BASE + '/user/scan', { waitUntil: 'networkidle2' });
    await sleep(1200);
    const inp = await page.$('input.el-input__inner:not([readonly]):not([type=password])');
    await inp.type(info.code, { delay: 10 });
    await sleep(200);
    await page.evaluate(() => {
      const b = [...document.querySelectorAll('button')].find((x) => /验真|查询/.test(x.textContent));
      if (b) b.click();
    });
    await sleep(3500);
    const t = await page.evaluate(() => document.body.innerText.replace(/\s+/g, ' '));
    const verified = t.includes('验真通过');
    await page.screenshot({ path: path.join(SHOTS, 'flow-6-verify.png') });
    step('扫码验真（链上哈希比对）', verified, verified ? '验真通过' : t.slice(0, 80));
  }

  await browser.close();
  fs.rmSync(tmp, { recursive: true, force: true });

  const pass = results.filter((r) => r.ok).length;
  const bad = apiCalls.filter((c) => c.code !== '200');
  console.log('\n================ 业务流程结果 ================');
  console.log(`  ${pass}/${results.length} 步通过`);
  console.log(`  接口业务码异常数：${bad.length}${bad.length ? ' -> ' + bad.map((b) => b.api + ':' + b.code).join(', ') : ''}`);
  console.log(`  商品：${goodsName}`);
  console.log(`  商品ID：${info.id || '-'}   一物一码：${info.code || '-'}`);
  console.log(`  截图：${SHOTS}/flow-*.png`);
  process.exit(pass === results.length && bad.length === 0 ? 0 : 1);
})();
