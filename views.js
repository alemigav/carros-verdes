/* Carros Verdes — renderizadores das visões (cartões, tabela, gráficos, custo de rodagem). */

function kwh(v) {
  if (v == null) return null;
  return Number.isInteger(v) ? n0(v) : n1(v);
}

function energyKwh100(car) {
  if (car.consumo_calc_kwh_100km) return car.consumo_calc_kwh_100km;
  if (car.eficiencia_km_kwh) return 100 / car.eficiencia_km_kwh;
  return null;
}

function blendedKwh() {
  const { homeShare, homePrice, publicPrice } = state.cost;
  const share = Math.min(100, Math.max(0, homeShare)) / 100;
  return share * homePrice + (1 - share) * publicPrice;
}

function cost100(car) {
  const mix = blendedKwh();
  const kwh100 = energyKwh100(car);
  if (car.motorizacao === 'BEV') return kwh100 == null ? null : kwh100 * mix;
  const electricShare = Math.min(100, Math.max(0, state.cost.electricShare)) / 100;
  const fuel100 = car.consumo_l_100km;
  const electric = kwh100 == null ? null : kwh100 * mix * electricShare;
  const fuel = fuel100 == null ? null : (fuel100 / 100) * state.cost.fuelPrice * (1 - electricShare);
  if (electric == null && fuel == null) return null;
  return (electric || 0) + (fuel || 0);
}

function annualCost(car) {
  const per100 = cost100(car);
  if (per100 == null) return null;
  return (per100 / 100) * state.cost.kmYear + state.cost.maintenance;
}

/* ---------- cartões ---------- */

function carBadges(car) {
  const badges = [];
  const body = BODY_LABELS[car.carroceria];
  if (body) badges.push({ text: body });
  if (car.tracao === 'AWD') badges.push({ text: 'AWD', cls: 'badge--accent' });
  if ((car.lugares || 0) >= 7) badges.push({ text: '7 lugares' });
  if (car.v2l === true) badges.push({ text: 'V2L' });
  if (car.bomba_de_calor === true) badges.push({ text: 'Bomba de calor' });
  if (car.nota >= 8.4) badges.push({ text: `★ ${n1(car.nota)}` });
  if (car.alerta_versao) badges.push({ text: 'conferir versão', cls: 'badge--warn' });
  if ((car.campos_preenchidos || 0) < 14) badges.push({ text: 'poucos dados', cls: 'badge--thin' });
  return badges
    .map((b) => `<span class="badge ${b.cls || ''}">${b.text}</span>`)
    .join('');
}

function stat(label, value, unit) {
  const missing = value == null;
  const shown = missing ? '—' : typeof value === 'string' ? value : n0(value);
  return `<div class="stat">
    <span class="stat__label">${label}</span>
    <span class="stat__value${missing ? ' is-missing' : ''}">${shown}${unit && !missing ? `<small>${unit}</small>` : ''}</span>
  </div>`;
}

