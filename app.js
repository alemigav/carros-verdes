/* Carros Verdes — estado central, motor de notas e controles. */

const MAX_COMPARE = 4;

const CRITERIA = [
  { key: 'value', label: 'Custo-benefício', hint: 'preço comparado ao de carros com especificações parecidas' },
  { key: 'range', label: 'Autonomia', hint: 'autonomia elétrica no ciclo Inmetro/WLTP' },
  { key: 'efficiency', label: 'Eficiência', hint: 'km por kWh de bateria' },
  { key: 'charging', label: 'Recarga rápida', hint: 'potência DC e tempo de 10–80%' },
  { key: 'performance', label: 'Desempenho', hint: 'relação peso-potência e 0–100 km/h' },
  { key: 'space', label: 'Espaço', hint: 'tamanho, porta-malas e lugares' },
  { key: 'safety', label: 'Segurança (equipamentos)', short: 'Segurança', hint: 'airbags e assistentes de direção — não é resultado de teste de colisão' },
  { key: 'comfort', label: 'Conforto e tecnologia', hint: 'telas, bancos, climatização, conectividade' },
];

const PRESETS = {
  balanced: { value: 6, range: 6, efficiency: 5, charging: 5, performance: 4, space: 5, safety: 6, comfort: 5 },
  city: { value: 9, range: 3, efficiency: 8, charging: 3, performance: 3, space: 5, safety: 6, comfort: 5 },
  road: { value: 6, range: 9, efficiency: 6, charging: 9, performance: 5, space: 6, safety: 7, comfort: 6 },
  family: { value: 6, range: 5, efficiency: 5, charging: 4, performance: 3, space: 9, safety: 9, comfort: 7 },
  loaded: { value: 4, range: 5, efficiency: 4, charging: 5, performance: 5, space: 6, safety: 9, comfort: 9 },
  fun: { value: 3, range: 4, efficiency: 3, charging: 5, performance: 10, space: 4, safety: 6, comfort: 6 },
};

const MUST_HAVES = [
  { key: 'acc', label: 'Piloto adaptativo (ACC)' },
  { key: 'aeb', label: 'Freagem automática (AEB)' },
  { key: 'camera_360', label: 'Câmera 360°' },
  { key: 'v2l', label: 'Tomada V2L' },
  { key: 'bomba_de_calor', label: 'Bomba de calor' },
  { key: 'teto_panoramico', label: 'Teto panorâmico' },
  { key: 'bancos_ventilados', label: 'Bancos ventilados' },
  { key: 'carregador_sem_fio', label: 'Carregador por indução' },
  { key: 'sete_lugares', label: '7 lugares' },
  { key: 'awd', label: 'Tração AWD' },
];

const BODY_LABELS = {
  hatch: 'Hatch',
  sedan: 'Sedã',
  suv: 'SUV',
  picape: 'Picape',
  minivan: 'Minivan',
  cupe: 'Cupê',
  perua: 'Perua',
  outro: 'Outro',
};

const COMFORT_KEYS = [
  'carregador_sem_fio', 'banco_eletrico', 'bancos_ventilados', 'bancos_aquecidos',
  'teto_panoramico', 'bomba_de_calor', 'keyless', 'partida_remota', 'head_up_display',
  'camera_360', 'sensor_estacionamento',
];

const ADAS_KEYS = ['acc', 'aeb', 'lka', 'ldw', 'bsm', 'rcta', 'tsr'];

const state = {
  cars: [],
  meta: null,
  mode: 'BEV',
  view: 'cards',
  sort: 'score',
  weights: { ...PRESETS.balanced },
  preset: 'balanced',
  filters: {
    search: '',
    priceMin: null,
    priceMax: null,
    range: 0,
    battery: 0,
    dc: 0,
    body: new Set(),
    brand: new Set(),
    must: new Set(),
    traction: new Set(),
  },
  selected: [],
  scores: new Map(),
  metrics: {},
  cost: {
    kmYear: 15000,
    homePrice: 0.95,
    publicPrice: 1.8,
    homeShare: 80,
    fuelPrice: 6.1,
    maintenance: 1800,
    electricShare: 70,
  },
};

/* ---------- formatação ---------- */

const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 });
const brl2 = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 2 });
const dec1 = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
const int = new Intl.NumberFormat('pt-BR');

const money = (v) => (v == null ? '—' : brl.format(v));
const money2 = (v) => (v == null ? '—' : brl2.format(v));
const n1 = (v) => (v == null ? '—' : dec1.format(v));
const n0 = (v) => (v == null ? '—' : int.format(v));
const dash = (v, unit = '') => (v == null || v === '' ? '<span class="missing">—</span>' : `${v}${unit}`);

/* ---------- auxiliares de estatística ---------- */

function percentile(cars, getter) {
  const pairs = [];
  for (const car of cars) {
    const value = getter(car);
    if (typeof value === 'number' && Number.isFinite(value)) pairs.push([car.id, value]);
  }
  const sorted = [...pairs].sort((a, b) => a[1] - b[1]);
  const map = new Map();
  const denom = Math.max(sorted.length - 1, 1);
  sorted.forEach(([id], i) => map.set(id, i / denom));
  return map;
}

function shareOf(car, keys) {
  const known = keys.filter((k) => typeof car[k] === 'boolean');
  if (known.length < 4 || known.length < keys.length / 2) return null;
  return known.filter((k) => car[k]).length / known.length;
}

function knownCount(car, keys) {
  return keys.filter((k) => typeof car[k] === 'boolean').length;
}

/* Preço contra a mediana de carros com bateria e potência parecidas. */
function peersValue(cars) {
  const usable = cars.filter(
    (c) => c.preco_brl && c.bateria_kwh && (c.potencia_cv || c.potencia_combinada_cv),
  );
  const ratios = new Map();
  if (usable.length < 5) return ratios;

  const power = (c) => c.potencia_cv || c.potencia_combinada_cv;
  const median = (values) => {
    const s = [...values].sort((a, b) => a - b);
    const mid = Math.floor(s.length / 2);
    return s.length % 2 ? s[mid] : (s[mid - 1] + s[mid]) / 2;
  };

  for (const car of usable) {
    let peers = usable.filter(
      (other) =>
        Math.abs(Math.log(other.bateria_kwh / car.bateria_kwh)) <= Math.log(1.25) &&
        Math.abs(Math.log(power(other) / power(car))) <= Math.log(1.3),
    );
    if (peers.length < 3) {
      peers = usable.filter(
        (other) =>
          Math.abs(Math.log(other.bateria_kwh / car.bateria_kwh)) <= Math.log(1.45) &&
          Math.abs(Math.log(power(other) / power(car))) <= Math.log(1.6),
      );
    }
    if (peers.length < 2) continue;
    ratios.set(car.id, car.preco_brl / median(peers.map((o) => o.preco_brl)));
  }
  return ratios;
}

/* ---------- pontuação ---------- */

