/*
 * Dashboard analítico (RF07). Todas las cifras provienen de /api/analitica/dashboard,
 * que las calcula con SQL sobre la base relacional; aquí solo se presentan.
 * Cada gráfico tiene una tabla "Ver datos" con los valores exactos.
 */

const $ = (s) => document.querySelector(s);
const graficos = {};
let ultimo = null;

async function iniciar() {
  try {
    const c = await api.catalogos();
    llenarSelect($('#f-categoria'), c.categorias, { vacio: 'Todas' });
    llenarSelect($('#f-prioridad'), c.prioridades, { vacio: 'Todas' });
  } catch (e) { mostrarError(e); }
  cargar();
}

async function cargar() {
  const filtros = Object.fromEntries(new FormData($('#filtros')));
  try {
    ultimo = await api.dashboard(filtros);
    pintar(ultimo);
  } catch (e) { mostrarError(e); }
  pintarRevisionesPendientes();
}

/** No depende de los filtros del dashboard: son todos los incidentes finalizados sin revisión. */
async function pintarRevisionesPendientes() {
  try {
    const r = await api.revisionesPendientes();
    $('#rev-nota').textContent = `${num(r.total)} incidente(s) finalizados sin revisión con prioridad de nivel ${r.nivelMinimo} o más, o con el SLA de resolución incumplido. Últimos:`;
    $('#rev-pendientes').innerHTML = tabla([
      ['#', (i) => `<a href="incidentes.html?id=${i.incidenteId}">${i.incidenteId}</a>`], ['Incidente', (i) => esc(i.titulo)],
      ['Prioridad', (i) => esc(i.prioridad)], ['Resuelto', (i) => fecha(i.fechaResolucion)], ['Motivo', (i) => esc(i.motivo)],
    ], r.ultimas);
  } catch (e) { mostrarError(e); }
}

// ---------- Indicadores ----------
function pintarKpis(r, a) {
  const kpi = (label, value, hint) =>
    `<div class="card kpi"><div class="label">${label}</div><div class="value">${value}</div><div class="hint">${hint}</div></div>`;
  $('#kpis').innerHTML = [
    kpi('Total de incidentes', num(r.totalIncidentes), `${num(r.pendientes)} pendientes`),
    kpi('Tasa de resolución', r.tasaResolucion == null ? '—' : num(r.tasaResolucion, 1) + '%', `${num(r.resueltos)} resueltos o cerrados`),
    kpi('Tiempo promedio', r.tiempoPromedioHoras == null ? '—' : num(r.tiempoPromedioHoras, 1) + ' h', `Mediana ${num(r.tiempoMedianaHoras, 1)} h`),
    kpi('Cumplimiento de SLA', r.cumplimientoSla == null ? '—' : num(r.cumplimientoSla, 1) + '%', 'Resueltos dentro del SLA de su prioridad'),
    kpi('Categoría más recurrente', esc(r.categoriaMasRecurrente ?? '—'), `${num(r.incidentesCategoriaMasRecurrente)} incidentes`),
    kpi('Consultas IA', num(a.totalConsultas), a.calificacionPromedio != null ? `Calificación promedio ${num(a.calificacionPromedio, 2)}/5` : 'Sin calificaciones'),
  ].join('');
}

// ---------- Gráficos ----------
function base(ejeX = true) {
  const texto = cssVar('--text-2');
  const grid = cssVar('--grid');
  return {
    responsive: true,
    maintainAspectRatio: false,
    animation: { duration: 250 },
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: cssVar('--surface'),
        titleColor: cssVar('--text'),
        bodyColor: cssVar('--text-2'),
        borderColor: cssVar('--border'),
        borderWidth: 1,
        padding: 10,
      },
    },
    scales: {
      x: { grid: { display: !ejeX, color: grid }, ticks: { color: texto }, border: { color: grid } },
      y: { grid: { color: grid }, ticks: { color: texto, precision: 0 }, border: { display: false }, beginAtZero: true },
    },
  };
}

function barras(id, etiquetas, valores, { horizontal = false, sufijo = '', decimales = 0 } = {}) {
  graficos[id]?.destroy();
  const opciones = base(!horizontal);
  if (horizontal) {
    opciones.indexAxis = 'y';
    opciones.scales.x.grid.display = true;
    opciones.scales.y.grid.display = false;
    opciones.interaction = { mode: 'nearest', axis: 'y', intersect: false };
  } else {
    opciones.interaction = { mode: 'nearest', axis: 'x', intersect: false };
  }
  opciones.plugins.tooltip.callbacks = { label: (c) => ` ${num(c.raw, decimales)}${sufijo}` };
  graficos[id] = new Chart(document.getElementById(id), {
    type: 'bar',
    data: {
      labels: etiquetas,
      datasets: [{
        data: valores,
        backgroundColor: cssVar('--series-1'),
        borderRadius: 4,
        borderSkipped: 'start',
        maxBarThickness: 28,
      }],
    },
    options: opciones,
  });
}

