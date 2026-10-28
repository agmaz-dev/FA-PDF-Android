import sys, json, subprocess, os, time, re, urllib.parse
import numpy as np, torch, numba
from transformers import Wav2Vec2ForCTC, Wav2Vec2Processor
rec, part, nparts = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
only = [int(x) for x in sys.argv[4].split(',')] if len(sys.argv) > 4 else None
torch.set_num_threads(os.cpu_count())
MID = 'jonatasgrosman/wav2vec2-large-xlsr-53-arabic'
proc = Wav2Vec2Processor.from_pretrained(MID); model = Wav2Vec2ForCTC.from_pretrained(MID).eval()
vocab = proc.tokenizer.get_vocab(); BL = vocab[proc.tokenizer.pad_token]; PIPE = vocab.get('|')
col = json.load(open('tools/collak_files.json'))
AY = json.load(open('tools/ayahs.json'))
def url(s):
    if rec == 'danis': return f'https://archive.org/download/ishak_danish/{s:03d}.mp3'
    return 'https://archive.org/download/fatihcollakk/' + urllib.parse.quote(col[str(s)])
MUQ = {(2,1),(3,1),(7,1),(10,1),(11,1),(12,1),(13,1),(14,1),(15,1),(19,1),(20,1),(26,1),(27,1),(28,1),(29,1),(30,1),(31,1),(32,1),(36,1),(38,1),(40,1),(41,1),(42,1),(42,2),(43,1),(44,1),(45,1),(46,1),(50,1),(68,1)}
NAMES = {'ا':'الف','ل':'لام','م':'ميم','ص':'صاد','ر':'را','ك':'كاف','ه':'ها','ي':'يا','ع':'عين','ط':'طا','س':'سين','ح':'حا','ق':'قاف','ن':'نون'}
def norm(t, key):
    t = t.replace('ٱ', 'ا').replace('ـ', '')
    t = re.sub('[ؐ-ًؚ-ٰٟۖ-ࣰۭ-ࣿ]', '', t)
    if key in MUQ:
        w = t.split(' '); w[0] = ' '.join(NAMES.get(c, c) for c in w[0]); t = ' '.join(w)
    return ''.join(c for c in t if c in vocab and c != '|' and c != ' ')
