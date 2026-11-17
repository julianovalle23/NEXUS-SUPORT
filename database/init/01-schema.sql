-- ===========================================================
-- NEXUS Support - Esquema relacional (PostgreSQL 16)
-- Se carga con psql después de database/00-crear-base.sql (ver README).
-- ===========================================================

SET client_encoding = 'UTF8';
SET timezone = 'America/Bogota';

-- -----------------------------------------------------------
-- Catálogos
-- -----------------------------------------------------------
CREATE TABLE usuario (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(120) NOT NULL,
    correo      VARCHAR(160) NOT NULL,
    area        VARCHAR(80)  NOT NULL,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_usuario_correo UNIQUE (correo),
    CONSTRAINT ck_usuario_correo CHECK (correo LIKE '%_@_%._%')
);

CREATE TABLE categoria (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(80)  NOT NULL,
    descripcion VARCHAR(300) NOT NULL,
    CONSTRAINT uk_categoria_nombre UNIQUE (nombre)
);

CREATE TABLE prioridad (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(40) NOT NULL,
    nivel       SMALLINT    NOT NULL,
    sla_horas   INTEGER     NOT NULL,
    CONSTRAINT uk_prioridad_nombre UNIQUE (nombre),
    CONSTRAINT uk_prioridad_nivel  UNIQUE (nivel),
    CONSTRAINT ck_prioridad_nivel  CHECK (nivel BETWEEN 1 AND 4),
    CONSTRAINT ck_prioridad_sla    CHECK (sla_horas > 0)
);

CREATE TABLE estado_incidente (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(40) NOT NULL,
    -- TRUE para estados que cierran el ciclo (Resuelto, Cerrado)
    es_final    BOOLEAN     NOT NULL DEFAULT FALSE,
    orden       SMALLINT    NOT NULL,
    CONSTRAINT uk_estado_nombre UNIQUE (nombre)
);

-- -----------------------------------------------------------
-- Incidentes
-- -----------------------------------------------------------
CREATE TABLE incidente (
    id                  BIGSERIAL PRIMARY KEY,
    titulo              VARCHAR(200) NOT NULL,
    descripcion         TEXT         NOT NULL,
    categoria_id        BIGINT       NOT NULL,
    prioridad_id        BIGINT       NOT NULL,
    estado_id           BIGINT       NOT NULL,
    usuario_id          BIGINT       NOT NULL,
    fecha_creacion      TIMESTAMP    NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMP    NOT NULL DEFAULT NOW(),
    fecha_resolucion    TIMESTAMP,
    CONSTRAINT fk_incidente_categoria FOREIGN KEY (categoria_id) REFERENCES categoria (id),
    CONSTRAINT fk_incidente_prioridad FOREIGN KEY (prioridad_id) REFERENCES prioridad (id),
    CONSTRAINT fk_incidente_estado    FOREIGN KEY (estado_id)    REFERENCES estado_incidente (id),
    CONSTRAINT fk_incidente_usuario   FOREIGN KEY (usuario_id)   REFERENCES usuario (id),
    CONSTRAINT ck_incidente_titulo    CHECK (length(trim(titulo)) > 0),
    CONSTRAINT ck_incidente_desc      CHECK (length(trim(descripcion)) > 0),
    CONSTRAINT ck_incidente_fechas    CHECK (fecha_resolucion IS NULL OR fecha_resolucion >= fecha_creacion)
);

CREATE INDEX idx_incidente_fecha_creacion ON incidente (fecha_creacion);
CREATE INDEX idx_incidente_categoria      ON incidente (categoria_id);
CREATE INDEX idx_incidente_prioridad      ON incidente (prioridad_id);
CREATE INDEX idx_incidente_estado         ON incidente (estado_id);
CREATE INDEX idx_incidente_usuario        ON incidente (usuario_id);

