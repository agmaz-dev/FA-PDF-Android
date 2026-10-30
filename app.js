'use strict';
/* Kur'an-ı Kerim – web uygulaması (iPhone / tarayıcı). Android uygulamasının okuma kısmı. */
const $ = s => document.querySelector(s);
const esc = s => String(s).replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));

// ---------- ayarlar (telefonda saklanır) ----------
const DEF = {lang:'tr', meal:'elmalili', font:'medina', theme:'krem', arZoom:1, mealZoom:1, page:1, mode:'meal', marks:[], reciter:'sudais', audioMode:'ar', ackRead:true, voice:'', pitch:0.8, rate:0.95};
const st = (() => { try { return Object.assign({}, DEF, JSON.parse(localStorage.getItem('kuran') || '{}')); } catch (e) { return Object.assign({}, DEF); } })();
const save = () => { try { localStorage.setItem('kuran', JSON.stringify(st)); } catch (e) {} };

// ---------- dil ----------
let I18N = {};
const t = (s, ...a) => { let r = (I18N[st.lang] || {})[s] || s; a.forEach((v, i) => r = r.split('%' + (i + 1)).join(v)); return r; };
const LANGS = [['tr','Türkçe'],['de','Deutsch'],['en','English'],['ar','العربية']];

// ---------- mealler ----------
const MEALLER = [
  {id:'hayrat', short:'Hayrat', name:'Hayrat Muhtasar Meâli', personal:true},
  {id:'elmalili', short:'Elmalılı', name:'Elmalılı Hamdi Yazır Meâli', file:'tr.yazir'},
  {id:'tr.diyanet', short:'Diyanet', name:'Diyanet İşleri Başkanlığı Meâli (Altuntaş – Şahin)', personal:true},
  {id:'tr.golpinarli', short:'Gölpınarlı', name:'Abdülbâki Gölpınarlı Meâli', file:'tr.golpinarli'},
  {id:'feridun', short:'Feridun A.', name:'Feridun A. – Uzun Meal', file:'feridun'},
  {id:'de.bubenheim', short:'Bubenheim', name:'Deutsch – Bubenheim & Elyas', file:'de.bubenheim'},
  {id:'en.sahih', short:'Saheeh Intl.', name:'English – Saheeh International', file:'en.sahih'},
  {id:'ar.muyassar', short:'الميسر', name:'العربية – التفسير الميسر', file:'ar.muyassar'},
];
const mealInfo = id => MEALLER.find(m => m.id === id) || MEALLER[1];

const FONTS = [
  ['medina','Medine Mushafı',"Kral Fahd Kur'an Matbaası'nın yazısı"],
  ['amiri','Klasik nesih','Geleneksel matbaa mushafı görünümü'],
  ['scheherazade','Büyük ve açık','İri harekeler, kolay okunur'],
  ['naskh','Sade, modern','Ekranda en net görünen yazı'],
  ['amiriClassic','Amiri','Klasik kitap neshi'],
  ['lateef','Lateef','Hint–Sind mushaf üslûbu'],
  ['harmattan','Harmattan','Batı Afrika mushaf üslûbu'],
  ['mada','Mada','Sade ve modern'],
  ['kufi','Kûfî','Modern kûfî tarz'],
  ['katibeh','Katibeh','El yazısı havasında'],
  ['taha','Osman Taha neshi','Kral Fahd · bazı işaretler eksik'],
  ['markazi','Markazi','Gazete neshi'],
  ['reemKufi','Reem Kufi','Kûfî'],
  ['kufam','Kufam','Kûfî'],
  ['ruqaa','Aref Ruqaa',"Rık'a"],
  ['gulzar','Gulzar',"Nesta'lîk"],
  ['nastaliq','Noto Nastaliq',"Nesta'lîk"],
  ['messiri','El Messiri','Modern'],
  ['rakkas','Rakkas','Dekoratif'],
  ['qahiri','Qahiri','Eski kûfî'],
];
(function fontFaces() {
  const css = FONTS.map(([k]) => `@font-face{font-family:"${k}";src:url(fonts/${k}.${k === 'taha' ? 'otf' : 'ttf'});font-display:swap}`).join('');
  const s = document.createElement('style'); s.textContent = css; document.head.appendChild(s);
})();

// ---------- veri ----------
let Q = null;              // {surahs, ayahs}
const MEAL = {};           // id -> dizi
let ACK = null;            // açıklamalar
let PERS = null;           // kişisel dosya (Hayrat, Diyanet, kırmızı kelimeler)
let RED = new Set();
let pageRanges = [], pageOfIdx = [], firstIdxOfSurah = {}, juzStart = [];
const PAGES = 604;

const getJSON = u => fetch(u).then(r => { if (!r.ok) throw new Error(u); return r.json(); });

// IndexedDB: kişisel dosya burada durur
const idb = {
  db: null,
  open() { return this.db ? Promise.resolve(this.db) : new Promise((res, rej) => { const r = indexedDB.open('kuran', 1); r.onupgradeneeded = () => r.result.createObjectStore('kv'); r.onsuccess = () => { this.db = r.result; res(this.db); }; r.onerror = () => rej(r.error); }); },
  async get(k) { const db = await this.open(); return new Promise(res => { const q = db.transaction('kv').objectStore('kv').get(k); q.onsuccess = () => res(q.result); q.onerror = () => res(null); }); },
  async set(k, v) { const db = await this.open(); return new Promise((res, rej) => { const tx = db.transaction('kv', 'readwrite'); tx.objectStore('kv').put(v, k); tx.oncomplete = res; tx.onerror = () => rej(tx.error); }); },
  async del(k) { const db = await this.open(); return new Promise(res => { const tx = db.transaction('kv', 'readwrite'); tx.objectStore('kv').delete(k); tx.oncomplete = res; }); },
};

function applyPersonal(p) {
  PERS = p || null;
  RED = new Set();
  if (PERS && PERS.tevafuk) PERS.tevafuk.forEach(([a, w]) => RED.add(a * 1000 + w));
  if (PERS && PERS.hayrat) MEAL['hayrat'] = PERS.hayrat.meal;
  else delete MEAL['hayrat'];
  if (PERS && PERS.diyanet) MEAL['tr.diyanet'] = PERS.diyanet; else delete MEAL['tr.diyanet'];
}
const hasMeal = id => { const m = mealInfo(id); return !m.personal || !!MEAL[id]; };

async function loadMeal(id) {
  const m = mealInfo(id);
  if (MEAL[id] || m.personal) return;
  const d = await getJSON('data/meal/' + m.file + '.json');
  if (id === 'feridun') {
    MEAL.feridun = d.meal;
    MEAL.feridunIntro = d.intro || {};
    const sec = {}; (d.bol || []).forEach(([i, h, n]) => sec[i] = [h, n]); MEAL.feridunSec = sec;
  } else MEAL[id] = d;
}

// ---------- Kur'an yardımcıları ----------
const DIG = '٠١٢٣٤٥٦٧٨٩';
const arDigits = n => String(n).split('').map(c => DIG[+c]).join('');
const marker = n => (st.font === 'medina' || st.font === 'taha' ? '' : '\u06DD') + arDigits(n);
const HARAKAT = /[ؐ-ًؚ-ٰٟۖ-ۭ]/g;
const isAllah = w => { const n = w.replace(HARAKAT, '').replace(/ٱ/g, 'ا'); return n.includes('لله') && n.length <= 8; };
const BASMALA = 'بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ';
const surah = n => Q.surahs[n - 1];
const sName = s => st.lang === 'ar' ? s[2].replace(/^سُورَةُ\s*/, '').trim() : (st.lang === 'tr' || !s[6]) ? s[1] : s[6];
const ay = i => Q.ayahs[i]; // [s, a, page, juz, text]
const ayahsOnPage = p => { const r = pageRanges[p - 1]; const out = []; if (r) for (let i = r[0]; i <= r[1]; i++) out.push(i); return out; };
const surahsOnPage = p => [...new Set(ayahsOnPage(p).map(i => ay(i)[0]))].map(surah);
const arWords = (i, sel) => ay(i)[4].split(' ').map((w, wi) => (isAllah(w) || RED.has(i * 1000 + wi)) ? `<span class="r">${w}</span>` : w).join(' ');
const basmalaHtml = () => BASMALA.split(' ').map(w => isAllah(w) ? `<span class="r">${w}</span>` : w).join(' ');

