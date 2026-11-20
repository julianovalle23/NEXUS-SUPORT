# Uso de herramientas de IA

Este archivo registra qué herramientas de IA se usaron en NEXUS Support y qué parte del trabajo hicieron. Solo incluye lo que consta; el resto lo completa el equipo.

## Herramientas

| Herramienta | Modelo | Entorno |
|---|---|---|
| Claude Code (aplicación de escritorio) | Claude Opus 5.5 (`claude-opus-5-5`) | Repositorio local e instancia de n8n del proyecto (vía MCP de n8n y navegador) |

## Participación registrada

### Workflows de n8n (2026-11-19)

**Qué hizo la IA:**

- **Análisis.**
  - Leyó el contrato del backend: `N8nClient`, `RagSolicitud`, `RagResultado`, `InternalController` y la configuración RAG.
  - Leyó el workflow combinado `nexus-support` y una ejecución real ya existente (138) para comprobar los formatos de Qdrant y OpenRouter.
- **Ajustes anteriores en n8n.**
  - Revisó y reorganizó `nexus-support`.
  - Archivó, a petición del equipo, dos workflows previos: `nexus-indexacion` y `nexus-rag`.
- **Diseño y generación.**
  - Generó los workflows `n8n/workflows/nexus-indexacion.json` y `n8n/workflows/nexus-rag.json`.
  - Los creó en n8n como **borradores**, sin publicar ni activar.
- **Pruebas.**
  - Escribió las pruebas unitarias de los scripts: `n8n/pruebas/pruebas-scripts.html` y `pruebas-scripts.js`.
  - Ejecutó esas pruebas en local: 124/124 correctas.
- **Documentación.** Redactó `docs/n8n/workflows-nexus.md` y este archivo.

**Qué no hizo la IA:**

- No ejecutó los nuevos workflows contra servicios reales.
- No hizo pruebas de integración, ni simuladas ni reales.
- No modificó el backend ni el frontend: el cambio necesario en `N8nClient` solo está documentado.
- No hizo commits.

**Revisión humana pendiente:**

- Revisar los JSON y las credenciales asignadas.
- Aplicar el cambio de autenticación en el backend.
- Ejecutar las pruebas reales R1–R10 de la documentación.
- Aprobar el cambio de `nexus-support` por los nuevos flujos.

### Historial de incidentes (2026-11-19)

**Herramienta:** Claude Code (Claude Opus 5.5).

**Qué hizo la IA:**

- Revisó el repositorio y comparó lo existente con funciones de mesas de servicio ITSM (SLA, activos y problemas, evaluación del RAG, historial, revisión posterior).
- Agregó la tabla `incidente_historial` en `database/init/05-mejoras.sql` (idempotente). Incluye llaves foráneas, restricciones e índices, y una fila `MIGRACION` por cada incidente existente.
- Agregó en el backend `TipoHistorial`, `IncidenteHistorial`, `IncidenteHistorialRepository`, `HistorialDto` y el endpoint `GET /api/incidentes/{id}/historial`.
- Modificó `IncidenteService` para registrar la creación y los cambios de estado, prioridad o responsable.
- Compiló el backend con `mvn compile`.

**Qué no hizo la IA:**

- No ejecutó pruebas ni arrancó la aplicación contra PostgreSQL.
- No modificó el frontend ni n8n.
- No hizo commits.

**Revisión humana pendiente:**

- Revisar la migración sobre una copia de la base.
- Validar que las filas `MIGRACION` no se usen como fecha real de un cambio.
- Escribir y ejecutar las pruebas recomendadas.

### Resultado de la asistencia y reaperturas (2026-11-19)

**Herramienta:** Claude Code (Claude Opus 5.5).

**Qué hizo la IA:**

- Agregó la columna opcional `calificacion.resolvio` en `database/init/05-mejoras.sql`. `NULL` significa que no se indicó.
- La añadió en `Calificacion`, `CalificacionRequest` y `CalificacionDto`.
- `CalificacionService` rechaza el campo en respuestas sin fuentes.
- Agregó `AnaliticaRepository.efectividad` y el DTO `Efectividad`, dentro de `/api/analitica/conocimiento`. Calcula con SQL, sobre `incidente_historial`, lo declarado al calificar y los incidentes reabiertos tras la primera respuesta con fuentes.
- Documentó la misma consulta en `docs/analisis/consultas-analiticas.sql` (sección 9).
- En el frontend (`asistencia.js`, `api.js`, `articulos.html`, `styles.css`):
  - Agregó la pregunta "¿La solución resolvió el incidente?" y una tabla de efectividad.
  - Al recalificar se conservan el comentario y la respuesta a esa pregunta.
  - Si se cancela el comentario, ya no se guarda nada.