function verdictBanner(cars) {
  if (state.sort !== 'score' || cars.length < 4) return '';
  const top = cars[0];
  const s = state.scores.get(top.id);
  if (!s) return '';

  const PHRASES = {
    value: 'custo-benefício',
    range: 'autonomia',
    efficiency: 'eficiência',
    charging: 'recarga rápida',
    performance: 'desempenho',
    space: 'espaço',
    safety: 'auxílios de direção',
    comfort: 'equipamentos',
  };
  const strengths = CRITERIA.map((c) => ({
    key: c.key,
    contribution: (s.parts[c.key] || 0) * (state.weights[c.key] || 0),
  }))
    .sort((a, b) => b.contribution - a.contribution)
    .slice(0, 2)
    .map((x) => PHRASES[x.key]);

  const ratio = s.valueRatio;
  const priceLine = ratio == null
    ? ''
    : ratio < 1
      ? ` e custa ${Math.abs((ratio - 1) * 100).toFixed(0)}% menos que carros com bateria e potência parecidas`
      : ` embora custe ${Math.abs((ratio - 1) * 100).toFixed(0)}% mais que carros com bateria e potência parecidas`;

  const priced = cars.filter((c) => c.preco_por_km_autonomia);
  const cheapestKm = priced.length ? priced.reduce((a, b) => (a.preco_por_km_autonomia < b.preco_por_km_autonomia ? a : b)) : null;
  const longest = cars.reduce((a, b) => ((a.autonomia_referencia_km || 0) > (b.autonomia_referencia_km || 0) ? a : b));
  const bestValue = cars
    .map((c) => ({ car: c, r: state.scores.get(c.id)?.valueRatio }))
    .filter((x) => x.r != null)
    .sort((a, b) => a.r - b.r)[0];

  const facts = [
    longest?.autonomia_referencia_km
      ? { label: 'Maior autonomia', value: `${longest.marca} ${longest.modelo}`, extra: `${n0(longest.autonomia_referencia_km)} km` }
      : null,
    cheapestKm
      ? { label: 'Km mais barato', value: `${cheapestKm.marca} ${cheapestKm.modelo}`, extra: `${money(cheapestKm.preco_por_km_autonomia)}/km` }
      : null,
    bestValue
      ? { label: 'Mais barato que os parecidos', value: `${bestValue.car.marca} ${bestValue.car.modelo}`, extra: `${bestValue.r > 1 ? '+' : '−'}${Math.abs((bestValue.r - 1) * 100).toFixed(0)}%` }
      : null,
  ].filter(Boolean);

  return `<div class="verdict">
    <div class="verdict__main">
      <span class="verdict__eyebrow">Topo da sua lista</span>
      <div class="verdict__pick">
        <button class="verdict__name" data-detail="${top.id}">${top.marca} ${top.modelo}</button>
        <span class="verdict__price">${money(top.preco_brl)} · ${Math.round(s.score)}/100</span>
      </div>
      <p>Lidera em ${strengths.join(' e ')}${priceLine}.</p>
    </div>
    <div class="verdict__facts">
      ${facts
        .map(
          (f) => `<div class="verdict__fact"><span>${f.label}</span><b>${f.value}</b><em>${f.extra}</em></div>`,
        )
        .join('')}
    </div>
  </div>`;
}

