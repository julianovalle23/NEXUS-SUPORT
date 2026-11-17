/*
 * Cliente de la API REST de Spring Boot.
 * El frontend SOLO habla con Spring Boot: nunca con n8n, Qdrant ni OpenRouter.
 * Spring Boot sirve estos archivos y la API en el mismo origen (http://localhost:8080),
 * así que se usa la ruta relativa /api. Se puede sobrescribir con window.NEXUS_API.
 *
 * Sesión: cookie de sesión HTTP. Toda escritura envía el token CSRF que el servidor deja en la
 * cookie XSRF-TOKEN (cabecera X-XSRF-TOKEN). Los permisos los decide el servidor.
 */
const API_BASE = window.NEXUS_API || (location.protocol === 'file:' ? 'http://localhost:8080/api' : '/api');
const METODOS_LECTURA = ['GET', 'HEAD', 'OPTIONS'];

class ApiError extends Error {
  constructor(status, body) {
    super(body?.mensaje || `Error HTTP ${status}`);
    this.status = status;
    this.campos = body?.campos || null;
  }
}

function tokenCsrf() {
  const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return m ? decodeURIComponent(m[1]) : null;
}

function irALogin() {
  if (location.pathname.endsWith('login.html')) return;
  location.href = 'login.html?volver=' + encodeURIComponent(location.pathname.split('/').pop() + location.search);
}

async function request(method, path, body) {
  const opts = { method, headers: {}, credentials: 'same-origin' };
  if (body !== undefined) {
    opts.headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(body);
  }
  if (!METODOS_LECTURA.includes(method)) {
    const csrf = tokenCsrf();
    if (csrf) opts.headers['X-XSRF-TOKEN'] = csrf;
  }
  let res;
  try {
    res = await fetch(API_BASE + path, opts);
  } catch (e) {
    throw new ApiError(0, { mensaje: 'No hay conexión con el servidor' });
  }
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch (e) { data = { mensaje: text }; }
  // Sesión vencida o cerrada: volver al inicio de sesión (salvo en el propio intento de login)
  if (res.status === 401 && !path.startsWith('/auth/')) irALogin();
  if (!res.ok) throw new ApiError(res.status, data);
  return data;
}

/** Construye un query string omitiendo valores vacíos. */
function qs(params) {
  const p = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') p.append(k, v);
  });
  const s = p.toString();
  return s ? '?' + s : '';
}

const api = {
  sesion: () => request('GET', '/auth/sesion'),
  login: (correo, clave) => request('POST', '/auth/login', { correo, clave }),
  logout: () => request('POST', '/auth/logout'),
  cambiarClave: (actual, nueva) => request('PUT', '/auth/clave', { actual, nueva }),

  usuarios: () => request('GET', '/usuarios'),
  crearUsuario: (datos) => request('POST', '/usuarios', datos),
  actualizarUsuario: (id, datos) => request('PUT', `/usuarios/${id}`, datos),
  asignarClave: (id, clave) => request('PUT', `/usuarios/${id}/clave`, { clave }),

  catalogos: () => request('GET', '/catalogos'),

  incidentes: (filtros) => request('GET', '/incidentes' + qs(filtros)),
  incidente: (id) => request('GET', `/incidentes/${id}`),
  crearIncidente: (datos) => request('POST', '/incidentes', datos),
  actualizarIncidente: (id, datos) => request('PUT', `/incidentes/${id}`, datos),
  cambiarEstadoIncidente: (id, estadoId) => request('PATCH', `/incidentes/${id}/estado`, { estadoId }),

  solicitarAsistencia: (id, pregunta) =>
    request('POST', `/incidentes/${id}/asistencia`, pregunta === undefined ? {} : { pregunta }),
  historialIncidente: (id) => request('GET', `/incidentes/${id}/consultas`),
  consultas: (filtros) => request('GET', '/consultas' + qs(filtros)),
  calificar: (respuestaId, puntuacion, comentario) =>
    request('PUT', `/respuestas/${respuestaId}/calificacion`, { puntuacion, comentario }),

  articulos: (filtros) => request('GET', '/articulos' + qs(filtros || {})),
  crearArticulo: (datos) => request('POST', '/articulos', datos),
  actualizarArticulo: (id, datos) => request('PUT', `/articulos/${id}`, datos),
  estadoArticulo: (id, activo, verificado) => request('PATCH', `/articulos/${id}/estado`, { activo, verificado }),
  reindexar: (forzar) => request('POST', '/articulos/reindexar' + qs({ forzar })),

  dashboard: (filtros) => request('GET', '/analitica/dashboard' + qs(filtros)),
  sla: (filtros) => request('GET', '/analitica/sla' + qs(filtros || {})),
  conocimiento: (filtros) => request('GET', '/analitica/conocimiento' + qs(filtros || {})),
};