- Compiló el backend con `mvn compile`.

**Qué no hizo la IA:**

- No ejecutó pruebas.
- No arrancó la aplicación ni probó la interfaz en el navegador.
- No revisó la sintaxis de JavaScript con herramientas: no hay Node instalado.
- No hizo commits.

**Revisión humana pendiente:**

- Validar la definición de reapertura.
- Probar la interfaz de calificación.
- Escribir y ejecutar las pruebas recomendadas.

### SLA de primera respuesta (2026-11-19)

**Herramienta:** Claude Code (Claude Opus 5.5).

**Qué hizo la IA:**

- Agregó en `database/init/05-mejoras.sql`:
  - la tabla `sla_objetivo`: minutos de primera respuesta por prioridad, opcionalmente por categoría;
  - la vista `v_primera_respuesta`. La primera respuesta es la primera fila del historial con un estado distinto del inicial.
- Agregó `EstadoPrimeraRespuesta`, los DTO `PrimeraRespuesta` y `PrimeraRespuestaResumen`, y su cálculo SQL en `AnaliticaRepository`.
- Agregó el endpoint `GET /api/incidentes/{id}/primera-respuesta` y el campo `sla.primeraRespuesta` del dashboard.
- En el frontend (`incidentes.html`, `dashboard.js`, `common.js`, `api.js`) muestra el dato en el detalle del incidente y en el dashboard.
- Compiló el backend y las pruebas con `mvn test-compile`, sin ejecutarlas.

**Qué no hizo la IA:**

- No ejecutó pruebas ni la aplicación.
- Cargó en `05-mejoras.sql` los objetivos generales que definió el usuario: Baja 480, Media 240, Alta 60 y Crítica 15 minutos. No hay objetivos por categoría.
- No cambió el SLA de resolución.
- No hizo commits.

**Revisión humana pendiente:**

- Confirmar los minutos objetivo cargados.
- Validar la definición de primera respuesta.
- Ejecutar las pruebas recomendadas.

### Elementos de TI afectados (2026-11-19)

**Herramienta:** Claude Code (Claude Opus 5.5).

**Qué hizo la IA:**

- Agregó en `database/init/05-mejoras.sql`:
  - el catálogo `elemento_ti` (equipo, aplicación o servicio). Se administra con SQL y va vacío: el inventario real es del equipo;
  - la columna opcional `incidente.elemento_id`, con llave foránea e índice.
- Agregó `TipoElemento`, `ElementoTi` y `ElementoTiRepository`.
- Agregó `elementoId` en `IncidenteRequest` y `elemento` en `IncidenteResponse`.
- Agregó los elementos activos en `/api/catalogos`.
- Validación en `IncidenteService`: solo se asignan elementos activos, pero el actual se conserva aunque se desactive.
- Agregó `porElemento` en el dashboard y la consulta 10 en `docs/analisis/consultas-analiticas.sql`.
- En el frontend:
  - agregó un selector opcional en los formularios de `incidentes.html`, que se oculta si el catálogo está vacío;
  - muestra el elemento en el detalle;
  - agregó una tabla en `index.html` y `dashboard.js`.
- Compiló el backend y las pruebas con `mvn test-compile`, sin ejecutarlas.

**Qué no hizo la IA:**

- No cargó elementos.
- No implementó problemas, que quedan para la etapa siguiente.
- No ejecutó pruebas ni la aplicación.
- No hizo commits.

**Revisión humana pendiente:**

- Cargar el inventario.
- Revisar que el `PUT` de incidentes ahora borra el elemento si no se envía `elementoId`.
- Ejecutar las pruebas recomendadas.

### Problemas (2026-11-19)

**Herramienta:** Claude Code (Claude Opus 5.5).

**Qué hizo la IA:**

- Agregó en `database/init/05-mejoras.sql` la tabla `problema` y la columna opcional `incidente.problema_id`, con llave foránea e índice.
- La tabla tiene restricciones para tres reglas:
  - `CAUSA_CONFIRMADA` exige causa y evidencia;
  - `fecha_cierre` solo existe en estado `CERRADO`;
  - el título y la descripción no pueden estar vacíos.
