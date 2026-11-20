// Pruebas unitarias de los nodos Code de los dos workflows de n8n (dobles de $input, $(), $env y $runIndex).
// NO llaman a servicios reales: no son evidencia de funcionamiento integral. Ver docs/n8n/workflows-nexus.md.
window.__correr = async () => {
const WI = await (await fetch('../workflows/nexus-indexacion.json')).json();
const WRk = await (await fetch('../workflows/nexus-rag.json')).json();
const cod = (wf, p) => wf.nodes.filter(n => n.type === 'n8n-nodes-base.code').map(n => [p + n.name, n.parameters.jsCode]);
const codigo = Object.fromEntries([...cod(WI, 'I:'), ...cod(WRk, 'R:')]);
const RAG = new Set(['Configuración RAG', 'Validar embedding', 'Validar búsqueda', 'Construir contexto', 'Validar respuesta']);
const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;
const resultados = [];
const compilar = (nombre) => new AsyncFunction('$input', '$', '$env', '$runIndex', codigo[nombre]);
const item = (json, pairedItem) => (pairedItem === undefined ? { json } : { json, pairedItem });

async function ejecutar(nombre, { input = [], datos = {}, env = {}, runIndex = 0, flujo } = {}) {
  const clave = (flujo ?? (RAG.has(nombre) ? 'R' : 'I')) + ':' + nombre;
  const runs = {};
  for (const [k, v] of Object.entries(datos)) {
    runs[k] = Array.isArray(v) ? v.map(r => r.map(x => (x.json ? x : item(x)))) : [[item(v)]];
  }
  const $ = (n) => {
    const r = runs[n];
    return {
      get isExecuted() { return !!r; },
      first() { if (!r) throw new Error(`Nodo ${n} sin ejecutar`); return r[r.length - 1][0]; },
      all(b = 0, run) { if (!r) throw new Error(`Nodo ${n} sin ejecutar`); return r[run ?? r.length - 1] ?? []; },
    };
  };
  const items = input.map(x => (x.json ? x : item(x)));
  const $input = { first: () => items[0], all: () => items };
  const $env = env === 'bloqueado' ? new Proxy({}, { get() { throw new Error('access to env vars denied'); } }) : env;
  return compilar(clave)($input, $, $env, runIndex);
}
async function prueba(titulo, fn) {
  try { await fn(); resultados.push(['OK', titulo]); }
  catch (e) { resultados.push(['FALLA', titulo, String(e && e.message || e)]); }
}
const igual = (a, b, m) => { if (JSON.stringify(a) !== JSON.stringify(b)) throw new Error(`${m}: ${JSON.stringify(a)} != ${JSON.stringify(b)}`); };
const verdad = (c, m) => { if (!c) throw new Error(m); };
async function lanza(promesa, prefijo) {
  try { await promesa; } catch (e) { verdad(String(e.message).startsWith(prefijo), `se esperaba ${prefijo} y llegó: ${e.message}`); return; }
  throw new Error(`se esperaba error ${prefijo}`);
}
const HASH = 'a'.repeat(64), HASH2 = 'b'.repeat(64);
const M = 'openai/text-embedding-3-small';
const vec = (n = 1536, x = 0.01) => Array(n).fill(x);

await prueba('todos los nodos Code compilan', async () => { Object.keys(codigo).forEach(compilar); });

// ---------------- configuración indexación
let cfgI;
await prueba('config indexación por defecto', async () => {
  cfgI = (await ejecutar('Configuración indexación', { datos: { 'Webhook indexación': { body: {} } } }))[0].json;
  igual([cfgI.dimension, cfgI.forzar, cfgI.origen, cfgI.coleccionUrl, cfgI.loteEmbeddings],
    [1536, false, 'entorno', 'http://host.docker.internal:6333/collections/articulos_conocimiento', 32], 'cfg');
});
await prueba('config indexación: $env bloqueado', async () => {
  const c = (await ejecutar('Configuración indexación', { env: 'bloqueado', datos: { 'Webhook indexación': { body: { forzar: true } } } }))[0].json;
  igual([c.origen.startsWith('valores por defecto'), c.forzar], [true, true], 'origen');
});
await prueba('config indexación: entorno con barra final', async () => {
  const c = (await ejecutar('Configuración indexación', { env: { NEXUS_QDRANT_URL: 'http://q:6333/' }, datos: { 'Webhook indexación': { body: {} } } }))[0].json;
  igual(c.coleccionUrl, 'http://q:6333/collections/articulos_conocimiento', 'url');
});
await prueba('config indexación: forzar inválido', () => lanza(ejecutar('Configuración indexación', { datos: { 'Webhook indexación': { body: { forzar: 'si' } } } }), '[VALIDACION]'));
await prueba('config indexación: cuerpo array', () => lanza(ejecutar('Configuración indexación', { datos: { 'Webhook indexación': { body: [] } } }), '[VALIDACION]'));
await prueba('config: dimensión inválida', () => lanza(ejecutar('Configuración indexación', { env: { NEXUS_EMBEDDING_DIMENSION: '15.5' }, datos: { 'Webhook indexación': { body: {} } } }), '[CONFIGURACION]'));
await prueba('config: colección inválida', () => lanza(ejecutar('Configuración indexación', { env: { NEXUS_QDRANT_COLLECTION: 'a/b' }, datos: { 'Webhook indexación': { body: {} } } }), '[CONFIGURACION]'));
await prueba('config: url inválida', () => lanza(ejecutar('Configuración indexación', { env: { NEXUS_BACKEND_URL: 'ftp://x' }, datos: { 'Webhook indexación': { body: {} } } }), '[CONFIGURACION]'));
const CI = { 'Configuración indexación': () => cfgI };

// ---------------- colección / creación / índice
const colOk = { statusCode: 200, body: { result: { config: { params: { vectors: { size: 1536, distance: 'Cosine' } } }, payload_schema: { fuente_valida: { data_type: 'bool' } } } } };
await prueba('colección existente válida', async () => igual((await ejecutar('Validar colección', { input: [colOk], datos: { 'Configuración indexación': cfgI } }))[0].json, { crear: false }, 'r'));
await prueba('colección 404 → crear', async () => igual((await ejecutar('Validar colección', { input: [{ statusCode: 404, body: {} }], datos: { 'Configuración indexación': cfgI } }))[0].json, { crear: true }, 'r'));
await prueba('colección dimensión distinta', () => lanza(ejecutar('Validar colección', { input: [{ statusCode: 200, body: { result: { config: { params: { vectors: { size: 768, distance: 'Cosine' } } } } } }], datos: { 'Configuración indexación': cfgI } }), '[CONFIGURACION]'));
await prueba('colección métrica Dot', () => lanza(ejecutar('Validar colección', { input: [{ statusCode: 200, body: { result: { config: { params: { vectors: { size: 1536, distance: 'Dot' } } } } } }], datos: { 'Configuración indexación': cfgI } }), '[CONFIGURACION]'));
await prueba('colección índice de otro tipo', () => lanza(ejecutar('Validar colección', { input: [{ statusCode: 200, body: { result: { config: { params: { vectors: { size: 1536, distance: 'Cosine' } } }, payload_schema: { fuente_valida: { data_type: 'keyword' } } } } }], datos: { 'Configuración indexación': cfgI } }), '[CONFIGURACION]'));
await prueba('colección vectores con nombre', () => lanza(ejecutar('Validar colección', { input: [{ statusCode: 200, body: { result: { config: { params: { vectors: { a: {} } } } } } }], datos: { 'Configuración indexación': cfgI } }), '[CONFIGURACION]'));
await prueba('colección HTTP 500', () => lanza(ejecutar('Validar colección', { input: [{ statusCode: 500, body: 'x' }], datos: { 'Configuración indexación': cfgI } }), '[QDRANT]'));
await prueba('colección error de red', () => lanza(ejecutar('Validar colección', { input: [{ error: { message: 'ECONNREFUSED' } }], datos: { 'Configuración indexación': cfgI } }), '[QDRANT] No se pudo leer la colección: ECONNREFUSED'));
await prueba('creación ok', async () => igual((await ejecutar('Validar creación', { input: [{ statusCode: 200, body: { result: true } }] }))[0].json, { creada: true }, 'r'));
await prueba('creación en conflicto', () => lanza(ejecutar('Validar creación', { input: [{ statusCode: 409, body: { status: { error: 'Collection already exists' } } }] }), '[QDRANT_CONFLICTO]'));
await prueba('creación falla', () => lanza(ejecutar('Validar creación', { input: [{ statusCode: 500, body: {} }] }), '[QDRANT]'));
await prueba('índice ok', async () => igual((await ejecutar('Validar índice', { input: [{ statusCode: 200, body: { status: 'ok' } }] }))[0].json, { indice: 'fuente_valida' }, 'r'));
await prueba('índice falla', () => lanza(ejecutar('Validar índice', { input: [{ statusCode: 400, body: { status: 'error' } }] }), '[QDRANT]'));

// ---------------- artículos
const art = (id, extra = {}) => ({ id, titulo: 'T' + id, categoria: 'Red', texto: 'texto ' + id, hash: HASH, fuenteValida: true, ...extra });
let articulos;
await prueba('artículos válidos', async () => {
  const r = (await ejecutar('Validar artículos', { input: [{ total: 3, articulos: [art(1), art(2, { categoria: null }), art(3, { fuenteValida: false })] }] }))[0].json;
  articulos = r.articulos;
  igual([r.siguiente, r.articulos.length, r.articulos[1].categoria], [null, 3, null], 'r');
});
await prueba('artículos: total no cuadra', () => lanza(ejecutar('Validar artículos', { input: [{ total: 2, articulos: [art(1)] }] }), '[BACKEND_CONTRATO]'));
await prueba('artículos: sin lista', () => lanza(ejecutar('Validar artículos', { input: [{ total: 0 }] }), '[BACKEND_CONTRATO]'));
await prueba('artículos: duplicado y hash malo', async () => {
  try { await ejecutar('Validar artículos', { input: [{ total: 3, articulos: [art(1), art(1), art(2, { hash: 'x' })] }] }); throw new Error('no lanzó'); }
  catch (e) { verdad(/duplicado/.test(e.message) && /hash/.test(e.message) && e.message.startsWith('[BACKEND_CONTRATO]'), e.message); }
});
await prueba('artículos: más de 5 errores', async () => {
  try { await ejecutar('Validar artículos', { input: [{ total: 7, articulos: Array.from({ length: 7 }, (_, i) => art(i + 1, { texto: '' })) }] }); throw new Error('no lanzó'); }
  catch (e) { verdad(/\(y 2 más\)/.test(e.message), e.message); }
});
await prueba('artículos: elemento null', () => lanza(ejecutar('Validar artículos', { input: [{ total: 1, articulos: [null] }] }), '[BACKEND_CONTRATO]'));
await prueba('artículos: error HTTP', () => lanza(ejecutar('Validar artículos', { input: [{ error: { httpCode: '401', message: 'Unauthorized' } }] }), '[BACKEND] No se pudieron leer los artículos: HTTP 401 - Unauthorized'));
const VA = () => ({ total: articulos.length, articulos, siguiente: null });

// ---------------- detectar cambios (paginado)
const pagina = (puntos, next = null) => ({ status: 'ok', result: { points: puntos, next_page_offset: next } });
const pt = (id, hash = HASH, modelo = M) => ({ id, payload: { hash, modelo } });
const datosDC = (paginas) => ({ 'Configuración indexación': cfgI, 'Validar artículos': VA(), 'Leer puntos de Qdrant': paginas.map(p => [item(p)]) });
await prueba('detectar: una página con cambios', async () => {
  const r = (await ejecutar('Detectar cambios', { datos: datosDC([pagina([pt(1), pt(2, HASH2), pt(9)])]) }))[0].json;
  igual([r.siguiente, r.upserts.map(a => a.id), r.eliminar, r.cuerpo.sinCambios, r.cuerpo.vectorizados, r.cuerpo.eliminados, r.cuerpo.totalArticulos, r.cuerpo.estado, r.cuerpo.resumen],
    [null, [2], [9], 1, [], [], 3, 'OK', { leidos: 3, fuentesValidas: 2, excluidos: 1, actualizados: 0, omitidos: 1, eliminados: 0, fallidos: 0 }], 'r');
});
await prueba('detectar: página intermedia devuelve cursor', async () => {
  const r = (await ejecutar('Detectar cambios', { datos: datosDC([pagina([pt(1)], 77)]) }))[0].json;
  igual(r.siguiente, 77, 'cursor');
});
await prueba('detectar: acumula dos páginas', async () => {
  const r = (await ejecutar('Detectar cambios', { runIndex: 1, datos: datosDC([pagina([pt(1)], 5), pagina([pt(2), pt(8)])]) }))[0].json;
  igual([r.siguiente, r.upserts.map(a => a.id), r.eliminar], [null, [], [8]], 'r');
});
await prueba('detectar: modelo distinto se revectoriza', async () => {
  const r = (await ejecutar('Detectar cambios', { datos: datosDC([pagina([pt(1, HASH, 'otro'), pt(2)])]) }))[0].json;
  igual(r.upserts.map(a => a.id), [1], 'r');
});
await prueba('detectar: forzar revectoriza todo', async () => {
  const r = (await ejecutar('Detectar cambios', { datos: { ...datosDC([pagina([pt(1), pt(2)])]), 'Configuración indexación': { ...cfgI, forzar: true } } }))[0].json;
  igual(r.upserts.map(a => a.id), [1, 2], 'r');
});
await prueba('detectar: límite de páginas', () => lanza(ejecutar('Detectar cambios', { datos: { ...datosDC([pagina([], 3)]), 'Configuración indexación': { ...cfgI, maxPaginas: 1 } } }), '[QDRANT]'));
await prueba('detectar: id ajeno', () => lanza(ejecutar('Detectar cambios', { datos: datosDC([pagina([{ id: 'uuid-x', payload: {} }])]) }), '[QDRANT_CONTRATO]'));
await prueba('detectar: id repetido entre páginas', () => lanza(ejecutar('Detectar cambios', { runIndex: 1, datos: datosDC([pagina([pt(1)], 4), pagina([pt(1)])]) }), '[QDRANT_CONTRATO]'));
await prueba('detectar: página con error', () => lanza(ejecutar('Detectar cambios', { datos: datosDC([{ error: { message: 'timeout' } }]) }), '[QDRANT] Falló la lectura de puntos: timeout'));
await prueba('detectar: página malformada', () => lanza(ejecutar('Detectar cambios', { datos: datosDC([{ status: 'ok', result: {} }]) }), '[QDRANT_CONTRATO]'));
await prueba('detectar: backend sin válidos protege la colección', () => lanza(ejecutar('Detectar cambios', { datos: { ...datosDC([pagina([pt(1)])]), 'Validar artículos': { articulos: [art(1, { fuenteValida: false })] } } }), '[BACKEND_CONTRATO]'));
await prueba('detectar: artículo demasiado largo', () => lanza(ejecutar('Detectar cambios', { datos: { ...datosDC([pagina([])]), 'Validar artículos': { articulos: [art(1, { texto: 'x'.repeat(30000) })] } } }), '[CONTENIDO]'));

// ---------------- lotes / embeddings / escritura / borrado
await prueba('preparar lotes', async () => {
  const ups = Array.from({ length: 70 }, (_, i) => ({ id: i + 1, texto: 't' }));
  const r = await ejecutar('Preparar lotes', { input: [{ upserts: ups }], datos: { 'Configuración indexación': cfgI } });
  igual(r.map(x => [x.json.lote, x.json.articulos.length]), [[0, 32], [1, 32], [2, 6]], 'lotes');
});
const lotes = [[item({ lote: 0, articulos: [art(1), art(2)] })]];
await prueba('validar embeddings: construye puntos', async () => {
  const r = await ejecutar('Validar embeddings', { input: [item({ data: [{ index: 1, embedding: vec() }, { index: 0, embedding: vec(1536, 0.2) }] }, { item: 0 })], datos: { 'Configuración indexación': cfgI, 'Preparar lotes': lotes } });
  igual([r[0].json.ids, r[0].json.points[0].vector[0], r[0].json.points[0].payload.modelo, r[0].json.points[1].payload.fuente_valida], [[1, 2], 0.2, M, true], 'r');
});
await prueba('validar embeddings: pairedItem numérico y en lista', async () => {
  const dos = [[item({ lote: 0, articulos: [art(1)] }), item({ lote: 1, articulos: [art(2)] })]];
  const r = await ejecutar('Validar embeddings', { input: [item({ data: [{ embedding: vec() }] }, [{ item: 1 }]), item({ data: [{ embedding: vec() }] }, 0)], datos: { 'Configuración indexación': cfgI, 'Preparar lotes': dos } });
  igual(r.map(x => x.json.ids[0]), [2, 1], 'orden');
});
const DL = { 'Configuración indexación': cfgI, 'Preparar lotes': lotes };
await prueba('validar embeddings: dimensión distinta (único lote) → error', () => lanza(ejecutar('Validar embeddings', { input: [item({ data: [{ embedding: vec(3) }, { embedding: vec(3) }] }, { item: 0 })], datos: DL }), '[EMBEDDINGS] Ningún lote obtuvo embeddings válidos (1 lotes): ' + M));
await prueba('validar embeddings: cantidad distinta', () => lanza(ejecutar('Validar embeddings', { input: [item({ data: [{ embedding: vec() }] }, { item: 0 })], datos: DL }), '[EMBEDDINGS] Ningún lote'));
await prueba('validar embeddings: valores no finitos', () => lanza(ejecutar('Validar embeddings', { input: [item({ data: [{ embedding: vec() }, { embedding: [...vec(1535), NaN] }] }, { item: 0 })], datos: DL }), '[EMBEDDINGS] Ningún lote'));
await prueba('validar embeddings: error del servicio', () => lanza(ejecutar('Validar embeddings', { input: [item({ error: { httpCode: 402, message: 'Payment Required' } }, { item: 0 })], datos: DL }), '[EMBEDDINGS] Ningún lote obtuvo embeddings válidos (1 lotes): Lote 1: HTTP 402 - Payment Required'));
await prueba('validar embeddings: modelo respondido sin prefijo es válido', async () => {
  const r = await ejecutar('Validar embeddings', { input: [item({ model: 'text-embedding-3-small', data: [{ embedding: vec() }, { embedding: vec() }] }, { item: 0 })], datos: DL });
  igual(r[0].json.fallosEmbeddings, [], 'sin fallos');
});
await prueba('validar embeddings: modelo distinto', () => lanza(ejecutar('Validar embeddings', { input: [item({ model: 'text-embedding-ada-002', data: [{ embedding: vec() }, { embedding: vec() }] }, { item: 0 })], datos: DL }), '[EMBEDDINGS] Ningún lote'));
await prueba('validar embeddings: lote parcial sigue y se informa', async () => {
  const dos = [[item({ lote: 0, articulos: [art(1)] }), item({ lote: 1, articulos: [art(2), art(3)] })]];
  const r = await ejecutar('Validar embeddings', { input: [item({ data: [{ embedding: vec() }] }, { item: 0 }), item({ error: { message: 'timeout' } }, { item: 1 })], datos: { ...DL, 'Preparar lotes': dos } });
  igual([r.length, r[0].json.ids, r[0].json.fallosEmbeddings.map(f => [f.lote, f.ids])], [1, [1], [[2, [2, 3]]]], 'r');
});
const cuerpoBase = { estado: 'OK', etapa: 'COMPLETADA', mensaje: 'Indexación completada', resumen: { leidos: 3, fuentesValidas: 3, excluidos: 0, actualizados: 0, omitidos: 0, eliminados: 0, fallidos: 0 }, vectorizados: [], eliminados: [], fallidos: [] };
const decision = { eliminar: [9], cuerpo: cuerpoBase };
const ok = { status: 'ok', result: { status: 'completed' } };
await prueba('validar escritura ok', async () => {
  const r = (await ejecutar('Validar escritura', { input: [item(ok, { item: 0 })], datos: { 'Validar embeddings': [[item({ lote: 0, ids: [1, 2], fallosEmbeddings: [] })]], '¿Hay artículos para vectorizar?': decision } }))[0].json;
  igual([r.completa, r.eliminar, r.cuerpo.estado, r.cuerpo.vectorizados, r.cuerpo.resumen.actualizados, r.cuerpo.resumen.fallidos], [true, [9], 'OK', [1, 2], 2, 0], 'r');
});
await prueba('validar escritura: lote no confirmado → ERROR parcial sin borrar', async () => {
  const r = (await ejecutar('Validar escritura', { input: [item(ok, { item: 0 }), item({ status: 'ok', result: { status: 'acknowledged' } }, { item: 1 })], datos: { 'Validar embeddings': [[item({ lote: 0, ids: [1], fallosEmbeddings: [] }), item({ lote: 1, ids: [2], fallosEmbeddings: [] })]], '¿Hay artículos para vectorizar?': decision } }))[0].json;
  igual([r.completa, r.cuerpo.estado, r.cuerpo.etapa, r.cuerpo.vectorizados, r.cuerpo.fallidos, r.cuerpo.eliminados, r.cuerpo.resumen.actualizados, r.cuerpo.resumen.fallidos, /\[QDRANT_CONTRATO\]/.test(r.cuerpo.mensaje)],
    [false, 'ERROR', 'INDEXACION_PARCIAL', [1], [2], [], 1, 1, true], 'r');
});
await prueba('validar escritura: suma fallos de embeddings', async () => {
  const fe = [{ lote: 2, ids: [5, 6], motivo: '[EMBEDDINGS] Lote 2: timeout' }];
  const r = (await ejecutar('Validar escritura', { input: [item(ok, { item: 0 })], datos: { 'Validar embeddings': [[item({ lote: 0, ids: [1], fallosEmbeddings: fe })]], '¿Hay artículos para vectorizar?': decision } }))[0].json;
  igual([r.completa, r.cuerpo.fallidos, r.cuerpo.resumen.fallidos, r.cuerpo.detalleFallos.length], [false, [5, 6], 2, 1], 'r');
});
await prueba('validar escritura: error HTTP en lote', async () => {
  const r = (await ejecutar('Validar escritura', { input: [item({ error: { message: 'ECONNRESET' } }, { item: 0 })], datos: { 'Validar embeddings': [[item({ lote: 0, ids: [1], fallosEmbeddings: [] })]], '¿Hay artículos para vectorizar?': decision } }))[0].json;
  igual([r.completa, r.cuerpo.estado, r.cuerpo.vectorizados], [false, 'ERROR', []], 'r');
});
await prueba('validar borrado ok', async () => {
  const r = (await ejecutar('Validar borrado', { input: [ok], datos: { '¿Hay vectores para eliminar?': decision } }))[0].json.cuerpo;
  igual([r.eliminados, r.resumen.eliminados, r.estado], [[9], 1, 'OK'], 'r');
});
await prueba('validar borrado falla', () => lanza(ejecutar('Validar borrado', { input: [{ error: { message: 'x' } }], datos: { '¿Hay vectores para eliminar?': decision } }), '[QDRANT]'));

// ---------------- RAG: configuración y solicitud
const WR = (body) => ({ 'Webhook RAG': { body } });
let cfgR;
await prueba('solicitud RAG válida con valores por defecto', async () => {
  cfgR = (await ejecutar('Configuración RAG', { datos: WR({ consultaId: 7, pregunta: '  Sin\u0007 internet ', incidente: { titulo: 'Red caída', categoria: 'Red' } }) }))[0].json;
  igual([cfgR.pregunta, cfgR.umbral, cfgR.topK, cfgR.minResultados, cfgR.textoBusqueda, cfgR.incidente.prioridad, cfgR.simularFalloEmbeddings, cfgR.modeloChat],
    ['Sin internet', 0.45, 5, 1, 'Red caída. Sin internet', null, false, 'openai/gpt-4o-mini'], 'r');
});
await prueba('solicitud sin incidente', async () => igual((await ejecutar('Configuración RAG', { datos: WR({ pregunta: 'xyz' }) }))[0].json.textoBusqueda, 'xyz', 't'));
for (const [titulo, body] of [
  ['pregunta vacía', { pregunta: '   ' }], ['sin pregunta', {}], ['cuerpo nulo', null], ['umbral fuera de rango', { pregunta: 'xyz', umbral: 2 }],
  ['umbral texto', { pregunta: 'xyz', umbral: '0.5' }], ['topK fuera de rango', { pregunta: 'xyz', topK: 9 }], ['minResultados > topK', { pregunta: 'xyz', topK: 3, minResultados: 4 }],
  ['consultaId negativo', { pregunta: 'xyz', consultaId: -1 }], ['simular no booleano', { pregunta: 'xyz', simularFalloEmbeddings: 'si' }],
  ['incidente array', { pregunta: 'xyz', incidente: [] }], ['título numérico', { pregunta: 'xyz', incidente: { titulo: 5 } }],
  ['pregunta larga', { pregunta: 'x'.repeat(5001) }], ['pregunta muy corta', { pregunta: 'ab' }], ['campo desconocido', { pregunta: 'xyz', otro: 1 }],
  ['incidente con campo desconocido', { pregunta: 'xyz', incidente: { titulo: 'a', x: 1 } }], ['incidente null explícito', { pregunta: 'xyz', incidente: [] }],
  ['topK 2', { pregunta: 'xyz', topK: 2 }], ['topK decimal', { pregunta: 'xyz', topK: 4.5 }], ['minResultados 0', { pregunta: 'xyz', minResultados: 0 }],
  ['umbral negativo', { pregunta: 'xyz', umbral: -0.1 }], ['umbral NaN', { pregunta: 'xyz', umbral: NaN }],
]) await prueba('solicitud: ' + titulo, () => lanza(ejecutar('Configuración RAG', { datos: WR(body) }), '[VALIDACION]'));
await prueba('solicitud: simulación usa host .invalid', async () => {
  const c = (await ejecutar('Configuración RAG', { datos: WR({ pregunta: 'xyz', simularFalloEmbeddings: true }) }))[0].json;
  igual([c.urlEmbeddings, c.simularFalloEmbeddings], ['http://servicio-embeddings-no-disponible.invalid/embeddings', true], 'url');
});
await prueba('solicitud: url normal de embeddings', async () => igual(cfgR.urlEmbeddings, 'https://openrouter.ai/api/v1/embeddings', 'url'));
await prueba('solicitud: contrato real de Spring Boot', async () => {
  const c = (await ejecutar('Configuración RAG', { datos: WR({ consultaId: 3, pregunta: 'No imprime', incidente: { titulo: 'Impresora', categoria: 'Hardware', prioridad: 'Alta' }, umbral: 0.45, topK: 5, minResultados: 1, simularFalloEmbeddings: false }) }))[0].json;
  igual([c.topK, c.minResultados, c.incidente.prioridad], [5, 1, 'Alta'], 'r');
});
await prueba('config RAG: topK min > max', () => lanza(ejecutar('Configuración RAG', { env: { NEXUS_TOPK_MIN: '6' }, datos: WR({ pregunta: 'xyz' }) }), '[CONFIGURACION]'));
const CRd = () => ({ 'Configuración RAG': cfgR });

await prueba('validar embedding ok', async () => igual((await ejecutar('Validar embedding', { input: [{ data: [{ embedding: vec() }] }], datos: CRd() }))[0].json.vector.length, 1536, 'v'));
await prueba('validar embedding: error', () => lanza(ejecutar('Validar embedding', { input: [{ error: { message: 'getaddrinfo ENOTFOUND' } }], datos: CRd() }), '[EMBEDDINGS]'));
await prueba('validar embedding: vacío', () => lanza(ejecutar('Validar embedding', { input: [{ data: [] }], datos: CRd() }), '[EMBEDDINGS_CONTRATO]'));
await prueba('validar embedding: modelo distinto', () => lanza(ejecutar('Validar embedding', { input: [{ model: 'otro-modelo', data: [{ embedding: vec() }] }], datos: CRd() }), '[EMBEDDINGS_CONTRATO]'));
await prueba('validar embedding: varios vectores', () => lanza(ejecutar('Validar embedding', { input: [{ data: [{ embedding: vec() }, { embedding: vec() }] }], datos: CRd() }), '[EMBEDDINGS_CONTRATO]'));
await prueba('validar embedding: dimensión', () => lanza(ejecutar('Validar embedding', { input: [{ data: [{ embedding: vec(3) }] }], datos: CRd() }), '[CONFIGURACION]'));

// ---------------- búsqueda
const qp = (id, score, modelo = M) => ({ id, score, payload: { articulo_id: id, titulo: 'T' + id, categoria: 'Red', modelo } });
let busqueda;
await prueba('búsqueda: umbral y modelo', async () => {
  busqueda = (await ejecutar('Validar búsqueda', { input: [{ status: 'ok', result: { points: [qp(1, 0.9), qp(5, 0.8, 'otro'), qp(2, 0.5), qp(3, 0.3)] } }], datos: CRd() }))[0].json;
  igual([busqueda.ids, busqueda.mejorSimilitud, busqueda.descartadosPorModelo, busqueda.cuerpo.auditoria.motivo, busqueda.cuerpo.consultaId],
    [[1, 2], 0.9, 1, 'BAJO_UMBRAL', 7], 'r');
});
await prueba('búsqueda: umbral exacto se acepta', async () => {
  const r = (await ejecutar('Validar búsqueda', { input: [{ status: 'ok', result: { points: [qp(1, 0.45)] } }], datos: CRd() }))[0].json;
  igual(r.ids, [1], 'ids');
});
await prueba('búsqueda: sin candidatos → cuerpo SIN_RESULTADOS', async () => {
  const r = (await ejecutar('Validar búsqueda', { input: [{ status: 'ok', result: { points: [qp(1, 0.2)] } }], datos: CRd() }))[0].json;
  igual([r.ids, r.cuerpo.estado, /mejor similitud encontrada: 0.2\)\./.test(r.cuerpo.respuesta)], [[], 'SIN_RESULTADOS', true], 'r');
});
await prueba('búsqueda: colección vacía', async () => {
  const r = (await ejecutar('Validar búsqueda', { input: [{ status: 'ok', result: { points: [] } }], datos: CRd() }))[0].json;
  igual([r.mejorSimilitud, r.cuerpo.respuesta.includes('de 0.45. Se recomienda')], [null, true], 'r');
});
await prueba('búsqueda: desordenada', () => lanza(ejecutar('Validar búsqueda', { input: [{ status: 'ok', result: { points: [qp(1, 0.5), qp(2, 0.9)] } }], datos: CRd() }), '[QDRANT_CONTRATO]'));
await prueba('búsqueda: score fuera de rango', () => lanza(ejecutar('Validar búsqueda', { input: [{ status: 'ok', result: { points: [qp(1, 1.5)] } }], datos: CRd() }), '[QDRANT_CONTRATO]'));
await prueba('búsqueda: articulo_id no coincide', () => lanza(ejecutar('Validar búsqueda', { input: [{ status: 'ok', result: { points: [{ id: 1, score: 0.9, payload: { articulo_id: 2, modelo: M } }] } }], datos: CRd() }), '[QDRANT_CONTRATO]'));
await prueba('búsqueda: demasiados resultados', () => lanza(ejecutar('Validar búsqueda', { input: [{ status: 'ok', result: { points: [1, 2, 3, 4, 5, 6].map(i => qp(i, 0.9)) } }], datos: CRd() }), '[QDRANT_CONTRATO]'));
await prueba('búsqueda: error', () => lanza(ejecutar('Validar búsqueda', { input: [{ error: { message: 'x' } }], datos: CRd() }), '[QDRANT]'));

