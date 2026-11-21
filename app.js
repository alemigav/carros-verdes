/* Car chooser — core state, scoring engine and controls. */

const MAX_COMPARE = 4;

const CRITERIA = [
  { key: 'value', label: 'Value vs peers', hint: 'price against what similar specs cost' },
  { key: 'range', label: 'Range', hint: 'electric range on the Inmetro/WLTP cycle' },
  { key: 'efficiency', label: 'Efficiency', hint: 'km per kWh of battery' },
  { key: 'charging', label: 'Fast charging', hint: 'DC power and 10–80% time' },
  { key: 'performance', label: 'Performance', hint: 'power-to-weight and 0–100 km/h' },
  { key: 'space', label: 'Space', hint: 'size, trunk and seats' },
  { key: 'safety', label: 'Safety & aids', hint: 'airbags and driver-assistance features' },
  { key: 'comfort', label: 'Comfort & tech', hint: 'screens, seats, climate, connectivity' },
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
  { key: 'acc', label: 'Adaptive cruise' },
  { key: 'aeb', label: 'Auto emergency braking' },
  { key: 'camera_360', label: '360° camera' },
  { key: 'v2l', label: 'V2L power outlet' },
  { key: 'bomba_de_calor', label: 'Heat pump' },
  { key: 'teto_panoramico', label: 'Panoramic roof' },
  { key: 'bancos_ventilados', label: 'Ventilated seats' },
  { key: 'carregador_sem_fio', label: 'Wireless charging' },
  { key: 'sete_lugares', label: '7 seats' },
  { key: 'awd', label: 'AWD' },
];

const BODY_LABELS = {
  hatch: 'Hatch',
  sedan: 'Sedan',
  suv: 'SUV',
  picape: 'Pickup',
  minivan: 'Minivan',
  cupe: 'Coupé',
  perua: 'Wagon',
  outro: 'Other',
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

/* ---------- formatting ---------- */

const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 });
const brl2 = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 2 });
const dec1 = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
const int = new Intl.NumberFormat('pt-BR');

const money = (v) => (v == null ? '—' : brl.format(v));
const money2 = (v) => (v == null ? '—' : brl2.format(v));
const n1 = (v) => (v == null ? '—' : dec1.format(v));
const n0 = (v) => (v == null ? '—' : int.format(v));
const dash = (v, unit = '') => (v == null || v === '' ? '<span class="missing">—</span>' : `${v}${unit}`);

/* ---------- stats helpers ---------- */

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
  if (!known.length) return null;
  return known.filter((k) => car[k]).length / keys.length;
}

/* Price against the median of cars with similar battery and power. */
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

/* ---------- scoring ---------- */

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

/* ---------- filtering ---------- */

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