- Agregó en el backend `EstadoProblema`, `Problema`, `ProblemaRepository`, `ProblemaDtos`, `ProblemaService` y `ProblemaController` (`/api/problemas`, solo para técnicos y administradores).
- El servicio aplica tres reglas:
  - un incidente pertenece a un solo problema;
  - no se vinculan incidentes a un problema cerrado;
  - una causa confirmada no puede quedarse sin causa ni evidencia.
- Agregó `problema` en `IncidenteResponse`, la consulta 11 en `docs/analisis/consultas-analiticas.sql` y la regla de acceso en `SeguridadConfig`.
- En el frontend agregó la página `problemas.html` (listado, detalle, alta y edición, cambio de estado, vincular y desvincular), su entrada de menú en `common.js`, sus llamadas en `api.js` y un enlace al problema en el detalle del incidente.
- Compiló el backend y las pruebas con `mvn test-compile`, sin ejecutarlas.

**Qué no hizo la IA:**

- No creó problemas ni vinculó incidentes.
- No ejecutó pruebas ni la aplicación.
- No probó la página en el navegador.
- No hizo commits.

**Revisión humana pendiente:**

- Revisar las reglas de estados.
- Revisar que el título del problema llega en el JSON del incidente también a solicitantes, aunque la interfaz no lo muestra.
- Ejecutar las pruebas recomendadas.

### Revisión posterior de incidentes (2026-11-19)

**Herramienta:** Claude Code (Claude Opus 5.5).

**Qué hizo la IA:**

- Agregó en `database/init/05-mejoras.sql` la tabla `revision_incidente`, una por incidente.
- La tabla guarda impacto, causa con estado `CONFIRMADA` o `PENDIENTE`, evidencia, solución y acciones preventivas. Una causa confirmada exige evidencia.
- Agregó en el backend `EstadoCausa`, `RevisionIncidente`, `RevisionIncidenteRepository`, `RevisionDtos`, `RevisionService` y `RevisionController`, más la regla de acceso en `SeguridadConfig`.
- Endpoints nuevos: `GET` y `PUT` en `/api/incidentes/{id}/revision`, y `GET /api/revisiones/pendientes`.
- La línea de tiempo no se guarda: se arma con `incidente_historial` y `consulta_ia`. Las filas `MIGRACION` salen marcadas como fecha aproximada.
- Se consideran pendientes de revisión los incidentes finalizados con prioridad Alta o superior, o con el SLA de resolución incumplido. La consulta va en `AnaliticaRepository` y también en `docs/analisis/consultas-analiticas.sql` (consulta 12).
- En el frontend agregó la sección de revisión en `incidentes.html`, la tabla de pendientes en `index.html` y `dashboard.js`, las llamadas en `api.js` y un estilo en `styles.css`.
- Compiló el backend y las pruebas con `mvn test-compile`, sin ejecutarlas.

**Qué no hizo la IA:**

- No redactó revisiones ni causas.
- No ejecutó pruebas ni la aplicación.
- No hizo commits.

**Revisión humana pendiente:**

- Validar qué cuenta como incidente importante (nivel 3 o SLA incumplido).
- Revisar los textos de la línea de tiempo.
- Ejecutar las pruebas recomendadas.

### Pruebas unitarias de las etapas ITSM (2026-11-19)

**Herramienta:** Claude Code (Claude Opus 5.5), a pedido del usuario.

**Qué hizo la IA:**

- Escribió en `backend/src/test/java/com/nexus/support/` 27 pruebas unitarias con Mockito, sin base de datos:
  - `service/Datos.java`: datos en memoria y sesión simulada, compartidos por las pruebas.
  - `service/IncidenteServiceTest.java`: 8 pruebas de historial y elemento afectado.
  - `service/CalificacionServiceTest.java`: 4 pruebas de "¿resolvió el incidente?".
  - `domain/ProblemaTest.java` (2 pruebas) y `service/ProblemaServiceTest.java` (8 pruebas): reglas de problemas.
  - `service/RevisionServiceTest.java`: 6 pruebas de revisión posterior y línea de tiempo.
- Las compiló con `mvn test-compile` y, con autorización del usuario, las ejecutó con `mvn test`: 42 pruebas, 0 fallos (27 nuevas y 15 que ya existían).

