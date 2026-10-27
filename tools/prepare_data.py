#!/usr/bin/env python3
"""Derleme sırasında Kur'an metnini, mealleri ve yazı tiplerini indirir.

Çıktılar:
  app/src/main/assets/quran.json
  app/src/main/res/font/amiri_quran.ttf, scheherazade.ttf, noto_naskh.ttf
"""
import json
import os
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
FONTS = os.path.join(ROOT, "app", "src", "main", "res", "font")
API = "https://api.alquran.cloud/v1"
TOKEN = os.environ.get("GITHUB_TOKEN", "")

TR_NAMES = [
    "Fâtiha", "Bakara", "Âl-i İmrân", "Nisâ", "Mâide", "En'âm", "A'râf", "Enfâl", "Tevbe", "Yûnus",
    "Hûd", "Yûsuf", "Ra'd", "İbrâhîm", "Hicr", "Nahl", "İsrâ", "Kehf", "Meryem", "Tâhâ",
    "Enbiyâ", "Hac", "Mü'minûn", "Nûr", "Furkân", "Şuarâ", "Neml", "Kasas", "Ankebût", "Rûm",
    "Lokmân", "Secde", "Ahzâb", "Sebe'", "Fâtır", "Yâsîn", "Sâffât", "Sâd", "Zümer", "Mü'min",
    "Fussilet", "Şûrâ", "Zuhruf", "Duhân", "Câsiye", "Ahkâf", "Muhammed", "Fetih", "Hucurât", "Kâf",
    "Zâriyât", "Tûr", "Necm", "Kamer", "Rahmân", "Vâkıa", "Hadîd", "Mücâdele", "Haşr", "Mümtehine",
    "Saf", "Cuma", "Münâfikûn", "Tegâbün", "Talâk", "Tahrîm", "Mülk", "Kalem", "Hâkka", "Meâric",
    "Nûh", "Cin", "Müzzemmil", "Müddessir", "Kıyâme", "İnsan", "Mürselât", "Nebe'", "Nâziât", "Abese",
    "Tekvîr", "İnfitâr", "Mutaffifîn", "İnşikâk", "Bürûc", "Târık", "A'lâ", "Gâşiye", "Fecr", "Beled",
    "Şems", "Leyl", "Duhâ", "İnşirâh", "Tîn", "Alak", "Kadr", "Beyyine", "Zilzâl", "Âdiyât",
    "Kâria", "Tekâsür", "Asr", "Hümeze", "Fîl", "Kureyş", "Mâûn", "Kevser", "Kâfirûn", "Nasr",
    "Tebbet", "İhlâs", "Felak", "Nâs",
]
assert len(TR_NAMES) == 114

MEAL_NAMES = {
    "bilmen": "Ömer Nasûhi Bilmen",
    "tr.diyanet": "Diyanet İşleri",
    "tr.yazir": "Elmalılı Hamdi Yazır",
}


def get(url, headers=None, tries=5):
    h = {"User-Agent": "kuran-android-build"}
    if headers:
        h.update(headers)
    last = None
    for i in range(tries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=180) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001
            last = e
            print(f"  tekrar ({i + 1}) {url}: {e}", flush=True)
            time.sleep(4 * (i + 1))
    sys.exit(f"HATA: indirilemedi: {url}: {last}")


def get_json(url, headers=None):
    return json.loads(get(url, headers).decode("utf-8"))


def strip_marks(s):
    return "".join(ch for ch in s if not ("ً" <= ch <= "ٟ" or ch in "ٰۣۖۗۘۙۚۛۜ۟۠ۡۢۤۥۦ۪ۭۧۨ۫۬"))


def edition(ident):
    print(f"İndiriliyor: {ident}", flush=True)
    data = get_json(f"{API}/quran/{ident}")
    surahs = data["data"]["surahs"]
    if len(surahs) != 114:
        sys.exit(f"HATA: {ident} 114 sûre içermiyor")
    return surahs


