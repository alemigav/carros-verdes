# -*- coding: utf-8 -*-
"""Aplica versões extras de um mesmo modelo em data/cars.json.

Uso: python3 data/add_versions.py

As versões extras ficam em data/extra_versions.json (com fonte e nível de confiança)
e são recalculadas aqui: preço por kWh, índice vs. referência, preço por km de
autonomia, cv por tonelada e volume externo. Rodar de novo atualiza as linhas em vez
de duplicar.
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CARS = os.path.join(ROOT, "data", "cars.json")
EXTRA = os.path.join(ROOT, "data", "extra_versions.json")


def benchmark(meta):
    base = meta.get("benchmark") or {}
    return base.get("preco_base"), base.get("bateria_base"), base.get("rs_por_kwh")


def derive(car, meta):
    preco = car.get("preco_brl")
    bateria = car.get("bateria_kwh")
    autonomia = car.get("autonomia_referencia_km")
    peso = car.get("peso_kg")
    potencia = car.get("potencia_cv")

    preco_base, bateria_base, rs_kwh = benchmark(meta)

    if preco and bateria:
        car["preco_kwh"] = round(preco / bateria, 2)
    if preco and preco_base and bateria and bateria_base and rs_kwh:
        proporcional = preco_base + (bateria - bateria_base) * rs_kwh
        car["preco_proporcional"] = round(proporcional, 2)
        car["indice_vs_base"] = round(preco / proporcional, 3)
    if preco and autonomia:
        car["preco_por_km_autonomia"] = round(preco / autonomia, 1)
    if preco and potencia:
        car["preco_por_cv"] = round(preco / potencia, 1)
    if potencia and peso:
        car["cv_por_tonelada"] = round(potencia / peso * 1000, 1)
    if all(car.get(k) for k in ("comprimento_mm", "largura_mm", "altura_mm")):
        car["volume_externo_m3"] = round(
            car["comprimento_mm"] * car["largura_mm"] * car["altura_mm"] / 1e9, 2
        )
    return car


def main():
    payload = json.load(open(CARS, encoding="utf-8"))
    extras = json.load(open(EXTRA, encoding="utf-8"))
    meta = payload.get("meta", {})
    cars = payload["cars"]
    index = {c["id"]: i for i, c in enumerate(cars)}

    added, updated = [], []
    for extra in extras:
        car = derive(dict(extra), meta)
        if car["id"] in index:
            cars[index[car["id"]]] = car
            updated.append(car["id"])
        else:
            cars.append(car)
            index[car["id"]] = len(cars) - 1
            added.append(car["id"])

    payload["meta"]["total"] = len(cars)
    payload["meta"]["bev"] = sum(1 for c in cars if c.get("motorizacao") == "BEV")
    payload["meta"]["phev"] = sum(1 for c in cars if c.get("motorizacao") == "PHEV")
    payload["meta"]["versoes_extra"] = sorted(
        {c["modelo"] for c in cars if c.get("id") in {e["id"] for e in extras}}
    )

    with open(CARS, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=1)

    print(f"adicionadas: {len(added)} {added}")
    print(f"atualizadas: {len(updated)} {updated}")
    print(f"total agora: {payload['meta']['total']} versões")


main()
