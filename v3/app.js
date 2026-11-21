/* Carros Verdes — V3. Lista ordenada, ficha enxuta e detalhe técnico sob demanda. */

const USE_WEIGHTS = {
  city: { price: 0.3, cost: 0.25, range: 0.1, charging: 0.1, space: 0.25 },
  mixed: { price: 0.25, cost: 0.2, range: 0.25, charging: 0.15, space: 0.15 },
  road: { price: 0.2, cost: 0.15, range: 0.3, charging: 0.25, space: 0.1 },
};

const WHY_TEXT = {
  price: 'custa menos que a maioria',
  cost: 'gasta pouco por quilômetro',
  range: 'anda bastante com uma carga',
  charging: 'recarrega rápido na estrada',
  space: 'é espaçoso para a família',
};

const COST = {
  kmYear: 15000,
  homePrice: 0.95,
  publicPrice: 1.8,
  homeShare: 80,
  fuelPrice: 6.1,
  fuelKmPerL: 10,
  maintenance: 1800,
  electricSharePhev: 70,
};

const state = {
  cars: [],
  photos: {},
  budget: 'any',
  use: 'mixed',
  shown: 12,
  ranked: [],
};

const moneyFmt = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 });
const money2Fmt = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', minimumFractionDigits: 2, maximumFractionDigits: 2 });
const n0Fmt = new Intl.NumberFormat('pt-BR');
const n1Fmt = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

const money = (v) => (v == null ? '—' : moneyFmt.format(v));
const money2 = (v) => (v == null ? '—' : money2Fmt.format(v));
const n0 = (v) => (v == null ? '—' : n0Fmt.format(v));
const n1 = (v) => (v == null ? '—' : n1Fmt.format(v));

/* ---------- custo ---------- */

function energyKwh100(car) {
  if (car.consumo_calc_kwh_100km) return car.consumo_calc_kwh_100km;
  if (car.eficiencia_km_kwh) return 100 / car.eficiencia_km_kwh;
  return null;
}

function blendedKwh() {
  const share = COST.homeShare / 100;
  return share * COST.homePrice + (1 - share) * COST.publicPrice;
}

function costPer100(car) {
  const mix = blendedKwh();
  const kwh100 = energyKwh100(car);
  if (car.motorizacao === 'BEV') return kwh100 == null ? null : kwh100 * mix;
  const electricShare = COST.electricSharePhev / 100;
  const electric = kwh100 == null ? null : kwh100 * mix * electricShare;
  const fuel = car.consumo_l_100km == null ? null : (car.consumo_l_100km / 100) * COST.fuelPrice * (1 - electricShare);
  if (electric == null && fuel == null) return null;
  return (electric || 0) + (fuel || 0);
}

function energyMonth(car) {
  const per100 = costPer100(car);
  if (per100 == null) return null;
  return (per100 / 100) * (COST.kmYear / 12);
}

function monthTotal(car) {
  const energy = energyMonth(car);
  if (energy == null) return null;
  return energy + COST.maintenance / 12;
}

function petrolMonth() {
  return ((COST.kmYear / 12) / COST.fuelKmPerL) * COST.fuelPrice;
}

/* ---------- notas ---------- */

