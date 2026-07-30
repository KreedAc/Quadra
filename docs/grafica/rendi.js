const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome' });
  for (const [file, w, h, out] of [
    ['icona.html', 512, 512, 'quadra-icona-512.png'],
    ['feature.html', 1024, 500, 'quadra-feature-1024x500.png'],
  ]) {
    const page = await browser.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
    await page.goto('file://' + __dirname + '/' + file);
    await page.screenshot({ path: out, omitBackground: false });
    console.log(out);
    await page.close();
  }
  await browser.close();
})();
