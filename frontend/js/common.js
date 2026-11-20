/* Utilidades compartidas por todas las páginas. */

/** Escapa texto antes de insertarlo como HTML (los datos vienen de usuarios y del modelo). */
function esc(v) {
  return String(v ?? '').replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

const fmtFecha = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
const fmtDia = new Intl.DateTimeFormat('es-CO', { day: '2-digit', month: 'short' });

function fecha(iso) {
  return iso ? fmtFecha.format(new Date(iso)) : '—';
}

function num(v, dec = 0) {
  if (v === null || v === undefined) return '—';
  return Number(v).toLocaleString('es-CO', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

function toast(msg, tipo) {
  let box = document.getElementById('toasts');
  if (!box) {
    box = document.createElement('div');
    box.id = 'toasts';
    box.setAttribute('role', 'status');
    document.body.appendChild(box);
  }
  const t = document.createElement('div');
  t.className = 'toast' + (tipo === 'err' ? ' err' : '');
  t.textContent = msg;
  box.appendChild(t);
  setTimeout(() => t.remove(), 4500);
}

function mostrarError(e) {
  console.error(e);
  const extra = e.campos ? ': ' + Object.values(e.campos).join(', ') : '';
  toast((e.message || 'Error inesperado') + extra, 'err');
}

const ESTADO_INCIDENTE_BADGE = { 'Abierto': 'warn', 'En progreso': 'info', 'Resuelto': 'ok', 'Cerrado': '' };
const ESTADO_CONSULTA = {
  PENDIENTE: { txt: 'Pendiente', cls: 'info' },
  RESPONDIDA: { txt: 'Respondida', cls: 'ok' },
  SIN_RESULTADOS: { txt: 'Sin resultados', cls: 'warn' },
  ERROR: { txt: 'Error', cls: 'err' },
};

function badgeEstadoIncidente(nombre) {
  return `<span class="badge ${ESTADO_INCIDENTE_BADGE[nombre] ?? ''}">${esc(nombre)}</span>`;
}

function badgeConsulta(estado) {
  const e = ESTADO_CONSULTA[estado] || { txt: estado, cls: '' };
  return `<span class="badge ${e.cls}">${esc(e.txt)}</span>`;
}

function llenarSelect(select, items, { vacio, valor = 'id', texto = 'nombre' } = {}) {
  select.innerHTML = (vacio ? `<option value="">${esc(vacio)}</option>` : '') +
    items.map((i) => `<option value="${esc(i[valor])}">${esc(i[texto])}</option>`).join('');
}

const NOMBRE_ROL = { SOLICITANTE: 'Solicitante', TECNICO: 'Técnico', ADMIN: 'Administrador' };
const PERSONAL = ['TECNICO', 'ADMIN'];

/** Páginas visibles por perfil. La restricción real la aplica el servidor en cada endpoint. */
const PAGINAS = [
  ['index.html', 'Dashboard', PERSONAL],
  ['incidentes.html', 'Incidentes', ['SOLICITANTE', ...PERSONAL]],
  ['alertas.html', 'Alertas SLA', PERSONAL],
  ['problemas.html', 'Problemas', PERSONAL],
  ['articulos.html', 'Base de conocimiento', PERSONAL],
  ['importaciones.html', 'Importar artículos', ['ADMIN']],
  ['consultas.html', 'Historial IA', PERSONAL],
  ['usuarios.html', 'Usuarios', ['ADMIN']],
];

/** Cuenta de la sesión actual ({ id, nombre, correo, rol }); se fija en iniciarLayout. */
let SESION = null;

function tieneRol(...roles) {
  return SESION != null && roles.includes(SESION.rol);
}

function paginaInicial(rol) {
  return PAGINAS.find(([, , roles]) => roles.includes(rol))[0];
}

/**
 * Verifica la sesión, dibuja la barra de navegación según el perfil y aplica el tema.
 * Devuelve una promesa con la cuenta; si no hay sesión redirige al inicio de sesión.
 * Los elementos con data-roles="TECNICO ADMIN" se ocultan a los demás perfiles.
 */
async function iniciarLayout(activa) {
  aplicarTema();
  let s;
  try { s = await api.sesion(); } catch (e) { mostrarError(e); return new Promise(() => {}); }
  if (!s.autenticado) { irALogin(); return new Promise(() => {}); }
  SESION = s.usuario;
  const pagina = PAGINAS.find(([href]) => href === activa);
  if (pagina && !pagina[2].includes(SESION.rol)) {
    location.replace(paginaInicial(SESION.rol));
    return new Promise(() => {});
  }
  document.querySelectorAll('[data-roles]').forEach((el) => {
    el.hidden = !el.dataset.roles.split(' ').includes(SESION.rol);
  });

  const header = document.createElement('header');
  header.className = 'topbar';
  header.innerHTML = `
    <div class="brand">NEXUS <span>Support</span></div>
    <nav class="nav">${PAGINAS.filter(([, , roles]) => roles.includes(SESION.rol)).map(([href, txt]) =>
      `<a href="${href}" class="${href === activa ? 'active' : ''}">${
        href === 'incidentes.html' && SESION.rol === 'SOLICITANTE' ? 'Mis incidentes' : txt}</a>`).join('')}</nav>
    <div class="spacer"></div>
    <span class="cuenta" title="${esc(SESION.correo)}">${esc(SESION.nombre)} <span class="badge info">${esc(NOMBRE_ROL[SESION.rol])}</span></span>
    <button class="theme-btn" type="button" id="btn-clave" title="Cambiar mi contraseña">Contraseña</button>
    <button class="theme-btn" type="button" id="btn-salir" title="Cerrar sesión">Salir</button>
    <button class="theme-btn" type="button" id="btn-tema" title="Cambiar tema">◐</button>`;
  document.body.prepend(header);

  header.querySelector('#btn-tema').addEventListener('click', alternarTema);
  header.querySelector('#btn-salir').addEventListener('click', async () => {
    try { await api.logout(); } catch (e) { /* la sesión ya no existe */ }
    location.href = 'login.html';
  });
  header.querySelector('#btn-clave').addEventListener('click', abrirCambioClave);
  return SESION;
}

function abrirCambioClave() {
  let dlg = document.getElementById('dlg-clave-propia');
  if (!dlg) {
    dlg = document.createElement('dialog');
    dlg.id = 'dlg-clave-propia';
    dlg.innerHTML = `
      <form method="dialog">
        <h2>Cambiar mi contraseña</h2>
        <div class="field"><label for="cp-actual">Contraseña actual</label>
          <input id="cp-actual" name="actual" type="password" autocomplete="current-password" required></div>
        <div class="field"><label for="cp-nueva">Nueva contraseña (8 a 72 caracteres)</label>
          <input id="cp-nueva" name="nueva" type="password" autocomplete="new-password" minlength="8" maxlength="72" required></div>
        <div class="field"><label for="cp-repetir">Repetir nueva contraseña</label>
          <input id="cp-repetir" name="repetir" type="password" autocomplete="new-password" required></div>
        <div class="errors"></div>
        <div class="actions">
          <button class="btn" type="button" data-cerrar>Cancelar</button>
          <button class="btn primary" type="submit">Guardar</button>
        </div>
      </form>`;
    document.body.appendChild(dlg);
    const form = dlg.querySelector('form');
    dlg.querySelector('[data-cerrar]').addEventListener('click', () => dlg.close());
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const errores = form.querySelector('.errors');
      if (form.nueva.value !== form.repetir.value) { errores.textContent = 'Las contraseñas nuevas no coinciden'; return; }
      try {
        await api.cambiarClave(form.actual.value, form.nueva.value);
        dlg.close();
        toast('Contraseña actualizada');
      } catch (err) {
        errores.textContent = err.campos ? Object.values(err.campos).join(' · ') : err.message;
      }
    });
  }
  dlg.querySelector('form').reset();
  dlg.querySelector('.errors').textContent = '';
  dlg.showModal();
}

function aplicarTema() {
  let tema = null;
  try { tema = localStorage.getItem('nexus-tema'); } catch (e) { /* almacenamiento no disponible */ }
  if (tema) document.documentElement.dataset.theme = tema;
}

function alternarTema() {
  const oscuro = document.documentElement.dataset.theme
    ? document.documentElement.dataset.theme === 'dark'
    : matchMedia('(prefers-color-scheme: dark)').matches;
  const nuevo = oscuro ? 'light' : 'dark';
  document.documentElement.dataset.theme = nuevo;
  try { localStorage.setItem('nexus-tema', nuevo); } catch (e) { /* sin persistencia */ }
  document.dispatchEvent(new CustomEvent('tema-cambiado'));
}

function cssVar(nombre) {
  return getComputedStyle(document.documentElement).getPropertyValue(nombre).trim();
}

const ESTADO_SLA = {
  EN_PLAZO: { txt: 'En plazo', cls: 'ok' },
  POR_VENCER: { txt: 'Por vencer', cls: 'warn' },
  VENCIDO: { txt: 'Vencido', cls: 'err' },
  CUMPLIDO: { txt: 'SLA cumplido', cls: 'ok' },
  INCUMPLIDO: { txt: 'SLA incumplido', cls: 'err' },
  // Solo primera respuesta
  SIN_OBJETIVO: { txt: 'Sin objetivo', cls: '' },
  SIN_DATOS: { txt: 'Sin datos', cls: '' },
};

function badgeSla(estado) {
  const e = ESTADO_SLA[estado] || { txt: estado ?? '—', cls: '' };
  return `<span class="badge ${e.cls}">${esc(e.txt)}</span>`;
}

/** Horas restantes (negativas = horas de retraso) en texto legible. */
function horasRestantes(h) {
  if (h === null || h === undefined) return '—';
  const v = Number(h);
  return v >= 0 ? `quedan ${num(v, 1)} h` : `vencido hace ${num(-v, 1)} h`;
}