/** Meal satırları: kaynağa göre âyet numarası etiketi ve metin */
function mealLines(source, idxs) {
  const out = [];
  const m = MEAL[source === 'elmalili' ? 'elmalili' : source] || MEAL['elmalili'] || [];
  for (const i of idxs) {
    const [s, a] = ay(i);
    if (source === 'hayrat' && PERS && PERS.hayrat) {
      const tx = PERS.hayrat.meal[i] || ''; if (!tx.trim()) continue;
      const g = (PERS.hayrat.group && PERS.hayrat.group[i]) || [i];
      out.push({i, label: g.map(x => ay(x)[1]).join(', '), text: tx});
    } else if (source === 'feridun' || source === 'elmalili' || source === 'tr.diyanet') {
      const tx = m[i] || ''; if (!tx.trim()) continue;
      let j = i + 1; while (j < Q.ayahs.length && ay(j)[0] === s && m[j] === '') j++;
      out.push({i, label: j - 1 > i ? `${a}-${ay(j - 1)[1]}` : `${a}`, text: tx});
    } else {
      const tx = m[i] || ''; if (!tx.trim()) continue;
      out.push({i, label: `${a}`, text: tx});
    }
  }
  return out;
}
const mealPlain = i => { const l = mealLines(st.meal, [i])[0]; return l ? l.text.replace(/⁽\d+⁾/g, '').trim() : ''; };
function mealHtml(text) {
  return esc(text).replace(/⁽(\d+)⁾/g, '<sup>($1)</sup>');
}

// ---------- sayfa çizimi ----------
function headHtml(p) {
  const ss = surahsOnPage(p); const a0 = ayahsOnPage(p)[0];
  const range = ss.length > 1 ? `${ss[0][0]}–${ss[ss.length - 1][0]}` : `${ss[0] ? ss[0][0] : ''}`;
  return `<div class="phead"><b>${p}</b><i>${t('Cüz: %1, Sûre: %2 (%3)', a0 != null ? ay(a0)[3] : '', range, ss.map(sName).join(' – '))}</i></div><div class="rule"></div>`;
}
function mealPage(p) {
  const src = st.meal; const idxs = ayahsOnPage(p); const lines = {}; mealLines(src, idxs).forEach(l => lines[l.i] = l);
  let h = '';
  for (const i of idxs) {
    const [s, a] = ay(i);
    if (a === 1) {
      const S = surah(s);
      h += `<div class="ti">${t('%1- %2 SÛRESİ', s, sName(S).toLocaleUpperCase(st.lang === 'tr' ? 'tr' : st.lang))}</div>`;
      h += `<div class="su">${t('[%1 devrinde nâzil olmuştur, %2 âyettir.]', S[4] === 'Mekkî' ? t('Mekke') : t('Medîne'), S[3])}</div>`;
      if (src === 'feridun' && MEAL.feridunIntro && MEAL.feridunIntro[s]) h += `<div class="in">${esc(MEAL.feridunIntro[s])}</div>`;
      if (s !== 1 && s !== 9) h += `<div class="bs">${basmalaHtml()}</div><div class="bt">${t("Rahmân, Rahîm olan Allah'ın ismiyle")}</div>`;
    }
    if (src === 'feridun' && MEAL.feridunSec && MEAL.feridunSec[i]) {
      const [hd, nt] = MEAL.feridunSec[i];
      h += `<div class="hd">${esc(hd)}</div>`; if (nt) h += `<div class="nt">${esc(nt)}</div>`;
    }
    const l = lines[i]; if (!l) continue;
    h += `<p class="ln${i === hl() ? ' sel' : ''}" data-i="${i}"><b>${l.label}- </b>${mealHtml(l.text)}</p>`;
  }
  if (!hasMeal(src)) h += `<p class="muted">${t('Bu meal kişisel dosyada. Ayarlar → Kişisel dosya bölümünden aktarın; şimdilik Elmalılı meali gösteriliyor.')}</p>`;
  return `<div class="paper">${headHtml(p)}<div class="fit" data-fit="meal"><div class="meal">${h}</div></div></div>`;
}
const ORN = '<svg class="orn" viewBox="0 0 24 24"><path d="M12 2l2.4 4.6L19 5l-1.6 4.6L22 12l-4.6 2.4L19 19l-4.6-1.6L12 22l-2.4-4.6L5 19l1.6-4.6L2 12l4.6-2.4L5 5l4.6 1.6z"/></svg>';
function surahHeader(S) {
  const rev = S[4] === 'Mekkî' ? 'مَكِّيَّةٌ' : 'مَدَنِيَّةٌ';
  return `<div class="shd">${ORN}<div class="ct"><span>${S[2]} ${rev}</span><small>آيَاتُهَا ${arDigits(S[3])}</small></div>${ORN}</div>`;
}
function mushafPage(p) {
  let h = ''; let para = [];
  const flush = () => { if (para.length) { h += `<div class="para">${para.join(' ')}</div>`; para = []; } };
  for (const i of ayahsOnPage(p)) {
    const [s, a] = ay(i);
    if (a === 1) { flush(); h += surahHeader(surah(s)); if (s !== 1 && s !== 9) h += `<div class="bsm">${basmalaHtml()}</div>`; }
    para.push(`<span data-i="${i}"${i === hl() ? ' class="sel"' : ''}>${arWords(i)} <span class="m">${marker(a)}</span></span>`);
  }
  flush();
  const ss = surahsOnPage(p);
  return `<div class="paper"><div class="phead mtop"><span>${p}</span><span style="font-family:amiri;font-size:16px;color:var(--ink)">${ss.length ? ss[ss.length - 1][2] : ''}</span></div>
    <div class="frame"><div class="frame2"><div class="fit" data-fit="ar"><div class="ar" style="font-family:'${st.font}',amiri">${h}</div></div></div></div></div>`;
}
const SHARE_SVG = '<svg viewBox="0 0 24 24"><circle cx="18" cy="5" r="2.5"/><circle cx="6" cy="12" r="2.5"/><circle cx="18" cy="19" r="2.5"/><path d="M8.2 10.8l7.6-4.4M8.2 13.2l7.6 4.4"/></svg>';
function aciklamaPage(p) {
  const idxs = ayahsOnPage(p);
  const cur = idxs.includes(hl()) ? hl() : idxs[0];
  const [s, a] = ay(cur); const S = surah(s);
  const l = mealLines(st.meal, [cur])[0];
  let h = `<div class="ah"><b>${t('%1- %2 Sûresi, %3. âyet', s, sName(S), a)}</b><button class="round" data-share="${cur}" aria-label="${t('Paylaş')}">${SHARE_SVG}</button></div>`;
  h += `<div class="arx" style="font-family:'${st.font}',amiri">${arWords(cur)} <span class="m" style="color:var(--gold)">${marker(a)}</span></div>`;
  if (l) h += `<div class="ml"><b>${l.label}- </b>${mealHtml(l.text)}</div>`;
  const A = ACK && ACK[s] && ACK[s].ayat[a];
  if (A) {
    h += `<div class="ex"><div class="et">${esc(A.t)}</div>`;
    if (A.ar && A.ar.length) h += `<div class="ek">${A.ar.map(esc).join('<span>/</span>')}</div>`;
    h += A.p.map(x => `<p class="ep">${esc(x)}</p>`).join('') + '</div>';
  } else if (ACK) h += `<div class="none">${t('Bu sûrenin açıklaması henüz hazırlanmadı.')}</div>`;
  return `<div class="paper ac"><h6>${t('AÇIKLAMA')}</h6><h2>${t("Kur'an Açıklamaları")}</h2>
    <div class="src">${t('Arapça anahtar ifadeler, kelime anlamları ve âyet açıklamaları')}</div><div class="rule" style="margin-top:8px"></div>
    <div class="fit"><div class="body">${h}</div></div>
    <div class="nav"><button class="btn" data-go="${cur - 1}" ${cur <= 0 ? 'disabled' : ''}>${t('‹ Önceki')}</button><button class="btn" data-go="${cur + 1}" ${cur >= Q.ayahs.length - 1 ? 'disabled' : ''}>${t('Sonraki ›')}</button></div></div>`;
}
const wide = () => innerWidth >= 760 && innerWidth > innerHeight;
function slotHtml(p) {
  if (st.mode === 'mushaf') return mushafPage(p);
  if (wide()) return (st.mode === 'aciklama' ? aciklamaPage(p) : mealPage(p)) + mushafPage(p);
  return st.mode === 'aciklama' ? aciklamaPage(p) : mealPage(p);
}