**Qué no hizo la IA:**

- No cubrió el SQL: vistas, primera respuesta, efectividad, pendientes y migración. Eso requiere pruebas de integración con PostgreSQL.
- No hizo commits.

### Corrección de autorización y estados (2026-11-19)

**Herramienta:** Claude Code (Claude Opus 5.5), a pedido del usuario. Las reglas y los escenarios de aceptación están en `docs/seguridad/roles-y-estados.md`.

**Qué ya existía antes de este cambio:**

- Las rutas por perfil en `SeguridadConfig`.
- El bloqueo para que una cuenta no cambie su propio perfil o estado.
- Una comprobación del último administrador, que no era atómica y contaba también cuentas sin contraseña.

**Qué hizo la IA:**

- En `05-mejoras.sql` agregó:
  - la tabla `auditoria_usuario`;
  - la tabla `transicion_estado`, con 6 transiciones y Cerrado como estado terminal;
  - el tipo `REAPERTURA` y la columna `motivo` en `incidente_historial`, con una restricción que exige motivo;
  - la columna `incidente.seguimiento_de_id`.
- En `UsuarioService` y `UsuarioRepository`:
  - Bloquea con `SELECT … FOR UPDATE` las cuentas de administración utilizables (activas y con contraseña) antes de validar un cambio.
  - Rechaza dejar el sistema sin ninguna de ellas.
  - Registra en la auditoría cada alta y cada cambio de perfil o estado.
  - `AdministradorInicial` registra el origen `ARRANQUE`.
  - Endpoint nuevo: `GET /api/usuarios/auditoria`.
- En `IncidenteService` y `CatalogoService`:
  - Valida las transiciones al editar y al cambiar el estado.
  - Rechaza los cambios sobre un estado terminal y el alta directa en un estado terminal.
  - Bloquea la fila del incidente (`findParaModificar`) al modificarlo, también desde `ProblemaService`.
  - `Incidente` pasa a usar `@DynamicUpdate`.
- Agregó dos acciones nuevas:
  - `POST /api/incidentes/{id}/reapertura`: solo ADMIN, motivo obligatorio y fila `REAPERTURA` en el historial.
  - `POST /api/incidentes/{id}/seguimiento`: crea un incidente nuevo vinculado al cerrado, sin modificar el original.
- Incluyó `REAPERTURA` en la analítica de efectividad (`AnaliticaRepository` y la consulta 9 de `docs/analisis/consultas-analiticas.sql`) y en la línea de tiempo.
- En el frontend:
  - `incidentes.html` solo ofrece los estados de destino permitidos.
  - En un incidente cerrado oculta la edición y muestra «Registrar seguimiento» y, solo a ADMIN, la reapertura excepcional.
  - `usuarios.html` deshabilita perfil, correo y estado al editar la propia cuenta y muestra la auditoría.
- Ajustó en las pruebas existentes solo los *stubs* necesarios para compilar (`findParaModificar`, `transicionPermitida`, `entidadParaModificar`).
- Compiló el backend y las pruebas con `mvn -o test-compile`.

**Qué no hizo la IA:**

- Con autorización del usuario ejecutó `mvn -o test`: 42 pruebas, 0 fallos. Son las pruebas que ya existían; ninguna cubre las reglas nuevas, así que los escenarios de aceptación **siguen sin verificarse**.
- No ejecutó la aplicación.
- A pedido del usuario escribió 27 pruebas unitarias nuevas con Mockito y, con autorización, las ejecutó con `mvn -o test`: 69 pruebas en total, 0 fallos (27 nuevas y 42 que ya existían):
  - `UsuarioServiceTest` (9): último administrador utilizable, cuentas sin contraseña, cambios sobre la propia cuenta y auditoría de altas y cambios.
  - `IncidenteEstadosTest` (18): Cerrado como estado terminal, transiciones no permitidas, bloqueo de fila, reapertura (perfil, motivo, origen, destino e historial) y seguimiento (vínculo y visibilidad del solicitante).
  - Las carreras reales entre transacciones, como dos administradores o dos técnicos actuando a la vez, solo se pueden comprobar con PostgreSQL y siguen sin cubrir.
- No hizo commits.

**Revisión humana pendiente:**

