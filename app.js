'use strict';
/* Kur'an-ı Kerim – web uygulaması (iPhone / tarayıcı). Android uygulamasının okuma kısmı. */
const $ = s => document.querySelector(s);
const esc = s => String(s).replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));

// ---------- ayarlar (telefonda saklanır) ----------
const DEF = {lang:'tr', meal:'elmalili', font:'medina', theme:'krem', arZoom:1, mealZoom:1, page:1, mode:'meal', marks:[]};
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
    h += `<p class="ln${i === sel ? ' sel' : ''}" data-i="${i}"><b>${l.label}- </b>${mealHtml(l.text)}</p>`;
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
    para.push(`<span data-i="${i}"${i === sel ? ' class="sel"' : ''}>${arWords(i)} <span class="m">${marker(a)}</span></span>`);
  }
  flush();
  const ss = surahsOnPage(p);
  return `<div class="paper"><div class="phead mtop"><span>${p}</span><span style="font-family:amiri;font-size:16px;color:var(--ink)">${ss.length ? ss[ss.length - 1][2] : ''}</span></div>
    <div class="frame"><div class="frame2"><div class="fit" data-fit="ar"><div class="ar" style="font-family:'${st.font}',amiri">${h}</div></div></div></div></div>`;
}
const SHARE_SVG = '<svg viewBox="0 0 24 24"><circle cx="18" cy="5" r="2.5"/><circle cx="6" cy="12" r="2.5"/><circle cx="18" cy="19" r="2.5"/><path d="M8.2 10.8l7.6-4.4M8.2 13.2l7.6 4.4"/></svg>';
function aciklamaPage(p) {
  const idxs = ayahsOnPage(p);
  const cur = idxs.includes(sel) ? sel : idxs[0];
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
const CATS = [['view', 'Görünüm', '#2F7D6D'], ['meal', 'Meal', '#C48A2C'], ['lang', 'Dil', '#3A6FD8'], ['pack', 'Kişisel dosya', '#6D6A64'], ['about', 'Hakkında ve kaynaklar', '#9A948A']];
function catValue(c) {
  if (c === 'view') return t(FONTS.find(f => f[0] === st.font)[1]) + ' · ' + t({krem: 'Krem', beyaz: 'Beyaz', gece: 'Gece'}[st.theme]);
  if (c === 'meal') return mealInfo(st.meal).short;
  if (c === 'lang') return LANGS.find(l => l[0] === st.lang)[1];
  if (c === 'pack') return PERS ? t('Aktarıldı') : t('Aktarılmadı');
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
  if (b.dataset.back != null) { setCat = null; drawSettings(); return; }
  if (b.dataset.theme) { st.theme = b.dataset.theme; save(); applyTheme(); drawSettings(); return; }
  if (b.dataset.font) { st.font = b.dataset.font; save(); drawSettings(); rerender(); return; }
  if (b.dataset.meal) { st.meal = b.dataset.meal; save(); loadMeal(st.meal).then(() => { drawSettings(); rerender(); }); return; }
  if (b.dataset.lang) { st.lang = b.dataset.lang; save(); document.documentElement.lang = st.lang; drawSettings(); updateBars(); rerender(); return; }
  if (b.dataset.import != null) { $('#fileIn').click(); return; }
  if (b.dataset.delpers != null) { idb.del('personal').then(() => { applyPersonal(null); if (mealInfo(st.meal).personal) st.meal = 'elmalili'; save(); drawSettings(); rerender(); toast(t('Kişisel dosya silindi')); }); }
}
function onSetInput(e) {
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
  buildPager(); updateBars(); bars(true);
  if (document.fonts && document.fonts.ready) document.fonts.load(`20px "${st.font}"`).then(() => rerender()).catch(() => {});
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
})();