function buildScores() {
  const cars = state.cars.filter((c) => c.motorizacao === state.mode);
  const pRange = percentile(cars, (c) => c.autonomia_referencia_km);
  const pEff = percentile(cars, (c) => c.eficiencia_km_kwh);
  const pDc = percentile(cars, (c) => c.recarga_dc_kw);
  const pDcTime = percentile(cars, (c) => c.recarga_dc_10_80_min);
  const pCvT = percentile(cars, (c) => c.cv_por_tonelada);
  const p0100 = percentile(cars, (c) => c.zero_a_cem_s);
  const pVol = percentile(cars, (c) => c.volume_externo_m3);
  const pTrunk = percentile(cars, (c) => c.porta_malas_l);
  const pSeats = percentile(cars, (c) => c.lugares);
  const pAir = percentile(cars, (c) => c.airbags);
  const pScreen = percentile(cars, (c) => c.tela_central_pol);
  const pSpeakers = percentile(cars, (c) => c.som_alto_falantes);
  const pNota = percentile(cars, (c) => c.nota);
  const ratios = peersValue(cars);
  const pValue = percentile(
    cars.map((c) => ({ id: c.id, r: ratios.has(c.id) ? ratios.get(c.id) : null })),
    (c) => c.r,
  );

  const NEUTRAL = 0.45;
  const pick = (map, id) => (map.has(id) ? map.get(id) : NEUTRAL);

  const scores = new Map();
  for (const car of cars) {
    const adas = shareOf(car, ADAS_KEYS);
    const comfort = shareOf(car, COMFORT_KEYS);

    const parts = {
      value: pValue.size ? 1 - pick(pValue, car.id) : NEUTRAL,
      range: pick(pRange, car.id),
      efficiency: pick(pEff, car.id),
      charging:
        car.recarga_dc_kw == null && car.recarga_dc_10_80_min == null
          ? NEUTRAL
          : 0.6 * pick(pDc, car.id) + 0.4 * (1 - pick(pDcTime, car.id)),
      performance:
        car.cv_por_tonelada == null && car.zero_a_cem_s == null
          ? NEUTRAL
          : 0.6 * pick(pCvT, car.id) + 0.4 * (1 - pick(p0100, car.id)),
      space: 0.4 * pick(pVol, car.id) + 0.35 * pick(pTrunk, car.id) + 0.25 * pick(pSeats, car.id),
      safety:
        adas == null && car.airbags == null
          ? NEUTRAL
          : 0.3 * pick(pAir, car.id) + 0.7 * (adas == null ? NEUTRAL : adas),
      comfort:
        comfort == null && car.tela_central_pol == null
          ? NEUTRAL
          : 0.55 * (comfort == null ? NEUTRAL : comfort) +
            0.25 * pick(pScreen, car.id) +
            0.1 * pick(pSpeakers, car.id) +
            0.1 * pick(pNota, car.id),
    };

    const totalWeight = CRITERIA.reduce((sum, c) => sum + (state.weights[c.key] || 0), 0) || 1;
    const total = CRITERIA.reduce((sum, c) => sum + parts[c.key] * (state.weights[c.key] || 0), 0);
    scores.set(car.id, { parts, score: (total / totalWeight) * 100, valueRatio: ratios.get(car.id) });
  }
  state.scores = scores;
}

/* ---------- filtros ---------- */

function matchesMust(car, key) {
  if (key === 'sete_lugares') return (car.lugares || 0) >= 7;
  if (key === 'awd') return car.tracao === 'AWD';
  return car[key] === true;
}

function currentCars() {
  const f = state.filters;
  return state.cars.filter((car) => {
    if (car.motorizacao !== state.mode) return false;
    if (car.preco_brl != null) {
      if (f.priceMin != null && car.preco_brl < f.priceMin) return false;
      if (f.priceMax != null && car.preco_brl > f.priceMax) return false;
    }
    if (f.range && (car.autonomia_referencia_km || 0) < f.range) return false;
    if (f.battery && car.bateria_kwh < f.battery) return false;
    if (f.dc && (car.recarga_dc_kw || 0) < f.dc) return false;
    if (f.body.size && !f.body.has(car.carroceria)) return false;
    if (f.brand.size && !f.brand.has(car.marca)) return false;
    if (f.traction.size && !f.traction.has(car.tracao)) return false;
    for (const key of f.must) if (!matchesMust(car, key)) return false;
    if (f.search) {
      const hay = `${car.marca} ${car.modelo} ${car.versao} ${car.segmento || ''}`.toLowerCase();
      if (!hay.includes(f.search)) return false;
    }
    return true;
  });
}

function sortedCars(cars) {
  const dir = {
    score: (a, b) => score(b) - score(a),
    price: (a, b) => (a.preco_brl || Infinity) - (b.preco_brl || Infinity),
    'price-desc': (a, b) => (b.preco_brl || 0) - (a.preco_brl || 0),
    range: (a, b) => (b.autonomia_referencia_km || 0) - (a.autonomia_referencia_km || 0),
    efficiency: (a, b) => (b.eficiencia_km_kwh || 0) - (a.eficiencia_km_kwh || 0),
    charging: (a, b) => (b.recarga_dc_kw || 0) - (a.recarga_dc_kw || 0),
    power: (a, b) => (b.potencia_cv || b.potencia_combinada_cv || 0) - (a.potencia_cv || a.potencia_combinada_cv || 0),
    space: (a, b) => (b.volume_externo_m3 || 0) - (a.volume_externo_m3 || 0),
    value: (a, b) => (state.scores.get(a.id)?.valueRatio ?? 9) - (state.scores.get(b.id)?.valueRatio ?? 9),
    nota: (a, b) => (b.nota || 0) - (a.nota || 0),
  }[state.sort];
  return [...cars].sort(dir);
}

const score = (car) => state.scores.get(car.id)?.score ?? 0;

/* ---------- controles ---------- */

function buildWeights() {
  const host = document.getElementById('weights');
  host.innerHTML = CRITERIA.map(
    (c) => `
    <div class="weight-row">
      <span class="weight-row__label" title="${c.hint}">${c.label}</span>
      <span class="weight-row__value" data-weight-value="${c.key}"></span>
      <input type="range" min="0" max="10" step="1" value="${state.weights[c.key]}"
             data-weight="${c.key}" aria-label="${c.label}">
    </div>`,
  ).join('');
  host.querySelectorAll('input[data-weight]').forEach((input) => {
    input.addEventListener('input', (event) => {
      state.weights[event.target.dataset.weight] = Number(event.target.value);
      state.preset = null;
      refreshWeights();
      recompute();
    });
  });
  refreshWeights();
}

function refreshWeights() {
  const total = CRITERIA.reduce((sum, c) => sum + (state.weights[c.key] || 0), 0) || 1;
  for (const c of CRITERIA) {
    const el = document.querySelector(`[data-weight-value="${c.key}"]`);
    if (el) el.textContent = `${Math.round(((state.weights[c.key] || 0) / total) * 100)}%`;
    const input = document.querySelector(`input[data-weight="${c.key}"]`);
    if (input) input.value = state.weights[c.key];
  }
  document.querySelectorAll('#presets button').forEach((btn) => {
    btn.classList.toggle('is-active', btn.dataset.preset === state.preset);
  });
}