/* ---------- controls ---------- */

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
      document.getElementById(label).textContent = state.filters[key] ? `${state.filters[key]} ${unit}` : 'any';
      return;
    }
    input.dataset.bound = '1';
    input.addEventListener('input', () => {
      state.filters[key] = Number(input.value);
      document.getElementById(label).textContent = input.value === '0' ? 'any' : `${input.value} ${unit}`;
      refresh();
    });
    document.getElementById(label).textContent = state.filters[key] ? `${state.filters[key]} ${unit}` : 'any';
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
    document.getElementById(id).textContent = 'any';
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
      `<div class="empty"><b>This view failed to render</b>${error.message}</div>`;
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
    `${cars.length} of ${total} ${state.mode === 'BEV' ? 'electric cars' : 'plug-in hybrids'}`;
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
    [f.range ? `Range ≥ ${f.range} km` : null, () => { f.range = 0; document.getElementById('f-range').value = 0; document.getElementById('range-readout').textContent = 'any'; }],
    [f.battery ? `Battery ≥ ${f.battery} kWh` : null, () => { f.battery = 0; document.getElementById('f-battery').value = 0; document.getElementById('battery-readout').textContent = 'any'; }],
    [f.dc ? `DC ≥ ${f.dc} kW` : null, () => { f.dc = 0; document.getElementById('f-dc').value = 0; document.getElementById('dc-readout').textContent = 'any'; }],
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
        <button data-drop="${id}" aria-label="Remove">×</button></span>`;
    })
    .join('');
  tray.querySelectorAll('[data-drop]').forEach((btn) => {
    btn.addEventListener('click', () => selectCar(btn.dataset.drop));
  });
  document.getElementById('dock-compare').disabled = state.selected.length < 2;
}

/* ---------- detail + compare sheets ---------- */

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

function boolText(value) {
  if (value === true) return '<span class="yes">Yes</span>';
  if (value === false) return '<span class="no">No</span>';
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
    ? 'no comparable cars'
    : ratio > 1
      ? 'above similar cars'
      : 'below similar cars';

  const groups = [
    {
      title: 'Price & value',
      items: [
        kv('List price', money(car.preco_brl)),
        kv('Per kWh', car.preco_kwh ? money(car.preco_kwh) : null),
        kv('Index vs benchmark', car.indice_vs_base ? car.indice_vs_base.toFixed(2) : null, '× R$ 3.067/kWh'),
        kv('Price per km of range', car.preco_por_km_autonomia ? money2(car.preco_por_km_autonomia) : null),
        kv('Price per cv', car.preco_por_cv ? money(car.preco_por_cv) : null),
        kv('vs. similar cars', ratioText, ratioExplain),
      ],
    },
    {
      title: isPhev ? 'Battery & electric range' : 'Battery & range',
      items: [
        kv('Nominal battery', kwh(car.bateria_kwh), 'kWh'),
        kv('Gross capacity', car.bateria_bruta_kwh ? kwh(car.bateria_bruta_kwh) : null, 'kWh'),
        kv('Chemistry', car.quimica_bateria),
        kv(isPhev ? 'Electric range' : 'Range (Inmetro)', car.autonomia_referencia_km, 'km'),
        kv('Range (WLTP)', car.autonomia_wltp_km, 'km'),
        kv('Efficiency', car.eficiencia_km_kwh ? n1(car.eficiencia_km_kwh) : null, 'km/kWh'),
        kv('Consumption', car.consumo_calc_kwh_100km ? n1(car.consumo_calc_kwh_100km) : null, 'kWh/100 km'),
      ],
    },
    {
      title: 'Charging',
      items: [
        kv('DC peak', car.recarga_dc_kw, 'kW'),
        kv('DC 10–80%', car.recarga_dc_10_80_min, 'min'),
        kv('AC onboard', car.recarga_ac_kw, 'kW'),
        kv('DC connector', car.conector_dc),
        kv('V2L outlet', car.v2l === true ? `Yes${car.v2l_kw ? ` (${n1(car.v2l_kw)} kW)` : ''}` : car.v2l === false ? 'No' : null),
        kv('Heat pump', car.bomba_de_calor === true ? 'Yes' : car.bomba_de_calor === false ? 'No' : null),
      ],
    },
    {
      title: 'Powertrain',
      items: [
        kv('Power', car.potencia_cv || car.potencia_combinada_cv, 'cv'),
        kv('Torque', car.torque_nm || car.torque_combinado_nm, 'Nm'),
        kv('Drivetrain', car.tracao),
        kv('Motors', car.motores_qtd),
        kv('0–100 km/h', car.zero_a_cem_s ? n1(car.zero_a_cem_s) : null, 's'),
        kv('Top speed', car.velocidade_max_kmh, 'km/h'),
        kv('Kerb weight', car.peso_kg ? n0(car.peso_kg) : null, 'kg'),
        kv('Power-to-weight', car.cv_por_tonelada ? n1(car.cv_por_tonelada) : null, 'cv/t'),
        ...(isPhev
          ? [
              kv('Combustion engine', car.motor_termico),
              kv('Fuel', car.combustivel),
              kv('Tank', car.tanque_l ? n1(car.tanque_l) : null, 'L'),
              kv('Fuel use', car.consumo_l_100km ? n1(car.consumo_l_100km) : null, 'L/100 km'),
              kv('Transmission', car.cambio),
            ]
          : []),
      ],
    },
    {
      title: 'Size & practicality',
      items: [
        kv('Body', BODY_LABELS[car.carroceria] || car.carroceria),
        kv('Segment', car.segmento),
        kv('Seats', car.lugares),
        kv('Length', car.comprimento_mm ? n0(car.comprimento_mm) : null, 'mm'),
        kv('Width', car.largura_mm ? n0(car.largura_mm) : null, 'mm'),
        kv('Height', car.altura_mm ? n0(car.altura_mm) : null, 'mm'),
        kv('Wheelbase', car.entre_eixos_mm ? n0(car.entre_eixos_mm) : null, 'mm'),
        kv('Ground clearance', car.altura_livre_solo_mm ? n0(car.altura_livre_solo_mm) : null, 'mm'),
        kv('Trunk', car.porta_malas_l ? n0(car.porta_malas_l) : null, 'L'),
        kv('Trunk (seats down)', car.porta_malas_max_l ? n0(car.porta_malas_max_l) : null, 'L'),
      ],
    },
    {
      title: 'Safety',
      items: [
        kv('Airbags', car.airbags),
        kv('Adaptive cruise', boolText(car.acc)),
        kv('City braking (AEB)', boolText(car.aeb)),
        kv('Lane keep', boolText(car.lka)),
        kv('Blind spot', boolText(car.bsm)),
        kv('Rear cross traffic', boolText(car.rcta)),
        kv('360° camera', boolText(car.camera_360)),
        kv('Parking sensors', boolText(car.sensor_estacionamento)),
        kv('Assist level', car.adas_nivel),
      ],
    },
    {
      title: 'Comfort & tech',
      items: [
        kv('Centre screen', car.tela_central_pol ? `${n1(car.tela_central_pol)}"` : null),
        kv('Digital cluster', car.cluster_digital_pol ? `${n1(car.cluster_digital_pol)}"` : null),
        kv('Apple CarPlay', car.carplay),
        kv('Android Auto', car.android_auto),
        kv('Wireless charging', boolText(car.carregador_sem_fio)),
        kv('Speakers', car.som_alto_falantes ? `${car.som_alto_falantes}${car.som_marca ? ` · ${car.som_marca}` : ''}` : null),
        kv('Climate', car.ar_condicionado),
        kv('Power seats', boolText(car.banco_eletrico)),
        kv('Ventilated seats', boolText(car.bancos_ventilados)),
        kv('Heated seats', boolText(car.bancos_aquecidos)),
        kv('Panoramic roof', boolText(car.teto_panoramico)),
        kv('Head-up display', boolText(car.head_up_display)),
      ],
    },
    {
      title: 'Warranty & ownership',
      items: [
        kv('Vehicle', car.garantia_veiculo_anos ? `${car.garantia_veiculo_anos} yr` : null,
          car.garantia_veiculo_km ? `· ${n0(car.garantia_veiculo_km)} km` : ''),
        kv('Battery', car.garantia_bateria_anos ? `${car.garantia_bateria_anos} yr` : null,
          car.garantia_bateria_km ? `· ${n0(car.garantia_bateria_km)} km` : ''),
        kv('Editorial rating', car.nota ? `${n1(car.nota)}/10` : null),
        kv('Spec fields verified', `${car.campos_preenchidos} of 61`),
      ],
    },
  ];

  const conf = car.confianca || 'baixa';
  const sourceList = [...new Set([car.fonte_principal, ...(car.fontes || [])].filter(Boolean))];

  const html = `
    <div class="sheet__head">
      <div>
        <p>${car.marca} · ${BODY_LABELS[car.carroceria] || car.carroceria || ''} ${car.motorizacao === 'PHEV' ? '· plug-in hybrid' : '· electric'}</p>
        <h2>${car.modelo}</h2>
        <p>${car.versao || car.versao_pesquisada || ''}</p>
      </div>
      <button class="close-btn" data-close aria-label="Close">×</button>
    </div>
    <div class="sheet__body">
      <div class="detail-hero">
        <div>
          <div class="detail-price">${money(car.preco_brl)} <small>${car.preco_brl ? 'list / campaign price' : 'no public price'}</small></div>
          <p class="hint" style="margin:6px 0 0">${car.observacao_preco || ''}</p>
        </div>
        <div style="text-align:right">
          <div class="ring" style="width:62px;height:62px;margin-left:auto">${ringSvg(s.score, 62)}</div>
          <span class="conf conf--${conf}"><i></i>${conf === 'alta' ? 'verified' : conf === 'media' ? 'partly verified' : 'thin sources'}</span>
        </div>
      </div>
      ${s
        ? `<section class="spec-section"><h4>How it scores with your weights</h4>
            <div class="breakdown">${CRITERIA.map((c) => {
              const value = Math.round((s.parts[c.key] || 0) * 100);
              const weight = Math.round(((state.weights[c.key] || 0) / (CRITERIA.reduce((sum, x) => sum + (state.weights[x.key] || 0), 0) || 1)) * 100);
              return `<div class="breakdown__row" title="${c.hint}">
                <span class="breakdown__label">${c.label}</span>
                <span class="breakdown__track"><span class="breakdown__fill" style="width:${value}%"></span></span>
                <span class="breakdown__value">${value}</span>
                <span class="breakdown__weight">${weight}%</span>
              </div>`;
            }).join('')}</div>
            <p class="hint" style="margin:8px 0 0">Each bar is how this car ranks against the other ${state.mode === 'BEV' ? 'electric' : 'plug-in'} cars on that criterion (0–100). The last column is how much weight you gave it.</p>
          </section>`
        : ''}
      ${groups
        .map(
          (g) => `<section class="spec-section"><h4>${g.title}</h4>
            <div class="kv-grid">${g.items.join('')}</div></section>`,
        )
        .join('')}
      ${car.alerta_versao ? `<div class="notes" style="background:#f8ecdf"><b>Version check</b>${car.alerta_versao}</div>` : ''}
      ${car.observacoes ? `<div class="notes"><b>What to know</b>${car.observacoes}</div>` : ''}
      ${sourceList.length
        ? `<div class="sources"><h4>Sources</h4>${sourceList
            .slice(0, 8)
            .map((u) => `<a href="${u}" target="_blank" rel="noopener">${u}</a>`)
            .join('')}</div>`
        : ''}
      <div style="margin-top:20px;display:flex;gap:8px">
        <button class="primary-btn" data-add="${car.id}" style="flex:1">${
          state.selected.includes(car.id) ? 'Remove from comparison' : 'Add to comparison'
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
}

function ringSvg(value, size = 54) {
  const stroke = size > 56 ? 4 : 3.5;
  const r = (size - stroke) / 2 - 1;
  const c = 2 * Math.PI * r;
  const pct = Math.max(0, Math.min(100, value)) / 100;
  const hue = value >= 60 ? 'var(--accent)' : value >= 50 ? '#c58b16' : '#b45a3c';
  return `<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
    <circle cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" stroke="var(--line)" stroke-width="${stroke}"/>
    <circle cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" stroke="${hue}" stroke-width="${stroke}"
      stroke-linecap="round" stroke-dasharray="${c * pct} ${c}" />
  </svg>
  <span class="ring__num">${Math.round(value)}<small>/100</small></span>`;
}

/* ---------- compare ---------- */

function openCompare() {
  const cars = state.selected.map((id) => state.cars.find((c) => c.id === id)).filter(Boolean);
  if (cars.length < 2) return;

  const rows = [
    { group: 'Price' },
    { label: 'List price', get: (c) => c.preco_brl, fmt: (v) => money(v), better: 'low' },
    { label: 'Per kWh', get: (c) => c.preco_kwh, fmt: (v) => money(v), better: 'low' },
    { label: 'Price per km of range', get: (c) => c.preco_por_km_autonomia, fmt: (v) => money2(v), better: 'low' },
    { label: 'vs. similar cars', get: (c) => state.scores.get(c.id)?.valueRatio, fmt: (v) => `${v > 1 ? '+' : '−'}${Math.abs((v - 1) * 100).toFixed(0)}%`, better: 'low' },
    { group: 'Battery & range' },
    { label: 'Battery (nominal)', get: (c) => c.bateria_kwh, fmt: (v) => `${kwh(v)} kWh`, better: 'high' },
    { label: 'Chemistry', get: (c) => c.quimica_bateria },
    { label: 'Range', get: (c) => c.autonomia_referencia_km, fmt: (v) => `${n0(v)} km`, better: 'high' },
    { label: 'Efficiency', get: (c) => c.eficiencia_km_kwh, fmt: (v) => `${n1(v)} km/kWh`, better: 'high' },
    { label: 'Consumption', get: (c) => c.consumo_calc_kwh_100km, fmt: (v) => `${n1(v)} kWh/100km`, better: 'low' },
    { group: 'Charging' },
    { label: 'DC peak', get: (c) => c.recarga_dc_kw, fmt: (v) => `${n0(v)} kW`, better: 'high' },
    { label: 'DC 10–80%', get: (c) => c.recarga_dc_10_80_min, fmt: (v) => `${n0(v)} min`, better: 'low' },
    { label: 'AC onboard', get: (c) => c.recarga_ac_kw, fmt: (v) => `${n1(v)} kW`, better: 'high' },
    { label: 'V2L', get: (c) => c.v2l, fmt: boolText },
    { label: 'Heat pump', get: (c) => c.bomba_de_calor, fmt: boolText },
    { group: 'Powertrain' },
    { label: 'Power', get: (c) => c.potencia_cv || c.potencia_combinada_cv, fmt: (v) => `${n0(v)} cv`, better: 'high' },
    { label: 'Torque', get: (c) => c.torque_nm || c.torque_combinado_nm, fmt: (v) => `${n0(v)} Nm`, better: 'high' },
    { label: 'Drivetrain', get: (c) => c.tracao },
    { label: '0–100 km/h', get: (c) => c.zero_a_cem_s, fmt: (v) => `${n1(v)} s`, better: 'low' },
    { label: 'Weight', get: (c) => c.peso_kg, fmt: (v) => `${n0(v)} kg`, better: 'low' },
    { label: 'Power-to-weight', get: (c) => c.cv_por_tonelada, fmt: (v) => `${n1(v)} cv/t`, better: 'high' },
    { group: 'Space' },
    { label: 'Body', get: (c) => BODY_LABELS[c.carroceria] || c.carroceria },
    { label: 'Seats', get: (c) => c.lugares, fmt: (v) => `${v}`, better: 'high' },
    { label: 'Length', get: (c) => c.comprimento_mm, fmt: (v) => `${n0(v)} mm`, better: 'high' },
    { label: 'Wheelbase', get: (c) => c.entre_eixos_mm, fmt: (v) => `${n0(v)} mm`, better: 'high' },
    { label: 'Ground clearance', get: (c) => c.altura_livre_solo_mm, fmt: (v) => `${n0(v)} mm`, better: 'high' },
    { label: 'Trunk', get: (c) => c.porta_malas_l, fmt: (v) => `${n0(v)} L`, better: 'high' },
    { group: 'Safety' },
    { label: 'Airbags', get: (c) => c.airbags, fmt: (v) => `${v}`, better: 'high' },
    { label: 'Adaptive cruise', get: (c) => c.acc, fmt: boolText },
    { label: 'City braking (AEB)', get: (c) => c.aeb, fmt: boolText },
    { label: 'Lane keep', get: (c) => c.lka, fmt: boolText },
    { label: 'Blind spot', get: (c) => c.bsm, fmt: boolText },
    { label: '360° camera', get: (c) => c.camera_360, fmt: boolText },
    { label: 'Parking sensors', get: (c) => c.sensor_estacionamento, fmt: boolText },
    { group: 'Comfort & tech' },
    { label: 'Centre screen', get: (c) => c.tela_central_pol, fmt: (v) => `${n1(v)}"`, better: 'high' },
    { label: 'CarPlay', get: (c) => c.carplay },
    { label: 'Android Auto', get: (c) => c.android_auto },
    { label: 'Wireless charging', get: (c) => c.carregador_sem_fio, fmt: boolText },
    { label: 'Speakers', get: (c) => c.som_alto_falantes, fmt: (v) => `${v}`, better: 'high' },
    { label: 'Climate', get: (c) => c.ar_condicionado },
    { label: 'Panoramic roof', get: (c) => c.teto_panoramico, fmt: boolText },
    { label: 'Ventilated seats', get: (c) => c.bancos_ventilados, fmt: boolText },
    { group: 'Ownership' },
    { label: 'Battery warranty', get: (c) => c.garantia_bateria_anos ? `${c.garantia_bateria_anos} yr / ${c.garantia_bateria_km ? n0(c.garantia_bateria_km) + ' km' : '—'}` : null },
    { label: 'Editorial rating', get: (c) => c.nota, fmt: (v) => `${n1(v)}/10`, better: 'high' },
    { label: 'Your score', get: (c) => state.scores.get(c.id)?.score, fmt: (v) => `${Math.round(v)}/100`, better: 'high' },
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
          data-unpick="${c.id}" aria-label="Remove">×</button>
      </th>`,
    )
    .join('');

  const html = `
    <div class="sheet__head">
      <div>
        <p>Side by side · best value in each row is marked</p>
        <h2>${cars.length} cars compared</h2>
      </div>
      <button class="close-btn" data-close aria-label="Close">×</button>
    </div>
    <div class="sheet__body">
      <div class="cmp-wrap">
        <table class="cmp">
          <thead><tr><th>Specification</th>${heads}</tr></thead>
          <tbody>${body}</tbody>
        </table>
      </div>
      <p class="hint" style="margin-top:12px">
        ◆ marks the best value in a row. A dash means no source we could verify published that number for this version —
        it is unknown, not zero.
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

