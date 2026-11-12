/**
 * 摄像头扫码功能冒烟测试（无头 Edge + 虚拟摄像头）
 *
 * 验证三件事：
 *   1. 扫码页能正常渲染，按钮与手输兜底都在
 *   2. 点击「打开摄像头扫码」后 html5-qrcode 能成功启动（按钮变为「停止扫描」），无 console 报错
 *   3. 手输/粘贴码值仍可正常验真（回归原有能力）
 *
 * 运行：
 *   cd code-design && NODE_PATH=node_modules node scripts/scan-camera-smoke.js
 */
const puppeteer = require('puppeteer-core');
const path = require('path');

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const BASE = 'http://localhost:5173';
const API = 'http://localhost:8080';
const SHOTS = path.join(__dirname, 'shots');

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const problems = [];

async function getSampleCode() {
  const login = await fetch(API + '/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'ent', password: '123456' })
  }).then((r) => r.json());
  const token = login.data.token;
  const list = await fetch(API + '/api/product/list', {
    headers: { Authorization: 'Bearer ' + token }
  }).then((r) => r.json());
  const p = list.data.find((x) => x.code);
  return p ? p.code : null;
}

(async () => {
  const code = await getSampleCode();
  if (!code) {
    console.log('拿不到样本商品码，退出');
    process.exit(1);
  }
  console.log('样本码：', code);

  const browser = await puppeteer.launch({
    executablePath: EDGE,
    headless: 'new',
    args: [
      '--use-fake-ui-for-media-stream',
      '--use-fake-device-for-media-stream',
      '--no-sandbox'
    ]
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 900 });
  const consoleErrors = [];
  page.on('console', (m) => {
    if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 160));
  });
  page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message.slice(0, 160)));

  // 登录用户端
  await page.goto(BASE + '/login', { waitUntil: 'networkidle2' });
  await page.evaluate(() => localStorage.clear());
  await page.reload({ waitUntil: 'networkidle2' });
  await sleep(800);
  await page.click('.el-select');
  await sleep(500);
  await page.evaluate(() => {
    const items = [...document.querySelectorAll('.el-select-dropdown__item')];
    const t = items.find((i) => i.textContent.includes('用户端'));
    if (t) t.click();
  });
  await sleep(400);
  const inputs = await page.$$('input.el-input__inner:not([readonly]):not([type=password])');
  const pwd = await page.$('input[type=password]');
  await inputs[0].type('user', { delay: 15 });
  await pwd.type('123456', { delay: 15 });
  await page.evaluate(() => {
    const b = [...document.querySelectorAll('button')].find((x) => x.textContent.includes('登录'));
    if (b) b.click();
  });
  await page.waitForFunction(() => !location.pathname.startsWith('/login'), { timeout: 20000 });
  await sleep(800);

  await page.goto(BASE + '/user/scan', { waitUntil: 'networkidle2' });
  await sleep(900);

  // 1) 页面元素
  const hasCameraBtn = await page.evaluate(() =>
    [...document.querySelectorAll('button')].some((b) => b.textContent.includes('打开摄像头扫码')));
  if (!hasCameraBtn) problems.push('页面上没有「打开摄像头扫码」按钮');
  await page.screenshot({ path: path.join(SHOTS, 'scan-1-idle.png') });

  // 2) 启动摄像头（虚拟设备）
  await page.evaluate(() => {
    const b = [...document.querySelectorAll('button')].find((x) => x.textContent.includes('打开摄像头扫码'));
    if (b) b.click();
  });
  await sleep(2500);
  const scanning = await page.evaluate(() =>
    [...document.querySelectorAll('button')].some((b) => b.textContent.includes('停止扫描')));
  const hasVideo = await page.evaluate(() => !!document.querySelector('#qr-reader-box video'));
  if (!scanning) problems.push('点击后未进入扫描状态（按钮没变成「停止扫描」）');
  if (!hasVideo) problems.push('取景框里没有生成 video 元素（html5-qrcode 未真正启动）');
  await page.screenshot({ path: path.join(SHOTS, 'scan-2-camera.png') });

  // 3) 停止扫描
  if (scanning) {
    await page.evaluate(() => {
      const b = [...document.querySelectorAll('button')].find((x) => x.textContent.includes('停止扫描'));
      if (b) b.click();
    });
    await sleep(1200);
    const stopped = await page.evaluate(() =>
      [...document.querySelectorAll('button')].some((b) => b.textContent.includes('打开摄像头扫码')));
    if (!stopped) problems.push('点击「停止扫描」后未回到初始状态');
  }

  // 4) 手输回归
  await page.evaluate(() => {
    const el = document.querySelector('input.el-input__inner');
    if (el) el.value = '';
  });
  const box = await page.$('input.el-input__inner');
  await box.type(code, { delay: 10 });
  await page.evaluate(() => {
    const b = [...document.querySelectorAll('button')].find((x) => x.textContent.trim() === '验真');
    if (b) b.click();
  });
  await sleep(2500);
  const passed = await page.evaluate(() => document.body.innerText.includes('验真通过'));
  if (!passed) problems.push('手输码值后未显示「验真通过」');
  await page.screenshot({ path: path.join(SHOTS, 'scan-3-verified.png') });

  // 5) 图片识别兜底（不需要摄像头权限的路径）：上传一张真实二维码 PNG
  const fileInput = await page.$('input[type=file]');
  if (!fileInput) {
    problems.push('页面上没有二维码图片上传控件');
  } else {
    await fileInput.uploadFile('D:/Work/backup/qr-test.png');
    await sleep(3500);
    const fileDecoded = await page.evaluate(() =>
      (document.querySelector('input.el-input__inner')?.value || '').includes('code='));
    if (!fileDecoded) problems.push('上传二维码图片后没有识别到内容（input 未回填扫码链接）');
    console.log('图片识别结果：', fileDecoded ? 'OK' : 'FAIL');
    await page.screenshot({ path: path.join(SHOTS, 'scan-4-file.png') });
  }

  await browser.close();

  console.log('\n===== 结果 =====');
  console.log('摄像头按钮：', hasCameraBtn ? 'OK' : 'FAIL');
  console.log('进入扫描态：', scanning ? 'OK' : 'FAIL');
  console.log('生成 video：', hasVideo ? 'OK' : 'FAIL');
  console.log('手输验真：', passed ? 'OK' : 'FAIL');
  console.log('console 报错：', consoleErrors.length ? consoleErrors : '无');
  console.log('问题清单：', problems.length ? problems : '无');
  process.exit(problems.length ? 1 : 0);
})();