function buildFilterChips() {
  const cars = state.cars.filter((c) => c.motorizacao === state.mode);
  const bodies = [...new Set(cars.map((c) => c.carroceria).filter(Boolean))];
  document.getElementById('f-body').innerHTML = bodies
    .map((b) => `<button data-body="${b}" class="${state.filters.body.has(b) ? 'is-active' : ''}">${BODY_LABELS[b] || b}</button>`)
    .join('');

  const brands = [...new Set(cars.map((c) => c.marca))].sort((a, b) => a.localeCompare(b));
  document.getElementById('f-brand').innerHTML = brands
    .map((b) => `<button data-brand="${b}" class="${state.filters.brand.has(b) ? 'is-active' : ''}">${b}</button>`)
    .join('');

  document.getElementById('f-must').innerHTML = MUST_HAVES.map(
    (m) => `<button data-must="${m.key}" class="${state.filters.must.has(m.key) ? 'is-active' : ''}">${m.label}</button>`,
  ).join('');

  const tractions = [...new Set(cars.map((c) => c.tracao).filter(Boolean))];
  const order = ['dianteira', 'traseira', 'AWD'];
  document.getElementById('f-traction').innerHTML = tractions
    .sort((a, b) => order.indexOf(a) - order.indexOf(b))
    .map((t) => `<button data-traction="${t}" class="${state.filters.traction.has(t) ? 'is-active' : ''}">${t}</button>`)
    .join('');

  const wire = (selector, attr, set) => {
    document.querySelectorAll(selector).forEach((btn) => {
      btn.addEventListener('click', () => {
        const value = btn.dataset[attr];
        if (set.has(value)) set.delete(value);
        else set.add(value);
        btn.classList.toggle('is-active');
        refresh();
      });
    });
  };
  wire('[data-body]', 'body', state.filters.body);
  wire('[data-brand]', 'brand', state.filters.brand);
  wire('[data-must]', 'must', state.filters.must);
  wire('[data-traction]', 'traction', state.filters.traction);
}

let priceSlidersBound = false;

function setupPriceSliders() {
  const cars = state.cars.filter((c) => c.motorizacao === state.mode && c.preco_brl);
  const min = Math.floor(Math.min(...cars.map((c) => c.preco_brl)) / 5000) * 5000;
  const max = Math.ceil(Math.max(...cars.map((c) => c.preco_brl)) / 50000) * 50000;
  const lo = document.getElementById('f-price-min');
  const hi = document.getElementById('f-price-max');
  [lo, hi].forEach((el) => {
    el.min = min;
    el.max = max;
    el.step = 5000;
  });
  lo.value = min;
  hi.value = max;
  state.filters.priceMin = min;
  state.filters.priceMax = max;

  const sync = () => {
    if (Number(lo.value) > Number(hi.value)) [lo.value, hi.value] = [hi.value, lo.value];
    state.filters.priceMin = Number(lo.value);
    state.filters.priceMax = Number(hi.value);
    document.getElementById('price-readout').textContent =
      `${money(state.filters.priceMin)} – ${money(state.filters.priceMax)}`;
    refresh();
  };

  if (!priceSlidersBound) {
    lo.addEventListener('input', sync);
    hi.addEventListener('input', sync);
    priceSlidersBound = true;
  }

  document.getElementById('price-readout').textContent =
    `${money(state.filters.priceMin)} – ${money(state.filters.priceMax)}`;

  const range = document.getElementById('f-range');
  const battery = document.getElementById('f-battery');
  const dc = document.getElementById('f-dc');
  range.max = 700;
  range.step = 50;
  battery.max = 120;
  battery.step = 5;
  dc.max = 350;
  dc.step = 25;

  const bind = (input, key, label, unit) => {
    input.value = state.filters[key] || 0;
    if (input.dataset.bound) {
      document.getElementById(label).textContent = state.filters[key] ? `${state.filters[key]} ${unit}` : 'qualquer';
      return;
    }
    input.dataset.bound = '1';
    input.addEventListener('input', () => {
      state.filters[key] = Number(input.value);
      document.getElementById(label).textContent = input.value === '0' ? 'qualquer' : `${input.value} ${unit}`;
      refresh();
    });
    document.getElementById(label).textContent = state.filters[key] ? `${state.filters[key]} ${unit}` : 'qualquer';
  };
  bind(range, 'range', 'range-readout', 'km');
  bind(battery, 'battery', 'battery-readout', 'kWh');
  bind(dc, 'dc', 'dc-readout', 'kW');

  const search = document.getElementById('f-search');
  if (!search.dataset.bound) {
    search.dataset.bound = '1';
    search.addEventListener('input', (event) => {
      state.filters.search = event.target.value.trim().toLowerCase();
      refresh();
    });
  }
}

function resetFilters() {
  state.filters.body.clear();
  state.filters.brand.clear();
  state.filters.must.clear();
  state.filters.traction.clear();
  state.filters.range = 0;
  state.filters.battery = 0;
  state.filters.dc = 0;
  state.filters.search = '';
  document.querySelectorAll('#f-body button, #f-brand button, #f-must button, #f-traction button')
    .forEach((b) => b.classList.remove('is-active'));
  document.querySelectorAll('.chips button').forEach((b) => b.classList.remove('is-active'));
  document.getElementById('f-search').value = '';
  ['f-range', 'f-battery', 'f-dc'].forEach((id) => { document.getElementById(id).value = 0; });
  ['range-readout', 'battery-readout', 'dc-readout'].forEach((id) => {
    document.getElementById(id).textContent = 'qualquer';
  });
  setupPriceSliders();
  refresh();
}

function selectCar(id) {
  const index = state.selected.indexOf(id);
  if (index >= 0) state.selected.splice(index, 1);
  else if (state.selected.length < MAX_COMPARE) state.selected.push(id);
  else state.selected = [...state.selected.slice(1), id];
  renderDock();
  if (state.view === 'cards') renderCards();
  if (state.view === 'table') renderTable();
}

function refresh() {
  const cars = sortedCars(currentCars());
  renderCount(cars);
  renderActiveChips();
  const render = {
    cards: renderCards,
    table: renderTable,
    charts: renderCharts,
    cost: renderCost,
  }[state.view];
  try {
    render(cars);
  } catch (error) {
    console.error(error);
    document.getElementById(`view-${state.view}`).innerHTML =
      `<div class="empty"><b>Esta visão não pôde ser exibida</b>${error.message}</div>`;
  }
}

function setView(view) {
  state.view = view;
  document.querySelectorAll('#view-switch button').forEach((b) => {
    b.classList.toggle('is-active', b.dataset.view === view);
  });
  document.querySelectorAll('.view').forEach((v) => {
    v.classList.toggle('is-active', v.id === `view-${view}`);
  });
  refresh();
}

function setMode(mode) {
  state.mode = mode;
  document.querySelectorAll('#dataset-switch button').forEach((b) => {
    const active = b.dataset.mode === mode;
    b.classList.toggle('is-active', active);
    b.setAttribute('aria-selected', String(active));
  });
  state.selected = [];
  renderDock();
  buildFilterChips();
  setupPriceSliders();
  buildScores();
  refresh();
}

function recompute() {
  buildScores();
  refresh();
}

function renderCount(cars) {
  const total = state.cars.filter((c) => c.motorizacao === state.mode).length;
  document.getElementById('result-count').textContent =
    `${cars.length} de ${total} ${state.mode === 'BEV' ? 'carros elétricos' : 'híbridos plug-in flex'}`;
}

