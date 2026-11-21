"""Merge base records + researched specs + open-data fallbacks into data/cars.json."""
import json
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "data"

NUMERIC = [
    "lugares",
    "comprimento_mm", "largura_mm", "altura_mm", "entre_eixos_mm", "altura_livre_solo_mm",
    "peso_kg", "porta_malas_l", "porta_malas_max_l",
    "potencia_cv", "torque_nm", "motores_qtd", "zero_a_cem_s", "velocidade_max_kmh",
    "bateria_bruta_kwh", "autonomia_inmetro_km", "autonomia_wltp_km", "autonomia_eletrica_km",
    "consumo_kwh_100km", "recarga_dc_kw", "recarga_dc_10_80_min", "recarga_ac_kw", "v2l_kw",
    "airbags", "tela_central_pol", "cluster_digital_pol", "som_alto_falantes",
    "garantia_veiculo_anos", "garantia_veiculo_km", "garantia_bateria_anos", "garantia_bateria_km",
    "potencia_combinada_cv", "torque_combinado_nm", "tanque_l", "consumo_l_100km",
]

BOOLEAN = [
    "v2l", "acc", "aeb", "lka", "ldw", "bsm", "rcta", "tsr", "camera_360", "camera_re",
    "sensor_estacionamento", "carregador_sem_fio", "banco_eletrico", "bancos_ventilados",
    "bancos_aquecidos", "teto_panoramico", "bomba_de_calor", "keyless", "partida_remota",
    "head_up_display",
]

TEXT = [
    "versao_pesquisada", "segmento", "carroceria", "tracao", "quimica_bateria", "conector_dc",
    "adas_nivel", "carplay", "android_auto", "som_marca", "ar_condicionado", "motor_termico",
    "combustivel", "cambio", "fonte_principal", "confianca", "observacoes",
]

REF_FALLBACK = {
    "carroceria": "carroceria",
    "autonomia_inmetro_km": "autonomia_inmetro_km",
    "autonomia_wltp_km": "autonomia_wltp_km",
    "autonomia_eletrica_km": "autonomia_eletrica_km",
    "potencia_cv": "potencia_cv",
    "zero_a_cem_s": "zero_a_cem_s",
    "recarga_dc_kw": "recarga_dc_kw",
    "porta_malas_l": "porta_malas_l",
}


def load_research():
    merged = {}
    features_dir = DATA / "features"
    if not features_dir.exists():
        return merged, []
    files = sorted(features_dir.glob("*.json"))
    for path in files:
        try:
            payload = json.loads(path.read_text())
        except json.JSONDecodeError as exc:
            print(f"!! {path.name} is not valid JSON: {exc}")
            continue
        if isinstance(payload, dict):
            payload = payload.get("cars") or payload.get("records") or []
        for rec in payload:
            cid = rec.get("id")
            if cid:
                merged[cid] = rec
    return merged, [p.name for p in files]


def clean(rec):
    out = {}
    for key in NUMERIC:
        val = rec.get(key)
        out[key] = val if isinstance(val, (int, float)) and not isinstance(val, bool) else None
    for key in BOOLEAN:
        val = rec.get(key)
        out[key] = val if isinstance(val, bool) else None
    for key in TEXT:
        val = rec.get(key)
        out[key] = val.strip() if isinstance(val, str) and val.strip() else None
    fontes = rec.get("fontes") or []
    out["fontes"] = [u for u in fontes if isinstance(u, str) and u.startswith("http")]
    return out


