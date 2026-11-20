package com.nexus.support.domain;

/** Origen de una fila de incidente_historial. */
public enum TipoHistorial {
    /** Registro del incidente. */
    CREACION,
    /** Cambio de estado, prioridad o responsable. */
    ACTUALIZACION,
    /** Situación conocida cuando se creó la tabla; no representa un cambio real en esa fecha. */
    MIGRACION,
    /** Reapertura excepcional de un incidente en estado terminal (solo ADMIN, con motivo obligatorio). */
    REAPERTURA
}