function syncChipClasses() {
  const f = state.filters;
  document.querySelectorAll('[data-body]').forEach((b) => b.classList.toggle('is-active', f.body.has(b.dataset.body)));
  document.querySelectorAll('[data-brand]').forEach((b) => b.classList.toggle('is-active', f.brand.has(b.dataset.brand)));
  document.querySelectorAll('[data-must]').forEach((b) => b.classList.toggle('is-active', f.must.has(b.dataset.must)));
  document.querySelectorAll('[data-traction]').forEach((b) => b.classList.toggle('is-active', f.traction.has(b.dataset.traction)));
}

function renderActiveChips() {
  const host = document.getElementById('active-chips');
  const f = state.filters;
  const chips = [];

  if (f.search) {
    chips.push({
      label: `“${f.search}”`,
      reset: () => {
        f.search = '';
        document.getElementById('f-search').value = '';
      },
    });
  }
  for (const value of f.body) {
    chips.push({ label: BODY_LABELS[value] || value, reset: () => f.body.delete(value) });
  }
  for (const value of f.brand) {
    chips.push({ label: value, reset: () => f.brand.delete(value) });
  }
  for (const value of f.must) {
    const label = MUST_HAVES.find((m) => m.key === value)?.label || value;
    chips.push({ label, reset: () => f.must.delete(value) });
  }
  for (const value of f.traction) {
    chips.push({ label: value, reset: () => f.traction.delete(value) });
  }
  for (const [label, reset] of [
    [f.range ? `Autonomia ≥ ${f.range} km` : null, () => { f.range = 0; document.getElementById('f-range').value = 0; document.getElementById('range-readout').textContent = 'qualquer'; }],
    [f.battery ? `Bateria ≥ ${f.battery} kWh` : null, () => { f.battery = 0; document.getElementById('f-battery').value = 0; document.getElementById('battery-readout').textContent = 'qualquer'; }],
    [f.dc ? `Recarga DC ≥ ${f.dc} kW` : null, () => { f.dc = 0; document.getElementById('f-dc').value = 0; document.getElementById('dc-readout').textContent = 'qualquer'; }],
  ]) {
    if (label) chips.push({ label, reset });
  }

  host.innerHTML = chips.map((c, i) => `<button data-chip="${i}">${c.label} ×</button>`).join('');
  host.querySelectorAll('button[data-chip]').forEach((btn, i) => {
    btn.addEventListener('click', () => {
      chips[i].reset();
      syncChipClasses();
      refresh();
    });
  });
}

function renderDock() {
  const dock = document.getElementById('dock');
  const tray = document.getElementById('dock-tray');
  const count = document.getElementById('dock-count');
  if (!state.selected.length) {
    dock.hidden = true;
    return;
  }
  dock.hidden = false;
  count.textContent = state.selected.length;
  tray.innerHTML = state.selected
    .map((id) => {
      const car = state.cars.find((c) => c.id === id);
      return `<span class="dock-chip">${car.marca} ${car.modelo}
        <button data-drop="${id}" aria-label="Remover">×</button></span>`;
    })
    .join('');
  tray.querySelectorAll('[data-drop]').forEach((btn) => {
    btn.addEventListener('click', () => selectCar(btn.dataset.drop));
  });
  document.getElementById('dock-compare').disabled = state.selected.length < 2;
}

/* ---------- fichas de detalhe e comparação ---------- */

const overlay = document.getElementById('overlay');
const sheet = document.getElementById('sheet');

function openSheet(html, drawer = false) {
  sheet.className = `sheet${drawer ? ' sheet--drawer' : ''}`;
  sheet.innerHTML = html;
  overlay.hidden = false;
  sheet.scrollTop = 0;
  sheet.querySelectorAll('[data-close]').forEach((el) => el.addEventListener('click', closeSheet));
  document.body.style.overflow = 'hidden';
}

function closeSheet() {
  overlay.hidden = true;
  sheet.innerHTML = '';
  document.body.style.overflow = '';
}

overlay.addEventListener('click', (event) => {
  if (event.target.dataset.close !== undefined) closeSheet();
});
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape' && !overlay.hidden) closeSheet();
});

function kv(label, value, unit = '', missingClass = true) {
  const isMissing = value == null || value === '';
  return `<div class="kv">
    <span class="kv__label">${label}</span>
    <span class="kv__value${isMissing && missingClass ? ' is-missing' : ''}">${
      isMissing ? '—' : `${value}${unit ? ` <small>${unit}</small>` : ''}`
    }</span>
  </div>`;
}

function safetyFacts(car) {
  const labels = {
    acc: 'piloto adaptativo', aeb: 'freagem automática', lka: 'centralização de faixa',
    ldw: 'aviso de faixa', bsm: 'ponto cego', rcta: 'tráfego cruzado traseiro', tsr: 'leitura de placas',
  };
  const on = ADAS_KEYS.filter((k) => car[k] === true).map((k) => labels[k]);
  const off = ADAS_KEYS.filter((k) => car[k] === false).map((k) => labels[k]);
  const unknown = ADAS_KEYS.filter((k) => typeof car[k] !== 'boolean');
  return [
    car.airbags ? `${car.airbags} airbags` : 'airbags não publicados',
    on.length ? `com ${on.slice(0, 3).join(', ')}` : null,
    off.length ? `sem ${off.slice(0, 2).join(', ')}` : null,
    `${unknown.length} de ${ADAS_KEYS.length} assistentes não publicados`,
  ].filter(Boolean).join(' · ');
}

function boolText(value) {
  if (value === true) return '<span class="yes">Sim</span>';
  if (value === false) return '<span class="no">Não</span>';
  return '<span class="missing">—</span>';
}