@numba.njit(cache=True)
def fwd(lp, tok, garb, K, keep):
    T = lp.shape[0]; L = len(tok); S = 2 * L + 1; N = S + 2
    NEG = -1e30
    a = np.full(N, NEG, np.float32); b = np.empty(N, np.float32)
    # idx 0 = G0, 1..S = ctc states, S+1 = Gend ; ctc state j (0-based) at idx j+1 ; blank if j even
    a[0] = garb[0]; a[1] = lp[0, tok[0] * 0 + keep]; a[2] = lp[0, tok[0]]
    ck = np.empty(((T + K - 1) // K, N), np.float32); ck[0] = a
    for t in range(1, T):
        b[0] = a[0] + garb[t]
        for j in range(S):
            i = j + 1
            best = a[i]
            if a[i - 1] > best: best = a[i - 1]
            if j % 2 == 1 and j >= 3 and tok[j // 2] != tok[j // 2 - 1]:
                if a[i - 2] > best: best = a[i - 2]
            if j == 1 and a[0] > best: best = a[0]
            e = lp[t, keep] if j % 2 == 0 else lp[t, tok[j // 2]]
            b[i] = best + e
        best = a[N - 1]
        if a[N - 2] > best: best = a[N - 2]
        if a[N - 3] > best: best = a[N - 3]
        b[N - 1] = best + garb[t]
        a, b = b, a
        if t % K == 0: ck[t // K] = a
    return ck, a
@numba.njit(cache=True)
def seg_back(lp, tok, garb, start_alpha, t0, t1, end_state, keep, path):
    # recompute frames t0..t1-1 from alpha at t0 (already includes frame t0), store backptrs, backtrack from end_state at t1-1
    L = len(tok); S = 2 * L + 1; N = S + 2; NEG = -1e30
    n = t1 - t0 + 1
    bp = np.zeros((n, N), np.int8)
    a = start_alpha.copy(); b = np.empty(N, np.float32)
    for k in range(1, n):
        t = t0 + k
        b[0] = a[0] + garb[t]; bp[k, 0] = 0
        for j in range(S):
            i = j + 1
            best = a[i]; d = 0
            if a[i - 1] > best: best = a[i - 1]; d = 1
            if j % 2 == 1 and j >= 3 and tok[j // 2] != tok[j // 2 - 1]:
                if a[i - 2] > best: best = a[i - 2]; d = 2
            if j == 1 and a[0] > best: best = a[0]; d = 2
            e = lp[t, keep] if j % 2 == 0 else lp[t, tok[j // 2]]
            b[i] = best + e; bp[k, i] = d
        best = a[N - 1]; d = 0
        if a[N - 2] > best: best = a[N - 2]; d = 1
        if a[N - 3] > best: best = a[N - 3]; d = 2
        b[N - 1] = best + garb[t]; bp[k, N - 1] = d
        a, b = b, a
    s = end_state
    for k in range(n - 1, -1, -1):
        path[t0 + k] = s
        if k > 0: s -= bp[k, s]
    return s
def viterbi(lp, tok, garb, keep):
    K = 1500
    T = lp.shape[0]; L = len(tok); S = 2 * L + 1; N = S + 2
    ck, last = fwd(lp, tok, garb, K, keep)
    end = int(np.argmax(last[[N - 1, N - 2, N - 3]])); end = [N - 1, N - 2, N - 3][end]
    path = np.zeros(T, np.int32)
    s = end
    for c in range((T - 1) // K, -1, -1):
        t0 = c * K; t1 = min(T - 1, t0 + K)
        s = seg_back(lp, tok, garb, ck[c], t0, t1, s, keep, path)
    return path
def emissions(x):
    out = []
    CH = 16000 * 20; PAD = 16000 * 1
    with torch.inference_mode():
        for i in range(0, len(x), CH):
            a = max(0, i - PAD); b = min(len(x), i + CH + PAD)
            seg = x[a:b]
            iv = proc(seg, sampling_rate=16000, return_tensors='pt').input_values
            lg = model(iv).logits[0].log_softmax(-1).numpy()
            fps = lg.shape[0] / (len(seg) / 16000)
            s0 = int(round((i - a) / 16000 * 50)); n = int(round(min(CH, len(x) - i) / 16000 * 50))
            out.append(lg[s0:s0 + n])
    return np.concatenate(out).astype(np.float32)
os.makedirs('out', exist_ok=True)
surahs = only or [s for s in range(1, 115) if (s - 1) % nparts == part]
res = {}
for s in surahs:
    if rec == 'collak' and str(s) not in col: continue
    for t in range(4):
        r = subprocess.run(['curl', '-sSL', '--fail', '-o', 'a.mp3', url(s)])
        if r.returncode == 0 and os.path.getsize('a.mp3') > 10000: break
        time.sleep(5)
    else:
        print('FAIL', s); continue
    p = subprocess.run(['ffmpeg', '-v', 'error', '-i', 'a.mp3', '-ac', '1', '-ar', '16000', '-f', 's16le', '-'], capture_output=True)
    x = np.frombuffer(p.stdout, dtype=np.int16).astype(np.float32) / 32768
    t0 = time.time()
    lp = emissions(x)
    if PIPE is not None:
        lp[:, BL] = np.logaddexp(lp[:, BL], lp[:, PIPE]); lp[:, PIPE] = -1e9
    texts = [(a[1], norm(a[2], (s, a[1]))) for a in AY if a[0] == s]
    tok = []; own = []
    for k, (n, tx) in enumerate(texts):
        for c in tx: tok.append(vocab[c]); own.append(k)
    tok = np.array(tok, np.int64); own = np.array(own)
    nb = np.where(np.arange(lp.shape[1]) == BL, -1e9, lp).max(1)
    garb = (np.maximum(nb, lp[:, BL]) - 1.0).astype(np.float32)
    path = viterbi(lp, tok, garb, BL)
    T = lp.shape[0]; L = len(tok); S = 2 * L + 1
    j = path - 1  # ctc state index; -1 => G0 ; S => Gend
    isTok = (j >= 0) & (j < S) & (j % 2 == 1)
    ti = np.where(isTok, j // 2, -1)
    na = len(texts); st = [None] * na; en = [None] * na; sc = [[] for _ in range(na)]
    for f in np.where(isTok)[0]:
        k = own[ti[f]]
        if st[k] is None: st[k] = int(f)
        en[k] = int(f); sc[k].append(lp[f, tok[ti[f]]])
    # greedy check: CER of free decoding inside each ayah span
    inv = {v: k for k, v in vocab.items()}
    def cer(a, b):
        d = list(range(len(b) + 1))
        for i in range(1, len(a) + 1):
            pr = d[0]; d[0] = i
            for jj in range(1, len(b) + 1):
                cur = d[jj]; d[jj] = min(d[jj] + 1, d[jj - 1] + 1, pr + (a[i - 1] != b[jj - 1])); pr = cur
        return d[len(b)] / max(1, len(b))
    g = lp.argmax(1)
    rows = []
    for k in range(na):
        seq = g[st[k]:en[k] + 1]; dec = []; prev = -1
        for c in seq:
            if c != prev and c != BL: dec.append(inv[int(c)])
            prev = c
        rows.append([st[k] * 20, (en[k] + 1) * 20, round(float(np.mean(sc[k])), 2), round(cer(''.join(dec), texts[k][1]), 2)])
    g0 = int(np.sum(j == -1)); ge = int(np.sum(j == S))
    res[s] = {'dur': round(len(x) / 16), 'pre': g0 * 20, 'post': ge * 20, 'ayahs': rows}
    c = [r[3] for r in rows]
    print(s, f'{len(x)/16000:.0f}s', f'{time.time()-t0:.0f}s', 'pre', g0 * 20, 'post', ge * 20, 'medCER', np.median(c), 'bad', sum(1 for v in c if v > 0.5), flush=True)
json.dump(res, open(f'out/al_{rec}_{part}.json', 'w'))