- Confirmar la tabla de transiciones; por ejemplo, si Abierto → Resuelto debe permitirse.
- Confirmar que un incidente cerrado no admita ninguna edición, ni siquiera del título.
- Confirmar el mínimo de 10 caracteres del motivo.
- Autorizar las verificaciones recomendadas en el documento.

### Conexión backend → n8n, etapa 1 (2026-11-19)

**Herramienta:** Claude Code (Claude Opus 5.5), a pedido del usuario.

**Qué hizo la IA:**

- Inspeccionó la instancia de n8n en modo lectura con el MCP. Confirmó:
  - que los flujos `knTs3gLtgMBQ9sLS` y `Eo7DZcs8CXzPNHw8` están publicados;
  - que el de RAG difiere del JSON del repositorio;
  - que `nexus-support` ya no existe.
- `WebConfig.n8nRestClient()` envía `X-Internal-Token` (valor de `INTERNAL_API_TOKEN`) como cabecera por defecto hacia n8n. Si no está configurado, avisa en el log sin mostrar valores.
- `articulos.html` interpreta `estado` y `etapa` del resultado de la indexación, en lugar de mostrar todo como éxito.
- Actualizó `docs/n8n/workflows-nexus.md`: estado real de los flujos, cambio aplicado y limitación de timeout pendiente.
- Compiló el backend con `mvn -o compile`.

**Qué no hizo la IA:**

- No modificó, publicó ni despublicó workflows de n8n.
- No ejecutó pruebas ni llamó a los webhooks.
- No hizo commits.

**Revisión humana pendiente:**

- Confirmar que `INTERNAL_API_TOKEN` coincide con la credencial `NEXUS - Token interno`.
- Ejecutar las verificaciones V1 a V4.

### Sincronización de workflows con n8n, etapa 2 (2026-11-19)

**Herramienta:** Claude Code (Claude Opus 5.5), a pedido del usuario, que eligió la versión de n8n como fuente de verdad.

**Qué hizo la IA:**

- Leyó con el MCP de n8n, en modo lectura, las versiones publicadas de `knTs3gLtgMBQ9sLS` y `Eo7DZcs8CXzPNHw8`.
- Las comparó nodo por nodo con los JSON del repositorio mediante un script en Python. El código de los Code nodes, los parámetros HTTP e IF, las conexiones y los ajustes eran idénticos. Solo cambiaban posiciones, ids de nodo, las 7 notas que se quitaron en RAG y un `method: GET` implícito.
- Regeneró `n8n/workflows/nexus-rag.json` (14 nodos) y `n8n/workflows/nexus-indexacion.json` (33 nodos) desde n8n, en formato importable (sin `id`, `versionId` ni `meta`, con `active: false`). Comprobó que no contienen secretos.
- El 2026-11-20, a pedido del usuario, revisó de nuevo n8n con el historial de versiones. La lógica de ambos flujos no había cambiado. Añadió a `nexus-rag.json` los 4 grupos visuales (`nodeGroups`) que existían en n8n.
- Actualizó `docs/n8n/workflows-nexus.md`.

**Qué no hizo la IA:**

- No modificó, publicó ni despublicó los workflows en n8n.
- No volvió a ejecutar `n8n/pruebas/pruebas-scripts.html`. Como el código no cambió, el resultado anterior (124/124) debería mantenerse, pero no se ha comprobado.
- No hizo commits.

### Indexación en segundo plano y fuentes candidatas, etapa 3 (2026-11-19)

**Herramienta:** Claude Code (Claude Opus 5.5), a pedido del usuario.

**Qué hizo la IA:**

- `IndexacionService`: una sola indexación a la vez por instancia, con coalescencia de solicitudes pendientes y estado consultable (`GET /api/articulos/indexacion`). `POST /api/articulos/reindexar` responde 202 sin esperar a n8n.
- `WebConfig` y `N8nClient`: un `RestClient` aparte para la indexación, con `N8N_INDEXACION_TIMEOUT_SEGUNDOS` (1860 s). El del RAG sigue con 60 s.
- `fuente_respuesta.usada` (en `05-mejoras.sql`): el backend guarda como `usada = false` las candidatas no citadas de `auditoria.candidatas`, revalidadas contra la base relacional.
- La analítica de uso de artículos y la revisión posterior cuentan solo las fuentes citadas.
- `articulos.html` consulta el estado cada 3 s. `asistencia.js` muestra las candidatas aparte de las fuentes citadas.
- Ajustó los stubs de `AsistenciaServiceTest` al nuevo campo `auditoria` de `RagResultado`. `mvn -o test-compile` compila.
- Con autorización del usuario escribió `IndexacionServiceTest` (13 pruebas: bloqueo, coalescencia, `forzar`, liberación tras fallo o rechazo, arranque) y 5 pruebas nuevas en `AsistenciaServiceTest` (candidatas no citadas, inactivas, repetidas o fuera de rango, sin auditoría). Compilan; no se han ejecutado.
- Actualizó `docs/n8n/workflows-nexus.md`, `.env.example` y `README.md`.

