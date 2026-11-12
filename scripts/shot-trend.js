/** 单独截取监管看板的「权属转移趋势」卡片（不产生任何业务数据） */
const puppeteer = require('puppeteer-core');
const path = require('path');

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const BASE = 'http://localhost:5173';
const SHOTS = path.join(__dirname, 'shots');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const browser = await puppeteer.launch({
    executablePath: EDGE, headless: 'new', args: ['--no-sandbox', '--no-proxy-server']
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 900 });

  await page.goto(BASE + '/login', { waitUntil: 'networkidle2' });
  await page.evaluate(() => localStorage.clear());
  await page.reload({ waitUntil: 'networkidle2' });
  await sleep(700);
  await page.click('.el-select');
  await sleep(400);
  await page.evaluate(() => {
    const items = [...document.querySelectorAll('.el-select-dropdown__item')];
    const t = items.find((i) => i.textContent.includes('监管'));
    if (t) t.click();
  });
  await sleep(400);
  const inputs = await page.$$('input.el-input__inner:not([readonly]):not([type=password])');
  const pwdEl = await page.$('input[type=password]');
  await inputs[0].type('admin', { delay: 10 });
  await pwdEl.type('123456', { delay: 10 });
  await page.evaluate(() => {
    const b = [...document.querySelectorAll('button')].find((x) => x.textContent.includes('登录'));
    if (b) b.click();
  });
  await page.waitForFunction(() => !location.pathname.startsWith('/login'), { timeout: 20000 });
  await page.goto(BASE + '/regulator/dashboard', { waitUntil: 'networkidle2' });
  await sleep(1800);

  const handle = await page.evaluateHandle(() =>
    [...document.querySelectorAll('.el-card')].find((c) => c.innerText.includes('权属转移趋势')));
  const card = handle.asElement();
  if (!card) {
    console.log('没找到趋势图卡片');
    process.exit(1);
  }
  await card.scrollIntoView();
  await sleep(400);
  await card.screenshot({ path: path.join(SHOTS, 'round2-trend-card.png') });
  console.log('趋势图卡片截图完成：round2-trend-card.png');
  await browser.close();
})();
