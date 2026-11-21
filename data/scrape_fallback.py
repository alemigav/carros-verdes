"""Fallback enrichment: scrape the cited open-data model pages for cars the research agents did not cover.

Writes data/features/aa_fallback.json (sorts before batch_*.json, so researched batches win in merge.py).
"""
import json
import re
import time
import unicodedata
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "data"
UA = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0 Safari/537.36"
)

ALIASES = {
    "byd-dolphin-mini-gl-30-08-kwh": ["byd/dolphin-mini"],
    "byd-dolphin-mini-gs-38-88-kwh": ["byd/dolphin-mini"],
    "byd-dolphin-gs": ["byd/dolphin"],
    "byd-yuan-pro-versao-de-entrada": ["byd/yuan-pro"],
    "byd-yuan-plus-versao-de-entrada": ["byd/yuan-plus"],
    "byd-seal-awd": ["byd/seal"],
    "byd-sealion-7-awd": ["byd/sealion-7"],
    "byd-tan-ev-awd": ["byd/tan-ev"],
    "byd-han-ev-awd": ["byd/han-ev"],
    "mini-cooper-cooper-e-3-portas": ["mini/cooper-e"],
    "mini-aceman-aceman-e": ["mini/aceman"],
    "mini-countryman-se-top-all4": ["mini/countryman"],
    "mg-mg4-urban-comfort-43-kwh": ["mg/mg4-urban"],
    "mg-mg4-versao-inicial-bateria-64-kwh": ["mg/mg4"],
    "mg-s5-comfort-rwd": ["mg/mg-s5"],
    "mg-im6-signature-rwd-bateria-de-entrada": ["im/im6"],
    "gac-aion-ut-premium-de-entrada": ["gac/aion-ut"],
    "gac-aion-es-versao-unica": ["gac/aion-es"],
    "gac-aion-y-premium": ["gac/aion-y"],
    "gac-aion-v-elite": ["gac/aion-v"],
    "gac-hyptec-ht-2027": ["gac/hyptec-ht"],
    "geely-ex2-pro-max-usa-bateria-de-mesma-familia": ["geely/ex2"],
    "geely-ex5-pro-versao-de-entrada": ["geely/ex5"],
    "zeekr-x-premium-rwd-linha-x": ["zeekr/x"],
    "zeekr-7x-premium-rwd": ["zeekr/7x"],
    "zeekr-001-premium-flagship-bateria-de-referencia": ["zeekr/001"],
    "kia-ev5-land-2wd": ["kia/ev5"],
    "volkswagen-id-4-tracao-traseira-lote-2026": ["volkswagen/id-4", "vw/id-4"],
    "hyundai-ioniq-5-signature-awd": ["hyundai/ioniq-5"],
    "toyota-bz4x-awd": ["toyota/bz4x"],
    "ford-mustang-mach-e-gt-performance-eawd": ["ford/mustang-mach-e"],
    "bmw-ix1-xdrive30-m-sport": ["bmw/ix1"],
    "renault-megane-e-tech-ev60": ["renault/megane-e-tech"],
    "volvo-ec40-single-motor": ["volvo/ec40"],
    "volvo-ex40-single-motor": ["volvo/ex40"],
    "volvo-ex30-single-motor-versao-de-entrada": ["volvo/ex30"],
    "baic-arcfox-t1-pro-2027-pre-venda-lancamento-novembro": ["baic/arcfox-t1"],
    "baic-arcfox-t5-max-pre-venda-novembro": ["baic/arcfox-t5"],
    "dfm-box-e3-urban": ["dfm/box"],
    "dfm-vigo-e2-urban-plus-referencia": ["dfm/vigo"],
    "jac-e-js1-versao-unica": ["jac/e-js1"],
    "jac-e-js4-versao-unica": ["jac/e-js4"],
    "jac-e-j7-versao-unica": ["jac/e-j7"],
    "leapmotor-b10-life-bev": ["leapmotor/b10"],
    "leapmotor-c10-bev-design": ["leapmotor/c10-reev"],
    "chevrolet-spark-euv-activ-versao-unica": ["chevrolet/spark-euv"],
    "chevrolet-captiva-ev-versao-unica": ["chevrolet/captiva-ev"],
    "omoda-e5-elite-2027": ["omoda/e5"],
    "gwm-ora-5-versao-unica": ["gwm/ora-5"],
    "gwm-ora-03-bev58": ["gwm/ora-03"],
    "fiat-500e-preco-sob-consulta": ["fiat/500e"],
    "lexus-es-500e-versao-de-entrada-lancamento-novembro": ["lexus/es-500e"],
    "lexus-rz-500e-direct4-awd": ["lexus/rz-500e"],
    "audi-a6-sportback-e-tron-s-line": ["audi/a6-e-tron"],
    "audi-q6-e-tron-s-line-quattro": ["audi/q6-e-tron"],
    "audi-sq6-sportback-e-tron-quattro": ["audi/sq6-sportback-e-tron", "audi/q6-e-tron"],
    "audi-rs-e-tron-gt-performance-ficha-2026": ["audi/rs-e-tron-gt"],
    "porsche-macan-electric-macan-4-electric": ["porsche/macan-electric"],
    "porsche-taycan-versao-de-entrada-bateria-performance-plus": ["porsche/taycan"],
    "porsche-cayenne-electric-versao-de-entrada": ["porsche/cayenne-electric"],
    "bmw-ix3-50-xdrive": ["bmw/ix3"],
    "bmw-i4-edrive40-m-sport": ["bmw/i4"],
    "bmw-i7-xdrive60": ["bmw/i7"],
    "bmw-i5-preco-sob-consulta": ["bmw/i5"],
    "bmw-ix-preco-sob-consulta": ["bmw/ix"],
    "bmw-ix2-xdrive30-m-sport": ["bmw/ix2"],
    "cadillac-lyriq-versao-de-entrada": ["cadillac/lyriq"],
    "cadillac-vistiq-awd": ["cadillac/vistiq"],
    "denza-z9-gt-1210ev-awd": ["denza/z9-gt"],
    "avatr-11-versao-de-entrada": ["avatr/11"],
    "volvo-es90-versao-de-entrada": ["volvo/es90"],
    "volvo-ex90-versao-de-entrada": ["volvo/ex90"],
    "kia-ev9-gtl-awd": ["kia/ev9"],
    "chevrolet-blazer-ev-rs-rwd": ["chevrolet/blazer-ev"],
    "mg-cyberster-awd": ["mg/cyberster"],
    "jac-e-jv7l-preco-sob-consulta-utilitario": ["jac/e-jv7l"],
    "jac-ev-hunter-preco-sob-consulta-picape-utilitario": ["jac/ev-hunter"],
    "jac-iev330p-preco-sob-consulta-picape-utilitario": ["jac/iev330p"],
}

