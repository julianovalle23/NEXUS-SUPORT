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