-- -----------------------------------------------------------
-- Base de conocimiento
-- -----------------------------------------------------------
CREATE TABLE articulo_conocimiento (
    id                  BIGSERIAL PRIMARY KEY,
    titulo              VARCHAR(200) NOT NULL,
    problema            TEXT         NOT NULL,
    sintomas            TEXT         NOT NULL,
    causa               TEXT         NOT NULL,
    solucion            TEXT         NOT NULL,
    categoria_id        BIGINT,
    fecha_creacion      TIMESTAMP    NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMP    NOT NULL DEFAULT NOW(),
    -- Solo artículos activos Y verificados pueden usarse como fuentes RAG
    activo              BOOLEAN      NOT NULL DEFAULT TRUE,
    verificado          BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_articulo_categoria FOREIGN KEY (categoria_id) REFERENCES categoria (id),
    CONSTRAINT uk_articulo_titulo    UNIQUE (titulo)
);

CREATE INDEX idx_articulo_estado    ON articulo_conocimiento (activo, verificado);
CREATE INDEX idx_articulo_categoria ON articulo_conocimiento (categoria_id);

-- -----------------------------------------------------------
-- Asistencia inteligente (RAG)
-- -----------------------------------------------------------
CREATE TABLE consulta_ia (
    id              BIGSERIAL PRIMARY KEY,
    incidente_id    BIGINT       NOT NULL,
    pregunta        TEXT         NOT NULL,
    fecha           TIMESTAMP    NOT NULL DEFAULT NOW(),
    estado          VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    umbral          NUMERIC(4,3) NOT NULL,
    detalle_error   TEXT,
    CONSTRAINT fk_consulta_incidente FOREIGN KEY (incidente_id) REFERENCES incidente (id) ON DELETE CASCADE,
    CONSTRAINT ck_consulta_estado    CHECK (estado IN ('PENDIENTE', 'RESPONDIDA', 'SIN_RESULTADOS', 'ERROR')),
    CONSTRAINT ck_consulta_pregunta  CHECK (length(trim(pregunta)) > 0),
    CONSTRAINT ck_consulta_umbral    CHECK (umbral BETWEEN 0 AND 1)
);

CREATE INDEX idx_consulta_incidente ON consulta_ia (incidente_id);
CREATE INDEX idx_consulta_fecha     ON consulta_ia (fecha);
CREATE INDEX idx_consulta_estado    ON consulta_ia (estado);

CREATE TABLE respuesta_ia (
    id              BIGSERIAL PRIMARY KEY,
    consulta_id     BIGINT      NOT NULL,
    respuesta       TEXT        NOT NULL,
    pasos           TEXT,
    modelo          VARCHAR(120),
    fecha           TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_respuesta_consulta FOREIGN KEY (consulta_id) REFERENCES consulta_ia (id) ON DELETE CASCADE,
    CONSTRAINT uk_respuesta_consulta UNIQUE (consulta_id)
);

CREATE TABLE fuente_respuesta (
    id              BIGSERIAL PRIMARY KEY,
    respuesta_id    BIGINT       NOT NULL,
    articulo_id     BIGINT       NOT NULL,
    similitud       NUMERIC(5,4) NOT NULL,
    CONSTRAINT fk_fuente_respuesta FOREIGN KEY (respuesta_id) REFERENCES respuesta_ia (id) ON DELETE CASCADE,
    CONSTRAINT fk_fuente_articulo  FOREIGN KEY (articulo_id)  REFERENCES articulo_conocimiento (id),
    CONSTRAINT uk_fuente           UNIQUE (respuesta_id, articulo_id),
    CONSTRAINT ck_fuente_similitud CHECK (similitud BETWEEN 0 AND 1)
);

CREATE INDEX idx_fuente_articulo ON fuente_respuesta (articulo_id);

CREATE TABLE calificacion (
    id              BIGSERIAL PRIMARY KEY,
    respuesta_id    BIGINT      NOT NULL,
    puntuacion      SMALLINT    NOT NULL,
    comentario      VARCHAR(500),
    fecha           TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_calificacion_respuesta FOREIGN KEY (respuesta_id) REFERENCES respuesta_ia (id) ON DELETE CASCADE,
    CONSTRAINT uk_calificacion_respuesta UNIQUE (respuesta_id),
    CONSTRAINT ck_calificacion_puntuacion CHECK (puntuacion BETWEEN 1 AND 5)
);
