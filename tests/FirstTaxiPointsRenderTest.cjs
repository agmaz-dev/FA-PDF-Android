const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('node:assert/strict');
const html=fs.readFileSync(path.resolve(__dirname,'../app/src/main/assets/index.html'),'utf8');
for(const match of html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g))new vm.Script(match[1]);
const code=html.split('\n').find(line=>line.startsWith('window.renderNative='));
const nodes=new Map();const $=id=>{if(!nodes.has(id))nodes.set(id,{textContent:'',setAttribute(){}});return nodes.get(id);};
const context={window:{},document:{},latest:{},editing:false,page(){},age(){},$,Date};vm.createContext(context);vm.runInContext(code,context);
const sample={number:'L150',state:'Wartend',rank:'95',points:'21',firstPoints:'38',total:'121',lCount:'64',mCount:'57',monitor:'Aktiv',connection:'Verbunden',updatedAt:Date.now()};
context.window.renderNative(sample);assert.equal($('firstpoints').textContent,'38');assert.equal($('points').textContent,'21 Punkte');
for(const value of ['0',0]){context.window.renderNative({...sample,firstPoints:value,points:value});assert.equal($('firstpoints').textContent,'0');assert.equal($('points').textContent,'0 Punkte');}
for(const value of ['–','',undefined,null]){context.window.renderNative({...sample,firstPoints:value,points:value});assert.equal($('firstpoints').textContent,'–');assert.equal($('points').textContent,'–');}
context.window.renderNative({...sample,firstPoints:'42',connection:'Fehler – Werte veraltet: HTTP 500'});assert.equal($('firstpoints').textContent,'42');assert.equal($('stale').hidden,false);
context.window.renderNative({...sample,firstPoints:'39'});assert.equal($('firstpoints').textContent,'39');assert.equal($('stale').hidden,true);
assert.ok(html.includes('class="hero-points"><span>Wartepunkte</span><strong id="points"'));assert.ok(html.includes('<div class="label">Erstes wartendes Taxi</div><strong id="firstpoints"'));
console.log('PASS: Wartepunkte im Warteplatz-Feld, erstes wartendes Taxi als Kachel, Null und fehlende Werte getrennt.');
