package com.nexus.support.domain;

/**
 * Valores de la tabla alerta_sla (database/init/05-mejoras.sql). Las alertas se leen y escriben con SQL
 * en AlertaSlaRepository, sin entidad JPA, porque se generan por lotes a partir de reglas.
 */
public final class AlertaSla {

    private AlertaSla() {
    }

    /** Plazo vigilado. */
    public enum Tipo { PRIMERA_RESPUESTA, RESOLUCION }

    /** AVISO antes del vencimiento (técnico responsable); ESCALAMIENTO después (también administradores). */
    public enum Nivel { AVISO, ESCALAMIENTO }

    public enum Estado { PENDIENTE, ATENDIDA, SUPERADA }
}
