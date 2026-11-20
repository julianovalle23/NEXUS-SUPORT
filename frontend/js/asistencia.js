/* Renderizado de consultas inteligentes (respuesta, pasos, fuentes, calificación). */

function filaFuente(f) {
  return `
    <div class="source">
      <span><a href="articulos.html#art-${f.articuloId}">#${f.articuloId}</a> ${esc(f.titulo)}</span>
      <span class="muted" style="white-space:nowrap"><span class="simbar"><b style="width:${Math.round(f.similitud * 100)}%"></b></span>${num(f.similitud, 4)}</span>
    </div>`;
}

/**
 * Fuentes citadas (evidencia de la respuesta) y, plegados aparte, los candidatos recuperados que el
 * modelo recibió pero no citó. Respuestas antiguas sin el campo "usada" se tratan como citadas.
 */
function renderFuentes(fuentes) {
  if (!fuentes?.length) return '';
  const usadas = fuentes.filter((f) => f.usada !== false);
  const candidatas = fuentes.filter((f) => f.usada === false);
  const citadas = usadas.length
    ? `<div style="margin-top:8px"><strong>Fuentes citadas y similitud</strong>${usadas.map(filaFuente).join('')}</div>`
    : '';
  const otras = candidatas.length
    ? `<details class="candidatas" style="margin-top:6px"><summary class="muted">Otros artículos recuperados, no citados por el asistente (${candidatas.length})</summary>${candidatas.map(filaFuente).join('')}</details>`
    : '';
  return citadas + otras;
}

/**
 * Estrellas de utilidad y, para respuestas con fuentes ya calificadas, la pregunta
 * "¿resolvió el incidente?". Ambas guardan la calificación completa (el PUT la reemplaza),
 * por eso cada grupo lleva los valores actuales en data-*.
 */
function renderEstrellas(respuesta, conFuentes) {
  const k = respuesta.calificacion;
  const actual = k?.puntuacion ?? 0;
  const datos = `data-respuesta="${respuesta.id}" data-puntuacion="${actual}"
    data-comentario="${esc(k?.comentario ?? '')}" data-resolvio="${k?.resolvio ?? ''}"`;
  const resolvio = conFuentes && actual ? `<span class="resolvio" ${datos} role="group" aria-label="¿Resolvió el incidente?">
      <span class="muted">¿La solución resolvió el incidente?</span>
      <button type="button" class="btn-mini ${k.resolvio === true ? 'on' : ''}" data-resolvio-valor="true">Sí</button>
      <button type="button" class="btn-mini ${k.resolvio === false ? 'on' : ''}" data-resolvio-valor="false">No</button>
    </span>` : '';
  return `<div class="stars" ${datos} role="group" aria-label="Calificar utilidad">
    ${[1, 2, 3, 4, 5].map((n) =>
      `<button type="button" data-valor="${n}" class="${n <= actual ? 'on' : ''}" aria-label="${n} de 5">★</button>`).join('')}
  </div>
  ${actual ? `<span class="muted">Calificada con ${actual}/5${k.comentario ? ' · ' + esc(k.comentario) : ''}</span>`
           : '<span class="muted">Califique la utilidad de esta respuesta</span>'}
  ${resolvio}`;
}

/** data-resolvio guarda "true", "false" o "" (no indicado). */
function leerResolvio(grupo) {
  return grupo.dataset.resolvio === '' ? null : grupo.dataset.resolvio === 'true';
}

function renderConsulta(c, { conIncidente = false } = {}) {
  const r = c.respuesta;
  let cuerpo;
  if (c.estado === 'ERROR') {
    cuerpo = `<div class="answer err">${esc(c.detalleError || 'Error en la asistencia')}</div>`;
  } else if (c.estado === 'PENDIENTE') {
    cuerpo = `<div class="answer">La consulta está en proceso.</div>`;
  } else {
    cuerpo = `
      <div class="answer ${c.estado === 'SIN_RESULTADOS' ? 'sin' : ''}">
        <div>${esc(r?.respuesta)}</div>
        ${r?.pasos?.length ? `<ol>${r.pasos.map((p) => `<li>${esc(p.replace(/^\d+[.)]\s*/, ''))}</li>`).join('')}</ol>` : ''}
        ${renderFuentes(r?.fuentes)}
      </div>
      ${r ? `<div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap">${renderEstrellas(r, c.estado === 'RESPONDIDA')}</div>` : ''}`;
  }
  return `<div class="history-item">
    <div style="display:flex;justify-content:space-between;gap:8px;flex-wrap:wrap">
      <span>${badgeConsulta(c.estado)} <span class="muted">Consulta #${c.id} · ${fecha(c.fecha)} · umbral ${num(c.umbral, 2)}</span></span>
      ${conIncidente ? `<a href="incidentes.html?id=${c.incidenteId}">Incidente #${c.incidenteId}</a>` : ''}
    </div>
    ${conIncidente ? `<div class="muted clip">${esc(c.incidenteTitulo)} — “${esc(c.pregunta)}”</div>` : ''}
    ${cuerpo}
  </div>`;
}

/** Conecta los botones de estrellas dentro de un contenedor. */
function activarCalificaciones(contenedor, alGuardar) {
  contenedor.querySelectorAll('.stars').forEach((grupo) => {
    grupo.addEventListener('click', async (e) => {
      const btn = e.target.closest('button[data-valor]');
      if (!btn) return;
      const comentario = prompt('Comentario opcional sobre la respuesta:', grupo.dataset.comentario);
      if (comentario === null) return; // cancelado: no se guarda nada
      try {
        await api.calificar(Number(grupo.dataset.respuesta), Number(btn.dataset.valor), comentario,
          leerResolvio(grupo));
        toast('Calificación guardada');
        alGuardar?.();
      } catch (err) { mostrarError(err); }
    });
  });
  contenedor.querySelectorAll('.resolvio').forEach((grupo) => {
    grupo.addEventListener('click', async (e) => {
      const btn = e.target.closest('button[data-resolvio-valor]');
      if (!btn) return;
      try {
        await api.calificar(Number(grupo.dataset.respuesta), Number(grupo.dataset.puntuacion),
          grupo.dataset.comentario, btn.dataset.resolvioValor === 'true');
        toast('Resultado guardado');
        alGuardar?.();
      } catch (err) { mostrarError(err); }
    });
  });
}
