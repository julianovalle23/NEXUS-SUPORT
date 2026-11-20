package com.nexus.support.service;

import static com.nexus.support.service.Datos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.nexus.support.domain.*;
import com.nexus.support.dto.AsistenciaDtos.CalificacionDto;
import com.nexus.support.dto.AsistenciaDtos.CalificacionRequest;
import com.nexus.support.exception.ReglaNegocioException;
import com.nexus.support.repository.CalificacionRepository;
import com.nexus.support.repository.RespuestaIARepository;

/** "¿La solución resolvió el incidente?" (etapa 2). */
class CalificacionServiceTest {

    private RespuestaIARepository respuestas;
    private CalificacionRepository calificaciones;
    private CalificacionService service;

    @BeforeEach
    void setUp() {
        respuestas = mock(RespuestaIARepository.class);
        calificaciones = mock(CalificacionRepository.class);
        service = new CalificacionService(respuestas, calificaciones);
        when(calificaciones.findByRespuestaId(any())).thenReturn(Optional.empty());
        when(calificaciones.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void indicarResolvioEnUnaRespuestaSinFuentesSeRechaza() {
        respuesta(EstadoConsulta.SIN_RESULTADOS);

        assertThatThrownBy(() -> service.calificar(40L, new CalificacionRequest(2, null, false)))
                .isInstanceOf(ReglaNegocioException.class);
        verify(calificaciones, never()).saveAndFlush(any());
    }

    @Test
    void sinIndicarResolvioSePuedeCalificarUnaRespuestaSinFuentes() {
        respuesta(EstadoConsulta.SIN_RESULTADOS);

        CalificacionDto dto = service.calificar(40L, new CalificacionRequest(2, null, null));

        assertThat(dto.puntuacion()).isEqualTo((short) 2);
        assertThat(dto.resolvio()).isNull();
    }

    @Test
    void enUnaRespuestaConFuentesSeGuardaLoDeclarado() {
        respuesta(EstadoConsulta.RESPONDIDA);

        CalificacionDto dto = service.calificar(40L, new CalificacionRequest(5, "  Funcionó  ", true));

        assertThat(dto.resolvio()).isTrue();
        assertThat(dto.comentario()).isEqualTo("Funcionó");
    }

    @Test
    void recalificarReemplazaElValorDeResolvio() {
        RespuestaIA r = respuesta(EstadoConsulta.RESPONDIDA);
        Calificacion previa = new Calificacion(r);
        previa.setResolvio(true);
        when(calificaciones.findByRespuestaId(40L)).thenReturn(Optional.of(previa));

        CalificacionDto dto = service.calificar(40L, new CalificacionRequest(3, null, false));

        assertThat(dto.resolvio()).isFalse();
    }

    private RespuestaIA respuesta(EstadoConsulta estado) {
        Incidente i = incidente(7, categoria(5, "Correo"), prioridad(2, "Media", 2, 24),
                estado(1, "Abierto", false), usuario(1, "Laura Gomez", Rol.TECNICO));
        ConsultaIA c = new ConsultaIA(i, "No envía correos", new BigDecimal("0.450"));
        c.setEstado(estado);
        RespuestaIA r = new RespuestaIA(c, "Respuesta", null, null);
        ReflectionTestUtils.setField(r, "id", 40L);
        when(respuestas.findById(40L)).thenReturn(Optional.of(r));
        return r;
    }
}