// ---------------- fuentes
const fuente = (id, extra = {}) => ({ id, titulo: 'T' + id, problema: 'p', sintomas: 's', causa: 'c', solucion: 'sol <b>' + id, ...extra });
let ctx;
await prueba('fuentes: contexto delimitado', async () => {
  ctx = (await ejecutar('Construir contexto', { input: [{ total: 2, fuentes: [fuente(1), fuente(2)] }], datos: { ...CRd(), 'Validar búsqueda': busqueda } }))[0].json;
  const u = ctx.mensajes[1].content;
  igual([ctx.suficiente, ctx.fuentes.map(f => f.articuloId), ctx.descartadasPorBD, u.includes('<b>'), u.includes('\\u003cb\\u003e'), u.startsWith('<incidente>')],
    [true, [1, 2], 0, false, true, true], 'ctx');
});
await prueba('fuentes: recorte de documentos', async () => {
  const r = (await ejecutar('Construir contexto', { input: [{ total: 1, fuentes: [fuente(1, { solucion: 'x'.repeat(7000) })] }], datos: { ...CRd(), 'Validar búsqueda': busqueda } }))[0].json;
  verdad(r.mensajes[1].content.includes('x'.repeat(6000) + ' [...]') && !r.mensajes[1].content.includes('x'.repeat(6001)), 'recorte');
});
await prueba('fuentes: insuficientes → cuerpo', async () => {
  const r = (await ejecutar('Construir contexto', { input: [{ total: 0, fuentes: [] }], datos: { ...CRd(), 'Validar búsqueda': busqueda } }))[0].json;
  igual([r.suficiente, r.descartadasPorBD, r.cuerpo.auditoria.motivo, r.cuerpo.estado], [false, 2, 'FUENTES_INSUFICIENTES', 'SIN_RESULTADOS'], 'r');
});
await prueba('fuentes: no solicitada', () => lanza(ejecutar('Construir contexto', { input: [{ total: 1, fuentes: [fuente(99)] }], datos: { ...CRd(), 'Validar búsqueda': busqueda } }), '[VALIDACION_FUENTES]'));
await prueba('fuentes: campo no texto', () => lanza(ejecutar('Construir contexto', { input: [{ total: 1, fuentes: [fuente(1, { causa: null })] }], datos: { ...CRd(), 'Validar búsqueda': busqueda } }), '[VALIDACION_FUENTES]'));
await prueba('fuentes: total no cuadra', () => lanza(ejecutar('Construir contexto', { input: [{ total: 3, fuentes: [] }], datos: { ...CRd(), 'Validar búsqueda': busqueda } }), '[VALIDACION_FUENTES]'));