function pintarTendencia(puntos, granularidad) {
  graficos.tendencia?.destroy();
  const etiquetas = puntos.map((p) => {
    const d = new Date(p.periodo + 'T00:00:00');
    return (granularidad === 'semana' ? 'Sem. ' : '') + fmtDia.format(d);
  });
  const linea = (label, data, color) => ({
    label, data, borderColor: color, backgroundColor: color,
    borderWidth: 2, pointRadius: puntos.length > 60 ? 0 : 3, pointHoverRadius: 5, tension: 0,
  });
  const opciones = base();
  opciones.scales.x.ticks.maxRotation = 0;
  opciones.scales.x.ticks.autoSkip = true;
  graficos.tendencia = new Chart($('#c-tendencia'), {
    type: 'line',
    data: {
      labels: etiquetas,
      datasets: [
        linea('Registrados', puntos.map((p) => p.total), cssVar('--series-1')),
        linea('Resueltos', puntos.map((p) => p.resueltos), cssVar('--series-2')),
      ],
    },
    options: opciones,
  });
}

// ---------- Tablas de datos (verificación) ----------
function tabla(columnas, filas) {
  if (!filas.length) return '<p class="muted">Sin datos para estos filtros.</p>';
  return `<div class="table-wrap"><table><thead><tr>${columnas.map(([t, , n]) =>
    `<th class="${n ? 'num' : ''}">${t}</th>`).join('')}</tr></thead><tbody>${filas.map((f) =>
    `<tr>${columnas.map(([, fn, n]) => `<td class="${n ? 'num' : ''}">${fn(f)}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`;
}

function pintarTablas(d) {
  $('#t-tendencia').innerHTML = tabla([
    ['Periodo', (p) => p.periodo], ['Registrados', (p) => num(p.total), 1], ['Resueltos', (p) => num(p.resueltos), 1],
  ], d.tendencia);
  $('#t-categoria').innerHTML = tabla([
    ['Categoría', (c) => esc(c.categoria)], ['Total', (c) => num(c.total), 1], ['%', (c) => num(c.porcentaje, 2), 1],
    ['Resueltos', (c) => num(c.resueltos), 1], ['Tasa resolución %', (c) => num(c.tasaResolucion, 2), 1],
  ], d.porCategoria);
  $('#t-tiempo').innerHTML = tabla([
    ['Categoría', (c) => esc(c.categoria)], ['Resueltos', (c) => num(c.resueltos), 1],
    ['Promedio (h)', (c) => num(c.tiempoPromedioHoras, 2), 1],
  ], d.porCategoria);
  $('#t-prioridad').innerHTML = tabla([
    ['Prioridad', (p) => esc(p.prioridad)], ['Total', (p) => num(p.total), 1], ['%', (p) => num(p.porcentaje, 2), 1],
    ['SLA (h)', (p) => num(p.slaHoras), 1], ['Promedio (h)', (p) => num(p.tiempoPromedioHoras, 2), 1],
    ['Cumple SLA %', (p) => num(p.cumplimientoSla, 2), 1],
  ], d.porPrioridad);
  $('#t-estado').innerHTML = tabla([
    ['Estado', (e) => esc(e.estado)], ['Total', (e) => num(e.total), 1], ['%', (e) => num(e.porcentaje, 2), 1],
  ], d.porEstado);
  $('#recurrentes').innerHTML = tabla([
    ['Problema', (r) => esc(r.titulo)], ['Categoría', (r) => esc(r.categoria)],
    ['Ocurrencias', (r) => num(r.ocurrencias), 1], ['Promedio (h)', (r) => num(r.tiempoPromedioHoras, 1), 1],
  ], d.recurrentes);
  const TIPO = { EQUIPO: 'Equipo', APLICACION: 'Aplicación', SERVICIO: 'Servicio' };
  $('#por-elemento').innerHTML = tabla([
    ['Elemento', (e) => e.elementoId == null ? '<span class="muted">Sin elemento indicado</span>' : esc(e.elemento)],
    ['Tipo', (e) => esc(TIPO[e.tipo] ?? '—')], ['Total', (e) => num(e.total), 1],
    ['Pendientes', (e) => num(e.pendientes), 1], ['Promedio (h)', (e) => num(e.tiempoPromedioHoras, 1), 1],
    ['Cumple SLA %', (e) => num(e.cumplimientoSla, 2), 1],
  ], d.porElemento);

  const a = d.asistencia;
  $('#asistencia').innerHTML = tabla([
    ['Estado', (x) => x[0]], ['Consultas', (x) => num(x[1]), 1],
  ], [
    [badgeConsulta('RESPONDIDA'), a.respondidas], [badgeConsulta('SIN_RESULTADOS'), a.sinResultados],
    [badgeConsulta('ERROR'), a.errores], [badgeConsulta('PENDIENTE'), a.pendientes],
  ]) + `<p class="muted">${num(a.calificadas)} respuestas calificadas${a.calificacionPromedio != null
    ? ` · promedio ${num(a.calificacionPromedio, 2)} / 5` : ''}.</p>`;
}

/**
 * Primera respuesta = el incidente sale del estado inicial (calculado en SQL sobre el historial).
 * Incluye incidentes finalizados; los que no tienen objetivo o fecha conocida se muestran aparte.
 */
function pintarPrimeraRespuesta(p) {
  const cifra = (estado, valor) => `<span>${badgeSla(estado)} <strong>${num(valor)}</strong></span>`;
  return `<p style="display:flex;gap:18px;flex-wrap:wrap">
      <span>Primera respuesta${p.cumplimiento != null ? ` · cumplimiento <strong>${num(p.cumplimiento, 2)} %</strong>` : ''}:</span>
      ${cifra('EN_PLAZO', p.enPlazo)} ${cifra('POR_VENCER', p.porVencer)} ${cifra('VENCIDO', p.vencidos)}
      ${cifra('CUMPLIDO', p.cumplidos)} ${cifra('INCUMPLIDO', p.incumplidos)}
      ${cifra('SIN_OBJETIVO', p.sinObjetivo)} ${cifra('SIN_DATOS', p.sinDatos)}
    </p>`;
}

/** Situación actual frente al SLA: se calcula en el backend con la hora del servidor. */
function pintarSla(s) {
  $('#sla-nota').textContent = `"Por vencer" = queda ${num(s.fraccionAlerta * 100)} % o menos del plazo de la prioridad`;
  const cifra = (estado, valor) => `<span>${badgeSla(estado)} <strong>${num(valor)}</strong></span>`;
  $('#sla').innerHTML = `
    <p style="display:flex;gap:18px;flex-wrap:wrap">
      <span>Abiertos o en progreso: <strong>${num(s.abiertos)}</strong></span>
      ${cifra('EN_PLAZO', s.enPlazo)} ${cifra('POR_VENCER', s.porVencer)} ${cifra('VENCIDO', s.vencidos)}
    </p>` + pintarPrimeraRespuesta(s.primeraRespuesta) + (s.criticos.length ? tabla([
    ['#', (i) => `<a href="incidentes.html?id=${i.id}">${i.id}</a>`], ['Incidente', (i) => esc(i.titulo)],
    ['Prioridad', (i) => `${esc(i.prioridad)} <span class="muted">(${i.slaHoras} h)</span>`],
    ['Técnico', (i) => esc(i.responsable)], ['Estado', (i) => badgeEstadoIncidente(i.estado)],
    ['Vence', (i) => fecha(i.vence)], ['SLA', (i) => `${badgeSla(i.estadoSla)} <span class="muted">${horasRestantes(i.horasRestantes)}</span>`],
  ], s.criticos) : '<p class="muted">No hay incidentes vencidos ni por vencer con estos filtros.</p>');
}

function pintar(d) {
  pintarKpis(d.resumen, d.asistencia);
  pintarSla(d.sla);
  pintarTendencia(d.tendencia, d.granularidad);
  barras('c-categoria', d.porCategoria.map((c) => c.categoria), d.porCategoria.map((c) => c.total), { horizontal: true, sufijo: ' incidentes' });
  const conTiempo = d.porCategoria.filter((c) => c.tiempoPromedioHoras != null)
    .sort((x, y) => y.tiempoPromedioHoras - x.tiempoPromedioHoras);
  barras('c-tiempo', conTiempo.map((c) => c.categoria), conTiempo.map((c) => Number(c.tiempoPromedioHoras)),
    { horizontal: true, sufijo: ' h', decimales: 1 });
  barras('c-prioridad', d.porPrioridad.map((p) => p.prioridad), d.porPrioridad.map((p) => p.total), { sufijo: ' incidentes' });
  barras('c-estado', d.porEstado.map((e) => e.estado), d.porEstado.map((e) => e.total), { sufijo: ' incidentes' });
  pintarTablas(d);
}

// ---------- Eventos ----------
$('#filtros').addEventListener('submit', (e) => { e.preventDefault(); cargar(); });
$('#filtros').addEventListener('reset', () => setTimeout(cargar));
document.querySelectorAll('[data-tabla]').forEach((b) => b.addEventListener('click', () => {
  const t = document.getElementById('t-' + b.dataset.tabla);
  t.hidden = !t.hidden;
  b.textContent = t.hidden ? 'Ver datos' : 'Ocultar datos';
}));
document.addEventListener('tema-cambiado', () => ultimo && pintar(ultimo));
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => ultimo && pintar(ultimo));

iniciarLayout('index.html').then(iniciar);
