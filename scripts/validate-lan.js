/**
 * 局域网演示端到端验证（手机视口）
 *
 * 验证三件事：
 *   1. 手机直访二维码里的 URL（走局域网 IP + 8080），能看到可读的 HTML 验真结果页
 *   2. 新生成的二维码 PNG 可被 html5-qrcode 解码，且内容确实是局域网地址
 *   3. 上传二维码图片 → 前端扫码页自动验真通过（不依赖摄像头权限）
 *
 * 运行：
 *   cd code-design && NODE_PATH=<workspace>/node_modules node scripts/validate-lan.js
 */
const puppeteer = require('puppeteer-core');
const path = require('path');
const fs = require('fs');

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const LAN_API = 'http://192.168.106.221:8080';
const LAN_WEB = 'http://192.168.106.221:5173';
const SHOTS = path.join(__dirname, 'shots');
const QR_DIR = path.join(__dirname, '..', 'backend', 'uploads', 'qr');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const problems = [];

async function sample() {
  const login = await fetch(LAN_API + '/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'ent', password: '123456' })
  }).then((r) => r.json());
  const list = await fetch(LAN_API + '/api/product/list', {
    headers: { Authorization: 'Bearer ' + login.data.token }
  }).then((r) => r.json());
  const p = list.data.find((x) => x.code);
  return p ? p.code : null;
}

(async () => {
  const code = await sample();
  if (!code) { console.log('拿不到样本商品码，退出'); process.exit(1); }
  console.log('样本码：', code);

  const browser = await puppeteer.launch({
    executablePath: EDGE,
    headless: 'new',
    args: ['--no-sandbox', '--no-proxy-server']
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 390, height: 844, deviceScaleFactor: 2, isMobile: true });

  // 1) 手机直访扫码落地页
  const viewUrl = `${LAN_API}/api/verify/view?code=${code}`;
  const resp = await page.goto(viewUrl, { waitUntil: 'networkidle2' });
  const html = await page.content();
  const passView = html.includes('验真通过') && html.includes('全生命周期履历');
  console.log('验真页 HTTP：', resp.status(), '| 内容断言：', passView ? 'OK' : 'FAIL');
  if (!passView) problems.push('手机验真页未显示「验真通过 / 全生命周期履历」');
  await page.screenshot({ path: path.join(SHOTS, 'lan-verify-mobile.png'), fullPage: true });

  // 2) 前端扫码页：登录用户端 → 上传新二维码 PNG → 自动识别并验真
  await page.setViewport({ width: 1280, height: 900, isMobile: false });
  await page.goto(LAN_WEB + '/login', { waitUntil: 'networkidle2' });
  await page.evaluate(() => localStorage.clear());
  await page.reload({ waitUntil: 'networkidle2' });
  await sleep(700);
  await page.click('.el-select');
  await sleep(400);
  await page.evaluate(() => {
    const items = [...document.querySelectorAll('.el-select-dropdown__item')];
    const t = items.find((i) => i.textContent.includes('用户端'));
    if (t) t.click();
  });
  await sleep(400);
  const inputs = await page.$$('input.el-input__inner:not([readonly]):not([type=password])');
  const pwd = await page.$('input[type=password]');
  await inputs[0].type('user', { delay: 10 });
  await pwd.type('123456', { delay: 10 });
  await page.evaluate(() => {
    const b = [...document.querySelectorAll('button')].find((x) => x.textContent.includes('登录'));
    if (b) b.click();
  });
  await page.waitForFunction(() => !location.pathname.startsWith('/login'), { timeout: 20000 });
  await page.goto(LAN_WEB + '/user/scan', { waitUntil: 'networkidle2' });
  await sleep(800);

  const qrPath = path.join(QR_DIR, code + '.png');
  if (!fs.existsSync(qrPath)) {
    problems.push('找不到二维码 PNG：' + qrPath);
  } else {
    const fileInput = await page.$('input[type=file]');
    await fileInput.uploadFile(qrPath);
    await sleep(4000);
    const decoded = await page.evaluate(() =>
      document.querySelector('input.el-input__inner')?.value || '');
    const hasLan = decoded.includes('192.168.106.221') && decoded.includes('/api/verify/view?code=');
    const verified = await page.evaluate(() => document.body.innerText.includes('验真通过'));
    console.log('二维码解码内容：', decoded.slice(0, 90));
    console.log('指向局域网地址：', hasLan ? 'OK' : 'FAIL', '| 验真通过：', verified ? 'OK' : 'FAIL');
    if (!hasLan) problems.push('二维码内容不是新的局域网验真页地址');
    if (!verified) problems.push('上传二维码后未显示「验真通过」');
    await page.screenshot({ path: path.join(SHOTS, 'lan-scan-file.png') });
  }

  await browser.close();
  console.log('\n===== 结果 =====');
  console.log('问题清单：', problems.length ? problems : '无');
  process.exit(problems.length ? 1 : 0);
})();
