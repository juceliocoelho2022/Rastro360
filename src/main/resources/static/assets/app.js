'use strict';

const $ = id => document.getElementById(id);
const state = { products: [], locations: [], units: [], shipments: [], dashboard: {} };
const safe = value => String(value ?? '').replace(/[&<>"']/g, char => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
}[char]));
const formatted = value => value ? new Date(value).toLocaleString('pt-BR') : '—';
const human = text => String(text || '').replaceAll('_', ' ');
const tag = status => `<span class="tag ${safe(status)}">${safe(human(status))}</span>`;
const formData = form => Object.fromEntries(new FormData(form).entries());

async function api(path, options = {}) {
  const response = await fetch('/api' + path, {
    credentials: 'same-origin',
    headers: options.body ? { 'Content-Type': 'application/json' } : {},
    ...options
  });
  if (!response.ok) {
    let error;
    try { error = await response.json(); } catch (_) { error = {}; }
    throw new Error(error.detail || (response.status === 401 ? 'Acesso não autorizado.' : `Erro HTTP ${response.status}`));
  }
  return response.json();
}

let toastTimer;
function toast(message, error = false) {
  const el = $('toast');
  el.hidden = false;
  el.className = 'toast' + (error ? ' error' : '');
  el.textContent = message;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.hidden = true; }, 4500);
}

function switchTab(id) {
  document.querySelectorAll('.tab').forEach(el => el.classList.toggle('active', el.id === 'tab-' + id));
  document.querySelectorAll('.nav').forEach(el => el.classList.toggle('active', el.dataset.tab === id));
  window.scrollTo({ top: 0, behavior: 'instant' });
}

function optionize(id, items, labelFn, placeholder = 'Selecione') {
  const select = $(id);
  const chosen = select.value;
  select.replaceChildren();
  const blank = document.createElement('option');
  blank.value = '';
  blank.textContent = items.length ? placeholder : 'Nenhum item disponível';
  select.appendChild(blank);
  for (const item of items) {
    const opt = document.createElement('option');
    opt.value = item.id;
    opt.textContent = labelFn(item);
    select.appendChild(opt);
  }
  if (items.some(item => item.id === chosen)) select.value = chosen;
}

function renderStats() {
  const cards = [
    ['▣', 'Produtos cadastrados', state.dashboard.products],
    ['▤', 'Volumes rastreados', state.dashboard.units],
    ['⇢', 'Volumes em transporte', state.dashboard.inTransitUnits],
    ['▰', 'Remessas abertas', state.dashboard.openShipments],
    ['✓', 'Remessas entregues', state.dashboard.deliveredShipments],
    ['◉', 'Volumes entregues', state.dashboard.deliveredUnits]
  ];
  $('stats').innerHTML = cards.map(([ico, label, count]) =>
    `<div class="stat"><span class="stat-icon">${ico}</span><div><small>${label}</small><strong>${count ?? 0}</strong></div></div>`).join('');
}

function renderUnits() {
  $('overviewUnits').innerHTML = state.units.length ? state.units.slice(0, 15).map(unit => `
    <tr><td><button class="codecopy mono" data-code="${safe(unit.trackingCode)}">${safe(unit.trackingCode)}</button></td>
    <td><strong>${safe(unit.productName)}</strong><div class="dim">Lote ${safe(unit.lot)} · ${unit.quantity} un.</div></td>
    <td>${safe(unit.locationName || unit.shipmentReference || 'Sem localização')}</td>
    <td>${tag(unit.status)}</td></tr>
  `).join('') : '<tr><td colspan="4" class="empty">Nenhum volume registrado. Comece em “Produção e cadastro”.</td></tr>';
}

function renderSelects() {
  optionize('productSelect', state.products, p => `${p.sku} · ${p.name}`);
  optionize('productionLocationSelect', state.locations.filter(l => l.kind === 'PRODUCAO'), l => `${l.code} · ${l.name}`);
  optionize('moveUnitSelect', state.units.filter(u => ['PRODUZIDO', 'ARMAZENADO'].includes(u.status)), u => `${u.trackingCode} · ${u.productName}`);
  optionize('destinationSelect', state.locations.filter(l => l.kind !== 'PRODUCAO'), l => `${l.code} · ${l.name}`);
  optionize('shipmentSelect', state.shipments.filter(s => s.status === 'PREPARACAO'), s => `${s.reference} · ${s.truckPlate}`);
  optionize('loadUnitSelect', state.units.filter(u =>
    ['PRODUZIDO', 'ARMAZENADO'].includes(u.status) &&
    state.locations.some(l => l.id === u.locationId && l.kind === 'EXPEDICAO')),
  u => `${u.trackingCode} · ${u.productName}`);
}