def main():
    cars = json.loads((DATA / "cars_base.json").read_text())
    research, files = load_research()

    for car in cars:
        spec = clean(research.get(car["id"], {}))
        ref = car.get("ref") or {}

        for field, ref_key in REF_FALLBACK.items():
            if spec.get(field) is None and ref.get(ref_key) is not None:
                spec[field] = ref[ref_key]
                spec.setdefault("_origem_ref", []).append(field)

        if spec.get("nota") is None:
            spec["nota"] = None
        spec["nota"] = ref.get("nota")

        fontes = list(dict.fromkeys((spec.get("fontes") or []) + (car.get("fontes") or [])))
        if ref.get("ficha_url"):
            fontes.append(ref["ficha_url"])
        if ref.get("review_url"):
            fontes.append(ref["review_url"])
        spec["fontes"] = list(dict.fromkeys(fontes))

        car.update(spec)
        car.pop("ref", None)

        bateria = car["bateria_kwh"]
        preco = car["preco_brl"]
        autonomia = car.get("autonomia_inmetro_km") or car.get("autonomia_wltp_km")
        if car["motorizacao"] == "PHEV":
            autonomia = car.get("autonomia_eletrica_km") or autonomia

        car["autonomia_referencia_km"] = autonomia
        car["eficiencia_km_kwh"] = round(autonomia / bateria, 2) if autonomia and bateria else None
        car["consumo_calc_kwh_100km"] = (
            round(bateria / autonomia * 100, 1) if autonomia and bateria else None
        )
        car["preco_por_km_autonomia"] = (
            round(preco / autonomia, 1) if preco and autonomia else None
        )
        car["preco_por_cv"] = round(preco / car["potencia_cv"], 0) if preco and car.get("potencia_cv") else None
        car["cv_por_tonelada"] = (
            round(car["potencia_cv"] / (car["peso_kg"] / 1000), 1)
            if car.get("potencia_cv") and car.get("peso_kg")
            else None
        )
        car["volume_externo_m3"] = (
            round(
                car["comprimento_mm"] * car["largura_mm"] * car["altura_mm"] / 1_000_000_000, 2
            )
            if all(car.get(k) for k in ("comprimento_mm", "largura_mm", "altura_mm"))
            else None
        )

        bruta = car.get("bateria_bruta_kwh")
        car["alerta_versao"] = None
        if bruta and bateria and abs(bruta - bateria) / bateria > 0.2:
            car["alerta_versao"] = (
                f"A ficha pesquisada descreve {bruta:g} kWh; a planilha precifica a versão de "
                f"{bateria:g} kWh. Confirme a versão antes de decidir."
            )

        preenchidos = sum(1 for k in NUMERIC + BOOLEAN + TEXT if car.get(k) not in (None, ""))
        car["campos_preenchidos"] = preenchidos

    cars.sort(key=lambda c: (c["motorizacao"] != "BEV", c["preco_brl"] or 10**9))

    meta = {
        "gerado_em": date.today().isoformat(),
        "preco_referencia": "20/11/2026",
        "planilha": "comparativo_eletricos_brasil_20-11-2026.xlsx",
        "benchmark": {"preco_base": 119000, "bateria_base": 38.8, "rs_por_kwh": 3067.01},
        "fontes_abertas": [
            {
                "nome": "Recarregados — ficha dos carros elétricos e híbridos",
                "url": "https://recarregados.com/dados/carros-eletricos-hibridos-ficha",
                "licenca": "CC BY 4.0",
                "versao": "2026-11-07",
            },
            {
                "nome": "Primeiro Carro Elétrico — catálogo",
                "url": "https://www.primeirocarroeletrico.com.br/modelos",
                "licenca": "uso editorial, link",
                "versao": "nov/2026",
            },
            {
                "nome": "Eletrificado — preços novembro 2026",
                "url": "https://eletrificado.com/precos-carros-eletricos-novembro-2026/",
                "licenca": "uso editorial, link",
                "versao": "nov/2026",
            },
        ],
        "arquivos_pesquisa": files,
        "total": len(cars),
        "bev": sum(1 for c in cars if c["motorizacao"] == "BEV"),
        "phev": sum(1 for c in cars if c["motorizacao"] == "PHEV"),
    }

    out = DATA / "cars.json"
    out.write_text(json.dumps({"meta": meta, "cars": cars}, ensure_ascii=False, indent=1))

    print(f"{len(cars)} cars -> {out} ({out.stat().st_size / 1024:.0f} KB)")
    print(f"research files: {files or 'none yet'}")
    by_fields = sorted(cars, key=lambda c: -c["campos_preenchidos"])
    print(f"best filled: {by_fields[0]['marca']} {by_fields[0]['modelo']} ({by_fields[0]['campos_preenchidos']} fields)")
    thin = [c for c in cars if c["campos_preenchidos"] < 12]
    print(f"thin records (<12 fields): {len(thin)}")
    cobertura = {
        campo: sum(1 for c in cars if c.get(campo) not in (None, ""))
        for campo in ("autonomia_referencia_km", "potencia_cv", "recarga_dc_kw", "porta_malas_l",
                      "comprimento_mm", "airbags", "acc", "garantia_bateria_anos", "zero_a_cem_s")
    }
    print("coverage:", cobertura)


if __name__ == "__main__":
    main()