function openDetail(id) {
  const car = state.cars.find((c) => c.id === id);
  if (!car) return;
  const s = state.scores.get(car.id);
  const isPhev = car.motorizacao === 'PHEV';
  const ratio = s?.valueRatio;
  const ratioText = ratio == null
    ? null
    : `${ratio > 1 ? '+' : '−'}${Math.abs((ratio - 1) * 100).toFixed(0)}%`;
  const ratioExplain = ratio == null
    ? 'sem carros comparáveis'
    : ratio > 1
      ? 'acima dos parecidos'
      : 'abaixo dos parecidos';

  const groups = [
    {
      title: 'Preço e valor',
      items: [
        kv('Preço de tabela', money(car.preco_brl)),
        kv('Por kWh', car.preco_kwh ? money(car.preco_kwh) : null),
        kv('Índice vs. referência', car.indice_vs_base ? car.indice_vs_base.toFixed(2) : null, '× R$ 3.067/kWh'),
        kv('Preço ÷ autonomia', car.preco_por_km_autonomia ? money2(car.preco_por_km_autonomia) : null),
        kv('Preço por cv', car.preco_por_cv ? money(car.preco_por_cv) : null),
        kv('vs. carros parecidos', ratioText, ratioExplain),
      ],
      note: 'Preço ÷ autonomia é o preço de tabela dividido pela autonomia certificada: quanto do valor do carro "compra" cada km de alcance. É um indicador de preço, não o custo de rodar — esse fica em Custo de rodagem.',
    },
    {
      title: 'Custo de rodagem (estimado)',
      items: [
        kv('Por km rodado', cost100(car) == null ? null : money2(cost100(car) / 100)),
        kv('Por 100 km', cost100(car) == null ? null : money2(cost100(car))),
        kv('Por mês', annualCost(car) == null ? null : money(annualCost(car) / 12)),
        kv('Por ano', annualCost(car) == null ? null : money(annualCost(car))),
      ],
      note: `Estimativa com ${state.cost.homeShare}% da carga em casa a ${money2(state.cost.homePrice)}/kWh e o restante em recarga pública a ${money2(state.cost.publicPrice)}/kWh`
        + (isPhev ? `, ${state.cost.electricShare}% dos km no modo elétrico e combustível a ${money2(state.cost.fuelPrice)}/L` : '')
        + `, ${n0(state.cost.kmYear)} km por ano e ${money(state.cost.maintenance)}/ano de manutenção e pneus.`
        + ' Fora da conta: seguro, depreciação, pedágios e instalação do carregador.',
      action: 'Ajustar premissas em Custos',
    },
    {
      title: isPhev ? 'Bateria e autonomia elétrica' : 'Bateria e autonomia',
      items: [
        kv('Bateria nominal', kwh(car.bateria_kwh), 'kWh'),
        kv('Capacidade bruta', car.bateria_bruta_kwh ? kwh(car.bateria_bruta_kwh) : null, 'kWh'),
        kv('Química', car.quimica_bateria),
        kv(isPhev ? 'Autonomia elétrica' : 'Autonomia (Inmetro)', car.autonomia_referencia_km, 'km'),
        kv('Autonomia (WLTP)', car.autonomia_wltp_km, 'km'),
        kv('Eficiência', car.eficiencia_km_kwh ? n1(car.eficiencia_km_kwh) : null, 'km/kWh'),
        kv('Consumo', car.consumo_calc_kwh_100km ? n1(car.consumo_calc_kwh_100km) : null, 'kWh/100 km'),
      ],
    },
    {
      title: 'Recarga',
      items: [
        kv('Pico DC', car.recarga_dc_kw, 'kW'),
        kv('DC 10–80%', car.recarga_dc_10_80_min, 'min'),
        kv('AC de bordo', car.recarga_ac_kw, 'kW'),
        kv('Conector DC', car.conector_dc),
        kv('Tomada V2L', car.v2l === true ? `Sim${car.v2l_kw ? ` (${n1(car.v2l_kw)} kW)` : ''}` : car.v2l === false ? 'Não' : null),
        kv('Bomba de calor', car.bomba_de_calor === true ? 'Sim' : car.bomba_de_calor === false ? 'Não' : null),
      ],
    },
    {
      title: 'Motorização',
      items: [
        kv('Potência', car.potencia_cv || car.potencia_combinada_cv, 'cv'),
        kv('Torque', car.torque_nm || car.torque_combinado_nm, 'Nm'),
        kv('Tração', car.tracao),
        kv('Motores', car.motores_qtd),
        kv('0–100 km/h', car.zero_a_cem_s ? n1(car.zero_a_cem_s) : null, 's'),
        kv('Velocidade máxima', car.velocidade_max_kmh, 'km/h'),
        kv('Peso em ordem de marcha', car.peso_kg ? n0(car.peso_kg) : null, 'kg'),
        kv('Relação peso-potência', car.cv_por_tonelada ? n1(car.cv_por_tonelada) : null, 'cv/t'),
        ...(isPhev
          ? [
              kv('Motor a combustão', car.motor_termico),
              kv('Combustível', car.combustivel),
              kv('Tanque', car.tanque_l ? n1(car.tanque_l) : null, 'L'),
              kv('Consumo de combustível', car.consumo_l_100km ? n1(car.consumo_l_100km) : null, 'L/100 km'),
              kv('Câmbio', car.cambio),
            ]
          : []),
      ],
    },
    {
      title: 'Dimensões e praticidade',
      items: [
        kv('Carroceria', BODY_LABELS[car.carroceria] || car.carroceria),
        kv('Segmento', car.segmento),
        kv('Lugares', car.lugares),
        kv('Comprimento', car.comprimento_mm ? n0(car.comprimento_mm) : null, 'mm'),
        kv('Largura', car.largura_mm ? n0(car.largura_mm) : null, 'mm'),
        kv('Altura', car.altura_mm ? n0(car.altura_mm) : null, 'mm'),
        kv('Entre-eixos', car.entre_eixos_mm ? n0(car.entre_eixos_mm) : null, 'mm'),
        kv('Vão livre do solo', car.altura_livre_solo_mm ? n0(car.altura_livre_solo_mm) : null, 'mm'),
        kv('Porta-malas', car.porta_malas_l ? n0(car.porta_malas_l) : null, 'L'),
        kv('Porta-malas (bancos rebatidos)', car.porta_malas_max_l ? n0(car.porta_malas_max_l) : null, 'L'),
      ],
    },
    {
      title: 'Segurança',
      items: [
        kv('Airbags', car.airbags),
        kv('Piloto adaptativo (ACC)', boolText(car.acc)),
        kv('Freagem urbana (AEB)', boolText(car.aeb)),
        kv('Centralização de faixa', boolText(car.lka)),
        kv('Ponto cego', boolText(car.bsm)),
        kv('Tráfego cruzado traseiro', boolText(car.rcta)),
        kv('360° camera', boolText(car.camera_360)),
        kv('Sensores de estacionamento', boolText(car.sensor_estacionamento)),
        kv('Nível de assistência', car.adas_nivel),
      ],
    },
    {
      title: 'Conforto e tecnologia',
      items: [
        kv('Tela central', car.tela_central_pol ? `${n1(car.tela_central_pol)}"` : null),
        kv('Quadro digital', car.cluster_digital_pol ? `${n1(car.cluster_digital_pol)}"` : null),
        kv('Apple CarPlay', car.carplay),
        kv('Android Auto', car.android_auto),
        kv('Carregador por indução', boolText(car.carregador_sem_fio)),
        kv('Alto-falantes', car.som_alto_falantes ? `${car.som_alto_falantes}${car.som_marca ? ` · ${car.som_marca}` : ''}` : null),
        kv('Climatização', car.ar_condicionado),
        kv('Bancos elétricos', boolText(car.banco_eletrico)),
        kv('Bancos ventilados', boolText(car.bancos_ventilados)),
        kv('Bancos aquecidos', boolText(car.bancos_aquecidos)),
        kv('Teto panorâmico', boolText(car.teto_panoramico)),
        kv('Head-up display', boolText(car.head_up_display)),
      ],
    },
    {
      title: 'Garantia e propriedade',
      items: [
        kv('Veículo', car.garantia_veiculo_anos ? `${car.garantia_veiculo_anos} anos` : null,
          car.garantia_veiculo_km ? `· ${n0(car.garantia_veiculo_km)} km` : ''),
        kv('Bateria', car.garantia_bateria_anos ? `${car.garantia_bateria_anos} anos` : null,
          car.garantia_bateria_km ? `· ${n0(car.garantia_bateria_km)} km` : ''),
        kv('Nota editorial', car.nota ? `${n1(car.nota)}/10` : null),
        kv('Campos verificados', `${car.campos_preenchidos} de 61`),
      ],
    },
  ];

  const conf = car.confianca || 'baixa';
  const sourceList = [...new Set([car.fonte_principal, ...(car.fontes || [])].filter(Boolean))];

  const html = `
    <div class="sheet__head">
      <div>
        <p>${car.marca} · ${BODY_LABELS[car.carroceria] || car.carroceria || ''} ${car.motorizacao === 'PHEV' ? '· híbrido plug-in flex' : '· elétrico'}</p>
        <h2>${car.modelo}</h2>
        <p>${car.versao || car.versao_pesquisada || ''}</p>
      </div>
      <button class="close-btn" data-close aria-label="Fechar">×</button>
    </div>
    <div class="sheet__body">
      <div class="detail-hero">
        <div>
          <div class="detail-price">${money(car.preco_brl)} <small>${car.preco_brl ? 'preço de tabela / campanha' : 'sem preço público'}</small></div>
          <p class="hint" style="margin:6px 0 0">${car.observacao_preco || ''}</p>
        </div>
        <div style="text-align:right">
          <div class="ring" style="width:62px;height:62px;margin-left:auto">${ringSvg(s.score, 62)}</div>
          <span class="conf conf--${conf}"><i></i>${conf === 'alta' ? 'verificado' : conf === 'media' ? 'parcialmente verificado' : 'poucas fontes'}</span>
        </div>
      </div>
      ${s
        ? (() => {
            const modeCars = state.cars.filter((c) => c.motorizacao === state.mode);
            const total = modeCars.length;
            const modeLabel = state.mode === 'BEV' ? 'elétricos' : 'plug-in flex';
            return `<section class="spec-section"><h4>Posição frente aos outros carros</h4>
              <p class="hint" style="margin:-2px 0 12px">Cada linha mostra a posição desta versão entre os ${total} ${modeLabel} deste painel naquele critério — 1º é o melhor da lista. É posição relativa, não uma nota de 0 a 100 e não é resultado de teste de colisão.</p>
              <div class="breakdown">${CRITERIA.map((c) => {
                const part = s.parts[c.key] || 0;
                const value = Math.round(part * 100);
                const rank = Math.max(1, Math.min(total, Math.round((1 - part) * (total - 1)) + 1));
                const weight = Math.round(((state.weights[c.key] || 0) / (CRITERIA.reduce((sum, x) => sum + (state.weights[x.key] || 0), 0) || 1)) * 100);
                return `<div class="breakdown__row" title="${c.hint}">
                  <span class="breakdown__label">${c.short || c.label}</span>
                  <span class="breakdown__track"><span class="breakdown__fill" style="width:${value}%"></span></span>
                  <span class="breakdown__value">${rank}º</span>
                  <span class="breakdown__weight">${weight}%</span>
                </div>`;
              }).join('')}</div>
              <p class="hint" style="margin:10px 0 0">A última coluna é o peso que você deu ao critério. Em segurança, a posição considera <b>quantidade de airbags e presença dos assistentes registrados na base</b>. Não inclui resultado de teste de colisão (como Latin NCAP), que não faz parte dos dados.</p>
              <p class="notes" style="margin-top:10px"><b>O que a base informa sobre segurança</b>${safetyFacts(car)}</p>
            </section>`;
          })()
        : ''}
      ${groups
        .map(
          (g) => `<section class="spec-section"><h4>${g.title}</h4>
            <div class="kv-grid">${g.items.join('')}</div>
            ${g.note ? `<p class="hint" style="margin:10px 0 0">${g.note}</p>` : ''}
            ${g.action ? `<button class="ghost-btn" data-cost-link style="margin-top:10px">${g.action}</button>` : ''}
          </section>`,
        )
        .join('')}
      ${car.alerta_versao ? `<div class="notes" style="background:var(--warn-soft)"><b>Atenção à versão</b>${car.alerta_versao}</div>` : ''}
      ${car.observacoes ? `<div class="notes"><b>O que saber</b>${car.observacoes}</div>` : ''}
      ${sourceList.length
        ? `<div class="sources"><h4>Fontes</h4>${sourceList
            .slice(0, 8)
            .map((u) => `<a href="${u}" target="_blank" rel="noopener">${u}</a>`)
            .join('')}</div>`
        : ''}
      <div style="margin-top:20px;display:flex;gap:8px">
        <button class="primary-btn" data-add="${car.id}" style="flex:1">${
          state.selected.includes(car.id) ? 'Remover da comparação' : 'Adicionar à comparação'
        }</button>
      </div>
    </div>`;

  openSheet(html, true);
  sheet.querySelectorAll('[data-add]').forEach((btn) => {
    btn.addEventListener('click', () => {
      selectCar(btn.dataset.add);
      closeSheet();
    });
  });
  sheet.querySelectorAll('[data-cost-link]').forEach((btn) => {
    btn.addEventListener('click', () => {
      closeSheet();
      setView('cost');
    });
  });
}

