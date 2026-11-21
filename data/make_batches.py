"""Split the canonical car list into research batches for parallel spec gathering."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
cars = json.loads((ROOT / "data" / "cars_base.json").read_text())

BATCH_SIZE = 10
out_dir = ROOT / "data" / "batches"
out_dir.mkdir(exist_ok=True)

for old in out_dir.glob("*.json"):
    old.unlink()

for i in range(0, len(cars), BATCH_SIZE):
    chunk = cars[i : i + BATCH_SIZE]
    payload = []
    for c in chunk:
        item = {
            "id": c["id"],
            "marca": c["marca"],
            "modelo": c["modelo"],
            "versao": c["versao"],
            "motorizacao": c["motorizacao"],
            "preco_brl_planilha": c["preco_brl"],
            "bateria_kwh_planilha": c["bateria_kwh"],
            "observacao": c["observacao_preco"],
        }
        if c["ref"]:
            item["referencia_dados_abertos"] = {
                k: c["ref"][k]
                for k in (
                    "versao",
                    "carroceria",
                    "autonomia_inmetro_km",
                    "potencia_cv",
                    "zero_a_cem_s",
                    "recarga_dc_kw",
                    "porta_malas_l",
                    "nota",
                )
            }
        payload.append(item)
    n = i // BATCH_SIZE + 1
    path = out_dir / f"batch_{n:02d}.json"
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=1))
    print(path.name, len(payload), [p["id"] for p in payload][:2], "...")