function renderCards(input) {
  const host = document.getElementById('view-cards');
  const cars = input || sortedCars(currentCars());
  if (!cars.length) {
    host.innerHTML = `<div class="empty"><b>Nenhum carro corresponde a esses filtros</b>Afrouxe um filtro à esquerda ou limpe tudo.<div class="empty__actions"><button class="ghost-btn" data-empty-reset>Limpar filtros</button></div></div>`;
    host.querySelector('[data-empty-reset]')?.addEventListener('click', resetFilters);
    return;
  }
  const selected = new Set(state.selected);
  host.innerHTML = `${verdictBanner(cars)}<div class="cards">${cars
    .map((car, index) => {
      const s = state.scores.get(car.id);
      const ratio = s?.valueRatio;
      const delta = ratio == null
        ? ''
        : `<em class="${ratio < 1 ? '' : 'is-penalty'}">${ratio < 1 ? '−' : '+'}${Math.abs((ratio - 1) * 100).toFixed(0)}% vs. parecidos</em>`;
      const per100 = cost100(car);
      const monthly = annualCost(car) == null ? null : annualCost(car) / 12;
      const running = per100 == null
        ? ''
        : `<div class="card__note" title="Energia (e combustível, nos híbridos plug-in) mais manutenção">Rodar ≈ ${money2(per100)}/100 km · ${money(monthly)}/mês a ${n0(state.cost.kmYear)} km/ano</div>`;

      return `<article class="card ${selected.has(car.id) ? 'is-selected' : ''}" data-id="${car.id}">
        <div class="card__top">
          <div class="card__ident">
            <span class="card__rank">#${index + 1}</span>
            <div class="card__brand">${car.marca}</div>
            <div class="card__model">${car.modelo}</div>
            <div class="card__version">${car.versao || car.versao_pesquisada || ''}</div>
          </div>
          <div class="ring">${ringSvg(s?.score || 0)}</div>
        </div>
        <div class="card__price">
          <strong>${money(car.preco_brl)}</strong>
          <span>${car.bateria_kwh ? `${kwh(car.bateria_kwh)} kWh` : ''}</span>
          ${delta}
        </div>
        <div class="card__stats">
          ${stat(car.motorizacao === 'PHEV' ? 'Autonomia elétrica' : 'Autonomia', car.autonomia_referencia_km, 'km')}
          ${stat('Potência', car.potencia_cv || car.potencia_combinada_cv, 'cv')}
          ${stat('DC', car.recarga_dc_kw, 'kW')}
          ${stat('0–100', car.zero_a_cem_s ? n1(car.zero_a_cem_s) : null, 's')}
        </div>
        <div class="card__badges">${carBadges(car)}</div>
        ${running}
        <div class="card__actions">
          <button class="ghost-btn" data-detail="${car.id}">Detalhes</button>
          <button class="pick ${selected.has(car.id) ? 'is-on' : ''}" data-pick="${car.id}">${
            selected.has(car.id) ? 'Selecionado' : 'Comparar'
          }</button>
        </div>
      </article>`;
    })
    .join('')}</div>`;

  host.querySelectorAll('[data-pick]').forEach((btn) => {
    btn.addEventListener('click', () => selectCar(btn.dataset.pick));
  });
  host.querySelectorAll('[data-detail]').forEach((btn) => {
    btn.addEventListener('click', () => openDetail(btn.dataset.detail));
  });
}

/* ---------- tabela ---------- */

function renderTable(input) {
  const host = document.getElementById('view-table');
  const cars = input || sortedCars(currentCars());
  if (!cars.length) {
    host.innerHTML = `<div class="empty"><b>Nenhum carro corresponde a esses filtros</b>Afrouxe um filtro à esquerda.</div>`;
    return;
  }
  const selected = new Set(state.selected);
  const rows = cars
    .map(
      (car) => `<tr class="${selected.has(car.id) ? 'is-selected' : ''}">
      <td class="name">
        <button class="cell-link" data-detail="${car.id}">
          <b>${car.marca} ${car.modelo}</b><br>
          <span>${car.versao || ''}</span>
        </button>
      </td>
      <td class="num">${money(car.preco_brl)}</td>
      <td class="num">${Math.round(score(car))}</td>
      <td class="num">${dash(car.autonomia_referencia_km)}</td>
      <td class="num">${dash(car.bateria_kwh ? kwh(car.bateria_kwh) : null)}</td>
      <td class="num">${dash(car.eficiencia_km_kwh ? n1(car.eficiencia_km_kwh) : null)}</td>
      <td class="num">${dash(car.potencia_cv || car.potencia_combinada_cv)}</td>
      <td class="num">${dash(car.zero_a_cem_s ? n1(car.zero_a_cem_s) : null)}</td>
      <td class="num">${dash(car.recarga_dc_kw)}</td>
      <td class="num">${dash(car.porta_malas_l)}</td>
      <td class="num">${dash(car.lugares)}</td>
      <td class="num">${car.nota ? n1(car.nota) : '<span class="missing">—</span>'}</td>
      <td><button class="pick ${selected.has(car.id) ? 'is-on' : ''}" data-pick="${car.id}">${
        selected.has(car.id) ? 'Selecionado' : '+'
      }</button></td>
    </tr>`,
    )
    .join('');

  host.innerHTML = `<div class="table-wrap">
    <table class="grid">
      <thead>
        <tr>
          <th>Carro</th><th>Preço</th><th>Nota</th><th>Autonomia km</th><th>Bateria kWh</th><th>km/kWh</th>
          <th>cv</th><th>0–100</th><th>DC kW</th><th>Porta-malas L</th><th>Lugares</th><th>Avaliação</th><th></th>
        </tr>
      </thead>
      <tbody>${rows}</tbody>
    </table>
  </div>`;

  host.querySelectorAll('[data-detail]').forEach((btn) => {
    btn.addEventListener('click', () => openDetail(btn.dataset.detail));
  });
  host.querySelectorAll('[data-pick]').forEach((btn) => {
    btn.addEventListener('click', () => selectCar(btn.dataset.pick));
  });
}