/** Yazıyı sayfaya sığdırır (Android'deki gibi), sonra kullanıcının büyütmesi uygulanır */
function fitAll(slot) {
  slot.querySelectorAll('.fit[data-fit]').forEach(box => {
    const inner = box.firstElementChild; const kind = box.dataset.fit;
    let lo = kind === 'ar' ? 12 : 11, hi = kind === 'ar' ? 40 : 26;
    const H = box.clientHeight; if (!H) return;
    for (let k = 0; k < 8; k++) { const mid = (lo + hi) / 2; inner.style.setProperty('--fs', mid + 'px'); if (inner.scrollHeight <= H) lo = mid; else hi = mid; }
    const z = kind === 'ar' ? st.arZoom : st.mealZoom;
    inner.style.setProperty('--fs', (lo * z) + 'px');
    if (kind === 'ar' && z <= 1.01) inner.style.minHeight = H + 'px';
  });
  slot.querySelectorAll('.ac .body').forEach(b => b.style.setProperty('--fs', (15 * st.mealZoom) + 'px'));
}

// ---------- sayfa çevirici ----------
let slots = [], cur = st.page, sel = null, rendered = new Set();
function buildPager() {
  const pg = $('#pager'); pg.innerHTML = ''; slots = []; rendered.clear();
  for (let p = 1; p <= PAGES; p++) { const d = document.createElement('div'); d.className = 'slot'; d.dataset.p = p; pg.appendChild(d); slots.push(d); }
  const io = new IntersectionObserver(es => es.forEach(e => { if (e.isIntersecting && e.intersectionRatio > 0.55) onPage(+e.target.dataset.p); }), {root: pg, threshold: [0.6]});
  slots.forEach(s => io.observe(s));
  goPage(cur, false);
}
function renderAround(p) {
  for (let q = p - 2; q <= p + 2; q++) if (q >= 1 && q <= PAGES && !rendered.has(q)) renderSlot(q);
  for (const q of [...rendered]) if (Math.abs(q - p) > 3) { slots[q - 1].innerHTML = ''; rendered.delete(q); }
}
function renderSlot(p) { const s = slots[p - 1]; s.innerHTML = slotHtml(p); rendered.add(p); fitAll(s); }
function rerender() { const ps = [...rendered]; rendered.clear(); ps.forEach(renderSlot); renderAround(cur); }
function onPage(p) {
  if (p === cur && rendered.has(p)) return;
  cur = p; st.page = p; save(); renderAround(p); updateBars();
  if (sel != null && Q.ayahs[sel] && ay(sel)[2] !== p) { sel = null; updateSel(); }
}
function goPage(p, smooth = true) {
  p = Math.max(1, Math.min(PAGES, p));
  if (sel != null && ay(sel)[2] !== p) { sel = null; updateSel(); }
  cur = p; st.page = p; save(); renderAround(p);
  slots[p - 1].scrollIntoView({behavior: smooth ? 'smooth' : 'auto', inline: 'start', block: 'nearest'});
  updateBars();
}

// ---------- çubuklar ----------
let barsTimer = 0;
function bars(on) { document.body.classList.toggle('barsOn', on); clearTimeout(barsTimer); if (on) barsTimer = setTimeout(() => bars(false), 6000); }
function updateBars() {
  const ss = surahsOnPage(cur);
  $('#tSurah').textContent = ss.length ? sName(ss[0]) : '';
  $('#tPage').textContent = '· ' + cur;
  $('#slider').value = cur;
  $('#bMark').classList.toggle('on', st.marks.includes(cur));
  document.querySelectorAll('#modes button').forEach(b => { b.classList.toggle('on', b.dataset.m === st.mode); b.textContent = t({meal: 'Meal', mushaf: 'Mushaf', aciklama: 'Açıklama'}[b.dataset.m]); });
}
function toast(s) { const e = $('#toast'); e.textContent = s; e.style.display = 'block'; clearTimeout(e._t); e._t = setTimeout(() => e.style.display = 'none', 2600); }

// ---------- seçili âyet ----------
function select(i) {
  sel = (sel === i && st.mode !== 'aciklama') ? null : i;
  const p = ay(i)[2];
  if (p !== cur) goPage(p); else rerender();
  updateSel();
}
function updateSel() {
  const b = $('#selBar');
  if (sel == null || st.mode === 'aciklama' || ay(sel)[2] !== cur) { b.classList.remove('on'); b.innerHTML = ''; return; }
  const [s, a] = ay(sel); const S = surah(s);
  if (st.mode === 'mushaf') {
    const m = mealPlain(sel) || t('Bu âyetin meâli bir önceki âyetle birlikte verilmiştir.');
    b.innerHTML = `<div class="selCard"><div class="h"><span>${t('%1 · %2. ÂYET', sName(S).toLocaleUpperCase(st.lang === 'tr' ? 'tr' : st.lang), a)}</span><button class="x" data-x>✕</button></div>
      <div class="tx">${esc(m)}</div><div class="bb"><button class="btn pri" data-share="${sel}">${t('Paylaş')}</button><button class="btn" data-open-ac>${t('Açıklamayı aç')}</button></div></div>`;
  } else {
    b.innerHTML = `<div class="selPill"><button class="sh" data-share="${sel}">${SHARE_SVG}<span>${t('Paylaş')} · ${esc(sName(S))} ${s}:${a}</span></button><button class="x" data-x>✕</button></div>`;
  }
  b.classList.add('on');
}
async function share(i) {
  const [s, a, , , ar] = ay(i);
  const ref = `${sName(surah(s))} ${s}:${a}`;
  const m = mealPlain(i).replace(/\(\d+\)/g, '').trim();
  const text = `${ar} ﴿${arDigits(a)}﴾` + (m ? `\n\n${m}` : '') + `\n\n— ${ref}`;
  try { if (navigator.share) await navigator.share({title: ref, text}); else { await navigator.clipboard.writeText(text); toast(t('Panoya kopyalandı')); } } catch (e) {}
}

