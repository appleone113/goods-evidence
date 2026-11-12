/* 截公开页（登录/注册），供论文界面展示用 */
const puppeteer = require('puppeteer-core');
const path = require('path');

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const BASE = 'http://localhost:5173';
const SHOTS = path.join(__dirname, 'shots');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const browser = await puppeteer.launch({
    executablePath: EDGE, headless: 'new',
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 900 });
  for (const [name, url] of Object.entries({ login: '/login', register: '/register' })) {
    await page.goto(BASE + url, { waitUntil: 'networkidle2' });
    await sleep(1200);
    await page.screenshot({ path: path.join(SHOTS, `${name}.png`) });
    console.log('saved', name + '.png');
  }
  await browser.close();
})();