/* ---------- gráficos ---------- */

const BODY_COLORS = {
  hatch: '#0d6b57',
  sedan: '#2f6f9f',
  suv: '#a9550c',
  picape: '#6b4a8c',
  minivan: '#8a6b1f',
  cupe: '#a83d5c',
  perua: '#4a6d2f',
  outro: '#787569',
};

function niceTicks(min, max, count = 5) {
  if (min === max) return [min];
  const raw = (max - min) / count;
  const mag = Math.pow(10, Math.floor(Math.log10(raw)));
  const norm = raw / mag;
  const mult = norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 2.5 ? 2.5 : norm <= 5 ? 5 : 10;
  const step = mult * mag;
  const start = Math.ceil(min / step) * step;
  const ticks = [];
  for (let v = start; v <= max + step * 0.001; v += step) ticks.push(Number(v.toFixed(6)));
  return ticks;
}

function renderCharts(input) {
  const host = document.getElementById('view-charts');
  const cars = (input || sortedCars(currentCars())).filter(
    (c) => c.preco_brl && c.autonomia_referencia_km,
  );
  if (cars.length < 2) {
    host.innerHTML = `<div class="empty"><b>Poucos carros com preço e autonomia</b>Afrouxe os filtros para desenhar o gráfico.</div>`;
    return;
  }

  const W = 900;
  const H = 430;
  const M = { top: 18, right: 24, bottom: 46, left: 58 };
  const xs = cars.map((c) => c.preco_brl);
  const ys = cars.map((c) => c.autonomia_referencia_km);
  const xMin = Math.min(...xs) * 0.96;
  const xMax = Math.max(...xs) * 1.02;
  const yMin = Math.min(...ys) * 0.92;
  const yMax = Math.max(...ys) * 1.04;
  const px = (v) => M.left + ((v - xMin) / (xMax - xMin)) * (W - M.left - M.right);
  const py = (v) => H - M.bottom - ((v - yMin) / (yMax - yMin)) * (H - M.top - M.bottom);

  const median = (arr) => {
    const s = [...arr].sort((a, b) => a - b);
    return s[Math.floor(s.length / 2)];
  };
  const medX = median(xs);
  const medY = median(ys);

  const xTicks = niceTicks(xMin, xMax, 7);
  const yTicks = niceTicks(yMin, yMax, 5);

  const grid = [
    ...xTicks.map(
      (t) => `<line class="grid-line" x1="${px(t)}" y1="${M.top}" x2="${px(t)}" y2="${H - M.bottom}"/>
        <text x="${px(t)}" y="${H - M.bottom + 16}" text-anchor="middle">${n0(t / 1000)}k</text>`,
    ),
    ...yTicks.map(
      (t) => `<line class="grid-line" x1="${M.left}" y1="${py(t)}" x2="${W - M.right}" y2="${py(t)}"/>
        <text x="${M.left - 10}" y="${py(t) + 3}" text-anchor="end">${n0(t)}</text>`,
    ),
  ].join('');

  const candidates = [...cars]
    .sort((a, b) => score(b) - score(a) || (b.autonomia_referencia_km || 0) - (a.autonomia_referencia_km || 0))
    .slice(0, 22);
  const placed = [];
  const labels = [];
  for (const car of candidates) {
    const text = car.modelo;
    const width = text.length * 5.3 + 8;
    const cx = px(car.preco_brl) + 8;
    const cy = py(car.autonomia_referencia_km) - 6;
    const fits = (x, y) =>
      x + width < W - 4 && x > M.left && y > M.top + 8 &&
      !placed.some((r) => x < r.x + r.w && x + width > r.x && y - 8 < r.y + r.h && y + 2 > r.y);
    let spot = null;
    if (fits(cx, cy)) spot = [cx, cy];
    else if (fits(cx, cy + 16)) spot = [cx, cy + 16];
    else if (fits(cx - width - 12, cy)) spot = [cx - width - 12, cy];
    if (!spot) continue;
    placed.push({ x: spot[0], y: spot[1] - 9, w: width, h: 13 });
    labels.push({ car, x: spot[0], y: spot[1] });
  }

  const dots = cars
    .map((car) => {
      const color = BODY_COLORS[car.carroceria] || BODY_COLORS.outro;
      const selected = state.selected.includes(car.id);
      return `<circle class="dot" cx="${px(car.preco_brl)}" cy="${py(car.autonomia_referencia_km)}"
        r="${selected ? 9 : 6.5}" fill="${color}" fill-opacity="${car.motorizacao === 'BEV' ? 0.82 : 0.6}"
        stroke="${selected ? '#17160f' : '#fffefb'}" stroke-width="${selected ? 2 : 1.2}"
        data-id="${car.id}">
        <title>${car.marca} ${car.modelo} ${car.versao || ''}
${money(car.preco_brl)} · ${n0(car.autonomia_referencia_km)} km · nota ${Math.round(score(car))}</title>
      </circle>`;
    })
    .join('');

  const labelNodes = labels
    .map((l) => `<text class="dot-label" x="${l.x}" y="${l.y}">${l.car.modelo}</text>`)
    .join('');

  const scatter = `
    <svg class="scatter" viewBox="0 0 ${W} ${H}" role="img" aria-label="Preço por autonomia">
      ${grid}
      <line class="axis" x1="${M.left}" y1="${H - M.bottom}" x2="${W - M.right}" y2="${H - M.bottom}"/>
      <line class="axis" x1="${M.left}" y1="${M.top}" x2="${M.left}" y2="${H - M.bottom}"/>
      <line class="grid-line" x1="${px(medX)}" y1="${M.top}" x2="${px(medX)}" y2="${H - M.bottom}"
        stroke-dasharray="3 4" stroke="#c6c1b1"/>
      <line class="grid-line" x1="${M.left}" y1="${py(medY)}" x2="${W - M.right}" y2="${py(medY)}"
        stroke-dasharray="3 4" stroke="#c6c1b1"/>
      <text x="${M.left + 8}" y="${M.top + 12}" style="font-size:10px;fill:#10724f">barato + muita autonomia — a esquina do valor</text>
      <text x="${W - M.right - 4}" y="${H - M.bottom - 10}" text-anchor="end" style="font-size:10px;fill:#a9550c">caro + pouca autonomia — mais difícil de justificar</text>
      ${dots}
      ${labelNodes}
      <text x="${(W + M.left) / 2}" y="${H - 10}" text-anchor="middle" style="font-size:11px">Preço (R$)</text>
      <text transform="translate(14,${(H - M.bottom + M.top) / 2}) rotate(-90)" text-anchor="middle" style="font-size:11px">
        ${state.mode === 'PHEV' ? 'Autonomia elétrica (km)' : 'Autonomia Inmetro (km)'}
      </text>
    </svg>`;

  const legendBodies = [...new Set(cars.map((c) => c.carroceria).filter(Boolean))];
  const legend = `<div class="legend">${legendBodies
    .map((b) => `<span><i style="background:${BODY_COLORS[b] || BODY_COLORS.outro}"></i>${BODY_LABELS[b] || b}</span>`)
    .join('')}</div>`;

  const perKm = [...cars]
    .filter((c) => c.preco_por_km_autonomia)
    .sort((a, b) => a.preco_por_km_autonomia - b.preco_por_km_autonomia)
    .slice(0, 14);
  const maxPerKm = perKm.length ? Math.max(...perKm.map((c) => c.preco_por_km_autonomia)) : 1;

  const eff = [...cars]
    .filter((c) => c.eficiencia_km_kwh)
    .sort((a, b) => b.eficiencia_km_kwh - a.eficiencia_km_kwh)
    .slice(0, 14);

  const nameCount = new Map();
  [...perKm, ...eff].forEach((c) => {
    const base = `${c.marca} ${c.modelo}`;
    nameCount.set(base, (nameCount.get(base) || 0) + 1);
  });
  const barLabel = (c) => {
    const base = `${c.marca} ${c.modelo}`;
    if ((nameCount.get(base) || 0) < 2) return base;
    const short = (c.versao || '').split(/[—/(·]/)[0].trim().slice(0, 14);
    return short ? `${base} · ${short}` : base;
  };

  const barsPerKm = perKm
    .map(
      (c) => `<div class="bar-row">
      <span class="bar-row__name" title="${c.marca} ${c.modelo} — ${c.versao || ''}">${barLabel(c)}</span>
      <span class="bar-row__track"><span class="bar-row__fill" style="width:${(c.preco_por_km_autonomia / maxPerKm) * 100}%"></span></span>
      <span class="bar-row__value">${money(c.preco_por_km_autonomia)}</span>
    </div>`,
    )
    .join('');

  const maxEff = eff.length ? Math.max(...eff.map((c) => c.eficiencia_km_kwh)) : 1;
  const barsEff = eff
    .map(
      (c) => `<div class="bar-row">
      <span class="bar-row__name" title="${c.marca} ${c.modelo} — ${c.versao || ''}">${barLabel(c)}</span>
      <span class="bar-row__track"><span class="bar-row__fill" style="width:${(c.eficiencia_km_kwh / maxEff) * 100}%"></span></span>
      <span class="bar-row__value">${n1(c.eficiencia_km_kwh)} km/kWh</span>
    </div>`,
    )
    .join('');

  host.innerHTML = `
    <div class="chart-card">
      <h3>Preço × autonomia</h3>
      <p class="sub">Cada ponto é uma versão. As linhas tracejadas são as medianas do filtro atual — embaixo à direita você paga muito por pouca autonomia; em cima à esquerda está o ponto doce. Clique em um ponto para abrir a ficha.</p>
      ${scatter}
      ${legend}
    </div>
    <div class="chart-card">
      <h3>Quilômetros de autonomia mais baratos</h3>
      <p class="sub">Preço dividido pela autonomia certificada — quanto do valor de tabela está comprando distância.</p>
      <div class="bars">${barsPerKm || '<p class="sub">Nenhum carro com preço e autonomia ao mesmo tempo.</p>'}</div>
    </div>
    <div class="chart-card">
      <h3>Mais eficientes</h3>
      <p class="sub">Autonomia certificada por kWh de bateria nominal. Mais alto significa quilômetro mais barato e menos bateria para trocar no futuro.</p>
      <div class="bars">${barsEff || '<p class="sub">Sem dados de eficiência neste filtro.</p>'}</div>
    </div>`;

  host.querySelectorAll('.scatter .dot').forEach((dot) => {
    dot.addEventListener('click', () => openDetail(dot.dataset.id));
  });
}

/* ---------- custo de rodagem ---------- */

function renderCost(input) {
  const host = document.getElementById('view-cost');
  const cars = (input || sortedCars(currentCars())).filter((c) => cost100(c) != null);
  const c = state.cost;

  const inputs = `
    <div class="panel">
      <div class="panel__head"><h2>Seu uso</h2></div>
      <p class="hint">Usado nos cartões e nesta tabela. Nada é gravado em lugar nenhum — é uma conta de rascunho.</p>
      <div class="cost-inputs">
        <label>Quilômetros por ano <b>${n0(c.kmYear)} km</b>
          <input type="number" id="cost-km" min="1000" max="120000" step="1000" value="${c.kmYear}"></label>
        <label>Energia em casa <b>R$/kWh</b>
          <input type="number" id="cost-home" min="0" max="5" step="0.01" value="${c.homePrice}"></label>
        <label>Recarga rápida pública <b>R$/kWh</b>
          <input type="number" id="cost-public" min="0" max="6" step="0.01" value="${c.publicPrice}"></label>
        <label>Carregado em casa <b>${c.homeShare}%</b>
          <input type="range" id="cost-share" min="0" max="100" step="5" value="${c.homeShare}"></label>
        <label>Gasolina / etanol <b>R$/L</b>
          <input type="number" id="cost-fuel" min="0" max="15" step="0.1" value="${c.fuelPrice}"></label>
        <label>Plug-in: km no elétrico <b>${c.electricShare}%</b>
          <input type="range" id="cost-eshare" min="0" max="100" step="5" value="${c.electricShare}"></label>
        <label>Manutenção e pneus por ano <b>R$</b>
          <input type="number" id="cost-maint" min="0" max="20000" step="100" value="${c.maintenance}"></label>
      </div>
    </div>`;

  const ranked = [...cars].sort((a, b) => annualCost(a) - annualCost(b));
  const cheapest = ranked.length ? annualCost(ranked[0]) : null;

  const rows = ranked
    .slice(0, 60)
    .map((car) => {
      const per100 = cost100(car);
      const year = annualCost(car);
      const delta = cheapest == null ? 0 : year - cheapest;
      return `<tr>
        <td class="name"><button class="cell-link" data-detail="${car.id}">
          <b>${car.marca} ${car.modelo}</b><br><span>${car.versao || ''}</span></button></td>
        <td class="num">${money(car.preco_brl)}</td>
        <td class="num">${energyKwh100(car) ? n1(energyKwh100(car)) : '<span class="missing">—</span>'}</td>
        <td class="num">${money2(per100)}</td>
        <td class="num">${money(year)}</td>
        <td class="num">${delta < 1 ? '<span class="yes">mais barato</span>' : `+${money(delta)}`}</td>
      </tr>`;
    })
    .join('');

  host.innerHTML = `<div class="cost-grid">
    ${inputs}
    <div>
      <div class="table-wrap">
        <table class="grid">
          <thead><tr>
            <th>Carro</th><th>Preço</th><th>kWh/100 km</th><th>Energia /100 km</th>
            <th>Por ano</th><th>Extra vs. mais barato</th>
          </tr></thead>
          <tbody>${rows || '<tr><td colspan="6" style="padding:24px;text-align:center;color:var(--muted)">Nenhum carro com dados de consumo neste filtro.</td></tr>'}</tbody>
        </table>
      </div>
      <p class="hint" style="margin-top:10px">
        Somente energia e manutenção. Preço de compra, seguro, depreciação, pedágios e instalação do carregador em casa
        ficam de fora, e o consumo real de um híbrido plug-in depende de quantas vezes você realmente o pluga —
        ajuste a fatia elétrica acima para ver os dois extremos.
      </p>
    </div>
  </div>`;

  const bind = (id, key, isRange) => {
    const el = document.getElementById(id);
    if (!el) return;
    el.addEventListener('input', () => {
      state.cost[key] = Number(el.value);
      renderCost(currentCars());
      if (state.view === 'cards') renderCards();
    });
  };
  bind('cost-km', 'kmYear');
  bind('cost-home', 'homePrice');
  bind('cost-public', 'publicPrice');
  bind('cost-share', 'homeShare', true);
  bind('cost-fuel', 'fuelPrice');
  bind('cost-eshare', 'electricShare', true);
  bind('cost-maint', 'maintenance');

  host.querySelectorAll('[data-detail]').forEach((btn) => {
    btn.addEventListener('click', () => openDetail(btn.dataset.detail));
  });
}
