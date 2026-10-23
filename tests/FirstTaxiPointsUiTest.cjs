const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const path=require('path'),assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROME_BIN,args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:393,height:873}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{window.TaxiAndroid=Object.fromEntries(['saveNumber','startMonitor','stopMonitor','settings','testAlarm','testCall','confirmCall','showFlights','hideFlights','refreshFlights','officialFlights','weatherSource','prayerSource','showHistory','hideHistory','timerToggle','timerReset','contactEmail','tariffSource'].map(k=>[k,()=>{}]));});
 await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
 const data={number:'L150',state:'Wartend',rank:'95',points:'21',firstPoints:'38',total:'121',lCount:'64',mCount:'57',monitor:'Aktiv',connection:'Verbunden',updatedAt:Date.now(),tone:'Zurna',vibration:'Ein'};
 await page.evaluate(d=>renderNative(d),data);
 assert.equal(await page.locator('#firstpoints').textContent(),'38');
 assert.equal(await page.locator('#points').textContent(),'21 Punkte');
 assert.equal(await page.locator('.hero-points span').textContent(),'Wartepunkte');
 assert.equal(await page.locator('#firstpoints').evaluate(el=>el.previousElementSibling.textContent),'Erstes wartendes Taxi');
 for(const width of [320,360,393,412,768]){
  await page.setViewportSize({width,height:900});
  const layout=await page.locator('.hero-points').evaluate(el=>{const a=el.children[0].getBoundingClientRect(),b=el.children[1].getBoundingClientRect();return {overlap:a.right>b.left,overflow:document.documentElement.scrollWidth>innerWidth,inHero:el.closest('.hero')!==null};});
  assert.deepEqual(layout,{overlap:false,overflow:false,inHero:true},String(width));
  const tiles=await page.locator('.grid.stats').evaluate(g=>{const cards=[...g.children].map(c=>c.getBoundingClientRect());const clipped=[...g.querySelectorAll('.label,strong')].some(e=>e.scrollWidth>e.clientWidth+1||e.getBoundingClientRect().right>e.closest('.card').getBoundingClientRect().right);return {count:cards.length,oneRow:cards.every(c=>Math.abs(c.top-cards[0].top)<1),clipped};});
  assert.deepEqual(tiles,{count:4,oneRow:true,clipped:false},'Kacheln '+width);
 }
 await page.evaluate(d=>renderNative({...d,firstPoints:'0'}),data);assert.equal(await page.locator('#firstpoints').textContent(),'0');
 await page.evaluate(d=>renderNative({...d,firstPoints:'–',total:'0',rank:'–',points:'–'}),data);assert.equal(await page.locator('#firstpoints').textContent(),'–');
 await page.evaluate(d=>{delete d.firstPoints;renderNative(d);},data);assert.equal(await page.locator('#firstpoints').textContent(),'–');
 await page.evaluate(d=>renderNative({...d,firstPoints:'42',connection:'Fehler – Werte veraltet: HTTP 500'}),data);assert.equal(await page.locator('#firstpoints').textContent(),'42');assert.equal(await page.locator('#stale').isVisible(),true);
 await page.evaluate(d=>renderNative(d),data);await page.setViewportSize({width:393,height:873});await page.screenshot({path:require('os').tmpdir()+'/taxi-preview.png',fullPage:true});
 assert.deepEqual(errors,[]);await browser.close();
 console.log('PASS: Wartepunkte im Warteplatz-Feld, erstes Taxi als Kachel (4 Kacheln in einer Reihe), getrennt von eigenen Punkten, Null, leere/alte Daten und Layout 320–768px. Android-Brücke simuliert.');
})().catch(e=>{console.error(e);process.exit(1);});