// ---------- dokunma ----------
let lp = null;
$('#pager').addEventListener('pointerdown', e => {
  const el = e.target.closest('[data-i]'); if (!el) return;
  lp = {x: e.clientX, y: e.clientY, i: +el.dataset.i, t: setTimeout(() => { if (lp) { lp.done = true; select(lp.i); } }, 450)};
});
$('#pager').addEventListener('pointermove', e => { if (lp && !lp.done && Math.hypot(e.clientX - lp.x, e.clientY - lp.y) > 10) { clearTimeout(lp.t); lp = null; } });
['pointerup', 'pointercancel'].forEach(ev => $('#pager').addEventListener(ev, () => { if (lp) { clearTimeout(lp.t); if (!lp.done) lp = null; } }));
document.addEventListener('contextmenu', e => e.preventDefault());
document.addEventListener('click', e => {
  const sh = e.target.closest('[data-share]'); if (sh) { share(+sh.dataset.share); return; }
  const go = e.target.closest('[data-go]'); if (go) { const i = +go.dataset.go; if (i >= 0 && i < Q.ayahs.length) { sel = i; const p = ay(i)[2]; if (p !== cur) goPage(p); else rerender(); } return; }
  if (e.target.closest('[data-x]')) { sel = null; rerender(); updateSel(); return; }
  if (e.target.closest('[data-open-ac]')) { setMode('aciklama'); return; }
  if (e.target.closest('#pager')) { if (lp && lp.done) { lp = null; return; } bars(!document.body.classList.contains('barsOn')); }
});
$('#tapTop').addEventListener('click', () => bars(!document.body.classList.contains('barsOn')));
$('#bars').addEventListener('pointerdown', () => bars(true));
$('#modes').addEventListener('pointerdown', () => bars(true));
$('#slider').addEventListener('input', e => { $('#tPage').textContent = '· ' + e.target.value; });
$('#slider').addEventListener('change', e => goPage(+e.target.value, false));
$('#bMark').addEventListener('click', () => {
  const i = st.marks.indexOf(cur); if (i >= 0) st.marks.splice(i, 1); else st.marks.push(cur);
  save(); updateBars(); toast(i >= 0 ? t('İşaret kaldırıldı') : t('Sayfa işaretlendi'));
});
$('#bMenu').addEventListener('click', () => openIndex());
$('#bTitle').addEventListener('click', () => openIndex());
$('#bSet').addEventListener('click', () => openSettings());
document.querySelectorAll('#modes button').forEach(b => b.addEventListener('click', () => setMode(b.dataset.m)));
function setMode(m) { st.mode = m; save(); updateBars(); rerender(); updateSel(); }
let rz = 0; addEventListener('resize', () => { clearTimeout(rz); rz = setTimeout(() => { rerender(); slots[cur - 1].scrollIntoView({inline: 'start', block: 'nearest'}); }, 150); });


// ---------- iki parmakla büyütme (Android'deki gibi) ----------
let pz = null;
const tdist = e => Math.hypot(e.touches[0].clientX - e.touches[1].clientX, e.touches[0].clientY - e.touches[1].clientY);
$('#pager').addEventListener('touchstart', e => {
  if (e.touches.length !== 2) return;
  const box = (e.target.closest && e.target.closest('.fit')) || null; if (!box) return;
  const ar = box.dataset.fit === 'ar'; const key = ar ? 'arZoom' : 'mealZoom';
  const els = [...slots[cur - 1].querySelectorAll(ar ? '.fit[data-fit=ar] > div' : '.fit[data-fit=meal] > div, .ac .body')];
  pz = {d0: tdist(e), key, f: 1, els: els.map(el => [el, parseFloat(el.style.getPropertyValue('--fs')) || 16])};
  if (lp) { clearTimeout(lp.t); lp = null; }
}, {passive: true});
$('#pager').addEventListener('touchmove', e => {
  if (!pz || e.touches.length !== 2) return;
  e.preventDefault();
  const lim = pz.key === 'arZoom' ? [0.6, 2.5] : [0.6, 2.2];
  pz.f = Math.max(lim[0] / st[pz.key], Math.min(lim[1] / st[pz.key], tdist(e) / pz.d0));
  pz.els.forEach(([el, fs]) => el.style.setProperty('--fs', (fs * pz.f) + 'px'));
}, {passive: false});
$('#pager').addEventListener('touchend', e => {
  if (!pz || e.touches.length) return;
  const z = pz; pz = null;
  if (Math.abs(z.f - 1) < 0.02) return;
  st[z.key] = Math.round(st[z.key] * z.f * 100) / 100; save(); rerender();
});
document.addEventListener('gesturestart', e => e.preventDefault());

// ---------- alt sayfa ----------
function openSheet(html) { $('#sheetIn').innerHTML = html; $('#sheet').classList.remove('hidden'); }
function closeSheet() { $('#sheet').classList.add('hidden'); $('#sheetIn').innerHTML = ''; }
$('#sheet').addEventListener('click', e => { if (e.target.id === 'sheet' || e.target.closest('[data-close]')) closeSheet(); });

// Fihrist
let idxTab = 'sure';
function openIndex() {
  bars(false);
  openSheet(`<div class="shTop"><h3>${t('Fihrist')}</h3><button class="x" data-close>✕</button></div>
    <div class="seg" id="ixSeg"><button data-t="sure">${t('Sûreler')}</button><button data-t="cuz">${t('Cüzler')}</button><button data-t="isaret">${t('İşaretler')}</button></div>
    <input class="search" id="ixQ" placeholder="${t('Sûre ara…')}" type="search"><div class="list" id="ixL"></div>`);
  $('#ixSeg').addEventListener('click', e => { const b = e.target.closest('[data-t]'); if (b) { idxTab = b.dataset.t; drawIndex(); } });
  $('#ixQ').addEventListener('input', drawIndex);
  $('#ixL').addEventListener('click', e => { const b = e.target.closest('[data-p]'); if (b) { closeSheet(); sel = null; updateSel(); goPage(+b.dataset.p, false); } });
  drawIndex();
}
function drawIndex() {
  document.querySelectorAll('#ixSeg button').forEach(b => b.classList.toggle('on', b.dataset.t === idxTab));
  $('#ixQ').style.display = idxTab === 'sure' ? '' : 'none';
  const q = ($('#ixQ').value || '').toLocaleLowerCase('tr').trim();
  const curS = surahsOnPage(cur).map(s => s[0]);
  let h = '';
  if (idxTab === 'sure') {
    for (const S of Q.surahs) {
      const nm = sName(S);
      if (q && !(nm.toLocaleLowerCase('tr').includes(q) || S[1].toLocaleLowerCase('tr').includes(q) || String(S[0]) === q)) continue;
      const p = ay(firstIdxOfSurah[S[0]])[2];
      h += `<button class="item${curS.includes(S[0]) ? ' cur' : ''}" data-p="${p}"><span class="n">${S[0]}</span><span class="tx"><b>${esc(nm)}</b><small>${t(S[4])} · ${t('%1 âyet', S[3])} · ${t('Sayfa %1', p)}</small></span><span class="arn">${S[2].replace(/^سُورَةُ\s*/, '')}</span></button>`;
    }
  } else if (idxTab === 'cuz') {
    juzStart.forEach((p, j) => { if (!p) return; const a = ay(pageRanges[p - 1][0]); h += `<button class="item" data-p="${p}"><span class="n">${j}</span><span class="tx"><b>${t('%1. Cüz', j)}</b><small>${esc(sName(surah(a[0])))} ${a[1]} · ${t('Sayfa %1', p)}</small></span></button>`; });
  } else {
    if (!st.marks.length) h = `<p class="muted">${t('Henüz işaretli sayfa yok. Üstteki işaret düğmesiyle bulunduğunuz sayfayı işaretleyebilirsiniz.')}</p>`;
    [...st.marks].sort((a, b) => a - b).forEach(p => { const ss = surahsOnPage(p); h += `<button class="item" data-p="${p}"><span class="n">${p}</span><span class="tx"><b>${esc(ss.map(sName).join(' – '))}</b><small>${t('Sayfa %1', p)}</small></span></button>`; });
  }
  $('#ixL').innerHTML = h;
}

