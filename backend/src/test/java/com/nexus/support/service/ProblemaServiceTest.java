package com.nexus.support.service;

import static com.nexus.support.service.Datos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.nexus.support.domain.*;
import com.nexus.support.dto.ProblemaDtos.ProblemaRequest;
import com.nexus.support.exception.ReglaNegocioException;
import com.nexus.support.repository.IncidenteRepository;
import com.nexus.support.repository.ProblemaRepository;
import com.nexus.support.repository.UsuarioRepository;

/** Reglas de problemas (etapa 4b). */
class ProblemaServiceTest {

    private final Usuario laura = usuario(1, "Laura Gomez", Rol.TECNICO);

    private ProblemaRepository problemas;
    private IncidenteService incidenteService;
    private ProblemaService service;
    private Problema problema;
    private Incidente incidente;

    @BeforeEach
    void setUp() {
        problemas = mock(ProblemaRepository.class);
        IncidenteRepository incidentes = mock(IncidenteRepository.class);
        incidenteService = mock(IncidenteService.class);
        service = new ProblemaService(problemas, incidentes, incidenteService, mock(CatalogoService.class),
                mock(UsuarioRepository.class));

        problema = problema(3L);
        when(problemas.findById(3L)).thenReturn(Optional.of(problema));
        incidente = incidente(7, categoria(5, "Correo"), prioridad(2, "Media", 2, 24),
                estado(1, "Abierto", false), laura);
        when(incidenteService.entidadParaModificar(7L)).thenReturn(incidente);
        when(incidentes.findByProblemaIdOrderByFechaCreacionDesc(any())).thenReturn(List.of());
    }

    @Test
    void confirmarLaCausaSinEvidenciaSeRechaza() {
        problema.setCausa("Certificado vencido");

        assertThatThrownBy(() -> service.cambiarEstado(3L, EstadoProblema.CAUSA_CONFIRMADA))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(problema.getEstado()).isEqualTo(EstadoProblema.ABIERTO);
    }

    @Test
    void confirmarLaCausaConEvidenciaSePermite() {
        problema.setCausa("Certificado vencido");
        problema.setEvidenciaCausa("Registro del servidor con el certificado expirado");

        service.cambiarEstado(3L, EstadoProblema.CAUSA_CONFIRMADA);

        assertThat(problema.getEstado()).isEqualTo(EstadoProblema.CAUSA_CONFIRMADA);
    }

    @Test
    void conLaCausaConfirmadaNoSePuedeVaciarLaEvidencia() {
        problema.setCausa("Certificado vencido");
        problema.setEvidenciaCausa("Registro del servidor");
        problema.cambiarEstado(EstadoProblema.CAUSA_CONFIRMADA);
        ProblemaRequest sinEvidencia = new ProblemaRequest("Correo no sale", "Varios usuarios", null,
                "Certificado vencido", "  ", null, null);

        assertThatThrownBy(() -> service.actualizar(3L, sinEvidencia)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void vincularAsignaElProblemaAlIncidente() {
        service.vincular(3L, 7L);

        assertThat(incidente.getProblema()).isSameAs(problema);
    }

    @Test
    void noSeVinculanIncidentesAUnProblemaCerrado() {
        problema.cambiarEstado(EstadoProblema.CERRADO);

        assertThatThrownBy(() -> service.vincular(3L, 7L)).isInstanceOf(ReglaNegocioException.class);
        assertThat(incidente.getProblema()).isNull();
    }

    @Test
    void unIncidenteDeOtroProblemaNoSeMueveSinDesvincularlo() {
        Problema otro = problema(4L);
        incidente.setProblema(otro);

        assertThatThrownBy(() -> service.vincular(3L, 7L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("#4");
        assertThat(incidente.getProblema()).isSameAs(otro);
    }

    @Test
    void desvincularUnIncidenteAjenoSeRechaza() {
        assertThatThrownBy(() -> service.desvincular(3L, 7L)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void desvincularQuitaElProblema() {
        incidente.setProblema(problema);

        service.desvincular(3L, 7L);

        assertThat(incidente.getProblema()).isNull();
    }

    private Problema problema(long id) {
        Problema p = new Problema(laura);
        ReflectionTestUtils.setField(p, "id", id);
        p.setTitulo("Correo no sale");
        p.setDescripcion("Varios usuarios no pueden enviar correos");
        return p;
    }
}
