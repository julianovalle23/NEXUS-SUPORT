# Reglas de autorización y de estados de incidentes

Este documento recoge las reglas que aplica el backend. Ocultar o deshabilitar botones en el frontend solo mejora la experiencia de uso: cada regla se vuelve a comprobar en el servidor.

## 1. Perfiles y permisos

| Operación | Ruta | SOLICITANTE | TECNICO | ADMIN |
|---|---|---|---|---|
| Listar, crear y editar cuentas, y ver la auditoría | `/api/usuarios/**` | — | — | ✔ |
| Registrar un incidente | `POST /api/incidentes` | ✔ (siempre nace Abierto) | ✔ | ✔ |
| Registrar el seguimiento de un incidente cerrado | `POST /api/incidentes/{id}/seguimiento` | ✔ (solo de los suyos) | ✔ | ✔ |
| Editar un incidente o cambiar su estado | `PUT /api/incidentes/{id}`, `PATCH /api/incidentes/{id}/estado` | — | ✔ | ✔ |
| Reapertura excepcional | `POST /api/incidentes/{id}/reapertura` | — | — | ✔ |

- La columna Ruta corresponde a `SeguridadConfig`. `IncidenteService.reabrir` vuelve a exigir el perfil ADMIN.
- Un acceso sin permiso devuelve **403** con el mensaje «Su perfil no tiene permiso para esta acción».
- Un incidente ajeno se le presenta al solicitante como inexistente (**404**).

### Cuentas de administración (`UsuarioService.actualizar`)

1. **Nadie puede cambiar su propio perfil, correo ni estado.** El servidor responde **422**. En `usuarios.html` esos campos aparecen deshabilitados al editar la propia cuenta.
2. **Siempre queda al menos una cuenta de administración utilizable**, es decir, con perfil ADMIN, activa y con contraseña.
   - Una cuenta sin contraseña no cuenta, porque no puede iniciar sesión.
   - Si el cambio dejara a la última cuenta utilizable sin perfil o inactiva, el servidor responde **422**.
3. **La comprobación es atómica.** Antes de leer la cuenta, `UsuarioRepository.bloquearAdministradoresUsables()` bloquea con `SELECT … FOR UPDATE` todas las cuentas de administración utilizables, siempre en orden de id.
   - Si dos administradores intentan quitarse el perfil al mismo tiempo, la segunda transacción espera a que termine la primera.
   - Al continuar, PostgreSQL vuelve a evaluar el filtro y la segunda transacción ve un administrador menos, así que se rechaza.
4. **Auditoría.** La tabla `auditoria_usuario` es de solo inserción y se consulta en `GET /api/usuarios/auditoria`.
   - Registra cada alta de cuenta y cada cambio de perfil o de estado: fecha, autor (`actor_id`), cuenta afectada (`usuario_id`), perfil anterior y nuevo, y estado anterior y nuevo.
   - Cuando `AdministradorInicial` crea o promueve al administrador inicial durante el arranque, la fila lleva `origen = 'ARRANQUE'` y `actor_id` nulo. Una restricción de la tabla solo admite autor nulo en ese caso.

No existen rutas de carga masiva, importación ni tareas programadas que modifiquen cuentas o incidentes. Los flujos de n8n solo leen y validan artículos mediante `/api/internal/articulos`.

## 2. Máquina de estados de incidentes

Las transiciones normales se guardan en la tabla `transicion_estado`, sembrada en `05-mejoras.sql`. Un estado sin transiciones de salida es **terminal**.

| Desde \ Hacia | Abierto | En progreso | Resuelto | Cerrado |
|---|---|---|---|---|
| **Abierto** | — | ✔ | ✔ | ✘ |
| **En progreso** | ✔ | — | ✔ | ✘ |
| **Resuelto** | ✘ | ✔ (no quedó resuelto) | — | ✔ |
| **Cerrado** (terminal) | ✘ | ✘ | ✘ | — |

- **Quién puede hacerlo:** TECNICO o ADMIN, con `PATCH /estado` o con `PUT` (campo `estadoId`). El solicitante no cambia estados.
- **Condiciones:**
  - Pedir el mismo estado en que ya está el incidente no hace nada ni genera historial.
  - Una transición que no está en la tabla se rechaza con **422**, por ejemplo: «No se permite pasar el incidente #7 de 'Abierto' a 'Cerrado'».
  - Para cerrar un incidente primero hay que resolverlo.
- **Estado terminal:** un incidente Cerrado no admite `PUT` ni `PATCH /estado`, tampoco para editar título o prioridad. El servidor responde **422** y sugiere registrar un seguimiento.
- **Alta:** un incidente no puede crearse directamente en un estado terminal.
- **Concurrencia:**
  - Editar, cambiar el estado, reabrir y vincular o desvincular un problema obtienen el incidente con `IncidenteRepository.findParaModificar`, que hace `SELECT … FOR UPDATE`. La transición se valida con el estado vigente después de esperar el bloqueo.
  - `Incidente` usa `@DynamicUpdate`, de modo que una operación que solo cambia el problema vinculado no reescribe la columna de estado.

### Reapertura excepcional (solo ADMIN)