// Ayarlar
let setCat = null;
const CATS = [['view', 'Görünüm', '#2F7D6D'], ['meal', 'Meal', '#C48A2C'], ['lang', 'Dil', '#3A6FD8'], ['audio', 'Sesli okuma', '#D2475F'], ['speech', 'Meal sesi', '#8A5CD6'], ['pack', 'Kişisel dosya', '#6D6A64'], ['about', 'Hakkında ve kaynaklar', '#9A948A']];
function catValue(c) {
  if (c === 'view') return t(FONTS.find(f => f[0] === st.font)[1]) + ' · ' + t({krem: 'Krem', beyaz: 'Beyaz', gece: 'Gece'}[st.theme]);
  if (c === 'meal') return mealInfo(st.meal).short;
  if (c === 'lang') return LANGS.find(l => l[0] === st.lang)[1];
  if (c === 'pack') return PERS ? t('Aktarıldı') : t('Aktarılmadı');
  if (c === 'audio') return reciterById(st.reciter).name.split(' ').pop();
  if (c === 'speech') return t({ar: 'Sadece Arapça', ar_meal: 'Arapça + meal', meal: 'Sadece meal'}[st.audioMode]);
  return '';
}
function openSettings() {
  bars(false);
  const w = innerWidth >= 760; if (w && !setCat) setCat = 'view';
  openSheet(`<div class="shTop"><h3>${t('Ayarlar')}</h3><button class="x" data-close>✕</button></div><div class="set ${w ? 'wide' : 'narrow'}${!w && setCat ? ' open' : ''}" id="setW"><div class="cats" id="setC"></div><div class="det" id="setD"></div></div>`);
  $('#setC').addEventListener('click', e => { const b = e.target.closest('[data-c]'); if (b) { setCat = b.dataset.c; drawSettings(); } });
  $('#setD').addEventListener('click', onSetClick);
  $('#setD').addEventListener('input', onSetInput);
  drawSettings();
}
function drawSettings() {
  const w = innerWidth >= 760; const W = $('#setW'); if (!W) return;
  W.classList.toggle('open', !w && !!setCat);
  $('#setC').innerHTML = CATS.map(([k, n, c]) => `<button class="cat${setCat === k && w ? ' on' : ''}" data-c="${k}"><span class="ic" style="background:${c}">${n[0]}</span><span class="tx"><b>${t(n)}</b><small>${esc(catValue(k))}</small></span><span>›</span></button>`).join('');
  let h = w ? '' : `<button class="backb" data-back>‹ ${t('Ayarlar')}</button>`;
  const c = setCat || 'view';
  if (c === 'view') {
    h += `<div class="card"><h4>${t('Tema')}</h4><div class="seg" style="margin:0">${[['krem', 'Krem'], ['beyaz', 'Beyaz'], ['gece', 'Gece']].map(([k, n]) => `<button data-theme="${k}" class="${st.theme === k ? 'on' : ''}">${t(n)}</button>`).join('')}</div></div>`;
    h += `<div class="card"><h4>${t('Arapça yazı büyüklüğü')}</h4><div class="sl"><input type="range" min="0.6" max="2.5" step="0.05" value="${st.arZoom}" data-zoom="arZoom"><b>${Math.round(st.arZoom * 100)}%</b></div>
      <h4 style="margin-top:12px">${t('Meal yazı büyüklüğü')}</h4><div class="sl"><input type="range" min="0.6" max="2.2" step="0.05" value="${st.mealZoom}" data-zoom="mealZoom"><b>${Math.round(st.mealZoom * 100)}%</b></div></div>`;
    h += `<h4 style="margin:12px 2px 8px">${t('Arapça yazı şekli')}</h4><div class="fonts">${FONTS.map(([k, n, s]) => `<button class="fc${st.font === k ? ' on' : ''}" data-font="${k}"><div class="s" style="font-family:'${k}',amiri">بِسْمِ ٱللَّهِ</div><b>${t(n)}</b><small>${t(s)}</small></button>`).join('')}</div>`;
  } else if (c === 'meal') {
    h += MEALLER.map(m => { const ok = hasMeal(m.id); return `<button class="opt${st.meal === m.id ? ' on' : ''}" data-meal="${m.id}" ${ok ? '' : 'disabled'}><span class="rd"></span><span class="tx"><b>${esc(m.short)}</b><small>${esc(m.name)}${ok ? '' : ' · ' + t('Kişisel dosya gerekli')}</small></span></button>`; }).join('');
  } else if (c === 'lang') {
    h += LANGS.map(([k, n]) => `<button class="opt${st.lang === k ? ' on' : ''}" data-lang="${k}"><span class="rd"></span><span class="tx"><b>${n}</b></span></button>`).join('');
  } else if (c === 'audio') { h += audioSettings();
  } else if (c === 'speech') { h += speechSettings();
  } else if (c === 'pack') {
    h += `<div class="card"><h4>${t('Kişisel dosya')}</h4><p class="muted">${t('Hayrat meali, Diyanet meali ve tevâfuk kırmızı kelimeleri kişisel izinle kullanıldığı için uygulamanın içinde değildir. Size verilen Kuran-Kisisel.json dosyasını bir kere aktarın; telefonda saklanır ve internetsiz çalışır.')}</p>
      <p><b>${PERS ? t('Durum: aktarıldı') : t('Durum: aktarılmadı')}</b></p>
      <div style="display:flex;gap:8px;flex-wrap:wrap"><button class="btn pri" data-import>${t('Dosyayı aktar')}</button>${PERS ? `<button class="btn" data-delpers>${t('Sil')}</button>` : ''}</div></div>`;
  } else {
    h += `<div class="card"><h4>${t('Hakkında ve kaynaklar')}</h4><p class="muted">
      Kur'an metni: Tanzil / alquran.cloud (Osmanî hat)<br>Elmalılı, Gölpınarlı, Bubenheim, Saheeh, Müyesser: alquran.cloud<br>Feridun A. – Uzun Meal ve Kur'an Açıklamaları: Feridun A.<br>
      Yazı tipleri: Kral Fahd Kur'an Matbaası (Medine, Osman Taha), Google Fonts (OFL)<br>Hayrat meali, Diyanet meali ve kırmızı kelimeler: kişisel dosya</p></div>`;
  }
  $('#setD').innerHTML = h;
}
function onSetClick(e) {
  const b = e.target.closest('button'); if (!b) return;
  if (onAudioSetClick(b)) return;
  if (b.dataset.back != null) { setCat = null; drawSettings(); return; }
  if (b.dataset.theme) { st.theme = b.dataset.theme; save(); applyTheme(); drawSettings(); return; }
  if (b.dataset.font) { st.font = b.dataset.font; save(); drawSettings(); rerender(); return; }
  if (b.dataset.meal) { st.meal = b.dataset.meal; save(); loadMeal(st.meal).then(() => { drawSettings(); rerender(); }); return; }
  if (b.dataset.lang) { st.lang = b.dataset.lang; save(); document.documentElement.lang = st.lang; drawSettings(); updateBars(); rerender(); return; }
  if (b.dataset.import != null) { $('#fileIn').click(); return; }
  if (b.dataset.delpers != null) { idb.del('personal').then(() => { applyPersonal(null); if (mealInfo(st.meal).personal) st.meal = 'elmalili'; save(); drawSettings(); rerender(); toast(t('Kişisel dosya silindi')); }); }
}
function onSetInput(e) {
  if (e.target.dataset.sp) { st[e.target.dataset.sp] = +e.target.value; e.target.nextElementSibling.textContent = Math.round(st[e.target.dataset.sp] * 100) + '%'; save(); return; }
  if (e.target.dataset.voice != null) { st.voice = e.target.value; save(); return; }
  const z = e.target.dataset.zoom; if (!z) return;
  st[z] = +e.target.value; e.target.nextElementSibling.textContent = Math.round(st[z] * 100) + '%'; save();
  clearTimeout(onSetInput._t); onSetInput._t = setTimeout(rerender, 120);
}
$('#fileIn').addEventListener('change', async e => {
  const f = e.target.files[0]; e.target.value = ''; if (!f) return;
  try {
    const d = JSON.parse(await f.text());
    if (!d.kuranKisisel) throw new Error('format');
    await idb.set('personal', d); applyPersonal(d);
    if (navigator.storage && navigator.storage.persist) navigator.storage.persist();
    toast(t('Kişisel dosya aktarıldı')); drawSettings(); rerender();
  } catch (err) { toast(t('Bu dosya okunamadı. Kuran-Kisisel.json dosyasını seçin.')); }
});
function applyTheme() {
  document.body.classList.remove('beyaz', 'gece'); if (st.theme !== 'krem') document.body.classList.add(st.theme);
  document.querySelector('meta[name=theme-color]').content = getComputedStyle(document.body).getPropertyValue('--app').trim();
}


