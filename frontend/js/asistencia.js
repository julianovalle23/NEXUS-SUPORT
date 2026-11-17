/* Renderizado de consultas inteligentes (respuesta, pasos, fuentes, calificación). */

function renderFuentes(fuentes) {
  if (!fuentes?.length) return '';
  return `<div style="margin-top:8px"><strong>Fuentes y similitud</strong>${fuentes.map((f) => `
    <div class="source">
      <span><a href="articulos.html#art-${f.articuloId}">#${f.articuloId}</a> ${esc(f.titulo)}</span>
      <span class="muted" style="white-space:nowrap"><span class="simbar"><b style="width:${Math.round(f.similitud * 100)}%"></b></span>${num(f.similitud, 4)}</span>
    </div>`).join('')}</div>`;
}

function renderEstrellas(respuesta) {
  const actual = respuesta.calificacion?.puntuacion ?? 0;
  return `<div class="stars" data-respuesta="${respuesta.id}" role="group" aria-label="Calificar utilidad">
    ${[1, 2, 3, 4, 5].map((n) =>
      `<button type="button" data-valor="${n}" class="${n <= actual ? 'on' : ''}" aria-label="${n} de 5">★</button>`).join('')}
  </div>
  ${actual ? `<span class="muted">Calificada con ${actual}/5${respuesta.calificacion.comentario ? ' · ' + esc(respuesta.calificacion.comentario) : ''}</span>`
           : '<span class="muted">Califique la utilidad de esta respuesta</span>'}`;
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
      ${r ? `<div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap">${renderEstrellas(r)}</div>` : ''}`;
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
      const comentario = prompt('Comentario opcional sobre la respuesta:') ?? '';
      try {
        await api.calificar(Number(grupo.dataset.respuesta), Number(btn.dataset.valor), comentario);
        toast('Calificación guardada');
        alGuardar?.();
      } catch (err) { mostrarError(err); }
    });
  });
}
