// İnternetsiz çalışma: dosyalar ilk açılışta saklanır, sonra telefondan okunur
const V = 'kuran-v8';
const CORE = ['./', 'index.html', 'app.css?v=8', 'app.js?v=8', 'manifest.webmanifest', 'data/core.json', 'data/i18n.json', 'data/aciklama.json',
  'data/meal/tr.yazir.json', 'data/meal/feridun.json', 'fonts/medina.ttf', 'fonts/amiri.ttf', 'icons/icon-192.png'];
self.addEventListener('install', e => { e.waitUntil(caches.open(V).then(c => c.addAll(CORE)).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => { e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k.startsWith('kuran-v') && k !== V).map(k => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;
  const u = new URL(e.request.url);
  if (u.origin !== location.origin) return;
  // uygulama dosyaları: önce internetten (güncel sürüm), olmazsa saklanan
  const fresh = /\/(index\.html|app\.js|app\.css|sw\.js)?$/.test(u.pathname) || u.pathname.endsWith('.html');
  if (fresh) {
    e.respondWith(fetch(e.request, {cache: 'no-store'}).then(r => { const c = r.clone(); caches.open(V).then(x => x.put(e.request, c)); return r; }).catch(() => caches.match(e.request).then(r => r || caches.match('index.html'))));
    return;
  }
  e.respondWith(caches.match(e.request).then(r => r || fetch(e.request).then(res => { if (res.ok) { const c = res.clone(); caches.open(V).then(x => x.put(e.request, c)); } return res; })));
});