// ---------- sesli okuma ----------
const RECITERS = [
  {id:'sudais', name:'Abdurrahman es-Südeys', folder:'Abdurrahmaan_As-Sudais_192kbps', mb:1300},
  {id:'shuraym', name:"Su'ûd eş-Şüreym", folder:'Saood_ash-Shuraym_128kbps', mb:800},
  {id:'maher', name:'Mâhir el-Muaykılî', folder:'MaherAlMuaiqly128kbps', mb:750},
  {id:'rifai', name:'Hânî er-Rifâî', folder:'Hani_Rifai_192kbps', mb:1500},
  {id:'basfar', name:'Abdullah Basfar', folder:'Abdullah_Basfar_192kbps', mb:1400},
  {id:'afasy', name:'Mişârî Râşid el-Afâsî', folder:'Alafasy_128kbps', mb:800},
  {id:'husary', name:'Mahmûd Halîl el-Husarî', folder:'Husary_128kbps', mb:900},
  {id:'danis', name:'İshak Danış', surah:true, base:'https://archive.org/download/ishak_danish/', mb:890},
  {id:'collak', name:'Fatih Çollak', surah:true, base:'https://archive.org/download/fatihcollakk/', mb:700, fallback:'danis'},
];
const reciterById = id => RECITERS.find(r => r.id === id) || RECITERS[0];
const code6 = (s, a) => String(s).padStart(3, '0') + String(a).padStart(3, '0');
const ayahUrl = (r, s, a) => `https://everyayah.com/data/${r.folder}/${code6(s, a)}.mp3`;
const TIMING = {};
const loadTiming = async id => TIMING[id] || (TIMING[id] = await getJSON('data/timing/' + id + '.json').catch(() => ({})));
const reciterFor = (r, s) => (TIMING[r.id] && TIMING[r.id][s]) || !r.fallback ? r : reciterById(r.fallback);
const surahUrl = (r, s) => r.base + ((TIMING[r.id] && TIMING[r.id][s] && TIMING[r.id][s].f) || String(s).padStart(3, '0') + '.mp3');

// İndirilen sesler telefonda (Cache Storage) saklanır, internetsiz çalınır
const SES = 'kuran-ses';
const Dl = {active:null, done:0, total:0, failed:0, stop:false, counts:{},
  async count(r) {
    try { const c = await caches.open(SES); const ks = await c.keys(); const key = r.surah ? r.base : '/' + r.folder + '/'; this.counts[r.id] = ks.filter(k => k.url.includes(key)).length; } catch (e) { this.counts[r.id] = 0; }
    return this.counts[r.id];
  },
  async items(r, surahNo) {
    if (r.surah) { await loadTiming(r.id); if (r.fallback) await loadTiming(r.fallback); return (surahNo ? [surahNo] : [...Array(114)].map((_, i) => i + 1)).map(s => surahUrl(reciterFor(r, s), s)); }
    const idxs = surahNo ? Q.ayahs.map((a, i) => a[0] === surahNo ? i : -1).filter(i => i >= 0) : Q.ayahs.map((_, i) => i);
    return [ayahUrl(r, 1, 1), ...idxs.map(i => ayahUrl(r, ay(i)[0], ay(i)[1]))];
  },
  async start(r, urls) {
    if (this.active) return;
    this.active = r.id; this.done = 0; this.total = urls.length; this.failed = 0; this.stop = false; drawSettings();
    if (navigator.storage && navigator.storage.persist) navigator.storage.persist();
    const c = await caches.open(SES);
    let k = 0; const N = r.surah ? 2 : 6;
    const worker = async () => {
      while (k < urls.length && !this.stop) {
        const u = urls[k++]; let ok = !!(await c.match(u));
        for (let tr = 0; !ok && tr < 3 && !this.stop; tr++) { try { const res = await fetch(u, {mode: 'cors'}); if (res.ok) { await c.put(u, res); ok = true; } } catch (e) {} if (!ok) await new Promise(z => setTimeout(z, 1500 * (tr + 1))); }
        this.done++; if (!ok) this.failed++;
        if (this.done % (r.surah ? 1 : 25) === 0) drawDl();
      }
    };
    await Promise.all([...Array(N)].map(worker));
    this.active = null; await this.count(r); drawSettings();
  },
  async del(r) { if (this.active === r.id) this.stop = true; const c = await caches.open(SES); const key = r.surah ? r.base : '/' + r.folder + '/'; for (const q of await c.keys()) if (q.url.includes(key)) await c.delete(q); await this.count(r); drawSettings(); },
};
function drawDl() { const e = document.getElementById('dlp'); if (e) { e.querySelector('progress').value = Dl.done; e.querySelector('span').textContent = t('İndiriliyor… %1 / %2', Dl.done, Dl.total); } }

// Telefonun konuşma sesi (meal ve açıklama)
const Sp = {
  voices() { const l = mealSpeechLang(); return (speechSynthesis.getVoices() || []).filter(v => v.lang.toLowerCase().startsWith(l)); },
  pick() { const vs = this.voices(); return vs.find(v => v.voiceURI === st.voice) || vs.find(v => /male|erkek|cem|tolga|daniel|markus|majed|maged|tarik/i.test(v.name) && !/female/i.test(v.name)) || vs[0]; },
  speak(text, done) {
    speechSynthesis.cancel();
    const parts = (text.match(/[^.!?;:]+[.!?;:]*\s*/g) || [text]).reduce((a, x) => { if (a.length && (a[a.length - 1] + x).length < 220) a[a.length - 1] += x; else a.push(x); return a; }, []);
    const v = this.pick(); let i = 0; const tok = Sp.tok = {};
    const next = () => {
      if (tok !== Sp.tok) return;
      if (i >= parts.length) { done && done(); return; }
      const u = new SpeechSynthesisUtterance(parts[i++]); u.lang = v ? v.lang : mealSpeechLang(); if (v) u.voice = v; u.pitch = st.pitch; u.rate = st.rate;
      u.onend = next; u.onerror = next; speechSynthesis.speak(u);
    };
    next();
  },
  stop() { Sp.tok = null; try { speechSynthesis.cancel(); } catch (e) {} },
};
const mealSpeechLang = () => st.meal.startsWith('de.') ? 'de' : st.meal.startsWith('en.') ? 'en' : st.meal.startsWith('ar.') ? 'ar' : 'tr';
if (window.speechSynthesis) speechSynthesis.onvoiceschanged = () => { if (setCat === 'speech') drawSettings(); };