**Qué no hizo la IA:**

- No modificó los workflows de n8n.
- No ejecutó las pruebas: escribirlas se autorizó aparte, pero su ejecución no.
- No probó la indexación ni el RAG contra n8n real.
- No hizo commits.

### Carga de archivos de artículos y origen en Qdrant, etapa 4 (2026-11-20)

**Herramienta:** Claude Code (Claude Opus 5.5), a pedido del usuario («etapa 4 y genera cambios en el n8n»). El usuario eligió que el cambio en n8n fuera guardar el origen de los artículos en Qdrant.

**Qué hizo la IA:**

- Tablas `importacion_articulo` e `importacion_fila`, y la columna `articulo_conocimiento.importacion_id`, en `05-mejoras.sql`. Incluyen un índice único parcial por SHA-256 para importaciones vigentes.
- Entidades `ImportacionArticulo` y `FilaImportacion`, y `ImportacionRepository`.
- `LectorArchivoArticulos`: lector de CSV escrito a mano siguiendo RFC 4180 (no hay librería CSV disponible sin conexión), Markdown y texto plano, con UTF-8 estricto.
- `ImportacionService` e `ImportacionController` (`/api/importaciones`, solo ADMIN): carga, vista previa, confirmación como borradores inactivos y sin verificar, y descarte.
- Página `importaciones.html`, enlace en el menú y marca «Importado #N» en `articulos.html`. Soporte de `FormData` en `api.js`.
- `/api/internal/articulos` envía `origen` e `importacionId`.
- En n8n, con el MCP, guardó un **borrador** del flujo de indexación (`4fb70e48-…`) con 4 nodos y una nota modificados, sin `if`, `else` ni ternarios. Comprobó que la versión publicada sigue siendo `b504d8aa-…`. Actualizó `n8n/workflows/nexus-indexacion.json` con el mismo contenido y comparó el diff de versiones de n8n con el del repositorio.
- `mvn -o test-compile` compila.

**Qué no hizo la IA:**

- No publicó el borrador en n8n ni modificó el flujo RAG.
- Con autorización posterior del usuario escribió `LectorArchivoArticulosTest` (20 pruebas: CSV RFC 4180, Markdown, texto plano y codificación) e `ImportacionServiceTest` (18 pruebas: validación por registro, límites, duplicados por hash y por título, confirmación como borradores inactivos, descarte). Con autorización las ejecutó (`mvn -o test`, 2026-11-20). En la primera ejecución pasaron 124 de 125. El fallo estaba en el dato de prueba de un CSV (un campo con cuatro comillas en lugar de tres), no en el lector. Tras corregir el dato: **125 pruebas, 0 fallos**. Esto incluye las 18 pruebas de la etapa 3, que no se habían ejecutado.
- No volvió a ejecutar `n8n/pruebas/pruebas-scripts.html`.
- No probó la carga en el navegador ni contra PostgreSQL real.
- No hizo commits.

### Alertas y escalamiento de SLA e ingreso guiado, etapa 5 (2026-11-20)

**Herramienta:** Claude Code (Claude Opus 5.5), a pedido del usuario («Sí, etapa 5»).

**Qué hizo la IA:**

- Tabla `alerta_sla` en `05-mejoras.sql`. Tiene tipo (resolución o primera respuesta), nivel (aviso o escalamiento),
  vencimiento, estado (pendiente, atendida o superada) y restricciones de coherencia. Una alerta es única por
  incidente, tipo, nivel y vencimiento.
- `AlertaSlaRepository`: las reglas están en SQL y reutilizan `prioridad.sla_horas`, `v_primera_respuesta` y
  `EstadoSla.FRACCION_ALERTA`. No hay IA en el cálculo.
