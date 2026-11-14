const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const path=require('path'),assert=require('node:assert/strict');
// Version 1.7.0: Startseite (Ring, Punkte, Prognose, Nord/Süd, Gebet, Reihenfolge), Prognose-Seite, Menü, Messen. Android-Brücke simuliert.
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROME_BIN,args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:393,height:873}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{window.bridgeCalls=[];window.TaxiAndroid=new Proxy({},{get:(t,k)=>(...a)=>{window.bridgeCalls.push([k,...a]);}});});
 await page.goto('file://'+path.resolve(__dirname,'../app/src/main/assets/index.html'));
 const M=60000,now=Date.now(),day=await page.evaluate(()=>berlinDay(Date.now()));
 const track=[];for(let k=0;k<=40;k+=4)track.push([now-(40-k)*M,24-Math.round(k*12/40)]);
 const base={version:'1.7.0',number:'L150',state:'Wartend',rank:'12',points:'38',firstPoints:'101',total:'26',lCount:'13',mCount:'13',monitor:'Aktiv',connection:'Verbunden',updatedAt:now-4000,tone:'Zurna',vibration:'Ein',hint10:true,
  insights:{at:now-1000,available:true,rank:12,total:26,north:14,south:8,lastHour:22,likelyNorth:6,buckets:[[3,2],[4,2],[3,3],[4,1]],every:2.7,joinNowMin:57,etaMin:34,earlyMin:27,lateMin:47,joinedAt:now-41*M,joinedExact:true,track},
  prayers:{date:day,today:{date:day,times:['06:01','07:49','12:00','14:12','16:12','17:53']},nextAt:now+75*M,nextIndex:3,nextDate:day},
  sequence:{number:'L150',qnr:'6399',target:'1N',at:now-50*M,before:[{number:'L226',target:'1S',at:now-51*M},{number:'M778',target:'1S',at:now-52*M},{number:'L285',target:'1N',at:now-53*M}],after:[{number:'L092',target:'1N',at:now-49*M}],waiting:[{number:'L092',position:1},{number:'L316',position:2},{number:'L172',position:3}]},
  waitStats:{today:2,week:11,todayAvg:68,weekAvg:72,hours:[{hour:15,avg:35,n:3},{hour:12,avg:90,n:3}],bestHour:15,bestAvg:35},
  messen:{fetchedAt:now-3600000,items:[{name:'Grüne Woche',start:'2099-01-15',end:'2099-01-24',venue:'Berlin',desc:'Ernährung',big:true},{name:'Kleiner Kongress',start:'2099-02-01',end:'2099-02-02',venue:'CityCube Berlin',desc:'',big:false},{name:'Vergangen',start:'2000-01-01',end:'2000-01-02',venue:'Berlin',big:true},{name:'ITB Berlin',start:'2099-03-16',end:'2099-03-18',venue:'Berlin',big:true}]}};
 const render=d=>page.evaluate(d=>renderNative(d),d);const text=id=>page.locator('#'+id).textContent();const vis=id=>page.locator('#'+id).isVisible();
 await render(base);
 // Punkte, L/M, Ring
 assert.equal(await text('points'),'38 P.');assert.equal(await text('firstpoints'),'101 P.');assert.equal(await text('lCount'),'13');assert.equal(await text('mCount'),'13');assert.equal(await text('rank'),'12');assert.equal(await text('ringsub'),'von 26 wartend');
 assert.ok(Number((await page.locator('#ringarc').getAttribute('stroke-dasharray')).split(' ')[0])>150,'Ring zeigt Fortschritt');
 for(const v of ['0',0]){await render({...base,points:v,firstPoints:v});assert.equal(await text('points'),'0 P.');assert.equal(await text('firstpoints'),'0 P.');}
 for(const v of ['–','',undefined,null]){await render({...base,points:v,firstPoints:v});assert.equal(await text('points'),'–');assert.equal(await text('firstpoints'),'–');}
 // Prognose sichtbar
 await render(base);assert.equal(await text('etalabel'),'Voraussichtlich raus');assert.equal(await text('etain'),'in ~34 Min');assert.equal(await vis('etarange'),true);assert.match(await text('waitsince'),/^41 Min$/);assert.equal(await text('waittotal'),'ca. 1 Std 15 Min');assert.match(await text('freshness'),/^Live · vor \d+ s$/);
 assert.match(await text('prayereta'),/vor İkindi \(\d+ Min Puffer\)/);
 // Noch keine Prognose, Überwachung aus, nicht eingereiht, ungenaue Startzeit, Verbindungsfehler
 await render({...base,insights:{...base.insights,available:false,etaMin:undefined}});assert.equal(await text('etatime'),'–');assert.match(await text('etanote'),/Prognose wird berechnet/);assert.equal(await vis('etarange'),false);assert.equal(await vis('prayereta'),false);
 await render({...base,monitor:'Aus'});assert.match(await text('etanote'),/Überwachung ist aus/);assert.match(await text('freshness'),/^Überwachung aus/);assert.equal(await text('nsnorth'),'–');
 await render({...base,state:'L150 nicht in der Warteschlange',rank:'–'});assert.equal(await text('etalabel'),'Wenn du dich jetzt einreihst');assert.equal(await text('etain'),'in ~57 Min');assert.equal(await vis('status'),true);assert.equal(await text('ringsub'),'26 warten');assert.equal(await vis('etawait'),false);
 await render({...base,insights:{...base.insights,joinedExact:false}});assert.match(await text('waitsince'),/^mind\. 41 Min$/);
 await render({...base,connection:'Fehler – Werte veraltet: HTTP 500'});assert.match(await text('freshness'),/Verbindung gestört/);assert.equal(await vis('stale'),true);
 await render({...base,insights:{...base.insights,at:now-10*M}});assert.equal(await text('etatime'),'–','alte Prognose wird nicht angezeigt');
 // Nord/Süd
 await render(base);assert.equal(await text('nsnorth'),'14');assert.equal(await text('nssouth'),'8');assert.equal(await text('nsnorthtext'),'Nord · zieht schneller');assert.equal(await text('nstotal'),'22 in 60 Min');assert.match(await text('nsevery'),/alle 3 Min/);
 // Reihenfolge des letzten Aufrufs
 assert.equal(await vis('seqcard'),true);const seq=await page.locator('#seqlist .seq-num').allTextContents();assert.deepEqual(seq,['L285','M778','L226','L150','L092','L316','L172']);assert.equal(await page.locator('#seqlist .seq-row.me').count(),1);
 await render({...base,sequence:{}});assert.equal(await vis('seqcard'),false);
 // Breiten: kein seitliches Scrollen, nichts abgeschnitten
 await render(base);
 for(const width of [320,360,393,412,768]){await page.setViewportSize({width,height:900});const r=await page.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth,clipped:[...document.querySelectorAll('.hr-list strong,.eta-time strong,.eta-in,.ns-counts strong')].some(e=>e.getBoundingClientRect().right>e.closest('.card').getBoundingClientRect().right+1)}));assert.deepEqual(r,{overflow:false,clipped:false},String(width));}
 await page.setViewportSize({width:393,height:873});
 // Prognose-Seite
 await page.locator('#herocard').click();assert.equal(await vis('prognose'),true);assert.deepEqual((await page.evaluate(()=>bridgeCalls)).filter(c=>c[0]==='showPrognose').length,1);
 assert.ok(await page.locator('#pgchart polyline').count()===1);assert.equal(await text('pgnorth'),'14 Aufrufe');assert.match(await text('pglikely'),/Nord \(etwa 6 von 10\)/);assert.equal(await page.locator('#pgbuckets .pg-col').count(),4);
 assert.match(await text('wsbest'),/15 Uhr/);assert.equal(await text('wstoday'),'2 Aufrufe');
 await page.locator('#hintswitch').click();assert.deepEqual((await page.evaluate(()=>bridgeCalls)).pop(),['setHint',false]);
 await page.evaluate(()=>closeSettings());assert.equal(await vis('overview'),true);assert.ok((await page.evaluate(()=>bridgeCalls)).some(c=>c[0]==='hidePrognose'));
 // Menü und Messen
 await page.locator('#topsettings').click();assert.equal(await vis('menu'),true);assert.match(await text('menumessen'),/^Nächste: Grüne Woche · in \d+ Tagen$/);assert.equal(await text('menuversion'),'1.7.0');
 await page.locator('#openmessen').click();assert.equal(await vis('messen'),true);assert.equal(await text('msnextname'),'Grüne Woche');assert.deepEqual(await page.locator('#messenlist .ms-body strong').allTextContents(),['Kleiner Kongress','ITB Berlin']);
 await page.locator('#messenbig').click();assert.deepEqual(await page.locator('#messenlist .ms-body strong').allTextContents(),['ITB Berlin']);
 await page.locator('#messenrefresh').click();assert.deepEqual((await page.evaluate(()=>bridgeCalls)).pop(),['refreshMessen']);
 await page.locator('#messenback').click();assert.equal(await vis('menu'),true);await page.locator('#menuback').click();assert.equal(await vis('overview'),true);
 // Aufruf hat Vorrang
 await render({...base,state:'AUFGERUFEN!',target:'1N'});assert.equal(await vis('called'),true);assert.equal(await vis('waiting'),false);
 assert.deepEqual(errors,[]);await browser.close();
 console.log('PASS: 1.7.0 Startseite (Punkte inkl. 0/fehlend, Ring, Prognose/keine Daten/aus/einreihen/ungenau/alt, Live-Status, Nord/Süd, Gebet vor/nach, Reihenfolge), 320–768 px ohne Abschneiden, Prognose-Seite, Hinweis-Schalter, Menü, Messen mit Filter; Brücke simuliert.');
})().catch(e=>{console.error(e);process.exit(1);});
