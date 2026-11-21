# -*- coding: utf-8 -*-
"""Baixa fotos de modelo do Wikimedia Commons (CC) para a V3 e grava os créditos.

Uso: python3 tools/fetch_photos.py [--force]
Saída: v3/photos/<slug>.jpg + v3/photos/credits.json

Só aceita arquivos cujo título contenha a marca (ou submarca/distribuidor) e os
tokens do modelo, e cuja licença seja CC/PD. Arquivo já baixado não é rebaixado
sem --force. Modelo sem correspondência fica sem foto (a UI usa placeholder).
"""
import json, os, re, sys, time, unicodedata, urllib.parse, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "v3", "photos")
UA = {"User-Agent": "CarrosVerdes/0.1 (https://github.com/alemigav/carros-verdes; alemigav)"}

# nome brasileiro -> nomes usados em outros mercados
ALIASES = {
    "BYD Dolphin Mini": ["BYD Seagull", "BYD Dolphin Surf"],
    "MG MG4 Urban": ["MG MG4", "MG4 Electric"],
    "Geely EX5": ["Geely Galaxy E5", "Geely EX5"],
    "GWM Ora 5": ["Ora 5", "GWM Ora 5"],
    "DFM BOX": ["Dongfeng Box", "Dongfeng EX1"],
    "DFM VIGO": ["Dongfeng Vigo", "Dongfeng Nano"],
    "Volvo EX40": ["Volvo C40", "Volvo EC40"],
    "Audi A6 Sportback e-tron": ["Audi A6 e-tron", "Audi A6 Sportback"],
    "Audi SQ6 Sportback e-tron": ["Audi SQ6 e-tron"],
    "Lexus ES 500e": ["Lexus ES", "Lexus ES 2026"],
    "JAC E-JV7L": ["JAC JV7", "JAC E-JV7"],
    "JAC iEV330P": ["JAC iEV330", "JAC T8"],
    "GWM Tank 400 Hi4-T PHEV Flex": ["Tank 400", "GWM Tank 400"],
    "GWM Haval H7 PHEV Flex": ["Haval H7"],
    "GWM Haval H6 PHEV19 Flex": ["Haval H6"],
    "GWM Haval H6 PHEV35 Flex": ["Haval H6"],
    "GWM Haval H6 GT Flex": ["Haval H6 GT", "Haval H6"],
    "MINI Cooper": ["Mini Cooper SE", "Mini Cooper E", "Mini Cooper J01"],
    "CAOA Changan CS55 Ultra-Hybrid Flex PHEV": ["Changan CS55", "Changan CS55 Plus"],
}

# marca do dado -> tokens aceitos no título do arquivo (distribuidor, submarca)
BRAND_TOKENS = {
    "CAOA Changan": ["changan"],
    "GWM": ["gwm", "haval", "tank", "ora", "wey"],
}

# casos em que o nome brasileiro não existe no Commons: arquivo exato, verificado à mão
MANUAL = {
    "BYD Dolphin Mini": "File:BYD Dolphin Surf IAA 2025 DSC 2031.jpg",
    "MG S5": "File:MG S5 EV 001.jpg",
}

# tokens aceitos no título quando o nome brasileiro difere do usado na foto
MODEL_TOKENS = {
    "MG4 Urban": ["mg4"],
    "EX5": ["e5", "ex5"],
}

# palavras de versão que não fazem parte do nome do modelo
TRIM = ("dm-i", "phev", "flex", "hi4-t", "ultra-hybrid", "plug-in", "hybrid")

BAD = ("logo", "interior", "cockpit", "dashboard", "emblem", "badge", "engine",
       "concept", "drawing", "diagram", "seat", "charger", "illustration", "blueprint", "svg")


def norm(text):
    text = unicodedata.normalize("NFKD", text)
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    return re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()


def clean_model(model):
    """'Haval H6 PHEV19 Flex' -> 'Haval H6'; 'Atto 2 DM-i Flex GL' -> 'Atto 2'."""
    kept = []
    for tok in model.replace("/", " ").split():
        if tok.lower() in TRIM:
            break
        kept.append(tok)
    return " ".join(kept) or model


def slug_of(modelo):
    base = re.sub(r"[^a-z0-9]+", "-", norm(clean_model(modelo))).strip("-")
    return base or "modelo"


def commons_file(title):
    params = {
        "action": "query", "format": "json", "titles": title, "prop": "imageinfo",
        "iiprop": "url|extmetadata", "iiurlwidth": "720",
    }
    url = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=30) as r:
        data = json.load(r)
    pages = list(((data.get("query") or {}).get("pages") or {}).values())
    return pages


