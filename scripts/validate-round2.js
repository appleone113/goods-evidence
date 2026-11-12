/**
 * 第二轮前端验收：真点按钮走完整流程（趋势图 / 商品列表缩略图 / 登记页上传并提交）
 *
 * 运行：
 *   cd code-design && NODE_PATH=<workspace>/node_modules node scripts/validate-round2.js
 */
const puppeteer = require('puppeteer-core');
const path = require('path');

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const BASE = 'http://localhost:5173';
const SHOTS = path.join(__dirname, 'shots');
const TEST_IMG = 'D:/Work/backup/demo-product.png';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const problems = [];

async function login(page, keyword, user, pwd) {
  await page.goto(BASE + '/login', { waitUntil: 'networkidle2' });
  await page.evaluate(() => localStorage.clear());
  await page.reload({ waitUntil: 'networkidle2' });
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
  await sleep(600);
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

  // 1) 监管看板：趋势图渲染 + 真点「近 7 天」切换
  await login(page, '监管', 'admin', '123456');
  await page.goto(BASE + '/regulator/dashboard', { waitUntil: 'networkidle2' });
  await sleep(1600);
  const hasTrend = await page.evaluate(() => document.body.innerText.includes('权属转移趋势'));
  const bars14 = await page.evaluate(() => document.querySelectorAll('[title*="笔"]').length);
  if (!hasTrend) problems.push('看板缺少「权属转移趋势」卡片');
  if (bars14 !== 14) problems.push('默认 14 天柱数=' + bars14 + '（应为 14）');

  await page.evaluate(() => {
    const b = [...document.querySelectorAll('button')].find((x) => x.textContent.includes('近 7 天'));
    if (b) b.click();
  });
  await sleep(1300);
  const bars7 = await page.evaluate(() => document.querySelectorAll('[title*="笔"]').length);
  if (bars7 !== 7) problems.push('点击「近 7 天」后柱数=' + bars7 + '（应为 7）');
  await page.screenshot({ path: path.join(SHOTS, 'round2-trend.png'), fullPage: true });
  console.log('趋势图：默认柱数 =', bars14, '| 切 7 天后 =', bars7);

  // 2) 企业端商品列表：图片列 + 带图行缩略图
  await login(page, '企业', 'ent', '123456');
  await page.goto(BASE + '/enterprise/list', { waitUntil: 'networkidle2' });
  await sleep(1300);
  const listInfo = await page.evaluate(() => ({
    hasImageCol: document.body.innerText.includes('图片'),
    firstRowHasImg: !!document.querySelector('.el-table__body img'),
    hasDemo: document.body.innerText.includes('演示样品')
  }));
  if (!listInfo.hasImageCol) problems.push('商品列表没有「图片」列');
  if (!listInfo.firstRowHasImg) problems.push('带图商品行没有渲染 img');
  if (!listInfo.hasDemo) problems.push('列表里没看到刚登记的带图商品');
  await page.screenshot({ path: path.join(SHOTS, 'round2-product-list.png'), fullPage: true });
  console.log('商品列表：图片列 =', listInfo.hasImageCol, '| 缩略图 =', listInfo.firstRowHasImg);

  // 3) 登记页：上传图片 → 填写名称 → 真点「提交登记」
  await page.goto(BASE + '/enterprise/register', { waitUntil: 'networkidle2' });
  await sleep(800);
  const fileInput = await page.$('input[type=file]');
  if (!fileInput) {
    problems.push('登记页没有图片上传控件');
  } else {
    await fileInput.uploadFile(TEST_IMG);
    await sleep(1600);
    const preview = await page.evaluate(() => !!document.querySelector('img[src*="/api/file/"]'));
    if (!preview) problems.push('上传图片后没有出现预览');
    console.log('上传预览 =', preview);
  }
  const nameInput = await page.$('input.el-input__inner');
  await nameInput.type('前端全流程验证样品', { delay: 10 });
  await page.evaluate(() => {
    const b = [...document.querySelectorAll('button')].find((x) => x.textContent.includes('提交登记'));
    if (b) b.click();
  });
  await sleep(2600);
  const registered = await page.evaluate(() => document.body.innerText.includes('登记成功'));
  if (!registered) problems.push('点击「提交登记」后未显示「登记成功」');
  await page.screenshot({ path: path.join(SHOTS, 'round2-register.png'), fullPage: true });
  console.log('提交登记 =', registered ? 'OK' : 'FAIL');

  await browser.close();
  console.log('\n===== 结果 =====');
  console.log('页面报错：', errors.length ? errors : '无');
  console.log('问题清单：', problems.length ? problems : '无');
  process.exit(problems.length ? 1 : 0);
})();
