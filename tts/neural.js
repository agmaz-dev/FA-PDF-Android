// Doğal Türkçe erkek sesi (Piper, tr_TR-dfki-medium) – telefonun kendisinde çalışır
import { createPiperPhonemize } from './piper.js';

const BASE = new URL('./', import.meta.url).href;
// her dil için doğal erkek sesi
const VOICES = {tr: 'tr_TR-dfki-medium', de: 'de_DE-thorsten-medium', en: 'en_US-ryan-medium', ar: 'ar_JO-kareem-medium'};
const CACHE = 'kuran-tts';
let session = null, cfg = null, loading = null, curVoice = null, lang = 'tr';
const MODEL = () => BASE + VOICES[lang] + '.onnx';
const CFG = () => BASE + VOICES[lang] + '.onnx.json';
export function setLang(l) { if (!VOICES[l]) l = 'tr'; if (l !== lang) { lang = l; session = null; loading = null; } }

function loadOrt() {
  if (window.ort) return Promise.resolve();
  return new Promise((res, rej) => { const s = document.createElement('script'); s.src = BASE + 'ort.min.js'; s.onload = res; s.onerror = rej; document.head.appendChild(s); });
}
async function cached(url, onProgress) {
  const c = await caches.open(CACHE);
  const hit = await c.match(url); if (hit) return hit.arrayBuffer();
  const r = await fetch(url); if (!r.ok) throw new Error('indirilemedi');
  const total = +(r.headers.get('Content-Length') || 0); const rd = r.body.getReader(); const parts = []; let got = 0;
  for (;;) { const {done, value} = await rd.read(); if (done) break; parts.push(value); got += value.length; onProgress && onProgress(got, total); }
  const blob = new Blob(parts); await c.put(url, new Response(blob)); return blob.arrayBuffer();
}
export async function isDownloaded() { try { return !!(await (await caches.open(CACHE)).match(MODEL())); } catch (e) { return false; } }
export async function remove() { await caches.delete(CACHE); session = null; loading = null; }
export function ensure(onProgress) {
  if (session) return Promise.resolve();
  if (!loading) loading = (async () => {
    await loadOrt();
    ort.env.wasm.numThreads = 1; ort.env.wasm.wasmPaths = BASE;
    cfg = JSON.parse(new TextDecoder().decode(await cached(CFG())));
    const buf = await cached(MODEL(), onProgress);
    session = await ort.InferenceSession.create(buf, {executionProviders: ['wasm']});
  })().catch(e => { loading = null; throw e; });
  return loading;
}
function phonemize(text) {
  return new Promise(async (res, rej) => {
    let done = false;
    try {
      const m = await createPiperPhonemize({
        print: l => { if (!done) { done = true; res(JSON.parse(l).phoneme_ids); } },
        printErr: () => {},
        locateFile: f => f.endsWith('.wasm') ? BASE + 'piper_phonemize.wasm' : f.endsWith('.data') ? BASE + 'piper_phonemize.data' : f,
      });
      m.callMain(['-l', cfg.espeak.voice, '--input', JSON.stringify([{text}]), '--espeak_data', '/espeak-ng-data']);
    } catch (e) { if (!done) rej(e); }
  });
}
function wav(f32, sr) {
  const n = f32.length, dv = new DataView(new ArrayBuffer(44 + n * 2));
  const w = (o, s) => { for (let i = 0; i < s.length; i++) dv.setUint8(o + i, s.charCodeAt(i)); };
  w(0, 'RIFF'); dv.setUint32(4, 36 + n * 2, true); w(8, 'WAVE'); w(12, 'fmt '); dv.setUint32(16, 16, true); dv.setUint16(20, 1, true); dv.setUint16(22, 1, true);
  dv.setUint32(24, sr, true); dv.setUint32(28, sr * 2, true); dv.setUint16(32, 2, true); dv.setUint16(34, 16, true); w(36, 'data'); dv.setUint32(40, n * 2, true);
  for (let i = 0, o = 44; i < n; i++, o += 2) { const s = Math.max(-1, Math.min(1, f32[i])); dv.setInt16(o, s < 0 ? s * 32768 : s * 32767, true); }
  return new Blob([dv.buffer], {type: 'audio/wav'});
}
/** Metni sese çevirir; rate: 1 normal, büyük = hızlı */
export async function synth(text, rate = 1) {
  await ensure();
  const ids = await phonemize(text);
  const inf = cfg.inference;
  const feeds = {
    input: new ort.Tensor('int64', BigInt64Array.from(ids.map(BigInt)), [1, ids.length]),
    input_lengths: new ort.Tensor('int64', BigInt64Array.from([BigInt(ids.length)]), [1]),
    scales: new ort.Tensor('float32', Float32Array.from([inf.noise_scale, inf.length_scale / rate, inf.noise_w]), [3]),
  };
  const out = await session.run(feeds);
  return wav(out.output.data, cfg.audio.sample_rate);
}
