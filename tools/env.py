import sys, json, subprocess, numpy as np, os, urllib.request, time
rec, part, nparts = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
col = json.load(open('tools/collak_files.json'))
def url(s):
    if rec == 'danis': return f'https://archive.org/download/ishak_danish/{s:03d}.mp3'
    return 'https://archive.org/download/fatihcollakk/' + urllib.parse.quote(col[str(s)])
import urllib.parse
os.makedirs('out', exist_ok=True)
surahs = [s for s in range(1, 115) if (s - 1) % nparts == part]
for s in surahs:
    for t in range(4):
        r = subprocess.run(['curl', '-sSL', '--fail', '-o', 'a.mp3', url(s)])
        if r.returncode == 0 and os.path.getsize('a.mp3') > 10000: break
        time.sleep(5)
    else:
        print('FAIL', s); continue
    p = subprocess.run(['ffmpeg', '-v', 'error', '-i', 'a.mp3', '-ac', '1', '-ar', '16000', '-f', 's16le', '-'], capture_output=True)
    x = np.frombuffer(p.stdout, dtype=np.int16).astype(np.float32)
    hop = 320  # 20 ms
    n = len(x) // hop
    fr = x[:n * hop].reshape(n, hop)
    rms = np.sqrt((fr ** 2).mean(1) + 1e-6)
    db = (20 * np.log10(rms + 1e-3)).astype(np.float16)
    np.save(f'out/env_{rec}_{s:03d}.npy', db)
    print(s, n * 0.02, flush=True)