// ---------------- respuesta del modelo
const chat = (obj, extra = {}) => ({ model: 'openai/gpt-4o-mini', choices: [{ finish_reason: 'stop', message: { content: typeof obj === 'string' ? obj : JSON.stringify(obj) } }], ...extra });
const DR = () => ({ ...CRd(), 'Construir contexto': ctx });
await prueba('respuesta: RESPONDIDA solo con fuentes citadas', async () => {
  const r = (await ejecutar('Validar respuesta', { input: [chat({ sin_informacion: false, motivo: '', solucion: ' Renovar IP ', pasos: ['a', ' ', 'b'], fuentes_usadas: [2] })], datos: DR() }))[0].json.cuerpo;
  igual([r.estado, r.respuesta, r.pasos, r.fuentes.map(f => [f.articuloId, f.usada]), r.auditoria.candidatas.map(c => c.citada), r.consultaId, r.modelo],
    ['RESPONDIDA', 'Renovar IP', ['a', 'b'], [[2, true]], [false, true], 7, 'openai/gpt-4o-mini'], 'r');
});
await prueba('respuesta: JSON en bloque de código', async () => {
  const r = (await ejecutar('Validar respuesta', { input: [chat('```json\n' + JSON.stringify({ sin_informacion: false, motivo: '', solucion: 's', pasos: [], fuentes_usadas: [1] }) + '\n```')], datos: DR() }))[0].json.cuerpo;
  igual(r.estado, 'RESPONDIDA', 'estado');
});
await prueba('respuesta: sin información', async () => {
  const r = (await ejecutar('Validar respuesta', { input: [chat({ sin_informacion: true, motivo: 'falta X', solucion: '', pasos: [], fuentes_usadas: [] })], datos: DR() }))[0].json.cuerpo;
  igual([r.estado, r.auditoria.motivo, r.fuentes, r.respuesta.endsWith('falta X')], ['SIN_RESULTADOS', 'MODELO_SIN_INFORMACION', [], true], 'r');
});
for (const [titulo, salida, extra] of [
  ['cita ajena', { sin_informacion: false, motivo: '', solucion: 's', pasos: [], fuentes_usadas: [99] }],
  ['sin citas', { sin_informacion: false, motivo: '', solucion: 's', pasos: [], fuentes_usadas: [] }],
  ['solución vacía', { sin_informacion: false, motivo: '', solucion: ' ', pasos: [], fuentes_usadas: [1] }],
  ['esquema inválido', { sin_informacion: 'no', motivo: 1, solucion: 's', pasos: 'x', fuentes_usadas: ['1'] }],
  ['demasiados pasos', { sin_informacion: false, motivo: '', solucion: 's', pasos: Array(31).fill('p'), fuentes_usadas: [1] }],
  ['no JSON', 'hola'],
  ['array', '[]'],
  ['null', 'null'],
  ['truncada', { sin_informacion: false, motivo: '', solucion: 's', pasos: [], fuentes_usadas: [1] }, 'length'],
]) await prueba('respuesta: ' + titulo, async () => {
  const entrada = chat(salida);
  entrada.choices[0].finish_reason = extra ?? 'stop';
  await lanza(ejecutar('Validar respuesta', { input: [entrada], datos: DR() }), '[GENERACION_CONTRATO]');
});
await prueba('respuesta: sin contenido', () => lanza(ejecutar('Validar respuesta', { input: [{ choices: [] }], datos: DR() }), '[GENERACION_CONTRATO]'));
await prueba('respuesta: error HTTP', () => lanza(ejecutar('Validar respuesta', { input: [{ error: { message: 'timeout of 25000ms exceeded' } }], datos: DR() }), '[GENERACION]'));

