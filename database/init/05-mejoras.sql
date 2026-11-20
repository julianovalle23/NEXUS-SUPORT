-- ===========================================================
-- NEXUS Support - Cambios de esquema posteriores a la versión inicial.
-- Idempotente: el backend lo ejecuta en cada arranque, también sobre bases ya creadas.
-- ===========================================================

-- Persona que reporta el incidente (texto libre). usuario_id sigue siendo el técnico responsable.
ALTER TABLE incidente ADD COLUMN IF NOT EXISTS solicitante VARCHAR(120);

-- Cuentas de acceso: perfil y contraseña (hash BCrypt) de cada usuario.
-- Los usuarios existentes quedan como técnicos sin contraseña: no pueden iniciar sesión
-- hasta que un administrador les asigne una.
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS rol VARCHAR(20) NOT NULL DEFAULT 'TECNICO';
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS clave_hash VARCHAR(100);
ALTER TABLE usuario DROP CONSTRAINT IF EXISTS ck_usuario_rol;
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_rol CHECK (rol IN ('SOLICITANTE', 'TECNICO', 'ADMIN'));

-- Cuenta del solicitante que registró el incidente (nulo si lo registró un técnico).
ALTER TABLE incidente ADD COLUMN IF NOT EXISTS solicitante_id BIGINT
    CONSTRAINT fk_incidente_solicitante REFERENCES usuario (id);
CREATE INDEX IF NOT EXISTS ix_incidente_solicitante ON incidente (solicitante_id);