def commons_search(query, limit=8):
    params = {
        "action": "query", "format": "json", "generator": "search", "gsrnamespace": "6",
        "gsrsearch": query, "gsrlimit": str(limit), "prop": "imageinfo",
        "iiprop": "url|extmetadata", "iiurlwidth": "720",
    }
    url = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=30) as r:
        data = json.load(r)
    return list(((data.get("query") or {}).get("pages") or {}).values())


def pick(marca, modelo, pages):
    brand_t = BRAND_TOKENS.get(marca, norm(marca).split())
    override = MODEL_TOKENS.get(clean_model(modelo))
    model_t = override or [t for t in norm(clean_model(modelo)).split() if len(t) > 1]
    best = None
    for page in pages:
        title = page.get("title", "")
        t = " " + norm(title) + " "
        if any(b in t for b in BAD):
            continue
        if not any(b in t for b in brand_t):
            continue
        tokens_ok = any(tok in t for tok in model_t) if override else all(tok in t for tok in model_t)
        if not model_t or not tokens_ok:
            continue
        info = (page.get("imageinfo") or [{}])[0]
        md = info.get("extmetadata", {})
        lic = (md.get("LicenseShortName") or {}).get("value", "")
        if not lic or not info.get("thumburl"):
            continue
        if not re.search(r"CC|Public domain|PDM|CC0", lic, re.I):
            continue
        score = -len(title)
        if t.strip().startswith(norm(marca)) or any(t.strip().startswith(b) for b in brand_t):
            score += 20
        if "auto" in t or "salon" in t or "zurich" in t:
            score -= 4
        if "rear" in t or "heck" in t or "back" in t:
            score -= 2
        if best is None or score > best["score"]:
            best = {
                "score": score,
                "title": title,
                "thumb": info["thumburl"],
                "page": info.get("descriptionurl", ""),
                "license": lic,
                "licenseUrl": (md.get("LicenseUrl") or {}).get("value", ""),
                "artist": re.sub(r"<[^>]+>", "", (md.get("Artist") or {}).get("value", "")).strip(),
            }
    return best


def download(url, path):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r, open(path, "wb") as f:
        f.write(r.read())


def main():
    force = "--force" in sys.argv
    os.makedirs(OUT, exist_ok=True)
    cars = json.load(open(os.path.join(ROOT, "data", "cars.json")))["cars"]

    models = {}
    for c in cars:
        models.setdefault(f"{c['marca']} {c['modelo']}", {"marca": c["marca"], "modelo": c["modelo"]})

    entries = {}
    for key, m in models.items():
        file_slug = slug_of(m["modelo"])
        if key in MANUAL:
            found = None
            try:
                pages = commons_file(MANUAL[key])
                info = (pages[0].get("imageinfo") or [{}])[0] if pages else {}
                md = info.get("extmetadata", {})
                if info.get("thumburl"):
                    found = {
                        "title": pages[0].get("title", MANUAL[key]),
                        "thumb": info["thumburl"],
                        "page": info.get("descriptionurl", ""),
                        "license": (md.get("LicenseShortName") or {}).get("value", ""),
                        "licenseUrl": (md.get("LicenseUrl") or {}).get("value", ""),
                        "artist": re.sub(r"<[^>]+>", "", (md.get("Artist") or {}).get("value", "")).strip(),
                        "query": MANUAL[key],
                    }
            except Exception as e:
                print(f"ERRO manual {key}: {e}")
        else:
            found = None
        for query in [] if found else ALIASES.get(key, []) + [f"{m['marca']} {clean_model(m['modelo'])}"]:
            try:
                candidate = pick(m["marca"], m["modelo"], commons_search(query))
            except Exception:
                candidate = None
            if candidate:
                candidate["query"] = query
                found = candidate
                break
            time.sleep(0.15)

        if not found:
            print("--- " + key)
            sys.stdout.flush()
            continue

        dest = os.path.join(OUT, file_slug + ".jpg")
        if force or not os.path.exists(dest):
            try:
                download(found["thumb"], dest)
            except Exception as e:
                print(f"ERRO baixando {key}: {e}")
                continue

        entry = entries.setdefault(file_slug, {
            "arquivo": f"photos/{file_slug}.jpg",
            "titulo": found["title"],
            "autor": found["artist"] or "Autor não indicado no Commons",
            "licenca": found["license"],
            "licencaUrl": found["licenseUrl"],
            "origem": found["page"],
            "modelos": [],
        })
        entry["modelos"].append(key)
        print("OK  " + key + " → " + found["title"][:64])
        sys.stdout.flush()
        time.sleep(0.2)

    json.dump(entries, open(os.path.join(OUT, "credits.json"), "w"), ensure_ascii=False, indent=1, sort_keys=True)
    fotos = len(entries)
    print(f"\nfotos: {fotos} | modelos atendidos: {sum(len(e['modelos']) for e in entries.values())}/{len(models)} | sem foto: {len(models) - sum(len(e['modelos']) for e in entries.values())}")


main()
