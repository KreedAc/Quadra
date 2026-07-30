const { chromium } = require('playwright');

// Le schermate del telefono sono 1200x2670, cioè rapporto 2,225. Google accetta al
// massimo 2:1, quindi vanno rimontate: qui finiscono su una tela 1200x2200 (1,83).
// Della schermata si taglia la barra di stato — ora, batteria, notifiche personali —
// che non racconta l'app e mostra roba di chi ha fatto lo scatto.
const SCATTI = [
  ['1-movimenti-chiaro.jpg', 'Quanto hai speso,<br>e quanto ti resta davvero'],
  ['2-conti-chiaro.jpg',     'Ogni conto col suo saldo,<br>ricalcolato a ogni movimento'],
  ['3-movimenti-scuro.jpg',  'Tema chiaro e tema scuro,<br>o quello che dice il telefono'],
  ['4-conti-scuro.jpg',      'Niente account, niente server,<br>nemmeno un permesso'],
];

const LARGA = 1200, ALTA = 2200;
const SCHERMO_L = 850;                                   // larghezza della schermata
const SCALA = SCHERMO_L / 1200;                          // dallo scatto originale
const TAGLIO = Math.round(100 * SCALA);                  // barra di stato, 100px in origine
const SCHERMO_A = Math.round(2670 * SCALA) - TAGLIO;

const pagina = (file, testo) => `<!doctype html><meta charset="utf-8"><style>
  html,body{margin:0;padding:0}
  body{width:${LARGA}px;height:${ALTA}px;background:#0D141C;position:relative;overflow:hidden;
       font-family:Roboto,"Segoe UI",-apple-system,sans-serif;
       display:flex;flex-direction:column;align-items:center}
  .alone{position:absolute;left:50%;top:-380px;transform:translateX(-50%);
         width:1500px;height:900px;border-radius:50%;
         background:radial-gradient(circle,rgba(47,107,255,.28) 0%,rgba(31,216,164,.10) 48%,transparent 72%)}
  h2{position:relative;color:#E8EEF5;font-size:58px;line-height:1.25;font-weight:700;
     letter-spacing:-1.4px;text-align:center;margin:96px 0 0;padding:0 70px}
  .schermo{position:relative;margin-top:auto;width:${SCHERMO_L}px;height:${SCHERMO_A}px;
           border-radius:46px 46px 0 0;overflow:hidden;
           box-shadow:0 -18px 70px rgba(0,0,0,.55)}
  .schermo img{width:${SCHERMO_L}px;display:block;margin-top:-${TAGLIO}px}
</style>
<div class="alone"></div>
<h2>${testo}</h2>
<div class="schermo"><img src="scatti/${file}"></div>`;

(async () => {
  const fs = require('fs');
  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome' });
  for (const [file, testo] of SCATTI) {
    const tmp = 'tmp-' + file.replace('.jpg', '.html');
    fs.writeFileSync(tmp, pagina(file, testo));
    const page = await browser.newPage({ viewport: { width: LARGA, height: ALTA }, deviceScaleFactor: 1 });
    await page.goto('file://' + __dirname + '/' + tmp);
    await page.waitForLoadState('networkidle');
    const out = 'play-' + file.replace('.jpg', '.png');
    await page.screenshot({ path: out });
    console.log(out);
    await page.close();
    fs.unlinkSync(tmp);
  }
  await browser.close();
})();
