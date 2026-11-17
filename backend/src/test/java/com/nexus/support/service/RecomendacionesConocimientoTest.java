package com.nexus.support.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.nexus.support.dto.AnaliticaDtos.ArticuloUso;
import com.nexus.support.dto.AnaliticaDtos.CoberturaCategoria;
import com.nexus.support.dto.AnaliticaDtos.Recomendacion;

class RecomendacionesConocimientoTest {

    @Test
    void sinHallazgosNoHayRecomendaciones() {
        var cobertura = List.of(new CoberturaCategoria(1L, "Conectividad", 7, 4, 4, 0, 0, new BigDecimal("4.50")));
        var uso = List.of(new ArticuloUso(2L, "DHCP", "Conectividad", true, 3, new BigDecimal("0.7"), new BigDecimal("4.5"), 2));
        assertTrue(AnaliticaService.recomendaciones(uso, cobertura).isEmpty());
    }

    @Test
    void cadaHallazgoGeneraSuReglaConEvidencia() {
        var cobertura = List.of(new CoberturaCategoria(2L, "Hardware", 0, 3, 1, 2, 1, new BigDecimal("2.00")));
        var uso = List.of(
                new ArticuloUso(9L, "Impresora", "Hardware", true, 2, new BigDecimal("0.5"), new BigDecimal("2.00"), 2),
                // Una sola calificación no basta para recomendar revisar el artículo
                new ArticuloUso(10L, "Monitor", "Hardware", true, 1, new BigDecimal("0.5"), new BigDecimal("1.00"), 1));
        List<String> reglas = AnaliticaService.recomendaciones(uso, cobertura).stream().map(Recomendacion::regla).toList();
        assertEquals(List.of("SIN_ARTICULOS_VALIDOS", "CONSULTAS_SIN_FUENTES", "RESPUESTAS_MAL_CALIFICADAS",
                "ARTICULO_MAL_CALIFICADO"), reglas);
    }
}