def main():
    os.makedirs(ASSETS, exist_ok=True)
    os.makedirs(FONTS, exist_ok=True)

    # Türkçe sürümleri listele (günlükte görünsün)
    eds = get_json(f"{API}/edition?language=tr")["data"]
    print("Türkçe sürümler:")
    for e in eds:
        print(f"  {e['identifier']}: {e.get('name')} / {e.get('englishName')} ({e.get('type')}, {e.get('format')})")
    ids = [e["identifier"] for e in eds if e.get("format") == "text"]

    wanted = []
    for ident in ("tr.yazir",):
        if ident in ids:
            wanted.append((ident, MEAL_NAMES[ident]))
    if not wanted:
        sys.exit("HATA: hiç Türkçe meal bulunamadı")

    ar = edition("quran-uthmani")
    surahs_out = []
    ayahs_out = []
    for s in ar:
        n = s["number"]
        typ = "Mekkî" if s.get("revelationType", "").lower().startswith("mec") else "Medenî"
        first_page = s["ayahs"][0]["page"]
        surahs_out.append([n, TR_NAMES[n - 1], s["name"], len(s["ayahs"]), typ, first_page, s.get("englishName", "")])
        for a in s["ayahs"]:
            t = a["text"].replace("﻿", "").strip()
            na = a["numberInSurah"]
            if na == 1 and n not in (1, 9):
                words = t.split(" ")
                if len(words) > 4 and strip_marks(words[0]).replace("ٱ", "ا").startswith("بس"):
                    t = " ".join(words[4:]).strip()
            ayahs_out.append([n, na, a["page"], a["juz"], t])
    print(f"Âyet sayısı: {len(ayahs_out)}")
    if len(ayahs_out) != 6236:
        sys.exit("HATA: âyet sayısı 6236 değil")

    meals = []
    meal_texts = {}
    for ident, name in wanted:
        sur = edition(ident)
        texts = [a["text"].strip() for s in sur for a in s["ayahs"]]
        if len(texts) != 6236:
            print(f"UYARI: {ident} eksik ({len(texts)}), atlandı")
            continue
        meals.append({"id": ident, "name": name})
        meal_texts[ident] = texts

    # Almanca ve İngilizce mealler
    for ident, name, alts in (("de.bubenheim", "Bubenheim & Elyas", ("de.aburida",)), ("en.sahih", "Saheeh International", ("en.pickthall",)), ("ar.muyassar", "التفسير الميسر", ("ar.jalalayn",)), ("tr.golpinarli", "Abdülbâki Gölpınarlı", ())):
        for cand in (ident,) + alts:
            try:
                sur = edition(cand)
            except SystemExit as e:
                print(f"UYARI: {cand} indirilemedi: {e}"); continue
            texts = [a["text"].strip() for s in sur for a in s["ayahs"]]
            if len(texts) == 6236:
                meals.append({"id": ident, "name": name, "source": cand})
                meal_texts[ident] = texts
                break
        else:
            sys.exit(f"HATA: {ident} meali bulunamadı")

    out = {"surahs": surahs_out, "ayahs": ayahs_out, "meals": meals, "mealTexts": meal_texts}
    with open(os.path.join(ASSETS, "quran.json"), "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    print("quran.json yazıldı:", os.path.getsize(os.path.join(ASSETS, "quran.json")), "bayt")

    # Yazı tipleri (Google Fonts deposu, SIL Open Font License)
    gh = {"Accept": "application/vnd.github+json"}
    if TOKEN:
        gh["Authorization"] = f"Bearer {TOKEN}"
    fonts = {
        "amiri_quran": "ofl/amiriquran",
        "scheherazade": "ofl/scheherazadenew",
        "noto_naskh": "ofl/notonaskharabic",
        "amiri_regular": "ofl/amiri",
        "lateef": "ofl/lateef",
        "harmattan": "ofl/harmattan",
        "mada": "ofl/mada",
        "noto_kufi": "ofl/notokufiarabic",
        "katibeh": "ofl/katibeh",
        "markazi": "ofl/markazitext",
        "reem_kufi": "ofl/reemkufi",
        "kufam": "ofl/kufam",
        "aref_ruqaa": "ofl/arefruqaa",
        "gulzar": "ofl/gulzar",
        "noto_nastaliq": "ofl/notonastaliqurdu",
        "el_messiri": "ofl/elmessiri",
        "rakkas": "ofl/rakkas",
        "qahiri": "ofl/qahiri",
    }
    for key, d in fonts.items():
        items = get_json(f"https://api.github.com/repos/google/fonts/contents/{d}", gh)
        ttfs = [i for i in items if i["name"].lower().endswith(".ttf")]
        if not ttfs:
            sys.exit(f"HATA: {d} içinde ttf yok")
        plain = [i for i in ttfs if "italic" not in i["name"].lower()] or ttfs
        pick = next((i for i in plain if "regular" in i["name"].lower()), plain[0])
        print(f"Yazı tipi {key}: {pick['name']}")
        data = get(pick["download_url"])
        with open(os.path.join(FONTS, key + ".ttf"), "wb") as f:
            f.write(data)
    print("Tamam.")


if __name__ == "__main__":
    main()