const Au = {
  el: new Audio(), playing: null, paused: false, phase: 'ar', loading: false, basm: false, sRec: null, sNum: 0, sFirst: 0, sT: null, resume: null, blob: null,
  async src(u) { try { const c = await caches.open(SES); const m = await c.match(u); if (m) { if (this.blob) URL.revokeObjectURL(this.blob); this.blob = URL.createObjectURL(await m.blob()); return this.blob; } } catch (e) {} return u; },
  set(i, phase) { this.playing = i; if (phase) this.phase = phase; updateHl(); updatePill(); mediaMeta(); },
  play(i) {
    const r = reciterById(st.reciter); this.paused = false;
    const [s, a] = ay(i); this.basm = a === 1 && s !== 1 && s !== 9;
    if (st.audioMode === 'meal') this.texts(i, () => this.next(i));
    else if (r.surah) this.startSurah(i); else this.startAyah(i);
  },
  async startAyah(i) {
    Sp.stop(); this.set(i, 'ar'); this.loading = true;
    const r = reciterById(st.reciter); const [s, a] = this.basm ? [1, 1] : ay(i);
    const tok = this.tok = {};
    const u = await this.src(ayahUrl(r, s, a)); if (tok !== this.tok) return;
    this.el.onended = () => { if (this.basm) { this.basm = false; this.startAyah(i); } else this.texts(i, () => this.next(i)); };
    this.el.ontimeupdate = null;
    this.el.src = u; this.el.play().then(() => { this.loading = false; }).catch(() => this.fail());
  },
  async startSurah(i, fromStart) {
    Sp.stop(); const [s, a] = ay(i); const R = reciterById(st.reciter);
    await loadTiming(R.id); if (R.fallback) await loadTiming(R.fallback);
    const r = reciterFor(R, s); const T = TIMING[r.id] && TIMING[r.id][s];
    this.set(i, 'ar');
    const seek = (fromStart || a === 1 || !T) ? 0 : Math.max(0, T.t[a - 1][0] - 150) / 1000;
    if (this.sRec === r.id && this.sNum === s && this.el.src) { this.el.currentTime = seek; this.el.play().catch(() => {}); return; }
    this.sRec = r.id; this.sNum = s; this.sT = T; this.sFirst = firstIdxOfSurah[s];
    const tok = this.tok = {}; this.loading = true;
    const u = await this.src(surahUrl(r, s)); if (tok !== this.tok) return;
    this.el.onended = () => this.surahDone();
    this.el.ontimeupdate = () => this.tick();
    this.el.src = u;
    const go = () => { this.el.currentTime = seek; this.el.play().then(() => { this.loading = false; }).catch(() => this.fail()); };
    if (seek > 0) { this.el.addEventListener('loadedmetadata', go, {once: true}); this.el.load(); } else go();
  },
  tick() {
    const T = this.sT; if (!T || this.phase !== 'ar' || this.paused || this.playing == null) return;
    const pos = this.el.currentTime * 1000; let k = 0;
    while (k + 1 < T.t.length && pos >= T.t[k + 1][0] - 120) k++;
    const g = this.sFirst + k;
    if (g !== this.playing) {
      if (g === this.playing + 1 && this.needTexts()) { this.el.pause(); const prev = this.playing; this.texts(prev, () => { this.set(g, 'ar'); this.el.play().catch(() => {}); }); return; }
      this.set(g);
    }
  },
  surahDone() {
    const nf = firstIdxOfSurah[this.sNum + 1]; const cur = this.playing;
    this.texts(cur, () => { if (nf == null) this.stop(); else this.startSurah(nf, true); });
  },
  needTexts() { return st.audioMode !== 'ar' || (st.mode === 'aciklama' && st.ackRead); },
  /** âyetten sonra okunacak Türkçe metinler: meal, açıklama sayfasında açıklama */
  texts(i, then) {
    const list = [];
    if (st.audioMode !== 'ar') { const m = mealPlain(i).replace(/⁽\d+⁾|\(\d+\)|\[\d+\]/g, '').trim(); if (m) list.push(m); }
    if (st.mode === 'aciklama' && st.ackRead) { const [s, a] = ay(i); const A = ACK && ACK[s] && ACK[s].ayat[a]; if (A) list.push(A.t + '. ' + A.p.join(' ')); }
    if (!list.length) { then(); return; }
    this.set(i, 'meal'); const tok = this.ttok = {};
    Sp.speak(list.join(' '), () => { if (tok === this.ttok && !this.paused) { this.phase = 'ar'; then(); } });
    this.resume = then;
  },
  next(i) {
    const n = i + 1; if (n >= Q.ayahs.length) { this.stop(); return; }
    const [s, a] = ay(n); this.basm = a === 1 && s !== 1 && s !== 9;
    if (st.audioMode === 'meal') this.texts(n, () => this.next(n));
    else if (reciterById(st.reciter).surah) this.startSurah(n, a === 1); else this.startAyah(n);
  },
  toggle() {
    if (this.playing == null) return;
    if (this.phase === 'meal') { if (this.paused) { this.paused = false; const r = this.resume; this.texts(this.playing, r); } else { this.paused = true; Sp.stop(); } }
    else if (this.paused) { this.paused = false; this.el.play().catch(() => {}); } else { this.paused = true; this.el.pause(); }
    updatePill();
  },
  stop() { this.tok = {}; this.ttok = {}; Sp.stop(); this.el.pause(); this.el.removeAttribute('src'); this.el.load(); this.playing = null; this.paused = false; this.sRec = null; this.sT = null; updateHl(); updatePill(); },
  fail() { this.loading = false; toast(t("Ses çalınamadı. İnternet bağlantısını kontrol edin ya da sesleri Ayarlar'dan indirin.")); this.stop(); },
};
Au.el.preload = 'auto';
const hl = () => Au.playing != null ? Au.playing : sel;
function updateHl() {
  const i = hl();
  if (i != null && Au.playing != null && ay(i)[2] !== cur) { goPage(ay(i)[2]); return; }
  if (st.mode === 'aciklama' && Au.playing != null) { renderSlot(cur); if (wide()) {} return; }
  document.querySelectorAll('.slot [data-i].sel').forEach(e => e.classList.remove('sel'));
  if (i != null) document.querySelectorAll(`.slot [data-i="${i}"]`).forEach(e => e.classList.add('sel'));
}
function updatePill() {
  const on = Au.playing != null;
  $('#apill').classList.toggle('on', on);
  $('#apPlay').innerHTML = (on && !Au.paused) ? PAUSE_SVG : PLAY_SVG;
  $('#bPlay').innerHTML = (on && !Au.paused) ? PAUSE_SVG : PLAY_SVG;
  if ('mediaSession' in navigator) navigator.mediaSession.playbackState = on ? (Au.paused ? 'paused' : 'playing') : 'none';
}
function mediaMeta() {
  if (!('mediaSession' in navigator) || Au.playing == null) return;
  const [s, a] = ay(Au.playing);
  try { navigator.mediaSession.metadata = new MediaMetadata({title: `${sName(surah(s))} ${s}:${a}`, artist: reciterById(st.reciter).name, album: "Kur'an-ı Kerim", artwork: [{src: 'icons/icon-512.png', sizes: '512x512', type: 'image/png'}]}); } catch (e) {}
}
if ('mediaSession' in navigator) { navigator.mediaSession.setActionHandler('play', () => Au.paused && Au.toggle()); navigator.mediaSession.setActionHandler('pause', () => !Au.paused && Au.toggle()); }
const PLAY_SVG = '<svg viewBox="0 0 24 24"><path d="M8 5v14l11-7z" fill="currentColor" stroke="none"/></svg>';
const PAUSE_SVG = '<svg viewBox="0 0 24 24"><path d="M7 5h3.5v14H7zM13.5 5H17v14h-3.5z" fill="currentColor" stroke="none"/></svg>';
function onPlay() {
  if (Au.playing == null) { const idxs = ayahsOnPage(cur); Au.play(sel != null && idxs.includes(sel) ? sel : idxs[0]); } else Au.toggle();
}

