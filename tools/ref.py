import json, urllib.request, os
os.makedirs('out', exist_ok=True)
res = {}
for ch in range(1, 115):
    u = f'https://api.quran.com/api/v4/chapter_recitations/6/{ch}?segments=true'
    for t in range(4):
        try:
            d = json.load(urllib.request.urlopen(u, timeout=60)); break
        except Exception as e:
            print('retry', ch, e)
    af = d['audio_file']
    res[ch] = {'duration': af.get('duration'), 'timings': [[v['verse_key'], v['timestamp_from'], v['timestamp_to']] for v in af['verse_timings']]}
json.dump(res, open('out/ref_husary.json', 'w'))
print('ok')
