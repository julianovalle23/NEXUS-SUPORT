package com.nexus.support.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class IncidenteTest {

    @Test
    void pasarAEstadoFinalRegistraFechaDeResolucionYReabrirLaElimina() {
        Incidente i = new Incidente();
        i.alCrear();

        i.cambiarEstado(estado("Resuelto", true));
        assertThat(i.getFechaResolucion()).isNotNull().isAfterOrEqualTo(i.getFechaCreacion());

        i.cambiarEstado(estado("En progreso", false));
        assertThat(i.getFechaResolucion()).isNull();
    }

    @Test
    void cerrarUnIncidenteResueltoConservaLaFechaOriginal() {
        Incidente i = new Incidente();
        i.alCrear();
        i.cambiarEstado(estado("Resuelto", true));
        var resuelto = i.getFechaResolucion();

        i.cambiarEstado(estado("Cerrado", true));
        assertThat(i.getFechaResolucion()).isEqualTo(resuelto);
    }

    @Test
    void creadoDirectamenteComoResueltoNoQuedaConResolucionAnteriorACreacion() {
        Incidente i = new Incidente();
        i.cambiarEstado(estado("Resuelto", true));
        i.alCrear();
        assertThat(i.getFechaResolucion()).isAfterOrEqualTo(i.getFechaCreacion());
    }

    private static EstadoIncidente estado(String nombre, boolean esFinal) {
        EstadoIncidente e = new EstadoIncidente();
        ReflectionTestUtils.setField(e, "nombre", nombre);
        ReflectionTestUtils.setField(e, "esFinal", esFinal);
        return e;
    }
}