- `AlertaSlaService`: revisión con `@Scheduled` (se activó `@EnableScheduling`). Primero marca como superadas las
  alertas que ya no aplican y después genera las nuevas. Los permisos son: el técnico atiende avisos de sus
  incidentes y el administrador atiende escalamientos.
- `AlertaSlaController` (`/api/alertas-sla`), reglas en `SeguridadConfig`, página `alertas.html` y entrada en el menú.
- Ingreso guiado: columnas `incidente.mensaje_error` y `incidente.pasos_intentados` (opcionales), campos en los
  formularios de alta y edición y en el detalle.
  - `AsistenciaService.preguntaGuiada` agrega a la consulta RAG por defecto el elemento afectado, el mensaje y los
    pasos, sin superar 5000 caracteres.
  - El contrato con n8n no cambió.
- Se añadió a `IncidenteRequest` un constructor sin los campos nuevos, para que las pruebas existentes compilen sin
  cambios. `mvn -o test-compile` compila.

**Decisiones a revisar por el equipo:**

- El escalamiento notifica dentro de la aplicación. No reasigna ni cambia la prioridad automáticamente y no envía
  correos.
- El aviso se genera con el 25 % del plazo restante, el mismo umbral que «por vencer» del dashboard.

**Qué no hizo la IA:**

- No probó las consultas SQL contra PostgreSQL real ni la página en el navegador.
- No modificó n8n.

**Pruebas (escritas a pedido del usuario, «Sí, escríbelas»):**

- `AlertaSlaServiceTest` (12): verifica que la revisión supera antes de generar, que un fallo programado no se propaga, la visibilidad por perfil y los permisos para atender. Comprueba también que la nota se recorta, que un incidente ajeno da 404 y que una alerta ya cerrada se rechaza.
- `PreguntaGuiadaTest` (5): composición de la consulta RAG con el ingreso guiado, campos en blanco, caso sin descripción y recorte a 5000 caracteres.
- `IncidenteServiceTest` (+3): guarda y limpia el mensaje de error y los pasos intentados, y revisa el constructor de compatibilidad.
- Ejecutadas con `mvn -o test` a pedido del usuario («Sí, ejecútalas»): la suite completa tiene **145 pruebas, 0 fallos y 0 errores**. Las 20 nuevas pasaron en la primera ejecución.
- Las pruebas usan mocks del repositorio, así que no cubren el SQL de las reglas de `AlertaSlaRepository`. Ese SQL requiere PostgreSQL.
- No hizo commits.

### Ingreso guiado en n8n, etapa 5 (2026-11-20)

**Herramienta:** Claude Code (Claude Opus 5.5), a pedido del usuario («adapta los nuevos cambios en n8n y qdrant»).

**Qué hizo la IA:**

- Comparó el estado de n8n con el repositorio. Ninguno de los dos flujos tenía cambios desde la última sincronización, y en RAG la versión publicada era igual al borrador.
- Guardó un **borrador** del flujo RAG (`aa1af1b5-…`) con *Configuración RAG* y *Construir contexto* modificados, sin `if`, `else` ni ternarios. Comprobó que la versión publicada sigue siendo `a7545fae-…` y actualizó `n8n/workflows/nexus-rag.json` con el mismo código.
- En el backend:
  - nueva propiedad `nexus.rag.ingreso-guiado-estructurado` (`RAG_INGRESO_GUIADO_ESTRUCTURADO`, por defecto `false`);
  - `IncidenteRag` gana `elemento`, `mensajeError` y `pasosIntentados`, y omite los nulos;
  - `AsistenciaService.incidenteRag`.
- Ajustó las dos llamadas a `new NexusProperties.Rag(...)` de `AsistenciaServiceTest` al nuevo parámetro (`false`), sin cambiar lo que prueban. `mvn -o test-compile` compila.
- Concluyó que Qdrant no necesita cambios en la etapa 5.

**Qué no hizo la IA:**

- No publicó el borrador ni activó `RAG_INGRESO_GUIADO_ESTRUCTURADO`.
- No ejecutó el flujo de n8n.
**Pruebas:**

