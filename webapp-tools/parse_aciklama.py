import re, json, sys
raw, quran, out = sys.argv[1], sys.argv[2], sys.argv[3]
L = open(raw).read().replace('\x0c', '\n').split('\n')
Q = json.load(open(quran))
AY = {(a[0], a[1]): a[4] for a in Q['ayahs']}
NAMES = {'Bakara':2,'Âl-i İmrân':3,'Nisâ':4,'Mâide':5,'A‘râf':6,'Enfâl':8,'Tevbe':9,'Yûnus':10,'Hûd':11,'Yûsuf':12,'Ra‘d':13,'İbrâhim':14}
NAMES['A‘râf']=7
AR = re.compile('[؀-ۿ]')
MARKS = re.compile('[ؐ-ًؚ-ٰٟۖ-ۭـ]')
def skel(t):
    t = MARKS.sub('', t)
    t = re.sub('[أإآٱءا]', '', t).replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي')
    return t
def find(phrase, ayah):
    words = ayah.split(' ')
    sk = [skel(w) for w in words]
    target = skel(phrase).replace(' ', '')
    if len(target) < 2: return None
    # concatenated skeleton with word index map
    cat = ''; owner = []
    for i, s in enumerate(sk):
        cat += s; owner += [i] * len(s)
    p = cat.find(target)
    if p < 0:
        import difflib
        best = (0, None)
        nw = max(1, len(phrase.split()))
        for a in range(len(words)):
            for n in range(max(1, nw - 2), nw + 3):
                b = a + n
                if b > len(words): break
                c = ''.join(sk[a:b])
                r = difflib.SequenceMatcher(None, target, c).ratio()
                if r > best[0]: best = (r, (a, b))
        if best[0] >= 0.72:
            a, b = best[1]
            R = lambda a, b: difflib.SequenceMatcher(None, target, ''.join(sk[a:b])).ratio()
            while b - a > 1 and R(a + 1, b) >= R(a, b) - 0.01: a += 1
            while b - a > 1 and R(a, b - 1) >= R(a, b) - 0.01: b -= 1
            return ' '.join(words[a:b])
        return None
    a, b = owner[p], owner[p + len(target) - 1]
    return ' '.join(words[a:b + 1])
def clean_raw(t):
    t = t.replace('‫', '').replace('‬', '').strip()
    return re.sub(r'\s+', ' ', t)
res = {}; stats = [0, 0]
heads = [i for i, l in enumerate(L) if l.strip().endswith(' Suresi') and len(l) < 30]
heads.append(len(L))
for k in range(len(heads) - 1):
    name = L[heads[k]].strip()[:-len(' Suresi')]
    sn = NAMES[name]
    seg = [l for l in L[heads[k] + 1:heads[k + 1]] if not re.match(r'^Kur’an Açıklamaları · \d+$', l.strip())]
    # blocks
    idx = [i for i, l in enumerate(seg) if re.match(r'^\d+\. Ayet\b', l)]
    intro_lines = [l for l in seg[1:idx[0]] if l.strip()]
    sur = {'intro': ' '.join(intro_lines).strip(), 'ayat': {}}
    idx.append(len(seg))
    for j in range(len(idx) - 1):
        blk = seg[idx[j]:idx[j + 1]]
        m = re.match(r'^(\d+)\. Ayet\s*[–-]?\s*(.*)$', blk[0])
        an = int(m.group(1)); title = m.group(2).strip()
        rest = blk[1:]
        ar = []; i = 0
        while i < len(rest) and (not rest[i].strip() or AR.search(rest[i]) and not re.search('[a-zA-ZçğışöüÇĞİŞÖÜâîû]', rest[i])):
            if rest[i].strip(): ar.append(rest[i])
            i += 1
        body = rest[i:]
        # Arapça ifadeler
        full = ' '.join(clean_raw(x) for x in ar if len(MARKS.sub('', clean_raw(x)).strip()) > 1)
        phrases = [p.strip() for p in full.split('/') if p.strip()][::-1]
        ayah = AY.get((sn, an), '')
        fixed = []
        for p in phrases:
            f = find(p, ayah); stats[1] += 1
            if f: stats[0] += 1; fixed.append(f)
            elif not re.search(r'\S [\u064B-\u0652]', p): fixed.append(p)
        # paragraflar
        txt = [l.rstrip() for l in body]
        mx = max([len(l) for l in txt if l] or [80])
        paras = []; cur = []
        for l in txt:
            if not l.strip():
                if cur: paras.append(' '.join(cur)); cur = []
                continue
            cur.append(l.strip())
            if re.search(r'[.!?”"):]$', l.strip()) and len(l) < 0.82 * mx:
                paras.append(' '.join(cur)); cur = []
        if cur: paras.append(' '.join(cur))
        sur['ayat'][an] = {'t': title, 'ar': fixed, 'p': paras}
    res[sn] = sur
json.dump(res, open(out, 'w'), ensure_ascii=False, separators=(',', ':'))
print('phrases matched', stats, 'surahs', len(res), 'ayat', sum(len(v['ayat']) for v in res.values()))