LABELS = {
    "lugares": "lugares",
    "porta-malas": "porta_malas_l",
    "porta-malas com bancos rebatidos": "porta_malas_max_l",
    "potência": "potencia_cv",
    "torque": "torque_nm",
    "tração": "tracao",
    "0 a 100 km/h": "zero_a_cem_s",
    "velocidade máxima": "velocidade_max_kmh",
    "bateria": "bateria_bruta_kwh",
    "química": "quimica_bateria",
    "autonomia inmetro/pbev": "autonomia_inmetro_km",
    "autonomia wltp": "autonomia_wltp_km",
    "autonomia elétrica": "autonomia_eletrica_km",
    "consumo de energia": "consumo_kwh_100km",
    "recarga em ac (casa)": "recarga_ac_kw",
    "recarga rápida dc (pico)": "recarga_dc_kw",
    "tempo na recarga rápida": "recarga_dc_time",
    "v2l (energia para aparelhos)": "v2l",
    "comprimento": "comprimento_mm",
    "largura": "largura_mm",
    "altura": "altura_mm",
    "entre-eixos": "entre_eixos_mm",
    "altura livre do solo": "altura_livre_solo_mm",
    "peso": "peso_kg",
    "airbags": "airbags",
    "garantia do carro": "garantia_carro",
    "garantia da bateria": "garantia_bateria",
    "motores elétricos": "motores_qtd",
    "conector": "conector_dc",
    "câmbio": "cambio",
    "porta-malas dianteiro": "_ignorar",
}

MATCH_ORDER = sorted(LABELS, key=len, reverse=True)


def strip_tags(text):
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", text)).strip()


def num(text):
    match = re.search(r"\d+(?:[.,]\d+)?", text.replace(".", "").replace(",", "."))
    if not match:
        return None
    return float(match.group())


def slug(text):
    text = unicodedata.normalize("NFKD", str(text))
    text = "".join(c for c in text if not unicodedata.combining(c)).lower()
    return re.sub(r"-+", "-", re.sub(r"[^a-z0-9]+", "-", text)).strip("-")


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "pt-BR"})
    try:
        with urllib.request.urlopen(req, timeout=25) as response:
            return response.read().decode("utf-8", errors="ignore")
    except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError):
        return None


def norm_label(text):
    text = strip_tags(text).lower()
    text = re.sub(r"varia\w*", "", text)
    text = re.sub(r"[^a-z0-9%/áéíóúâêôãõçà ,.-]", "", text)
    return re.sub(r"\s+", " ", text).strip()


def parse_page(html):
    raw = {}
    for label, value in re.findall(r"<dt>(.*?)</dt><dd>(.*?)</dd>", html, re.S):
        key_text = norm_label(label)
        for candidate in MATCH_ORDER:
            if key_text.startswith(candidate):
                field = LABELS[candidate]
                if field != "_ignorar" and field not in raw:
                    raw[field] = strip_tags(value)
                break
    return raw