/* ---------- export ---------- */

function exportCsv() {
  const cars = sortedCars(currentCars());
  const columns = [
    ['Marca', (c) => c.marca],
    ['Modelo', (c) => c.modelo],
    ['Versao', (c) => c.versao],
    ['Motorizacao', (c) => c.motorizacao],
    ['Preco R$', (c) => c.preco_brl],
    ['Score', (c) => Math.round(score(c))],
    ['Bateria kWh', (c) => c.bateria_kwh],
    ['Autonomia km', (c) => c.autonomia_referencia_km],
    ['km/kWh', (c) => c.eficiencia_km_kwh],
    ['Potencia cv', (c) => c.potencia_cv || c.potencia_combinada_cv],
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
    ['Camera 360', (c) => c.camera_360],
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
    if (typeof value === 'boolean') return value ? 'sim' : 'nao';
    if (typeof value === 'number') return String(Math.round(value * 100) / 100).replace('.', ',');
    return `"${String(value).replace(/"/g, '""')}"`;
  };

  const lines = [columns.map(([name]) => `"${name}"`).join(';')];
  for (const car of cars) lines.push(columns.map(([, get]) => cell(get(car))).join(';'));

  const blob = new Blob([`\ufeff${lines.join('\r\n')}`], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `carros-${state.mode.toLowerCase()}-${new Date().toISOString().slice(0, 10)}.csv`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

/* ---------- boot ---------- */

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
    `Prices ${state.meta.preco_referencia} · ${bev} electric + ${phev} plug-in hybrid · ${state.cars.length} versions`;

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
    `<div class="empty"><b>Could not load the dataset</b>${error.message}<br>Run a local server in this folder: <code>python3 -m http.server 8000</code></div>`;
});