function ringSvg(value, size = 54) {
  const stroke = size > 56 ? 4 : 3.5;
  const r = (size - stroke) / 2 - 1;
  const c = 2 * Math.PI * r;
  const pct = Math.max(0, Math.min(100, value)) / 100;
  const hue = value >= 60 ? 'var(--accent)' : value >= 50 ? '#d8640a' : '#d8342f';
  return `<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
    <circle cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" stroke="var(--line)" stroke-width="${stroke}"/>
    <circle cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" stroke="${hue}" stroke-width="${stroke}"
      stroke-linecap="round" stroke-dasharray="${c * pct} ${c}" />
  </svg>
  <span class="ring__num">${Math.round(value)}<small>/100</small></span>`;
}

/* ---------- comparação ---------- */

function openCompare() {
  const cars = state.selected.map((id) => state.cars.find((c) => c.id === id)).filter(Boolean);
  if (cars.length < 2) return;

  const rows = [
    { group: 'Preço' },
    { label: 'Preço de tabela', get: (c) => c.preco_brl, fmt: (v) => money(v), better: 'low' },
    { label: 'Por kWh', get: (c) => c.preco_kwh, fmt: (v) => money(v), better: 'low' },
    { label: 'Preço ÷ autonomia', get: (c) => c.preco_por_km_autonomia, fmt: (v) => money2(v), better: 'low' },
    { label: 'vs. carros parecidos', get: (c) => state.scores.get(c.id)?.valueRatio, fmt: (v) => `${v > 1 ? '+' : '−'}${Math.abs((v - 1) * 100).toFixed(0)}%`, better: 'low' },
    { group: 'Custo de rodagem (estimado)' },
    { label: 'Por km rodado', get: (c) => cost100(c), fmt: (v) => money2(v / 100), better: 'low' },
    { label: 'Por 100 km', get: (c) => cost100(c), fmt: (v) => money2(v), better: 'low' },
    { label: 'Por mês', get: (c) => annualCost(c), fmt: (v) => money(v / 12), better: 'low' },
    { group: 'Bateria e autonomia' },
    { label: 'Bateria (nominal)', get: (c) => c.bateria_kwh, fmt: (v) => `${kwh(v)} kWh`, better: 'high' },
    { label: 'Química', get: (c) => c.quimica_bateria },
    { label: 'Autonomia', get: (c) => c.autonomia_referencia_km, fmt: (v) => `${n0(v)} km`, better: 'high' },
    { label: 'Eficiência', get: (c) => c.eficiencia_km_kwh, fmt: (v) => `${n1(v)} km/kWh`, better: 'high' },
    { label: 'Consumo', get: (c) => c.consumo_calc_kwh_100km, fmt: (v) => `${n1(v)} kWh/100 km`, better: 'low' },
    { group: 'Recarga' },
    { label: 'Pico DC', get: (c) => c.recarga_dc_kw, fmt: (v) => `${n0(v)} kW`, better: 'high' },
    { label: 'DC 10–80%', get: (c) => c.recarga_dc_10_80_min, fmt: (v) => `${n0(v)} min`, better: 'low' },
    { label: 'AC de bordo', get: (c) => c.recarga_ac_kw, fmt: (v) => `${n1(v)} kW`, better: 'high' },
    { label: 'V2L', get: (c) => c.v2l, fmt: boolText },
    { label: 'Bomba de calor', get: (c) => c.bomba_de_calor, fmt: boolText },
    { group: 'Motorização' },
    { label: 'Potência', get: (c) => c.potencia_cv || c.potencia_combinada_cv, fmt: (v) => `${n0(v)} cv`, better: 'high' },
    { label: 'Torque', get: (c) => c.torque_nm || c.torque_combinado_nm, fmt: (v) => `${n0(v)} Nm`, better: 'high' },
    { label: 'Tração', get: (c) => c.tracao },
    { label: '0–100 km/h', get: (c) => c.zero_a_cem_s, fmt: (v) => `${n1(v)} s`, better: 'low' },
    { label: 'Peso', get: (c) => c.peso_kg, fmt: (v) => `${n0(v)} kg`, better: 'low' },
    { label: 'Relação peso-potência', get: (c) => c.cv_por_tonelada, fmt: (v) => `${n1(v)} cv/t`, better: 'high' },
    { group: 'Espaço' },
    { label: 'Carroceria', get: (c) => BODY_LABELS[c.carroceria] || c.carroceria },
    { label: 'Lugares', get: (c) => c.lugares, fmt: (v) => `${v}`, better: 'high' },
    { label: 'Comprimento', get: (c) => c.comprimento_mm, fmt: (v) => `${n0(v)} mm`, better: 'high' },
    { label: 'Entre-eixos', get: (c) => c.entre_eixos_mm, fmt: (v) => `${n0(v)} mm`, better: 'high' },
    { label: 'Vão livre do solo', get: (c) => c.altura_livre_solo_mm, fmt: (v) => `${n0(v)} mm`, better: 'high' },
    { label: 'Porta-malas', get: (c) => c.porta_malas_l, fmt: (v) => `${n0(v)} L`, better: 'high' },
    { group: 'Segurança' },
    { label: 'Airbags', get: (c) => c.airbags, fmt: (v) => `${v}`, better: 'high' },
    { label: 'Piloto adaptativo (ACC)', get: (c) => c.acc, fmt: boolText },
    { label: 'Freagem urbana (AEB)', get: (c) => c.aeb, fmt: boolText },
    { label: 'Centralização de faixa', get: (c) => c.lka, fmt: boolText },
    { label: 'Ponto cego', get: (c) => c.bsm, fmt: boolText },
    { label: '360° camera', get: (c) => c.camera_360, fmt: boolText },
    { label: 'Sensores de estacionamento', get: (c) => c.sensor_estacionamento, fmt: boolText },
    { group: 'Conforto e tecnologia' },
    { label: 'Tela central', get: (c) => c.tela_central_pol, fmt: (v) => `${n1(v)}"`, better: 'high' },
    { label: 'CarPlay', get: (c) => c.carplay },
    { label: 'Android Auto', get: (c) => c.android_auto },
    { label: 'Carregador por indução', get: (c) => c.carregador_sem_fio, fmt: boolText },
    { label: 'Alto-falantes', get: (c) => c.som_alto_falantes, fmt: (v) => `${v}`, better: 'high' },
    { label: 'Climatização', get: (c) => c.ar_condicionado },
    { label: 'Teto panorâmico', get: (c) => c.teto_panoramico, fmt: boolText },
    { label: 'Bancos ventilados', get: (c) => c.bancos_ventilados, fmt: boolText },
    { group: 'Propriedade' },
    { label: 'Garantia da bateria', get: (c) => c.garantia_bateria_anos ? `${c.garantia_bateria_anos} anos / ${c.garantia_bateria_km ? n0(c.garantia_bateria_km) + ' km' : '—'}` : null },
    { label: 'Nota editorial', get: (c) => c.nota, fmt: (v) => `${n1(v)}/10`, better: 'high' },
    { label: 'Sua nota', get: (c) => state.scores.get(c.id)?.score, fmt: (v) => `${Math.round(v)}/100`, better: 'high' },
  ];

  const groups = [];
  let current = null;
  for (const row of rows) {
    if (row.group) {
      current = { title: row.group, rows: [] };
      groups.push(current);
      continue;
    }
    const values = cars.map((c) => row.get(c));
    if (values.every((v) => v == null || v === '')) continue;
    current.rows.push({ row, values });
  }

  const body = groups
    .filter((g) => g.rows.length)
    .map((group) => {
      const head = `<tr class="group-row"><th colspan="${cars.length + 1}">${group.title}</th></tr>`;
      return head + group.rows
        .map(({ row, values }) => {
      let bestIndex = -1;
      if (row.better) {
        const numeric = values.map((v, i) => [i, typeof v === 'number' ? v : null]).filter(([, v]) => v != null);
        if (numeric.length > 1) {
          const target = row.better === 'high' ? Math.max(...numeric.map(([, v]) => v)) : Math.min(...numeric.map(([, v]) => v));
          if (numeric.filter(([, v]) => v === target).length === 1) bestIndex = numeric.find(([, v]) => v === target)[0];
        }
      }
      const cells = values
        .map((v, i) => {
          if (v == null) return '<td class="missing">—</td>';
          const shown = row.fmt ? row.fmt(v) : v;
          return `<td class="${i === bestIndex ? 'best' : ''}">${shown}</td>`;
        })
        .join('');
      return `<tr><th>${row.label}</th>${cells}</tr>`;
        })
        .join('');
    })
    .join('');

  const heads = cars
    .map(
      (c) => `<th>
        <div class="cmp-name">${c.modelo}</div>
        <div class="cmp-sub">${c.marca} · ${c.versao || ''}</div>
        <div class="cmp-price">${money(c.preco_brl)}</div>
        <button class="close-btn" style="width:22px;height:22px;font-size:12px;margin-top:6px"
          data-unpick="${c.id}" aria-label="Remover">×</button>
      </th>`,
    )
    .join('');

  const html = `
    <div class="sheet__head">
      <div>
        <p>Lado a lado · o melhor valor de cada linha está marcado</p>
        <h2>${cars.length} carros comparados</h2>
      </div>
      <button class="close-btn" data-close aria-label="Fechar">×</button>
    </div>
    <div class="sheet__body">
      <div class="cmp-wrap">
        <table class="cmp">
          <thead><tr><th>Especificação</th>${heads}</tr></thead>
          <tbody>${body}</tbody>
        </table>
      </div>
      <p class="hint" style="margin-top:12px">
        ◆ marca o melhor valor da linha. Um traço significa que nenhuma fonte que pudéssemos verificar publicou esse número
        para esta versão — é desconhecido, não zero.
      </p>
    </div>`;

  openSheet(html, false);
  sheet.querySelectorAll('[data-unpick]').forEach((btn) => {
    btn.addEventListener('click', () => {
      selectCar(btn.dataset.unpick);
      closeSheet();
    });
  });
}