def map_record(raw):
    out = {
        "lugares": None, "porta_malas_l": None, "porta_malas_max_l": None,
        "potencia_cv": None, "torque_nm": None, "tracao": None, "zero_a_cem_s": None,
        "velocidade_max_kmh": None, "bateria_bruta_kwh": None, "quimica_bateria": None,
        "autonomia_inmetro_km": None, "autonomia_wltp_km": None, "autonomia_eletrica_km": None,
        "consumo_kwh_100km": None, "recarga_ac_kw": None, "recarga_dc_kw": None,
        "recarga_dc_10_80_min": None, "comprimento_mm": None, "largura_mm": None,
        "altura_mm": None, "entre_eixos_mm": None, "altura_livre_solo_mm": None,
        "peso_kg": None, "airbags": None, "v2l": None,
        "garantia_veiculo_anos": None, "garantia_veiculo_km": None,
        "garantia_bateria_anos": None, "garantia_bateria_km": None,
    }
    for key in ("lugares", "potencia_cv", "torque_nm", "zero_a_cem_s", "velocidade_max_kmh",
                "bateria_bruta_kwh", "autonomia_inmetro_km", "autonomia_wltp_km",
                "autonomia_eletrica_km", "consumo_kwh_100km", "recarga_ac_kw", "recarga_dc_kw",
                "comprimento_mm", "largura_mm", "altura_mm", "entre_eixos_mm",
                "altura_livre_solo_mm", "peso_kg", "airbags"):
        if key in raw:
            out[key] = num(raw[key])
    if "porta_malas_l" in raw:
        out["porta_malas_l"] = num(raw["porta_malas_l"])
    if "porta_malas_max_l" in raw:
        out["porta_malas_max_l"] = num(raw["porta_malas_max_l"])

    for key, field in (("tracao", "tracao"), ("quimica_bateria", "quimica_bateria")):
        if key in raw:
            out[field] = raw[key][:40]

    if "v2l" in raw:
        low = raw["v2l"].lower()
        if low.startswith("sim"):
            out["v2l"] = True
        elif low.startswith("não") or low.startswith("nao"):
            out["v2l"] = False

    if "recarga_dc_time" in raw:
        match = re.search(r"\((\d+)%\s*a\s*(\d+)%\)", raw["recarga_dc_time"])
        if match and match.group(1) == "10" and match.group(2) == "80":
            out["recarga_dc_10_80_min"] = num(raw["recarga_dc_time"])

    for key, years_field, km_field in (
        ("garantia_carro", "garantia_veiculo_anos", "garantia_veiculo_km"),
        ("garantia_bateria", "garantia_bateria_anos", "garantia_bateria_km"),
    ):
        if key in raw:
            text = raw[key]
            years = re.search(r"(\d+)\s*anos?", text)
            km = re.search(r"([\d.]+)\s*km", text)
            if years:
                out[years_field] = int(years.group(1))
            if km:
                out[km_field] = int(km.group(1).replace(".", ""))

    return {k: v for k, v in out.items() if v is not None}


def main():
    base = json.loads((DATA / "cars_base.json").read_text())
    covered = set()
    for path in sorted((DATA / "features").glob("batch_*.json")):
        try:
            payload = json.loads(path.read_text())
        except json.JSONDecodeError:
            continue
        for rec in payload if isinstance(payload, list) else payload.get("cars", []):
            if rec.get("id"):
                covered.add(rec["id"])

    results, skipped, failed = [], [], []
    for car in base:
        if car["id"] in covered:
            continue
        candidates = ALIASES.get(car["id"]) or [f"{slug(car['marca'])}/{slug(car['modelo'])}"]
        raw = None
        used = None
        for candidate in candidates:
            url = f"https://recarregados.com/marca/{candidate}"
            html = fetch(url)
            time.sleep(0.4)
            if not html:
                continue
            parsed = parse_page(html)
            if len(parsed) >= 6:
                raw, used = parsed, url
                break
        if not raw:
            failed.append(car["id"])
            continue

        record = {"id": car["id"], **map_record(raw), "fonte_principal": used,
                  "fontes": [used], "confianca": "media",
                  "observacoes": "Ficha da fonte aberta (ficha técnica do modelo); confirme a versão exata antes de decidir."}
        record["versao_pesquisada"] = None
        results.append(record)
        print(f"  ok {car['marca']:12} {car['modelo']:18} {len(map_record(raw))} campos")

    out = DATA / "features" / "aa_fallback.json"
    out.write_text(json.dumps(results, ensure_ascii=False, indent=1))
    print(f"\n{len(results)} fallback records, {len(skipped)} skipped, {len(failed)} failed -> {out}")
    if failed:
        print("failed:", ", ".join(failed[:20]))


if __name__ == "__main__":
    main()