- A pedido del usuario («Sí, ejecútalas») se ejecutó `mvn -o test` tras este cambio: **145 pruebas, 0 fallos, 0 errores**.
- Después, a pedido del usuario («Sí, escríbelas»), se escribió `IngresoGuiadoRagTest` (6 pruebas). Cubre las claves del JSON que recibe n8n en ambos modos (sin modo estructurado, solo `titulo`, `categoria` y `prioridad`), la omisión de campos vacíos, la pregunta por defecto en cada modo y el respeto de una pregunta escrita.
- Ejecutadas con `mvn -o test` a pedido del usuario («Sí, ejecútalas»): la suite completa tiene **151 pruebas, 0 fallos y 0 errores**. Las 6 de `IngresoGuiadoRagTest` pasaron en la primera ejecución.
- Ninguna prueba ejecuta el borrador de n8n.

### n8n y Qdrant, etapa A de §13 (2026-11-20)

**Herramienta:** Claude Code (Claude Opus 5.5), a pedido del usuario («Sí, etapa A»).

**Qué hizo la IA:**

- Inspeccionó las versiones reales: Qdrant 1.20.0 (colección, índices, alias, snapshots, puntos) y los dos flujos de n8n.
- Guardó **borradores** de los dos flujos (RAG `e93a0472-…`, indexación `aa063219-…`) y comprobó que las versiones publicadas no cambiaron. Revisó el diff de versiones de indexación.
- Filtro por `modelo` en Qdrant antes de recuperar, índice de payload `modelo` (keyword), `version_articulo` en el payload, y eco de `correlationId` y `ejecucionId` en las respuestas. Sin `if`, `else` ni ternarios.
- En el backend: cabecera `X-Correlation-Id`, columnas `consulta_ia.correlacion_id` y `n8n_ejecucion_id` (`05-mejoras.sql`), registro de etapa, duración y resultado sin texto sensible. `mvn -o test-compile` compila.
- Actualizó `docs/n8n/workflows-nexus.md` (§2, punto 13).

**Qué no hizo la IA:**

- No publicó ni ejecutó los flujos, no indexó y no tocó los puntos de Qdrant.
- No probó el SQL en PostgreSQL.
- Después, a pedido del usuario, ejecutó la suite existente (151 pruebas, 0 fallos) y escribió `TrazabilidadN8nTest` (10 pruebas: cabecera `X-Correlation-Id` sin la clave en el cuerpo, reemplazo de un id inseguro, compatibilidad con el flujo publicado sin eco, eco en la indexación, `idSeguro`, y persistencia de `correlacion_id` y `n8n_ejecucion_id`). Luego, a pedido del usuario, ejecutó la suite completa: 161 pruebas, 0 fallos.
- No implementó las etapas B a E (resultado por artículo, reintentos, reconciliación, búsqueda híbrida, seguridad y respaldo).

### n8n y Qdrant, etapa B de §13 (2026-11-20)

**Herramienta:** Claude Code (Claude Opus 5.5), a pedido del usuario («Sí, etapa B»).

**Qué hizo la IA:**

- Comprobó que ninguno de los dos flujos había cambiado en n8n desde la etapa A.
- Guardó **borradores** (RAG `e938abd6-…`, indexación `5596a372-…`). Comprobó que las versiones publicadas no cambiaron y que los parámetros en n8n son idénticos a los JSON del repositorio.
- Indexación: resultado por artículo (`PROCESADO`, `OMITIDO`, `FALLIDO`) con motivos de lista fija; lo vectorizado solo se declara procesado tras confirmar la escritura. Los dos flujos: `tipoError` y `reintentable` en las respuestas de error. Sin `if`, `else` ni ternarios.
- Backend: reintento con espera creciente en `IndexacionService` (`INDEXACION_REINTENTOS`, `INDEXACION_ESPERA_REINTENTO_SEGUNDOS`), sin forzar y sin reintentar `SIN_CONEXION`. `articulos.html` muestra `tipoError`. `mvn -o test-compile` compila.
- Documentó el diseño en `docs/n8n/workflows-nexus.md` (§2, punto 14).

**Qué no hizo la IA:**

- No publicó ni ejecutó los flujos: la clasificación de errores y el uso de `$(nodo).last()` en *Clasificar error* no se han verificado con una ejecución real.
- No ejecutó la suite de pruebas ni escribió pruebas para el reintento.
- No implementó las etapas C a E.

### Otras partes del proyecto

> Pendiente de completar por el equipo: herramientas, fechas y alcance de la IA en el backend, el frontend, la base de datos, el análisis de datos y el workflow `nexus-support` original, si se usó.

| Fecha | Herramienta | Área | Participación | Revisado por |
|---|---|---|---|---|
| | | | | |
