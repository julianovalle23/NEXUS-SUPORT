package com.nexus.support.domain;

/** Perfil de una cuenta. Se guarda como texto en usuario.rol. */
public enum Rol {
    /** Registra incidentes y consulta solo los suyos. */
    SOLICITANTE,
    /** Gestiona y reasigna incidentes, solicita asistencia y consulta indicadores. */
    TECNICO,
    /** Todo lo del técnico, más la base de conocimiento y las cuentas de usuario. */
    ADMIN
}