function renderShipments() {
  $('shipmentsTable').innerHTML = state.shipments.length ? state.shipments.map(s => {
    const action = s.status === 'PREPARACAO' ? `<button class="smallbtn" data-ship="${s.id}" data-action="depart">Registrar saída →</button>` :
      s.status === 'EM_TRANSITO' ? `<button class="smallbtn" data-ship="${s.id}" data-action="arrive">Registrar chegada →</button>` :
      s.status === 'NO_CLIENTE' ? `<button class="smallbtn" data-ship="${s.id}" data-action="deliver">Confirmar entrega →</button>` : 'Concluído';
    return `<tr><td><strong>${safe(s.reference)}</strong><div class="dim">${safe(s.truckPlate)} · ${safe(s.driverName)}</div></td>
      <td><strong>${safe(s.customerName)}</strong><div class="dim">${safe(s.customerAddress)}</div></td>
      <td>${tag(s.status)}</td><td>${action}</td></tr>`;
  }).join('') : '<tr><td colspan="4" class="empty">Nenhuma remessa cadastrada.</td></tr>';
}

async function refresh() {
  const [products, locations, units, shipments, dashboard] = await Promise.all([
    api('/products'), api('/locations'), api('/units'), api('/shipments'), api('/dashboard')
  ]);
  Object.assign(state, { products, locations, units, shipments, dashboard });
  renderStats(); renderUnits(); renderSelects(); renderShipments();
}

function showTracking(detail) {
  const u = detail.unit;
  const publicUrl = `${window.location.origin}/tracking.html?code=${encodeURIComponent(u.trackingCode)}`;
  const location = u.locationName ? `Local atual: ${u.locationName}` : u.shipmentReference ? `Remessa: ${u.shipmentReference}` : '';
  $('trackingInput').value = u.trackingCode;
  $('traceResult').innerHTML = `
    <div class="trace-summary"><div><div class="eyebrow">IDENTIFICADOR ÚNICO</div><h2>${safe(u.productName)} — ${safe(u.productSku)}</h2>
    <div class="mono">${safe(u.trackingCode)}</div><p class="subtle" style="margin:8px 0 0">Lote ${safe(u.lot)} · Quantidade ${u.quantity} · ${safe(location)}</p></div>${tag(u.status)}</div>
    <div style="padding-top:17px;display:flex;gap:9px;flex-wrap:wrap">
      <button id="copyLink" class="smallbtn">Copiar link de rastreio do cliente</button>
      <a class="smallbtn" href="${safe(publicUrl)}" target="_blank" rel="noopener">Abrir visão do cliente ↗</a>
      <a class="smallbtn" href="/label.html?code=${encodeURIComponent(u.trackingCode)}" target="_blank" rel="noopener">▦ Imprimir etiqueta QR</a>
      <a class="smallbtn" href="/scanner.html" target="_blank" rel="noopener">⌕ Ler QR pela câmera</a>
    </div><div class="section-head"><h2>Linha do tempo de eventos</h2><span>${detail.events.length} registro(s)</span></div>
    <div class="timeline">${detail.events.map(ev => `
      <div class="timeline-item"><strong>${safe(human(ev.type))}</strong><time>${formatted(ev.occurredAt)} · ${safe(ev.actor)}</time>
      <p>${safe(ev.details || 'Evento operacional registrado')}</p>
      ${ev.fromLocationId || ev.toLocationId ? `<p class="mono">Origem: ${safe(state.locations.find(l => l.id === ev.fromLocationId)?.name || '—')} → Destino: ${safe(state.locations.find(l => l.id === ev.toLocationId)?.name || '—')}</p>` : ''}</div>`).join('')}</div>`;
  $('copyLink').addEventListener('click', async () => {
    try { await navigator.clipboard.writeText(publicUrl); toast('Link copiado.'); }
    catch (_) { toast('Copie o link: ' + publicUrl); }
  });
  switchTab('trace');
}