// ---------------- clasificar error
await prueba('error con etapa (RAG) oculta secretos', async () => {
  const r = (await ejecutar('Clasificar error', { flujo: 'R', input: [{ error: '[EMBEDDINGS] Falló: Bearer sk-or-v1-abc123' }], datos: WR({ consultaId: 7 }) }))[0].json.cuerpo;
  igual([r.estado, r.etapa, r.mensaje, r.consultaId, r.fuentes, r.respuesta, typeof r.generadoEn], ['ERROR', 'EMBEDDINGS', 'Falló: [oculto]', 7, [], null, 'string'], 'r');
});
await prueba('error RAG con cuerpo inválido', async () => {
  const r = (await ejecutar('Clasificar error', { flujo: 'R', input: [{ error: '[VALIDACION] x' }], datos: WR(null) }))[0].json.cuerpo;
  igual([r.etapa, r.consultaId], ['VALIDACION', null], 'r');
});
await prueba('error sin etapa (indexación)', async () => {
  const r = (await ejecutar('Clasificar error', { flujo: 'I', input: [{ error: { message: "Cannot read properties of undefined (reading 'x')" } }], datos: { 'Webhook indexación': { body: {} } } }))[0].json.cuerpo;
  igual([r.etapa, r.mensaje.startsWith('Error interno del flujo'), r.resumen, r.vectorizados], ['INTERNO', true, null, []], 'r');
});
await prueba('error con objeto sin mensaje', async () => {
  const r = (await ejecutar('Clasificar error', { flujo: 'I', input: [{ foo: 1 }], datos: { 'Webhook indexación': { body: {} } } }))[0].json.cuerpo;
  igual(r.etapa, 'INTERNO', 'etapa');
});
await prueba('error oculta token interno', async () => {
  const r = (await ejecutar('Clasificar error', { flujo: 'I', input: [{ error: '[BACKEND] X-Internal-Token: abc123 rechazado' }], datos: { 'Webhook indexación': { body: {} } } }))[0].json.cuerpo;
  verdad(!r.mensaje.includes('abc123'), r.mensaje);
});

return resultados;
};
