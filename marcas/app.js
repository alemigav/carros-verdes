/* Carros Verdes — mapa de grupos e marcas. */

const GROUPS = [
  {
    name: 'Volkswagen Group', country: 'Alemanha',
    note: 'Também controla Škoda, Seat/Cupra, Bentley e Lamborghini.',
    brands: ['Volkswagen', 'Audi', 'Porsche'],
  },
  {
    name: 'Geely Holding', country: 'China',
    note: 'Controla Volvo e Zeekr; também Polestar, Lotus e a joint venture smart.',
    brands: ['Geely', 'Volvo', 'Zeekr'],
  },
  {
    name: 'BMW Group', country: 'Alemanha',
    note: 'Também controla Rolls-Royce.',
    brands: ['BMW', 'MINI'],
  },
  {
    name: 'BYD', country: 'China', independent: true,
    note: 'Grupo independente. A Denza é joint venture com a Mercedes-Benz, com maioria do BYD.',
    brands: ['BYD'],
  },
  {
    name: 'General Motors', country: 'Estados Unidos',
    note: 'Também GMC e Buick.',
    brands: ['Chevrolet', 'Cadillac'],
  },
  {
    name: 'Hyundai Motor Group', country: 'Coreia do Sul',
    note: 'Também Genesis.',
    brands: ['Hyundai', 'Kia'],
  },
  {
    name: 'Toyota', country: 'Japão',
    note: 'Também Daihatsu e Hino. A Lexus é a divisão de luxo.',
    brands: ['Toyota', 'Lexus'],
  },
  {
    name: 'Stellantis', country: 'Países Baixos',
    note: 'Fusão de FCA e PSA; também Peugeot, Citroën, Jeep, Ram e Opel.',
    brands: ['Fiat'],
  },
  {
    name: 'SAIC Motor', country: 'China',
    note: 'Também Roewe, Maxus e a joint venture com a Volkswagen na China.',
    brands: ['MG'],
  },
  {
    name: 'Great Wall Motor', country: 'China', independent: true,
    note: 'Haval, Ora, Tank e Wey são submarcas da GWM.',
    brands: ['GWM'],
  },
  {
    name: 'GAC Group', country: 'China',
    note: 'Aion e Hyptec são submarcas da GAC.',
    brands: ['GAC'],
  },
  {
    name: 'Changan Automobile', country: 'China',
    note: 'No Brasil vende pela parceria com a CAOA.',
    brands: ['CAOA Changan'],
  },
  {
    name: 'Dongfeng Motor', country: 'China',
    note: 'Também Voyah, Aeolus e a joint venture com a PSA.',
    brands: ['DFM'],
  },
  {
    name: 'BAIC Group', country: 'China',
    note: 'A Arcfox é a marca premium elétrica da BAIC.',
    brands: ['BAIC'],
  },
  {
    name: 'JAC Motors', country: 'China',
    note: 'Grupo Anhui Jianghuai; no Brasil já operou em parceria com a SHC.',
    brands: ['JAC'],
  },
  {
    name: 'Chery', country: 'China',
    note: 'Omoda e Jaecoo são submarcas da Chery.',
    brands: ['Omoda'],
  },
  { name: 'Ford', country: 'Estados Unidos', independent: true, note: 'Também Lincoln.', brands: ['Ford'] },
  { name: 'Renault Group', country: 'França', note: 'Também Dacia, Alpine e Mobilize.', brands: ['Renault'] },
];

const JOINT_VENTURES = [
  {
    name: 'Denza',
    owners: ['BYD', 'Mercedes-Benz'],
    note: 'Nasceu como joint venture 50/50 em 2010; hoje o BYD tem a maioria. Vende o D9 e o Z9 GT no Brasil.',
    brand: 'Denza',
  },
  {
    name: 'Avatr',
    owners: ['Changan', 'Huawei', 'CATL'],
    note: 'Marca criada em 2018: Changan cuida do carro, Huawei da parte digital e a CATL das baterias.',
    brand: 'Avatr',
  },
  {
    name: 'Leapmotor',
    owners: ['Leapmotor (China)', 'Stellantis'],
    note: 'Empresa chinesa; a Stellantis tem participação e faz a distribuição fora da China pela Leapmotor International.',
    brand: 'Leapmotor',
  },
  {
    name: 'CAOA Changan',
    owners: ['CAOA', 'Changan'],
    note: 'Parcela da operação brasileira é feita pela CAOA, que produz e distribui os carros da Changan por aqui.',
    brand: 'CAOA Changan',
  },
];

const counts = new Map();
const escapeHtml = (text) => String(text).replace(/[&<>"]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[ch]));

function brandChip(name) {
  const total = counts.get(name) || 0;
  const label = total ? `${total} ${total === 1 ? 'versão' : 'versões'}` : 'sem versão na base';
  const href = `../?q=${encodeURIComponent(name)}`;
  return `<a class="brand-chip" href="${href}" title="Ver as versões da ${escapeHtml(name)} no comparador">
    <b>${escapeHtml(name)}</b>
    <span>${label}</span>
  </a>`;
}

function groupCard(group) {
  const single = group.brands.length === 1;
  const versions = group.brands.reduce((sum, brand) => sum + (counts.get(brand) || 0), 0);
  return `<article class="group">
    <div class="group__parent">
      <div class="group__label">
        <h3>${escapeHtml(group.name)}</h3>
        <p class="group__meta">${escapeHtml(group.country)}${group.independent ? ' · grupo independente' : ''}</p>
      </div>
      <span class="group__count">${versions} ${versions === 1 ? 'versão' : 'versões'}</span>
    </div>
    <div class="group__stem" aria-hidden="true"></div>
    <div class="group__brands${single ? ' group__brands--single' : ''}" style="--n:${group.brands.length}">
      ${group.brands.map(brandChip).join('')}
    </div>
    <p class="group__note">${escapeHtml(group.note)}</p>
  </article>`;
}

function jvCard(item) {
  const total = counts.get(item.brand) || 0;
  return `<article class="jv">
    <div class="jv__head">
      <h3>${escapeHtml(item.name)}</h3>
      <span class="chip chip--jv">JV</span>
    </div>
    <p class="jv__owners">${item.owners.map(escapeHtml).join(' + ')}</p>
    <p class="jv__note">${escapeHtml(item.note)}</p>
    ${total ? brandChip(item.brand) : ''}
  </article>`;
}

async function boot() {
  const response = await fetch('../data/cars.json');
  const payload = await response.json();
  payload.cars.forEach((car) => {
    counts.set(car.marca, (counts.get(car.marca) || 0) + 1);
  });

  const ordered = [...GROUPS].sort((a, b) => {
    const total = (group) => group.brands.reduce((sum, brand) => sum + (counts.get(brand) || 0), 0);
    return total(b) - total(a);
  });

  document.getElementById('groups').innerHTML = ordered.map(groupCard).join('');
  document.getElementById('joint-ventures').innerHTML = JOINT_VENTURES.map(jvCard).join('');
}

boot().catch((error) => {
  document.getElementById('groups').innerHTML =
    `<p class="error">Não foi possível carregar os dados: ${escapeHtml(error.message)}</p>`;
});