async function afterSubmit(form, action, success) {
  const button = form.querySelector('[type="submit"]');
  button.disabled = true;
  try {
    const result = await action(formData(form));
    form.reset();
    await refresh();
    toast(success);
    return result;
  } catch (error) {
    toast(error.message, true);
    return null;
  } finally {
    button.disabled = false;
  }
}

function setupForms() {
  $('productForm').addEventListener('submit', async e => {
    e.preventDefault();
    await afterSubmit(e.currentTarget, data => api('/products', { method: 'POST', body: JSON.stringify(data) }), 'Produto cadastrado.');
  });
  $('locationForm').addEventListener('submit', async e => {
    e.preventDefault();
    await afterSubmit(e.currentTarget, data => api('/locations', { method: 'POST', body: JSON.stringify(data) }), 'Local cadastrado.');
  });
  $('produceForm').addEventListener('submit', async e => {
    e.preventDefault();
    const result = await afterSubmit(e.currentTarget, data => api('/units', {
      method: 'POST', body: JSON.stringify({ ...data, quantity: Number(data.quantity) })
    }), 'Volume produzido e identificado.');
    if (result) showTracking(result);
  });
  $('moveForm').addEventListener('submit', async e => {
    e.preventDefault();
    const result = await afterSubmit(e.currentTarget, ({unitId, ...data}) => api(`/units/${unitId}/move`, {
      method: 'POST', body: JSON.stringify(data)
    }), 'Movimentação registrada.');
    if (result) showTracking(result);
  });
  $('shipmentForm').addEventListener('submit', async e => {
    e.preventDefault();
    await afterSubmit(e.currentTarget, data => api('/shipments', { method: 'POST', body: JSON.stringify(data) }), 'Remessa criada.');
  });
  $('loadForm').addEventListener('submit', async e => {
    e.preventDefault();
    await afterSubmit(e.currentTarget, ({shipmentId, unitId}) => api(`/shipments/${shipmentId}/load/${unitId}`, {
      method: 'POST'
    }), 'Carregamento registrado.');
  });
  $('traceForm').addEventListener('submit', async e => {
    e.preventDefault();
    try { showTracking(await api(`/units/${encodeURIComponent(formData(e.currentTarget).trackingCode.trim())}`)); }
    catch (error) { toast(error.message, true); }
  });
}

async function shipmentAction(button) {
  const { ship, action } = button.dataset;
  let payload;
  if (action === 'depart') {
    if (!confirm('Confirmar a saída deste caminhão? O carregamento será encerrado.')) return;
  } else if (action === 'arrive') {
    if (!confirm('Confirmar que o caminhão chegou ao cliente?')) return;
  } else if (action === 'deliver') {
    const name = prompt('Nome de quem recebeu a remessa:');
    if (name === null) return;
    if (!name.trim() || name.trim().length > 150) { toast('Informe um nome válido (até 150 caracteres).', true); return; }
    payload = { receivedBy: name.trim() };
  }
  button.disabled = true;
  try {
    await api(`/shipments/${ship}/${action}`, { method: 'POST', ...(payload && { body: JSON.stringify(payload) }) });
    await refresh();
    toast('Etapa de expedição registrada com sucesso.');
  } catch (error) { toast(error.message, true); }
  finally { button.disabled = false; }
}

function setupNavigation() {
  document.querySelectorAll('[data-tab]').forEach(el => el.addEventListener('click', () => switchTab(el.dataset.tab)));
  document.querySelectorAll('[data-switch]').forEach(el => el.addEventListener('click', () => switchTab(el.dataset.switch)));
  $('refreshBtn').addEventListener('click', () => refresh().then(() => toast('Painel atualizado.')).catch(e => toast(e.message, true)));
  document.addEventListener('click', e => {
    const code = e.target.closest('[data-code]');
    if (code) api(`/units/${encodeURIComponent(code.dataset.code)}`).then(showTracking).catch(err => toast(err.message, true));
    const action = e.target.closest('[data-action]');
    if (action) shipmentAction(action);
  });
}

setupNavigation();
setupForms();
refresh().then(async () => {
  const initialCode = new URLSearchParams(window.location.search).get('track');
  if (initialCode) {
    try {
      showTracking(await api('/units/' + encodeURIComponent(initialCode)));
    } catch (error) {
      toast('Falha ao abrir código do QR: ' + error.message, true);
    }
  }
}).catch(error => toast('Falha ao carregar dados: ' + error.message, true));