function audioSettings() {
  let h = `<p class="muted" style="margin:0 2px 10px">${t('Okuyucuyu seçin. Sesleri telefona indirirseniz internetsiz dinlersiniz; indirmezseniz internetten çalınır.')}</p>`;
  const cs = surahsOnPage(cur)[0]; const cn = cs ? cs[0] : 1;
  for (const r of RECITERS) {
    const n = Dl.counts[r.id]; const all = r.surah ? 114 : Q.ayahs.length + 1;
    const stt = n == null ? '…' : n >= all ? t('Tamamı indirildi') : n > 0 ? (r.surah ? t('%1 / %2 sûre indirildi', n, 114) : t('%1 / %2 âyet indirildi', n, all)) : t('İnternetten çalınır · tamamı yaklaşık %1 MB', r.mb);
    h += `<div class="card" style="padding:12px 14px"><button class="opt${st.reciter === r.id ? ' on' : ''}" data-rec="${r.id}" style="padding:0;margin:0 0 8px;box-shadow:none"><span class="rd"></span><span class="tx"><b>${esc(r.name)}</b><small>${r.surah ? t('Türk hafız · sûre sûre') + ' · ' : ''}${stt}</small></span></button>`;
    if (Dl.active === r.id) h += `<div id="dlp"><progress max="${Dl.total}" value="${Dl.done}" style="width:100%"></progress><div style="display:flex;align-items:center;gap:8px"><span class="muted" style="flex:1">${t('İndiriliyor… %1 / %2', Dl.done, Dl.total)}</span><button class="btn" data-dlstop>${t('Durdur')}</button></div></div>`;
    else h += `<div style="display:flex;gap:8px;flex-wrap:wrap"><button class="btn" data-dl="${r.id}" data-s="${cn}" ${Dl.active ? 'disabled' : ''}>${t('Bu sûreyi indir')}</button><button class="btn" data-dl="${r.id}" data-s="0" ${Dl.active ? 'disabled' : ''}>${t('Tümünü indir')}</button>${n ? `<button class="btn" data-dldel="${r.id}">${t('Sil')}</button>` : ''}</div>`;
    if (Dl.failed && !Dl.active && st.reciter === r.id) h += `<p class="muted" style="color:var(--red)">${t('%1 dosya indirilemedi. İnternet bağlantısını kontrol edip tekrar deneyin.', Dl.failed)}</p>`;
    h += '</div>';
  }
  h += `<p class="muted">${t('Kaynaklar: EveryAyah (Arap kâriler), archive.org (İshak Danış, Fatih Çollak). iPhone ekranı kilitlenince sesi durdurabilir.')}</p>`;
  if (RECITERS.some(r => Dl.counts[r.id] == null)) Promise.all(RECITERS.map(r => Dl.count(r))).then(() => { if (setCat === 'audio') drawSettings(); });
  return h;
}
function speechSettings() {
  const vs = window.speechSynthesis ? Sp.voices() : [];
  let h = `<div class="card"><h4>${t('Ne okunsun?')}</h4><div class="seg" style="margin:0">${[['ar', 'Sadece Arapça'], ['ar_meal', 'Arapça + meal'], ['meal', 'Sadece meal']].map(([k, n]) => `<button data-amode="${k}" class="${st.audioMode === k ? 'on' : ''}">${t(n)}</button>`).join('')}</div>
    <button class="opt${st.ackRead ? ' on' : ''}" data-ackread style="margin:10px 0 0;padding:6px 0;box-shadow:none"><span class="rd"></span><span class="tx"><b>${t('Açıklama sayfasında açıklamayı da oku')}</b><small>${t('Açıklama sayfasındayken âyetten sonra açıklama Türkçe okunur')}</small></span></button></div>`;
  h += `<div class="card"><h4>${t('Ses')}</h4>${vs.length ? `<select data-voice style="width:100%;height:40px;border-radius:10px;font-size:16px">${vs.map(v => `<option value="${esc(v.voiceURI)}" ${Sp.pick() === v ? 'selected' : ''}>${esc(v.name)}</option>`).join('')}</select>` : `<p class="muted">${t('Bu dilde konuşma sesi bulunamadı. iPhone Ayarlar → Erişilebilirlik → Seslendirilen İçerik → Sesler bölümünden Türkçe ses indirebilirsiniz.')}</p>`}
    <h4 style="margin-top:12px">${t('Ses kalınlığı')}</h4><div class="sl"><input type="range" min="0.5" max="1.5" step="0.05" value="${st.pitch}" data-sp="pitch"><b>${Math.round(st.pitch * 100)}%</b></div>
    <h4 style="margin-top:12px">${t('Konuşma hızı')}</h4><div class="sl"><input type="range" min="0.5" max="1.5" step="0.05" value="${st.rate}" data-sp="rate"><b>${Math.round(st.rate * 100)}%</b></div>
    <button class="btn pri" data-sample style="margin-top:10px">${t('Örnek dinle')}</button></div>`;
  return h;
}
function onAudioSetClick(b) {
  const d = b.dataset;
  if (d.rec) { st.reciter = d.rec; save(); if (Au.playing != null) Au.stop(); drawSettings(); return true; }
  if (d.dl) { const r = reciterById(d.dl); st.reciter = r.id; save(); Dl.items(r, +d.s || null).then(u => Dl.start(r, u)); return true; }
  if (d.dlstop != null) { Dl.stop = true; return true; }
  if (d.dldel) { Dl.del(reciterById(d.dldel)); return true; }
  if (d.amode) { st.audioMode = d.amode; save(); drawSettings(); return true; }
  if (d.ackread != null) { st.ackRead = !st.ackRead; save(); drawSettings(); return true; }
  if (d.sample != null) { Sp.speak({de: 'Im Namen Allahs, des Allerbarmers, des Barmherzigen.', en: 'In the name of Allah, the Entirely Merciful, the Especially Merciful.', ar: 'بسم الله الرحمن الرحيم'}[mealSpeechLang()] || "Rahmân ve Rahîm olan Allah'ın adıyla."); return true; }
  return false;
}
$('#bPlay').addEventListener('click', onPlay);
$('#apPlay').addEventListener('click', onPlay);
$('#apClose').addEventListener('click', () => Au.stop());

// ---------- başlat ----------
(async function init() {
  applyTheme();
  document.documentElement.lang = st.lang;
  try {
    const [core, i18n] = await Promise.all([getJSON('data/core.json'), getJSON('data/i18n.json')]);
    Q = core; I18N = i18n;
  } catch (e) { document.body.innerHTML = '<p style="padding:40px">Veriler yüklenemedi. İnternet bağlantısını kontrol edin.</p>'; return; }
  Q.ayahs.forEach((a, i) => {
    const p = a[2]; pageOfIdx[i] = p;
    if (!pageRanges[p - 1]) pageRanges[p - 1] = [i, i]; else pageRanges[p - 1][1] = i;
    if (a[1] === 1) firstIdxOfSurah[a[0]] = i;
    if (!juzStart[a[3]]) juzStart[a[3]] = p;
  });
  try { applyPersonal(await idb.get('personal')); } catch (e) {}
  if (!hasMeal(st.meal)) st.meal = 'elmalili';
  await Promise.all([loadMeal('elmalili'), loadMeal(st.meal)]).catch(() => {});
  getJSON('data/aciklama.json').then(d => { ACK = d; if (st.mode === 'aciklama') rerender(); }).catch(() => {});
  buildPager(); updateBars(); updatePill(); bars(true);
  if (document.fonts && document.fonts.ready) document.fonts.load(`20px "${st.font}"`).then(() => rerender()).catch(() => {});
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
})();
