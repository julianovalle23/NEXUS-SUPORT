package com.nexus.support.domain;

/**
 * Situación de un incidente frente al objetivo de primera respuesta (sla_objetivo).
 * La calcula SQL (vista v_primera_respuesta + AnaliticaRepository) con la misma regla de
 * "por vencer" que {@link EstadoSla#FRACCION_ALERTA}.
 */
public enum EstadoPrimeraRespuesta {
    /** Sin responder y dentro del plazo. */
    EN_PLAZO,
    /** Sin responder y le queda la fracción de alerta del plazo o menos. */
    POR_VENCER,
    /** Sin responder y pasado el plazo. */
    VENCIDO,
    /** Respondido dentro del plazo. */
    CUMPLIDO,
    /** Respondido después del plazo. */
    INCUMPLIDO,
    /** No hay objetivo configurado para su prioridad y categoría. */
    SIN_OBJETIVO,
    /** Ya estaba respondido cuando se creó el historial: no se sabe cuándo. */
    SIN_DATOS
}
