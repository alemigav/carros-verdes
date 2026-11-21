"""Build the canonical car list from the user's spreadsheet + Recarregados open data."""
import json
import re
import unicodedata
from pathlib import Path

import openpyxl

ROOT = Path(__file__).resolve().parent.parent
XLSX = Path("/Users/alemigav/Downloads/comparativo_eletricos_brasil_20-11-2026.xlsx")
RECARREGADOS = ROOT / "data" / "recarregados.json"
OUT = ROOT / "data" / "cars_base.json"

BASE_PRICE = 119000
BASE_BATTERY = 38.8
BENCHMARK = BASE_PRICE / BASE_BATTERY


def strip_accents(text: str) -> str:
    nfkd = unicodedata.normalize("NFKD", text)
    return "".join(c for c in nfkd if not unicodedata.combining(c))


def norm(text: str) -> str:
    text = strip_accents(str(text)).lower()
    text = re.sub(r"[^a-z0-9]+", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def slug(text: str) -> str:
    return norm(text).replace(" ", "-")


BRAND_ALIASES = {
    "caoa changan": "changan",
    "caoa chery": "chery",
    "mini": "mini",
    "mercedes benz": "mercedes-benz",
    "volkswagen": "volkswagen",
    "vw": "volkswagen",
}
MODEL_ALIASES = {
    "e js1": "e-js1",
    "e js4": "e-js4",
    "e j7": "e-j7",
    "e jv7l": "e-jv7l",
    "iev330p": "iev330p",
    "mg4 urban": "mg4 urban",
    "arcfox t1": "arcfox t1",
    "arcfox t5": "arcfox t5",
    "q6 e tron": "q6 e-tron",
    "a6 sportback e tron": "a6 e-tron",
    "sq6 sportback e tron": "sq6 sportback e-tron",
    "rs e tron gt": "rs e-tron gt",
    "megane e tech": "megane e-tech",
    "mustang mach e": "mustang mach-e",
    "es 500e": "es 500e",
    "rz": "rz 500e",
    "ora 03": "ora 03",
    "ora 5": "ora 5",
    "ev hunter": "ev hunter",
    "id 4": "id.4",
}


def read_sheet(sheet_name: str, motorizacao: str):
    wb = openpyxl.load_workbook(XLSX, data_only=True)
    ws = wb[sheet_name]
    rows = []
    for row in ws.iter_rows(min_row=4, values_only=True):
        if not row or not row[0]:
            continue
        marca, modelo, versao, preco, bateria = row[0], row[1], row[2], row[3], row[4]
        if not isinstance(bateria, (int, float)):
            continue
        preco = preco if isinstance(preco, (int, float)) else None
        rows.append(
            {
                "marca": str(marca).strip(),
                "modelo": str(modelo).strip(),
                "versao": str(versao).strip() if versao else "",
                "preco_brl": int(preco) if preco else None,
                "bateria_kwh": float(bateria),
                "observacao_preco": str(row[9]).strip() if len(row) > 9 and row[9] else "",
                "fontes": [u.strip() for u in str(row[10]).split() if u.strip().startswith("http")]
                if len(row) > 10 and row[10]
                else [],
                "motorizacao": motorizacao,
            }
        )
    return rows


def main():
    cars = read_sheet("BEV 100% elétricos", "BEV") + read_sheet("PHEV Flex", "PHEV")

    recarregados = json.loads(RECARREGADOS.read_text())["records"]
    index = {}
    for rec in recarregados:
        for cond in ("novo", "usado"):
            index[(norm(rec["marca"]), norm(rec["modelo"]), cond)] = rec

    seen = set()
    for car in cars:
        key = (norm(car["marca"]), norm(car["modelo"]), car["versao"])
        base_id = f"{slug(car['marca'])}-{slug(car['modelo'])}-{slug(car['versao']) or 'unica'}"[:70]
        car_id, n = base_id, 2
        while car_id in seen:
            car_id = f"{base_id}-{n}"
            n += 1
        seen.add(car_id)
        car["id"] = car_id

        match = index.get((norm(car["marca"]), norm(car["modelo"]), "novo")) or index.get(
            (norm(car["marca"]), norm(car["modelo"]), "usado")
        )
        car["ref"] = None
        if match:
            same_battery = (
                match["bateria_kwh"] is not None
                and abs(match["bateria_kwh"] - car["bateria_kwh"]) <= 0.15 * car["bateria_kwh"]
            )
            car["ref"] = {
                "id": match["id"],
                "versao": match["versao"],
                "carroceria": match["carroceria"],
                "bateria_kwh": match["bateria_kwh"],
                "autonomia_inmetro_km": match["autonomia_inmetro_km"],
                "autonomia_wltp_km": match["autonomia_wltp_km"],
                "autonomia_eletrica_km": match["autonomia_eletrica_km"],
                "potencia_cv": match["potencia_cv"],
                "zero_a_cem_s": match["zero_a_cem_s"],
                "recarga_dc_kw": match["recarga_dc_kw"],
                "porta_malas_l": match["porta_malas_l"],
                "nota": match["nota"],
                "condicao": match["condicao"],
                "review_url": match["review_url"],
                "ficha_url": match["ficha_url"],
                "mesma_bateria": same_battery,
            }
        car["preco_kwh"] = round(car["preco_brl"] / car["bateria_kwh"], 2) if car["preco_brl"] else None
        car["preco_proporcional"] = round(car["bateria_kwh"] * BENCHMARK, 2)
        car["indice_vs_base"] = (
            round(car["preco_brl"] / car["preco_proporcional"], 3) if car["preco_brl"] else None
        )

    OUT.write_text(json.dumps(cars, ensure_ascii=False, indent=1))

    matched = sum(1 for c in cars if c["ref"])
    same = sum(1 for c in cars if c["ref"] and c["ref"]["mesma_bateria"])
    print(f"{len(cars)} cars -> {OUT}")
    print(f"matched open data: {matched} (same battery: {same})")
    print("\nUNMATCHED:")
    for c in cars:
        if not c["ref"]:
            print(f"  [{c['motorizacao']}] {c['marca']} {c['modelo']} | {c['versao']}")
    print("\nBATTERY MISMATCH (open data refers to another version):")
    for c in cars:
        if c["ref"] and not c["ref"]["mesma_bateria"]:
            print(
                f"  {c['marca']} {c['modelo']}: sheet {c['bateria_kwh']} vs "
                f"{c['ref']['bateria_kwh']} ({c['ref']['versao']})"
            )


if __name__ == "__main__":
    main()
