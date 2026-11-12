/**
 * 监管看板巡检/告警功能冒烟：登录 admin → 打开看板 → 点「立即巡检」→ 截图
 * 运行：NODE_PATH="C:/Users/Lenovo/.workbuddy/binaries/node/workspace/node_modules" node scripts/integrity-smoke.js
 */
const puppeteer = require('puppeteer-core');
const path = require('path');

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const BASE = 'http://localhost:5173';
const SHOTS = path.join(__dirname, 'shots');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const browser = await puppeteer.launch({ executablePath: EDGE, headless: 'new', args: ['--no-sandbox'] });
  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 1000 });
  const errs = [];
  page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text().slice(0, 150)); });
  page.on('pageerror', (e) => errs.push('pageerror: ' + e.message.slice(0, 150)));

  await page.goto(BASE + '/login', { waitUntil: 'networkidle2' });
  await page.evaluate(() => localStorage.clear());
  await page.reload({ waitUntil: 'networkidle2' });
  await sleep(800);
  await page.click('.el-select');
  await sleep(500);
  await page.evaluate(() => {
    const items = [...document.querySelectorAll('.el-select-dropdown__item')];
    const t = items.find((i) => i.textContent.includes('监管端'));
    if (t) t.click();
  });
  await sleep(400);
  const inputs = await page.$$('input.el-input__inner:not([readonly]):not([type=password])');
  const pwd = await page.$('input[type=password]');
  await inputs[0].type('admin', { delay: 15 });
  await pwd.type('123456', { delay: 15 });
  await page.evaluate(() => {
    const b = [...document.querySelectorAll('button')].find((x) => x.textContent.includes('登录'));
    if (b) b.click();
  });
  await page.waitForFunction(() => !location.pathname.startsWith('/login'), { timeout: 20000 });
  await sleep(1500);

  const hasIntegrityCard = await page.evaluate(() => document.body.innerText.includes('数据完整性巡检'));
  const hasAlertCard = await page.evaluate(() => document.body.innerText.includes('异常告警'));
  await page.evaluate(() => {
    const b = [...document.querySelectorAll('button')].find((x) => x.textContent.includes('立即巡检'));
    if (b) b.click();
  });
  await sleep(4000);
  const showResult = await page.evaluate(() => document.body.innerText.includes('存证比对数'));
  await page.screenshot({ path: path.join(SHOTS, 'adm-integrity.png'), fullPage: true });
  await browser.close();

  console.log('完整性卡片：', hasIntegrityCard ? 'OK' : 'FAIL');
  console.log('告警卡片：', hasAlertCard ? 'OK' : 'FAIL');
  console.log('巡检出结果：', showResult ? 'OK' : 'FAIL');
  console.log('console 报错：', errs.length ? errs : '无');
  process.exit(hasIntegrityCard && hasAlertCard && showResult && !errs.length ? 0 : 1);
})();
