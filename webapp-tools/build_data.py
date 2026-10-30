import json, shutil, os
Q = json.load(open('/home/claude/b20/x/assets/quran.json'))
S = 'site/data'
core = {'surahs': Q['surahs'], 'ayahs': [[a[0], a[1], a[2], a[3], a[4]] for a in Q['ayahs']]}
json.dump(core, open(f'{S}/core.json', 'w'), ensure_ascii=False, separators=(',', ':'))
for k, v in Q['mealTexts'].items():
    json.dump(v, open(f'{S}/meal/{k}.json', 'w'), ensure_ascii=False, separators=(',', ':'))
shutil.copy('/home/claude/b20/assets/pack/feridun3.json', f'{S}/meal/feridun.json')
shutil.copy('/tmp/claude-0/-home-claude/8d4b9c9c-b592-5ace-bd29-debc868da718/scratchpad/acik/aciklama.json', f'{S}/aciklama.json')
G = '/home/claude/fonts2/gf/ofl/'
F = {'amiri': 'amiriquran/AmiriQuran-Regular.ttf', 'scheherazade': 'scheherazadenew/ScheherazadeNew-Regular.ttf',
     'naskh': 'notonaskharabic/NotoNaskhArabic[wght].ttf', 'amiriClassic': 'amiri/Amiri-Regular.ttf', 'lateef': 'lateef/Lateef-Regular.ttf',
     'harmattan': 'harmattan/Harmattan-Regular.ttf', 'mada': 'mada/Mada[wght].ttf', 'kufi': 'notokufiarabic/NotoKufiArabic[wght].ttf',
     'katibeh': 'katibeh/Katibeh-Regular.ttf', 'markazi': 'markazitext/MarkaziText[wght].ttf', 'reemKufi': 'reemkufi/ReemKufi[wght].ttf',
     'kufam': 'kufam/Kufam[wght].ttf', 'ruqaa': 'arefruqaa/ArefRuqaa-Regular.ttf', 'gulzar': 'gulzar/Gulzar-Regular.ttf',
     'nastaliq': 'notonastaliqurdu/NotoNastaliqUrdu[wght].ttf', 'messiri': 'elmessiri/ElMessiri[wght].ttf', 'rakkas': 'rakkas/Rakkas-Regular.ttf',
     'qahiri': 'qahiri/Qahiri-Regular.ttf'}
for k, p in F.items(): shutil.copy(G + p, f'site/fonts/{k}.ttf')
shutil.copy('/home/claude/b20/assets/pack/kfgqpc_hafs.ttf', 'site/fonts/medina.ttf')
shutil.copy('/home/claude/b20/assets/pack/kfgqpc_taha.otf', 'site/fonts/taha.otf')
print(os.popen('du -sh site/data site/fonts; ls -la site/data site/data/meal').read())
