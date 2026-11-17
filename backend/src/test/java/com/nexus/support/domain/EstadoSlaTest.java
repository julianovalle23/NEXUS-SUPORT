package com.nexus.support.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class EstadoSlaTest {

    private static final LocalDateTime CREADO = LocalDateTime.of(2026, 11, 16, 8, 0);

    @Test
    void abiertoSeClasificaSegunElTiempoRestante() {
        // SLA de 8 h: vence a las 16:00; "por vencer" desde las 14:00 (25 % del plazo)
        assertEquals(EstadoSla.EN_PLAZO, EstadoSla.calcular(CREADO, null, false, 8, CREADO.plusHours(5)));
        assertEquals(EstadoSla.POR_VENCER, EstadoSla.calcular(CREADO, null, false, 8, CREADO.plusHours(6)));
        assertEquals(EstadoSla.POR_VENCER, EstadoSla.calcular(CREADO, null, false, 8, CREADO.plusHours(8)));
        assertEquals(EstadoSla.VENCIDO, EstadoSla.calcular(CREADO, null, false, 8, CREADO.plusHours(8).plusMinutes(1)));
    }

    @Test
    void finalizadoSeEvaluaConLaFechaDeResolucion() {
        LocalDateTime ahora = CREADO.plusDays(10);
        assertEquals(EstadoSla.CUMPLIDO, EstadoSla.calcular(CREADO, CREADO.plusHours(8), true, 8, ahora));
        assertEquals(EstadoSla.INCUMPLIDO, EstadoSla.calcular(CREADO, CREADO.plusHours(9), true, 8, ahora));
    }
}