function percentileMap(cars, getter) {
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

function rankCars() {
  const budget = state.budget === 'any' ? Infinity : Number(state.budget);
  const pool = state.cars.filter((c) => c.preco_brl != null && c.preco_brl <= budget);

  const pPrice = percentileMap(pool, (c) => c.preco_brl);
  const pCost = percentileMap(pool, costPer100);
  const pRange = percentileMap(pool, (c) => c.autonomia_referencia_km);
  const pDc = percentileMap(pool, (c) => c.recarga_dc_kw);
  const pDcTime = percentileMap(pool, (c) => c.recarga_dc_10_80_min);
  const pTrunk = percentileMap(pool, (c) => c.porta_malas_l);

  const weights = USE_WEIGHTS[state.use];

  state.ranked = pool
    .map((car) => {
      const parts = {
        price: pPrice.has(car.id) ? 1 - pPrice.get(car.id) : 0.35,
        cost: pCost.has(car.id) ? 1 - pCost.get(car.id) : 0.35,
        range: pRange.get(car.id) ?? 0.35,
        charging: 0.6 * (pDc.get(car.id) ?? 0.35) + 0.4 * (pDcTime.has(car.id) ? 1 - pDcTime.get(car.id) : 0.35),
        space: pTrunk.get(car.id) ?? 0.35,
      };
      const score = Object.keys(parts).reduce((sum, key) => sum + parts[key] * (weights[key] || 0), 0) * 100;
      const contributions = Object.keys(parts)
        .map((key) => ({ key, value: parts[key] * (weights[key] || 0) }))
        .sort((a, b) => b.value - a.value)
        .slice(0, 2)
        .map((x) => WHY_TEXT[x.key]);
      return { car, score, parts, contributions };
    })
    .sort((a, b) => b.score - a.score || (a.car.preco_brl || 0) - (b.car.preco_brl || 0));
}

/* ---------- fotos ---------- */

function photoFor(car) {
  return state.photos[`${car.marca} ${car.modelo}`] || null;
}

function placeholderSvg(carroceria) {
  const label = { hatch: 'hatch', sedan: 'sedã', suv: 'SUV', picape: 'picape', minivan: 'minivan', cupe: 'cupê', perua: 'perua' }[carroceria] || 'carro';
  const tall = carroceria === 'suv' || carroceria === 'minivan' || carroceria === 'picape';
  const bodyY = tall ? 78 : 86;
  const bodyH = tall ? 52 : 46;
  const cabin = tall
    ? 'M 88 81 Q 102 44 138 42 L 190 42 Q 216 46 226 81 Z'
    : 'M 94 89 Q 108 58 140 56 L 186 56 Q 212 60 222 89 Z';
  const wheelY = tall ? 130 : 132;
  return `<svg viewBox="0 0 320 180" role="img" aria-label="Ilustração de um ${label}" preserveAspectRatio="xMidYMid slice">
    <rect width="320" height="180" fill="#efeee6"/>
    <line x1="20" y1="${wheelY + 18}" x2="300" y2="${wheelY + 18}" stroke="#dcd8ca" stroke-width="4"/>
    <path d="${cabin}" fill="#dcefe8" stroke="#0d6b57" stroke-width="4" stroke-linejoin="round"/>
    <rect x="34" y="${bodyY}" width="252" height="${bodyH}" rx="16" fill="#dcefe8" stroke="#0d6b57" stroke-width="4"/>
    <circle cx="96" cy="${wheelY}" r="20" fill="#3a382f"/><circle cx="96" cy="${wheelY}" r="9" fill="#efeee6"/>
    <circle cx="224" cy="${wheelY}" r="20" fill="#3a382f"/><circle cx="224" cy="${wheelY}" r="9" fill="#efeee6"/>
  </svg>`;
}

/* ---------- textos ---------- */

function rangePhrase(km) {
  if (km == null) return 'Não temos a autonomia publicada para esta versão.';
  if (km < 250) return 'Dá para o dia a dia na cidade, com recarga durante a semana. Viagens longas vão exigir mais paradas.';
  if (km < 400) return 'Cobre bem o dia a dia e viagens de fim de semana. Em viagens longas, programe uma parada.';
  return 'Sobra folga para viajar: dá para encarar trechos longos com poucas paradas.';
}

function chargeText(car) {
  const parts = [];
  if (car.recarga_dc_10_80_min) parts.push(`Na estrada, ${n0(car.recarga_dc_10_80_min)} minutos para ir de 10% a 80%.`);
  else if (car.recarga_dc_kw) parts.push(`Tem recarga rápida de ${n0(car.recarga_dc_kw)} kW, mas o tempo de 10% a 80% não foi publicado.`);
  else parts.push('Não há recarga rápida publicada para esta versão.');
  if (car.recarga_ac_kw) parts.push(`Em casa, ${n1(car.recarga_ac_kw)} kW — a bateria cheia durante a noite.`);
  else parts.push('Em casa, carrega na tomada comum, geralmente durante a noite.');
  return parts.join(' ');
}

function trunkText(car) {
  if (car.porta_malas_l == null) return 'Porta-malas não publicado para esta versão.';
  const seats = car.lugares ? ` Tem ${car.lugares} lugares.` : '';
  const extra = car.porta_malas_l >= 500 ? ' Espaço de sobra para bagagem de família.' : car.porta_malas_l >= 350 ? ' Cabe a mala de uma viagem de quatro pessoas.' : ' É compacto: bom para a cidade, apertado em viagem longa.';
  return `Porta-malas de ${n0(car.porta_malas_l)} litros.${extra}${seats}`;
}

function warrantyText(car) {
  if (!car.garantia_bateria_anos) return 'Garantia da bateria não publicada para esta versão.';
  const km = car.garantia_bateria_km ? ` ou ${n0(car.garantia_bateria_km)} km` : '';
  return `Bateria garantida por ${car.garantia_bateria_anos} anos${km}.`;
}

/* ---------- lista ---------- */

function carCard(entry, index) {
  const car = entry.car;
  const photo = photoFor(car);
  const monthly = monthTotal(car);
  const km = car.autonomia_referencia_km;
  const dcText = car.recarga_dc_10_80_min ? `${n0(car.recarga_dc_10_80_min)} min` : '—';
  const dcLabel = car.recarga_dc_10_80_min ? 'recarga rápida (10% a 80%)' : 'tempo de recarga não publicado';
  const trunk = car.porta_malas_l ? `${n0(car.porta_malas_l)} L` : '—';

  const media = photo
    ? `<img src="${photo.arquivo}" alt="${car.marca} ${car.modelo}" loading="lazy" width="560" height="420">
       <a class="car__credit" href="${photo.origem}" target="_blank" rel="noopener" title="Foto ilustrativa do modelo, de acervo público">Foto: ${photo.autor} · ${photo.licenca} · imagem ilustrativa</a>`
    : `${placeholderSvg(car.carroceria)}<span class="car__credit car__credit--plain">Sem foto licenciada · ilustração do tipo de carro</span>`;

  return `<article class="car">
    <div class="car__media">${media}</div>
    <div class="car__body">
      <p class="car__rank">${index + 1}º para você</p>
      <h3 class="car__name">${car.marca} ${car.modelo}</h3>
      <p class="car__version">${car.versao || car.versao_pesquisada || ''}</p>
      <p class="car__price">${money(car.preco_brl)} à vista</p>
      <p class="car__month"><strong>${money(monthly)}</strong> por mês rodando ${n0(COST.kmYear)} km/ano</p>
      <p class="car__hint">Esse valor é energia mais manutenção. Não inclui seguro nem as parcelas do carro.</p>
      <ul class="car__facts">
        <li><b>${km ? `${n0(km)} km` : '—'}</b><span>com uma carga</span></li>
        <li><b>${dcText}</b><span>${dcLabel}</span></li>
        <li><b>${trunk}</b><span>porta-malas</span></li>
      </ul>
      <p class="car__why">Por que aparece aqui: ${entry.contributions.join(' e ')}.</p>
      <div class="car__actions">
        <button class="btn" data-understand="${car.id}">Entender este carro</button>
        <button class="btn btn--quiet" data-specs="${car.id}">Ficha técnica</button>
      </div>
    </div>
  </article>`;
}

function renderResults() {
  const list = document.getElementById('list');
  const note = document.getElementById('results-note');
  const more = document.getElementById('more');

  if (!state.ranked.length) {
    list.innerHTML = `<div class="empty"><b>Nenhum carro nessa faixa de preço.</b><br>Experimente “Até R$ 400 mil” ou “Qualquer preço”.</div>`;
    note.textContent = 'Nenhum carro atende à faixa escolhida.';
    more.hidden = true;
    return;
  }

  const total = state.ranked.length;
  const visible = state.ranked.slice(0, state.shown);

  list.innerHTML = visible.map(carCard).join('');
  note.textContent = state.budget === 'any'
    ? `${total} carros à venda no Brasil, do mais indicado para o seu uso ao menos indicado.`
    : `${total} carros dentro da sua faixa de preço, do mais indicado para o seu uso ao menos indicado.`;

  more.hidden = visible.length >= total;
  more.textContent = `Ver mais carros (${Math.min(12, total - visible.length)} de ${total - visible.length} restantes)`;

  list.querySelectorAll('[data-understand]').forEach((btn) => {
    btn.addEventListener('click', () => openUnderstand(btn.dataset.understand));
  });
  list.querySelectorAll('[data-specs]').forEach((btn) => {
    btn.addEventListener('click', () => openSpecs(btn.dataset.specs));
  });
}

/* ---------- painéis ---------- */

const overlay = document.getElementById('overlay');
const sheet = document.getElementById('sheet');

function openSheet(html) {
  sheet.innerHTML = html;
  overlay.hidden = false;
  document.body.style.overflow = 'hidden';
  sheet.scrollTop = 0;
  sheet.querySelectorAll('[data-close]').forEach((el) => el.addEventListener('click', closeSheet));
  const closeBtn = sheet.querySelector('.sheet__close');
  if (closeBtn) closeBtn.focus();
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

function header(car, subtitle) {
  return `<div class="sheet__head">
    <div>
      <h2 id="sheet-title">${car.marca} ${car.modelo}</h2>
      <p>${subtitle}</p>
    </div>
    <button class="sheet__close" data-close aria-label="Fechar">×</button>
  </div>`;
}

function openUnderstand(id) {
  const car = state.cars.find((c) => c.id === id);
  if (!car) return;

  const monthly = monthTotal(car);
  const energy = energyMonth(car);
  const petrol = petrolMonth();
  const saving = energy == null ? null : petrol - energy;
  const per100 = costPer100(car);
  const isPhev = car.motorizacao === 'PHEV';

  const blocks = [
    `<section class="block">
      <h3>Quanto custa rodar</h3>
      <p class="big">${money(monthly)} por mês</p>
      <p>São ${per100 == null ? '—' : money2(per100 / 100)} por quilômetro, em ${n0(COST.kmYear)} km por ano. A conta inclui energia${isPhev ? ' (e combustível, porque é híbrido plug-in)' : ''} e manutenção com pneus. Não inclui seguro, desvalorização do carro e pedágio.</p>
      ${saving == null ? '' : `<p>Num carro a gasolina que faz ${COST.fuelKmPerL} km por litro, você gastaria cerca de ${money(petrol)} por mês só de combustível. Aqui, a economia fica em torno de <b>${money(saving)} por mês</b>.</p>`}
    </section>`,
    `<section class="block">
      <h3>Quantos quilômetros com uma carga</h3>
      <p class="big">${car.autonomia_referencia_km ? `${n0(car.autonomia_referencia_km)} km` : 'Não publicado'}</p>
      <p>${rangePhrase(car.autonomia_referencia_km)}</p>
    </section>`,
    `<section class="block">
      <h3>Quanto tempo para carregar</h3>
      <p>${chargeText(car)}</p>
    </section>`,
    `<section class="block">
      <h3>Espaço e família</h3>
      <p>${trunkText(car)}</p>
    </section>`,
    `<section class="block">
      <h3>Garantia da bateria</h3>
      <p>${warrantyText(car)}</p>
    </section>`,
  ];

  if (car.observacoes) {
    blocks.push(`<section class="block">
      <h3>Vale saber</h3>
      <p>${car.observacoes}</p>
    </section>`);
  }

  openSheet(`${header(car, `${car.versao || car.versao_pesquisada || ''} · ${isPhev ? 'híbrido plug-in flex' : '100% elétrico'}`)}
    <div class="sheet__body">
      ${blocks.join('')}
      <section class="block">
        <h3>Quer os números todos?</h3>
        <p>Toque em “Ficha técnica” na lista, ou abra o <a href="../">comparador completo</a> para ver todas as versões lado a lado.</p>
      </section>
    </div>`);
}

function openSpecs(id) {
  const car = state.cars.find((c) => c.id === id);
  if (!car) return;

  const groups = [
    ['Preço', [
      ['Preço de tabela', money(car.preco_brl)],
      ['Preço por kWh de bateria', car.preco_kwh ? money(car.preco_kwh) : null],
    ]],
    ['Bateria e autonomia', [
      ['Bateria (capacidade)', car.bateria_kwh ? `${n1(car.bateria_kwh)} kWh` : null],
      ['Autonomia (Inmetro)', car.autonomia_referencia_km ? `${n0(car.autonomia_referencia_km)} km` : null],
      ['Autonomia (WLTP)', car.autonomia_wltp_km ? `${n0(car.autonomia_wltp_km)} km` : null],
      ['Consumo', car.consumo_calc_kwh_100km ? `${n1(car.consumo_calc_kwh_100km)} kWh/100 km` : null],
      ['Química da bateria', car.quimica_bateria],
    ]],
    car.motorizacao === 'PHEV'
      ? ['Motor e combustível', [
          ['Potência combinada', car.potencia_combinada_cv ? `${n0(car.potencia_combinada_cv)} cv` : null],
          ['Motor a combustão', car.motor_termico],
          ['Combustível', car.combustivel],
          ['Tanque', car.tanque_l ? `${n1(car.tanque_l)} L` : null],
          ['Consumo de combustível', car.consumo_l_100km ? `${n1(car.consumo_l_100km)} L/100 km` : null],
          ['Câmbio', car.cambio],
        ]]
      : ['Motor e desempenho', [
          ['Potência', car.potencia_cv ? `${n0(car.potencia_cv)} cv` : null],
          ['Torque', car.torque_nm ? `${n0(car.torque_nm)} Nm` : null],
          ['Tração', car.tracao],
          ['0 a 100 km/h', car.zero_a_cem_s ? `${n1(car.zero_a_cem_s)} s` : null],
          ['Velocidade máxima', car.velocidade_max_kmh ? `${n0(car.velocidade_max_kmh)} km/h` : null],
        ]],
    ['Recarga', [
      ['Recarga rápida (DC)', car.recarga_dc_kw ? `${n0(car.recarga_dc_kw)} kW` : null],
      ['Tempo de 10% a 80%', car.recarga_dc_10_80_min ? `${n0(car.recarga_dc_10_80_min)} min` : null],
      ['Recarga em casa (AC)', car.recarga_ac_kw ? `${n1(car.recarga_ac_kw)} kW` : null],
      ['Conector', car.conector_dc],
      ['Tomada de força (V2L)', car.v2l === true ? 'Sim' : car.v2l === false ? 'Não' : null],
    ]],
    ['Tamanho e espaço', [
      ['Carroceria', car.carroceria ? car.carroceria.charAt(0).toUpperCase() + car.carroceria.slice(1) : null],
      ['Lugares', car.lugares ? `${car.lugares}` : null],
      ['Porta-malas', car.porta_malas_l ? `${n0(car.porta_malas_l)} L` : null],
      ['Comprimento', car.comprimento_mm ? `${n0(car.comprimento_mm)} mm` : null],
      ['Entre-eixos', car.entre_eixos_mm ? `${n0(car.entre_eixos_mm)} mm` : null],
      ['Peso', car.peso_kg ? `${n0(car.peso_kg)} kg` : null],
    ]],
    ['Segurança e conforto', [
      ['Airbags', car.airbags ? `${car.airbags}` : null],
      ['Piloto adaptativo (ACC)', car.acc === true ? 'Sim' : car.acc === false ? 'Não' : null],
      ['Freagem automática (AEB)', car.aeb === true ? 'Sim' : car.aeb === false ? 'Não' : null],
      ['Câmera 360°', car.camera_360 === true ? 'Sim' : car.camera_360 === false ? 'Não' : null],
      ['Central multimídia', car.tela_central_pol ? `${n1(car.tela_central_pol)} polegadas` : null],
      ['Apple CarPlay', car.carplay],
      ['Android Auto', car.android_auto],
      ['Ar-condicionado', car.ar_condicionado],
    ]],
    ['Garantia', [
      ['Garantia do carro', car.garantia_veiculo_anos ? `${car.garantia_veiculo_anos} anos${car.garantia_veiculo_km ? ` ou ${n0(car.garantia_veiculo_km)} km` : ''}` : null],
      ['Garantia da bateria', car.garantia_bateria_anos ? `${car.garantia_bateria_anos} anos${car.garantia_bateria_km ? ` ou ${n0(car.garantia_bateria_km)} km` : ''}` : null],
    ]],
  ];

  const rows = groups
    .map(([title, items]) => {
      const body = items
        .map(([label, value]) => `<tr><th>${label}</th><td class="${value == null || value === '' ? 'is-empty' : ''}">${value == null || value === '' ? 'não publicado' : value}</td></tr>`)
        .join('');
      return `<tr class="group"><th colspan="2">${title}</th></tr>${body}`;
    })
    .join('');

  openSheet(`${header(car, `${car.versao || car.versao_pesquisada || ''} · ficha técnica`)}
    <div class="sheet__body">
      <table class="specs">
        <caption class="sr-only">Ficha técnica de ${car.marca} ${car.modelo}</caption>
        <tbody>${rows}</tbody>
      </table>
      <section class="block">
        <h3>Sobre os dados</h3>
        <p>Preços de 20/11/2026. “Não publicado” significa que nenhuma fonte verificável publicou o número para esta versão — não é zero.</p>
      </section>
    </div>`);
}

/* ---------- controles ---------- */

function bindPills(containerId, attr, onPick) {
  const container = document.getElementById(containerId);
  container.addEventListener('click', (event) => {
    const btn = event.target.closest(`button[data-${attr}]`);
    if (!btn) return;
    container.querySelectorAll('button').forEach((b) => {
      const on = b === btn;
      b.classList.toggle('is-on', on);
      b.setAttribute('aria-pressed', String(on));
    });
    onPick(btn.dataset[attr]);
  });
}

function restart() {
  state.budget = 'any';
  state.use = 'mixed';
  state.shown = 12;
  document.querySelectorAll('#f-budget button, #f-use button').forEach((b) => {
    const isBudgetDefault = b.dataset.budget === 'any';
    const isUseDefault = b.dataset.use === 'mixed';
    const on = isBudgetDefault || isUseDefault;
    b.classList.toggle('is-on', on);
    b.setAttribute('aria-pressed', String(on));
  });
  rankCars();
  renderResults();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

/* ---------- início ---------- */

async function boot() {
  const [carsResponse, photosResponse] = await Promise.all([
    fetch('../data/cars.json'),
    fetch('photos/credits.json'),
  ]);
  const payload = await carsResponse.json();
  state.cars = payload.cars;

  const credits = await photosResponse.json();
  Object.values(credits).forEach((entry) => {
    entry.modelos.forEach((key) => {
      state.photos[key] = entry;
    });
  });

  bindPills('f-budget', 'budget', (value) => {
    state.budget = value;
    state.shown = 12;
    rankCars();
    renderResults();
  });

  bindPills('f-use', 'use', (value) => {
    state.use = value;
    rankCars();
    renderResults();
  });

  document.getElementById('more').addEventListener('click', () => {
    state.shown += 12;
    renderResults();
  });

  document.getElementById('restart').addEventListener('click', restart);

  rankCars();
  renderResults();
}

boot().catch((error) => {
  document.getElementById('list').innerHTML =
    `<div class="empty"><b>Não foi possível carregar os dados.</b><br>${error.message}<br>Rode um servidor local na pasta do projeto.</div>`;
  document.getElementById('results-note').textContent = '';
});
