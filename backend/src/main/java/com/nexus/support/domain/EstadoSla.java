package com.nexus.support.domain;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Situación de un incidente frente al SLA de su prioridad (prioridad.sla_horas, medido desde la creación).
 * Abiertos: EN_PLAZO, POR_VENCER o VENCIDO. Resueltos/cerrados: CUMPLIDO o INCUMPLIDO.
 */
public enum EstadoSla {
    EN_PLAZO, POR_VENCER, VENCIDO, CUMPLIDO, INCUMPLIDO;

    /** Un incidente abierto está "por vencer" cuando le queda este porcentaje del plazo o menos. */
    public static final double FRACCION_ALERTA = 0.25;

    public static LocalDateTime vencimiento(LocalDateTime creacion, int slaHoras) {
        return creacion.plusHours(slaHoras);
    }

    public static EstadoSla calcular(LocalDateTime creacion, LocalDateTime resolucion, boolean finalizado,
                                     int slaHoras, LocalDateTime ahora) {
        LocalDateTime vence = vencimiento(creacion, slaHoras);
        if (finalizado && resolucion != null) {
            return resolucion.isAfter(vence) ? INCUMPLIDO : CUMPLIDO;
        }
        if (ahora.isAfter(vence)) return VENCIDO;
        long alertaMinutos = Math.round(slaHoras * 60 * FRACCION_ALERTA);
        return Duration.between(ahora, vence).toMinutes() <= alertaMinutos ? POR_VENCER : EN_PLAZO;
    }
}