`POST /api/incidentes/{id}/reapertura` con el cuerpo `{ "estadoId": 1, "motivo": "…" }`.

- Solo se aplica a incidentes en estado terminal. El destino debe ser un estado no final (Abierto o En progreso).
- El motivo es obligatorio y debe tener entre 10 y 1000 caracteres.
- Deja una fila `REAPERTURA` en `incidente_historial` con el autor (`actor_id`), la fecha, el nuevo estado y el motivo. La restricción `ck_historial_motivo` impide guardar esa fila sin motivo.
- Esta acción no reutiliza la transición genérica: es un método y una ruta propios, con sus propias validaciones.
- La analítica de efectividad cuenta la reapertura como «reabierto».

### Seguimiento de un caso cerrado

`POST /api/incidentes/{id}/seguimiento` con el mismo cuerpo que el alta de un incidente.

- Crea un incidente **nuevo** con `seguimiento_de_id` apuntando al original. El original, su estado y su historial no cambian.
- Solo se admite si el original está en estado terminal. Un solicitante solo puede registrar el seguimiento de sus propios incidentes.

## 3. Escenarios de aceptación

**Estos escenarios no se han ejecutado.** Se describen para verificarlos cuando se autorice.

| # | Dado | Cuando | Entonces |
|---|---|---|---|
| A1 | Un único ADMIN activo con contraseña (A) y otro ADMIN (B) | B quita el perfil ADMIN a A | 422 «No se puede quitar el perfil de administración…»; A conserva el perfil; no se crea fila de auditoría |
| A2 | A es ADMIN | A edita su propia cuenta y se pone TECNICO (llamando directamente a la API) | 422 «No puede cambiar su propio perfil…» |
| A3 | Dos ADMIN utilizables, A y B | A degrada a B y B degrada a A **en paralelo** | Una petición termina con 200 y la otra con 422; queda exactamente un ADMIN utilizable |
| A4 | Un TECNICO autenticado | `PUT /api/usuarios/{id}` con `rol=ADMIN` | 403; no se registra ningún cambio |
| A5 | Dos ADMIN, A y B | A cambia a B a TECNICO | 200; aparece una fila en `auditoria_usuario` con actor A, usuario B, ADMIN→TECNICO y la fecha |
| A6 | Un ADMIN utilizable y otro ADMIN sin contraseña | Se intenta degradar al utilizable | 422 (el ADMIN sin contraseña no cuenta) |
| E1 | Un incidente Cerrado | Un TECNICO hace `PATCH /estado` a Abierto | 422; el estado sigue siendo Cerrado; no se añade historial |
| E2 | Un incidente Cerrado | Un TECNICO hace `PUT` cambiando el título | 422 |
| E3 | Un incidente Cerrado | Se abre el detalle en la interfaz como TECNICO | No se muestran el selector de estado ni «Editar»; aparecen «Caso cerrado» y «Registrar seguimiento» |
| E4 | Un incidente Abierto | `PATCH /estado` a Cerrado | 422 «No se permite pasar…» |
| E5 | Un incidente Cerrado | Un ADMIN hace `POST /reapertura` sin motivo o con menos de 10 caracteres | 400 o 422; no hay cambios |
| E6 | Un incidente Cerrado | Un TECNICO hace `POST /reapertura` | 403 |
| E7 | Un incidente Cerrado | Un ADMIN hace `POST /reapertura` con destino En progreso y motivo válido | 200; fila `REAPERTURA` con actor, fecha y motivo; `fecha_resolucion` queda nula |
| E8 | Un incidente Cerrado del solicitante S | S hace `POST /seguimiento` | 201; el nuevo incidente tiene `seguimientoDe` igual al original; el historial del original no cambia |
| E9 | Un incidente En progreso | `POST /seguimiento` | 422 |
| E10 | Un incidente Resuelto | Dos técnicos cambian el estado a la vez, a Cerrado y a En progreso | Las peticiones se ejecutan en serie; si gana Cerrado, la otra recibe 422 |

## 4. Verificaciones recomendadas

1. **Unitarias** (`mvn -o test`):
   - Para `UsuarioService`: escenarios A1, A2, A5 y A6, con un *mock* de `bloquearAdministradoresUsables`.
   - Para `IncidenteService`: escenarios E1, E2, E4, E5, E7 y E9, con un *mock* de `transicionPermitida` y `esTerminal`.
2. **Integración con PostgreSQL** (clases `*IT`, que no se ejecutan con `mvn test`):
   - Escenarios A3 y E10 con dos hilos y dos transacciones reales.
   - Las restricciones `ck_historial_motivo` y `ck_auditoria_actor`, y que el arranque con `05-mejoras.sql` sea idempotente.
3. **Manuales con la API**, mediante Swagger o curl con sesión: A4, E6 y E8.
4. **Manuales en la interfaz:** E3, la sección de auditoría en `usuarios.html` y el bloqueo de campos al editar la propia cuenta.
5. **SQL de control:**
   - `SELECT * FROM transicion_estado;` debe devolver seis filas. Si la tabla queda vacía porque los nombres de estado no coinciden, todos los estados pasarían a ser terminales.
   - `SELECT tipo, count(*) FROM incidente_historial GROUP BY tipo;`
