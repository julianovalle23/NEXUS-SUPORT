package com.nexus.support.service;

import static com.nexus.support.service.Datos.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nexus.support.domain.Incidente;
import com.nexus.support.domain.Rol;

/** Consulta RAG por defecto armada con el ingreso guiado (etapa 5). */
class PreguntaGuiadaTest {

    private Incidente inc;

    @BeforeEach
    void setUp() {
        inc = incidente(7, categoria(5, "Correo"), prioridad(2, "Media", 2, 24), estado(1, "Abierto", false),
                usuario(1, "Laura Gomez", Rol.TECNICO));
    }

    @Test
    void sinDatosGuiadosEsLaDescripcion() {
        assertThat(AsistenciaService.preguntaGuiada(inc)).isEqualTo("Los correos se quedan en la bandeja de salida.");
    }

    @Test
    void agregaElementoMensajeYPasosEnOrden() {
        inc.setElemento(elemento(30, "Outlook 365", true));
        inc.setMensajeError("  0x800CCC0E  ");
        inc.setPasosIntentados("Reinicié el equipo");

        assertThat(AsistenciaService.preguntaGuiada(inc)).isEqualTo("""
                Los correos se quedan en la bandeja de salida.
                Equipo, aplicación o servicio afectado: Outlook 365
                Mensaje de error: 0x800CCC0E
                Pasos ya intentados: Reinicié el equipo""");
    }

    @Test
    void omiteLosCamposEnBlanco() {
        inc.setMensajeError("   ");
        inc.setPasosIntentados("Probé con otro usuario");

        assertThat(AsistenciaService.preguntaGuiada(inc))
                .doesNotContain("Mensaje de error")
                .endsWith("\nPasos ya intentados: Probé con otro usuario");
    }

    @Test
    void sinDescripcionDevuelveNullAunqueHayaDatosGuiados() {
        inc.setDescripcion("   ");
        inc.setMensajeError("0x800CCC0E");

        assertThat(AsistenciaService.preguntaGuiada(inc)).isNull();
    }

    @Test
    void siNoCabeSeAcortaLaDescripcionYSeConservanLosDatosGuiados() {
        inc.setDescripcion("x".repeat(AsistenciaService.MAX_PREGUNTA));
        inc.setMensajeError("E".repeat(1000));
        inc.setPasosIntentados("P".repeat(2000));

        String p = AsistenciaService.preguntaGuiada(inc);

        assertThat(p).hasSizeLessThanOrEqualTo(AsistenciaService.MAX_PREGUNTA)
                .contains("x…\nMensaje de error: " + "E".repeat(1000))
                .endsWith("Pasos ya intentados: " + "P".repeat(2000));
    }
}