/* ---------- exportação ---------- */

function exportCsv() {
  const cars = sortedCars(currentCars());
  const columns = [
    ['Marca', (c) => c.marca],
    ['Modelo', (c) => c.modelo],
    ['Versão', (c) => c.versao],
    ['Motorização', (c) => c.motorizacao],
    ['Preço R$', (c) => c.preco_brl],
    ['Nota', (c) => Math.round(score(c))],
    ['Bateria kWh', (c) => c.bateria_kwh],
    ['Autonomia km', (c) => c.autonomia_referencia_km],
    ['km/kWh', (c) => c.eficiencia_km_kwh],
    ['Potência cv', (c) => c.potencia_cv || c.potencia_combinada_cv],
    ['Torque Nm', (c) => c.torque_nm || c.torque_combinado_nm],
    ['0-100 s', (c) => c.zero_a_cem_s],
    ['DC kW', (c) => c.recarga_dc_kw],
    ['DC 10-80 min', (c) => c.recarga_dc_10_80_min],
    ['AC kW', (c) => c.recarga_ac_kw],
    ['Carroceria', (c) => c.carroceria],
    ['Lugares', (c) => c.lugares],
    ['Porta-malas L', (c) => c.porta_malas_l],
    ['Comprimento mm', (c) => c.comprimento_mm],
    ['Peso kg', (c) => c.peso_kg],
    ['Airbags', (c) => c.airbags],
    ['ACC', (c) => c.acc],
    ['AEB', (c) => c.aeb],
    ['Câmera 360', (c) => c.camera_360],
    ['V2L', (c) => c.v2l],
    ['Bomba de calor', (c) => c.bomba_de_calor],
    ['Garantia bateria (anos)', (c) => c.garantia_bateria_anos],
    ['Nota editorial', (c) => c.nota],
    ['Energia R$/100 km', (c) => cost100(c)],
    ['Custo anual R$', (c) => annualCost(c)],
    ['vs similares', (c) => {
      const r = state.scores.get(c.id)?.valueRatio;
      return r == null ? null : Math.round((r - 1) * 1000) / 10;
    }],
    ['Fontes', (c) => [...new Set([c.fonte_principal, ...(c.fontes || [])].filter(Boolean))].join(' | ')],
  ];

  const cell = (value) => {
    if (value == null || value === '') return '';
    if (typeof value === 'boolean') return value ? 'sim' : 'não';
    if (typeof value === 'number') return String(Math.round(value * 100) / 100).replace('.', ',');
    return `"${String(value).replace(/"/g, '""')}"`;
  };

  const lines = [columns.map(([name]) => `"${name}"`).join(';')];
  for (const car of cars) lines.push(columns.map(([, get]) => cell(get(car))).join(';'));

  const blob = new Blob([`\ufeff${lines.join('\r\n')}`], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `carros-verdes-${state.mode === 'BEV' ? 'eletricos' : 'plug-in-flex'}-${new Date().toISOString().slice(0, 10)}.csv`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

/* ---------- inicialização ---------- */

async function boot() {
  const response = await fetch('data/cars.json');
  const payload = await response.json();
  state.cars = payload.cars;
  state.meta = payload.meta;

  const bev = state.cars.filter((c) => c.motorizacao === 'BEV').length;
  const phev = state.cars.filter((c) => c.motorizacao === 'PHEV').length;
  document.getElementById('count-bev').textContent = bev;
  document.getElementById('count-phev').textContent = phev;
  document.getElementById('topbar-meta').textContent =
    `Preços de ${state.meta.preco_referencia} · ${bev} elétricos + ${phev} plug-in flex · ${state.cars.length} versões`;

  const priced = state.cars.filter((c) => c.preco_brl);
  const from = priced.length ? Math.min(...priced.map((c) => c.preco_brl)) : null;
  document.getElementById('hero-bev').textContent = bev;
  document.getElementById('hero-phev').textContent = phev;
  document.getElementById('hero-total').textContent = state.cars.length;
  document.getElementById('hero-range').textContent = money(from);

  buildWeights();
  buildFilterChips();
  setupPriceSliders();
  buildScores();

  document.getElementById('presets').addEventListener('click', (event) => {
    const btn = event.target.closest('button[data-preset]');
    if (!btn) return;
    state.preset = btn.dataset.preset;
    state.weights = { ...PRESETS[state.preset] };
    refreshWeights();
    recompute();
  });

  document.getElementById('reset-weights').addEventListener('click', () => {
    state.preset = 'balanced';
    state.weights = { ...PRESETS.balanced };
    refreshWeights();
    recompute();
  });

  document.getElementById('reset-filters').addEventListener('click', resetFilters);

  document.getElementById('dataset-switch').addEventListener('click', (event) => {
    const btn = event.target.closest('button[data-mode]');
    if (btn && btn.dataset.mode !== state.mode) setMode(btn.dataset.mode);
  });

  document.getElementById('view-switch').addEventListener('click', (event) => {
    const btn = event.target.closest('button[data-view]');
    if (btn) setView(btn.dataset.view);
  });

  document.getElementById('sort').addEventListener('change', (event) => {
    state.sort = event.target.value;
    refresh();
  });

  document.getElementById('btn-export').addEventListener('click', exportCsv);

  document.getElementById('dock-clear').addEventListener('click', () => {
    state.selected = [];
    renderDock();
    refresh();
  });

  document.getElementById('dock-compare').addEventListener('click', openCompare);

  document.getElementById('btn-sidebar').addEventListener('click', (event) => {
    const sidebar = document.getElementById('sidebar');
    sidebar.classList.toggle('is-open');
    event.target.setAttribute('aria-expanded', String(sidebar.classList.contains('is-open')));
  });

  const params = new URLSearchParams(location.search);
  const paramsQuery = params.get('q');
  if (paramsQuery) {
    state.filters.search = paramsQuery.trim().toLowerCase();
    document.getElementById('f-search').value = paramsQuery;
  }
  const paramsMode = params.get('mode');
  const paramsView = params.get('view');
  if (paramsMode === 'PHEV' || paramsMode === 'BEV') setMode(paramsMode);
  if (['cards', 'table', 'charts', 'cost'].includes(paramsView)) setView(paramsView);

  renderDock();
  refresh();

  const cmpParam = params.get('compare');
  const carParam = params.get('car');
  if (cmpParam) {
    const ids = cmpParam.split(',').filter((id) => state.cars.some((c) => c.id === id)).slice(0, MAX_COMPARE);
    if (ids.length >= 2) {
      state.selected = ids;
      renderDock();
      refresh();
      openCompare();
    }
  } else if (carParam) {
    openDetail(carParam);
  }
}

boot().catch((error) => {
  document.getElementById('view-cards').innerHTML =
    `<div class="empty"><b>Não foi possível carregar os dados</b>${error.message}<br>Rode um servidor local nesta pasta: <code>python3 -m http.server 8000</code></div>`;
});