-- Historial de incidentes: una fila por creación o cambio de estado, prioridad o responsable.
-- Cada fila guarda la situación completa después del cambio (instantánea); lo que cambió
-- se obtiene comparando con la fila anterior (LAG). Solo se inserta, nunca se modifica.
CREATE TABLE IF NOT EXISTS incidente_historial (
    id              BIGSERIAL PRIMARY KEY,
    incidente_id    BIGINT      NOT NULL,
    fecha           TIMESTAMP   NOT NULL DEFAULT NOW(),
    -- CREACION y ACTUALIZACION los registra el backend. MIGRACION = situación conocida al crear
    -- esta tabla (fecha = fecha_actualizacion); los cambios anteriores no se pueden reconstruir.
    tipo            VARCHAR(20) NOT NULL,
    estado_id       BIGINT      NOT NULL,
    prioridad_id    BIGINT      NOT NULL,
    responsable_id  BIGINT      NOT NULL,
    -- Cuenta que hizo el cambio; nula solo en MIGRACION.
    actor_id        BIGINT,
    CONSTRAINT fk_historial_incidente   FOREIGN KEY (incidente_id)   REFERENCES incidente (id) ON DELETE CASCADE,
    CONSTRAINT fk_historial_estado      FOREIGN KEY (estado_id)      REFERENCES estado_incidente (id),
    CONSTRAINT fk_historial_prioridad   FOREIGN KEY (prioridad_id)   REFERENCES prioridad (id),
    CONSTRAINT fk_historial_responsable FOREIGN KEY (responsable_id) REFERENCES usuario (id),
    CONSTRAINT fk_historial_actor       FOREIGN KEY (actor_id)       REFERENCES usuario (id),
    CONSTRAINT ck_historial_tipo        CHECK (tipo IN ('CREACION', 'ACTUALIZACION', 'MIGRACION')),
    CONSTRAINT ck_historial_actor       CHECK (tipo = 'MIGRACION' OR actor_id IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS ix_historial_incidente_fecha ON incidente_historial (incidente_id, fecha, id);
CREATE INDEX IF NOT EXISTS ix_historial_estado ON incidente_historial (estado_id);

-- Incidentes sin historial (los existentes antes de esta tabla): una fila MIGRACION con su situación actual.
INSERT INTO incidente_historial (incidente_id, fecha, tipo, estado_id, prioridad_id, responsable_id, actor_id)
SELECT i.id, i.fecha_actualizacion, 'MIGRACION', i.estado_id, i.prioridad_id, i.usuario_id, NULL
FROM incidente i
WHERE NOT EXISTS (SELECT 1 FROM incidente_historial h WHERE h.incidente_id = i.id);

-- ¿La solución sugerida resolvió el incidente? La declara quien califica la respuesta.
-- NULL = no se indicó (incluye todas las calificaciones anteriores a esta columna).
ALTER TABLE calificacion ADD COLUMN IF NOT EXISTS resolvio BOOLEAN;

-- Objetivos de SLA de primera respuesta, por prioridad y opcionalmente por categoría.
-- La fila con categoria_id = NULL es el objetivo general de la prioridad; una fila con categoría
-- lo reemplaza para esa combinación. Sin fila aplicable, la primera respuesta no se evalúa
-- (SIN_OBJETIVO).
-- Objetivos generales acordados (2026-11-19): ver la carga al final de este archivo.
CREATE TABLE IF NOT EXISTS sla_objetivo (
    id                        BIGSERIAL PRIMARY KEY,
    prioridad_id              BIGINT  NOT NULL,
    categoria_id              BIGINT,
    minutos_primera_respuesta INTEGER NOT NULL,
    CONSTRAINT fk_sla_objetivo_prioridad FOREIGN KEY (prioridad_id) REFERENCES prioridad (id),
    CONSTRAINT fk_sla_objetivo_categoria FOREIGN KEY (categoria_id) REFERENCES categoria (id),
    CONSTRAINT uk_sla_objetivo           UNIQUE NULLS NOT DISTINCT (prioridad_id, categoria_id),
    CONSTRAINT ck_sla_objetivo_minutos   CHECK (minutos_primera_respuesta > 0)
);

-- Primera respuesta de cada incidente = primera fila del historial (CREACION o ACTUALIZACION) con un
-- estado distinto del inicial (el de menor orden: Abierto). Solo cambiar prioridad o responsable no cuenta.
--   objetivo_minutos: de sla_objetivo para la prioridad ACTUAL (categoría específica o general).
--   respondido_en:    NULL si sigue en el estado inicial.
--   sin_datos:        TRUE si al crear el historial (fila MIGRACION) ya había salido del estado inicial:
--                     se respondió, pero no se sabe cuándo.
CREATE OR REPLACE VIEW v_primera_respuesta AS
SELECT i.id AS incidente_id,
       COALESCE(oc.minutos_primera_respuesta, og.minutos_primera_respuesta) AS objetivo_minutos,
       (SELECT MIN(h.fecha) FROM incidente_historial h
        WHERE h.incidente_id = i.id AND h.tipo IN ('CREACION', 'ACTUALIZACION') AND h.estado_id <> ini.id) AS respondido_en,
       EXISTS (SELECT 1 FROM incidente_historial h
               WHERE h.incidente_id = i.id AND h.tipo = 'MIGRACION' AND h.estado_id <> ini.id) AS sin_datos
FROM incidente i
CROSS JOIN (SELECT id FROM estado_incidente ORDER BY orden, id LIMIT 1) ini
LEFT JOIN sla_objetivo oc ON oc.prioridad_id = i.prioridad_id AND oc.categoria_id = i.categoria_id
LEFT JOIN sla_objetivo og ON og.prioridad_id = i.prioridad_id AND og.categoria_id IS NULL;

-- Objetivos generales de primera respuesta por prioridad (minutos), acordados el 2026-11-19.
-- Solo se insertan si la prioridad aún no tiene objetivo general: un ajuste hecho después
-- directamente en la base no se sobrescribe al arrancar.
INSERT INTO sla_objetivo (prioridad_id, categoria_id, minutos_primera_respuesta)
SELECT p.id, NULL, v.minutos
FROM (VALUES ('Baja', 480), ('Media', 240), ('Alta', 60), ('Crítica', 15)) AS v (nombre, minutos)
JOIN prioridad p ON p.nombre = v.nombre
ON CONFLICT ON CONSTRAINT uk_sla_objetivo DO NOTHING;

-- Elementos de TI que puede afectar un incidente: equipos, aplicaciones o servicios.
-- Catálogo administrado con SQL, como categorías y prioridades. Un elemento inactivo deja de
-- ofrecerse para nuevos incidentes, pero los incidentes que ya lo tienen lo conservan.
-- Ejemplo: INSERT INTO elemento_ti (nombre, tipo, descripcion) VALUES ('ERP Nómina', 'APLICACION', 'Aplicación de nómina')
CREATE TABLE IF NOT EXISTS elemento_ti (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(120) NOT NULL,
    tipo        VARCHAR(20)  NOT NULL,
    descripcion VARCHAR(300),
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_elemento_ti_nombre UNIQUE (nombre),
    CONSTRAINT ck_elemento_ti_tipo   CHECK (tipo IN ('EQUIPO', 'APLICACION', 'SERVICIO')),
    CONSTRAINT ck_elemento_ti_nombre CHECK (length(trim(nombre)) > 0)
);

-- Elemento afectado por el incidente (opcional).
ALTER TABLE incidente ADD COLUMN IF NOT EXISTS elemento_id BIGINT
    CONSTRAINT fk_incidente_elemento REFERENCES elemento_ti (id);
CREATE INDEX IF NOT EXISTS ix_incidente_elemento ON incidente (elemento_id);

-- Problemas: causa común de varios incidentes. Una causa solo puede figurar como CONFIRMADA si
-- se registra también la evidencia que la respalda; sin evidencia queda como hipótesis.
CREATE TABLE IF NOT EXISTS problema (
    id                  BIGSERIAL PRIMARY KEY,
    titulo              VARCHAR(200) NOT NULL,
    descripcion         TEXT         NOT NULL,
    -- ABIERTO -> EN_ANALISIS -> CAUSA_CONFIRMADA -> CERRADO (se puede cerrar sin causa confirmada)
    estado              VARCHAR(20)  NOT NULL DEFAULT 'ABIERTO',
    elemento_id         BIGINT,
    causa               TEXT,
    evidencia_causa     TEXT,
    solucion_temporal   TEXT,
    solucion_definitiva TEXT,
    creador_id          BIGINT       NOT NULL,
    fecha_creacion      TIMESTAMP    NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMP    NOT NULL DEFAULT NOW(),
    fecha_cierre        TIMESTAMP,
    CONSTRAINT fk_problema_elemento FOREIGN KEY (elemento_id) REFERENCES elemento_ti (id),
    CONSTRAINT fk_problema_creador  FOREIGN KEY (creador_id)  REFERENCES usuario (id),
    CONSTRAINT ck_problema_estado   CHECK (estado IN ('ABIERTO', 'EN_ANALISIS', 'CAUSA_CONFIRMADA', 'CERRADO')),
    CONSTRAINT ck_problema_titulo   CHECK (length(trim(titulo)) > 0),
    CONSTRAINT ck_problema_desc     CHECK (length(trim(descripcion)) > 0),
    CONSTRAINT ck_problema_causa    CHECK (estado <> 'CAUSA_CONFIRMADA'
                                           OR (length(trim(coalesce(causa, ''))) > 0
                                               AND length(trim(coalesce(evidencia_causa, ''))) > 0)),
    CONSTRAINT ck_problema_cierre   CHECK ((estado = 'CERRADO') = (fecha_cierre IS NOT NULL)),
    CONSTRAINT ck_problema_fechas   CHECK (fecha_cierre IS NULL OR fecha_cierre >= fecha_creacion)
);
CREATE INDEX IF NOT EXISTS ix_problema_estado ON problema (estado);

-- Problema al que pertenece el incidente (opcional).
ALTER TABLE incidente ADD COLUMN IF NOT EXISTS problema_id BIGINT
    CONSTRAINT fk_incidente_problema REFERENCES problema (id);
CREATE INDEX IF NOT EXISTS ix_incidente_problema ON incidente (problema_id);

-- Revisión posterior de un incidente finalizado (una por incidente). La línea de tiempo no se guarda:
-- se arma al consultar con incidente_historial y consulta_ia, para que siempre refleje los datos.
-- El problema relacionado es el del incidente (incidente.problema_id), así que tampoco se repite aquí.
CREATE TABLE IF NOT EXISTS revision_incidente (
    id                   BIGSERIAL PRIMARY KEY,
    incidente_id         BIGINT      NOT NULL,
    impacto              TEXT        NOT NULL,
    -- CONFIRMADA exige evidencia; PENDIENTE = causa aún no demostrada (puede ser una hipótesis o estar vacía).
    estado_causa         VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    causa                TEXT,
    evidencia_causa      TEXT,
    solucion             TEXT,
    acciones_preventivas TEXT,
    autor_id             BIGINT      NOT NULL,
    fecha_creacion       TIMESTAMP   NOT NULL DEFAULT NOW(),
    fecha_actualizacion  TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_revision_incidente  UNIQUE (incidente_id),
    CONSTRAINT fk_revision_incidente  FOREIGN KEY (incidente_id) REFERENCES incidente (id) ON DELETE CASCADE,
    CONSTRAINT fk_revision_autor      FOREIGN KEY (autor_id)     REFERENCES usuario (id),
    CONSTRAINT ck_revision_estado     CHECK (estado_causa IN ('CONFIRMADA', 'PENDIENTE')),
    CONSTRAINT ck_revision_impacto    CHECK (length(trim(impacto)) > 0),
    CONSTRAINT ck_revision_causa      CHECK (estado_causa <> 'CONFIRMADA'
                                             OR (length(trim(coalesce(causa, ''))) > 0
                                                 AND length(trim(coalesce(evidencia_causa, ''))) > 0))
);

-- ===========================================================
-- Reglas de autorización y de estados (ver docs/seguridad/roles-y-estados.md)
-- ===========================================================

-- Auditoría de perfiles y estado de cuentas: quién cambió qué, a quién y cuándo.
-- actor_id NULL solo cuando el cambio lo hace el arranque (cuenta de administración inicial).
CREATE TABLE IF NOT EXISTS auditoria_usuario (
    id              BIGSERIAL PRIMARY KEY,
    fecha           TIMESTAMP   NOT NULL DEFAULT NOW(),
    origen          VARCHAR(20) NOT NULL,
    actor_id        BIGINT,
    usuario_id      BIGINT      NOT NULL,
    rol_anterior    VARCHAR(20),
    rol_nuevo       VARCHAR(20) NOT NULL,
    activo_anterior BOOLEAN,
    activo_nuevo    BOOLEAN     NOT NULL,
    CONSTRAINT fk_auditoria_actor   FOREIGN KEY (actor_id)   REFERENCES usuario (id),
    CONSTRAINT fk_auditoria_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT ck_auditoria_origen  CHECK (origen IN ('API', 'ARRANQUE')),
    CONSTRAINT ck_auditoria_actor   CHECK (origen = 'ARRANQUE' OR actor_id IS NOT NULL),
    CONSTRAINT ck_auditoria_roles   CHECK (rol_nuevo IN ('SOLICITANTE', 'TECNICO', 'ADMIN')
                                           AND (rol_anterior IS NULL OR rol_anterior IN ('SOLICITANTE', 'TECNICO', 'ADMIN')))
);
CREATE INDEX IF NOT EXISTS ix_auditoria_usuario ON auditoria_usuario (usuario_id, fecha);

-- Máquina de estados de incidentes: solo estas transiciones son válidas en operaciones normales.
-- Un estado sin transiciones de salida es terminal (Cerrado). Salir de él solo es posible con la
-- reapertura excepcional (solo ADMIN, con motivo, registrada en incidente_historial como REAPERTURA).
CREATE TABLE IF NOT EXISTS transicion_estado (
    estado_origen_id  BIGINT NOT NULL,
    estado_destino_id BIGINT NOT NULL,
    CONSTRAINT pk_transicion_estado PRIMARY KEY (estado_origen_id, estado_destino_id),
    CONSTRAINT fk_transicion_origen  FOREIGN KEY (estado_origen_id)  REFERENCES estado_incidente (id),
    CONSTRAINT fk_transicion_destino FOREIGN KEY (estado_destino_id) REFERENCES estado_incidente (id),
    CONSTRAINT ck_transicion_distinta CHECK (estado_origen_id <> estado_destino_id)
);

INSERT INTO transicion_estado (estado_origen_id, estado_destino_id)
SELECT o.id, d.id
FROM (VALUES ('Abierto', 'En progreso'), ('Abierto', 'Resuelto'),
             ('En progreso', 'Abierto'), ('En progreso', 'Resuelto'),
             ('Resuelto', 'En progreso'), ('Resuelto', 'Cerrado')) AS t (origen, destino)
JOIN estado_incidente o ON o.nombre = t.origen
JOIN estado_incidente d ON d.nombre = t.destino
ON CONFLICT ON CONSTRAINT pk_transicion_estado DO NOTHING;

-- Reapertura excepcional: nuevo tipo de fila en el historial, con motivo obligatorio.
ALTER TABLE incidente_historial ADD COLUMN IF NOT EXISTS motivo TEXT;
ALTER TABLE incidente_historial DROP CONSTRAINT IF EXISTS ck_historial_tipo;
ALTER TABLE incidente_historial ADD CONSTRAINT ck_historial_tipo
    CHECK (tipo IN ('CREACION', 'ACTUALIZACION', 'MIGRACION', 'REAPERTURA'));
ALTER TABLE incidente_historial DROP CONSTRAINT IF EXISTS ck_historial_motivo;
ALTER TABLE incidente_historial ADD CONSTRAINT ck_historial_motivo
    CHECK (tipo <> 'REAPERTURA' OR length(trim(coalesce(motivo, ''))) > 0);

-- Seguimiento: un incidente nuevo puede referirse a uno cerrado sin modificar el original.
ALTER TABLE incidente ADD COLUMN IF NOT EXISTS seguimiento_de_id BIGINT
    CONSTRAINT fk_incidente_seguimiento REFERENCES incidente (id);
CREATE INDEX IF NOT EXISTS ix_incidente_seguimiento ON incidente (seguimiento_de_id);
ALTER TABLE incidente DROP CONSTRAINT IF EXISTS ck_incidente_seguimiento;
ALTER TABLE incidente ADD CONSTRAINT ck_incidente_seguimiento
    CHECK (seguimiento_de_id IS NULL OR seguimiento_de_id <> id);

-- Fuentes de una respuesta RAG: usada = true si el modelo la citó (evidencia), false si solo fue un
-- candidato recuperado y entregado como contexto. Las filas existentes eran todas citadas.
ALTER TABLE fuente_respuesta ADD COLUMN IF NOT EXISTS usada BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX IF NOT EXISTS ix_fuente_usada ON fuente_respuesta (articulo_id) WHERE usada;

-- Carga de archivos TXT, MD o CSV con artículos. Cada archivo es una importación. Sus registros se
-- previsualizan y, al confirmar, se crean como borradores inactivos y sin verificar (nunca como fuente RAG).
CREATE TABLE IF NOT EXISTS importacion_articulo (
    id                BIGSERIAL    PRIMARY KEY,
    nombre_archivo    VARCHAR(255) NOT NULL,
    formato           VARCHAR(5)   NOT NULL,
    hash              VARCHAR(64)  NOT NULL,
    tamano_bytes      INTEGER      NOT NULL,
    estado            VARCHAR(20)  NOT NULL DEFAULT 'RECIBIDO',
    total_registros   INTEGER      NOT NULL DEFAULT 0,
    detalle_error     VARCHAR(1000),
    categoria_id      BIGINT       CONSTRAINT fk_importacion_categoria REFERENCES categoria (id),
    cargado_por_id    BIGINT       NOT NULL CONSTRAINT fk_importacion_cargado REFERENCES usuario (id),
    fecha_carga       TIMESTAMP    NOT NULL DEFAULT NOW(),
    revisado_por_id   BIGINT       CONSTRAINT fk_importacion_revisado REFERENCES usuario (id),
    fecha_revision    TIMESTAMP,
    CONSTRAINT ck_importacion_formato CHECK (formato IN ('TXT', 'MD', 'CSV')),
    CONSTRAINT ck_importacion_estado  CHECK (estado IN ('RECIBIDO', 'PROCESANDO', 'PENDIENTE_REVISION',
                                                        'IMPORTADO', 'ERROR', 'DESCARTADO')),
    CONSTRAINT ck_importacion_hash    CHECK (hash ~ '^[0-9a-f]{64}$')
);
-- El mismo archivo (mismo SHA-256) no puede tener dos importaciones vigentes a la vez.
CREATE UNIQUE INDEX IF NOT EXISTS uk_importacion_hash_vigente ON importacion_articulo (hash)
    WHERE estado NOT IN ('ERROR', 'DESCARTADO');
CREATE INDEX IF NOT EXISTS ix_importacion_fecha ON importacion_articulo (fecha_carga DESC);

CREATE TABLE IF NOT EXISTS importacion_fila (
    id              BIGSERIAL    PRIMARY KEY,
    importacion_id  BIGINT       NOT NULL CONSTRAINT fk_fila_importacion REFERENCES importacion_articulo (id) ON DELETE CASCADE,
    numero          INTEGER      NOT NULL,
    titulo          VARCHAR(300),
    problema        TEXT,
    sintomas        TEXT,
    causa           TEXT,
    solucion        TEXT,
    categoria_id    BIGINT       CONSTRAINT fk_fila_categoria REFERENCES categoria (id),
    estado          VARCHAR(20)  NOT NULL,
    errores         VARCHAR(2000),
    articulo_id     BIGINT       CONSTRAINT fk_fila_articulo REFERENCES articulo_conocimiento (id) ON DELETE SET NULL,
    CONSTRAINT ck_fila_estado CHECK (estado IN ('VALIDA', 'INVALIDA', 'DUPLICADA', 'IMPORTADA', 'DESCARTADA')),
    CONSTRAINT uk_fila_numero UNIQUE (importacion_id, numero)
);

-- Origen del artículo: NULL si se escribió a mano, o la importación que lo creó.
ALTER TABLE articulo_conocimiento ADD COLUMN IF NOT EXISTS importacion_id BIGINT
    CONSTRAINT fk_articulo_importacion REFERENCES importacion_articulo (id);
CREATE INDEX IF NOT EXISTS ix_articulo_importacion ON articulo_conocimiento (importacion_id);

-- Ingreso guiado: datos que el formulario pide antes de consultar el asistente. Son opcionales para no
-- invalidar los incidentes existentes. Si existen, el backend los agrega a la consulta RAG por defecto.
ALTER TABLE incidente ADD COLUMN IF NOT EXISTS mensaje_error VARCHAR(1000);
ALTER TABLE incidente ADD COLUMN IF NOT EXISTS pasos_intentados VARCHAR(2000);

-- Alertas y escalamiento de SLA (etapa 5). Las genera la revisión programada de AlertaSlaService,
-- solo con reglas sobre datos relacionales y sin IA.
--   tipo  RESOLUCION:        vence = creación + prioridad.sla_horas
--         PRIMERA_RESPUESTA: vence = creación + objetivo de v_primera_respuesta, mientras no haya respuesta
--   nivel AVISO:             queda como máximo EstadoSla.FRACCION_ALERTA del plazo, para el técnico responsable
--         ESCALAMIENTO:      venció el plazo, para el técnico responsable y los administradores
--   estado PENDIENTE -> ATENDIDA (alguien la revisó, con nota opcional) o SUPERADA (la regla ya no se cumple:
--         incidente finalizado, primera respuesta dada, vencimiento cambiado por otra prioridad, o el aviso
--         pasó a escalamiento). Atender una alerta no detiene ni modifica el SLA.
-- Un mismo (incidente, tipo, nivel, vencimiento) se registra una sola vez. Si el incidente se reabre,
-- la alerta SUPERADA vuelve a PENDIENTE.
CREATE TABLE IF NOT EXISTS alerta_sla (
    id               BIGSERIAL    PRIMARY KEY,
    incidente_id     BIGINT       NOT NULL CONSTRAINT fk_alerta_incidente REFERENCES incidente (id) ON DELETE CASCADE,
    tipo             VARCHAR(20)  NOT NULL,
    nivel            VARCHAR(20)  NOT NULL,
    vence            TIMESTAMP    NOT NULL,
    responsable_id   BIGINT       NOT NULL CONSTRAINT fk_alerta_responsable REFERENCES usuario (id),
    generada_en      TIMESTAMP    NOT NULL,
    estado           VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    atendida_por_id  BIGINT       CONSTRAINT fk_alerta_atendida_por REFERENCES usuario (id),
    fecha_cierre     TIMESTAMP,
    nota             VARCHAR(500),
    CONSTRAINT ck_alerta_tipo     CHECK (tipo IN ('PRIMERA_RESPUESTA', 'RESOLUCION')),
    CONSTRAINT ck_alerta_nivel    CHECK (nivel IN ('AVISO', 'ESCALAMIENTO')),
    CONSTRAINT ck_alerta_estado   CHECK (estado IN ('PENDIENTE', 'ATENDIDA', 'SUPERADA')),
    CONSTRAINT ck_alerta_cierre   CHECK ((estado = 'PENDIENTE') = (fecha_cierre IS NULL)),
    CONSTRAINT ck_alerta_atendida CHECK ((estado = 'ATENDIDA') = (atendida_por_id IS NOT NULL)),
    CONSTRAINT uk_alerta_sla      UNIQUE (incidente_id, tipo, nivel, vence)
);
CREATE INDEX IF NOT EXISTS ix_alerta_sla_estado ON alerta_sla (estado, nivel, vence);

-- Trazabilidad con n8n (sección 13, etapa A). correlacion_id lo genera Spring Boot por consulta y viaja en la
-- cabecera X-Correlation-Id. n8n_ejecucion_id es el id de la ejecución que devuelve el flujo (NULL con el flujo
-- publicado anterior, que no lo informa). El registro de ejecuciones de n8n no sustituye a esta tabla.
ALTER TABLE consulta_ia ADD COLUMN IF NOT EXISTS correlacion_id VARCHAR(64);
ALTER TABLE consulta_ia ADD COLUMN IF NOT EXISTS n8n_ejecucion_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS ix_consulta_correlacion ON consulta_ia (correlacion_id);
