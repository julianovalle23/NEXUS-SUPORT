package com.nexus.support.service;

import static com.nexus.support.service.Datos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;

import com.nexus.support.domain.AlertaSla;
import com.nexus.support.domain.EstadoSla;
import com.nexus.support.domain.Rol;
import com.nexus.support.domain.Usuario;
import com.nexus.support.dto.AlertaSlaDtos.AlertaSlaDto;
import com.nexus.support.dto.AlertaSlaDtos.AlertasSla;
import com.nexus.support.dto.AlertaSlaDtos.RevisionAlertas;
import com.nexus.support.dto.CatalogoDtos.Ref;
import com.nexus.support.exception.RecursoNoEncontradoException;
import com.nexus.support.exception.ReglaNegocioException;
import com.nexus.support.repository.AlertaSlaRepository;

/** Alertas y escalamiento de SLA (etapa 5): orden de la revisión, visibilidad y permisos para atender. */
class AlertaSlaServiceTest {

    private final Usuario laura = usuario(1, "Laura Gomez", Rol.TECNICO);
    private final Usuario andres = usuario(2, "Andres Martinez", Rol.TECNICO);
    private final Usuario admin = usuario(20, "Admin Nexus", Rol.ADMIN);
    private final Usuario solicitante = usuario(30, "Pedro Solicitante", Rol.SOLICITANTE);

    private AlertaSlaRepository repo;
    private AlertaSlaService service;

    @BeforeEach
    void setUp() {
        repo = mock(AlertaSlaRepository.class);
        service = new AlertaSlaService(repo);
        when(repo.contarPendientes(any())).thenReturn(new long[] {3, 1});
    }

    @AfterEach
    void tearDown() {
        cerrarSesion();
    }

    // ---------- Revisión ----------

    @Test
    void revisarSuperaAntesDeGenerarConLaMismaHoraYFraccion() {
        when(repo.superar(any(), anyDouble())).thenReturn(2);
        when(repo.generar(any(), anyDouble())).thenReturn(5);

        RevisionAlertas r = service.revisar();

        InOrder orden = inOrder(repo);
        orden.verify(repo).superar(eq(r.fecha()), eq(EstadoSla.FRACCION_ALERTA));
        orden.verify(repo).generar(eq(r.fecha()), eq(EstadoSla.FRACCION_ALERTA));
        assertThat(r.superadas()).isEqualTo(2);
        assertThat(r.generadas()).isEqualTo(5);
    }

    @Test
    void laRevisionProgramadaNoPropagaUnFalloDeLaBase() {
        when(repo.superar(any(), anyDouble())).thenThrow(new IllegalStateException("conexión rechazada"));

        service.revisarProgramado();

        verify(repo, never()).generar(any(), anyDouble());
    }

    // ---------- Listado ----------

    @Test
    void elAdministradorVeTodasLasAlertas() {
        sesion(admin);
        when(repo.listar(AlertaSla.Estado.PENDIENTE, null, AlertaSlaService.LIMITE_LISTA))
                .thenReturn(List.of(alerta(1, AlertaSla.Nivel.AVISO, laura)));

        AlertasSla r = service.listar(AlertaSla.Estado.PENDIENTE);

        verify(repo).contarPendientes(null);
        assertThat(r.pendientes()).isEqualTo(3);
        assertThat(r.escalamientosPendientes()).isEqualTo(1);
        assertThat(r.alertas()).hasSize(1);
    }

    @Test
    void elTecnicoSoloVeLasDeSusIncidentes() {
        sesion(laura);

        service.listar(null);

        verify(repo).listar(null, 1L, AlertaSlaService.LIMITE_LISTA);
        verify(repo).contarPendientes(1L);
    }

    @Test
    void unSolicitanteNoVeAlertas() {
        sesion(solicitante);

        assertThatThrownBy(() -> service.listar(null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repo);
    }

    // ---------- Atender ----------

    @Test
    void elTecnicoResponsableAtiendeUnAvisoConNotaRecortada() {
        sesion(laura);
        when(repo.buscar(7L)).thenReturn(Optional.of(alerta(7, AlertaSla.Nivel.AVISO, laura)));
        when(repo.atender(eq(7L), eq(1L), any(), any())).thenReturn(true);

        service.atender(7L, "  Llamé al usuario  ");

        verify(repo).atender(eq(7L), eq(1L), eq("Llamé al usuario"), any(LocalDateTime.class));
    }

    @Test
    void unaNotaEnBlancoSeGuardaComoNula() {
        sesion(admin);
        when(repo.buscar(7L)).thenReturn(Optional.of(alerta(7, AlertaSla.Nivel.AVISO, laura)));
        when(repo.atender(eq(7L), eq(20L), any(), any())).thenReturn(true);

        service.atender(7L, "   ");

        verify(repo).atender(eq(7L), eq(20L), isNull(), any(LocalDateTime.class));
    }

    @Test
    void unTecnicoNoAtiendeUnEscalamiento() {
        sesion(laura);
        when(repo.buscar(8L)).thenReturn(Optional.of(alerta(8, AlertaSla.Nivel.ESCALAMIENTO, laura)));

        assertThatThrownBy(() -> service.atender(8L, null)).isInstanceOf(AccessDeniedException.class);
        verify(repo, never()).atender(any(), any(), any(), any());
    }

    @Test
    void elAdministradorAtiendeUnEscalamiento() {
        sesion(admin);
        when(repo.buscar(8L)).thenReturn(Optional.of(alerta(8, AlertaSla.Nivel.ESCALAMIENTO, laura)));
        when(repo.atender(eq(8L), eq(20L), any(), any())).thenReturn(true);

        service.atender(8L, "Reasignado a Andrés");

        verify(repo).atender(eq(8L), eq(20L), eq("Reasignado a Andrés"), any(LocalDateTime.class));
    }

    @Test
    void unTecnicoNoVeNiAtiendeAlertasDeIncidentesAjenos() {
        sesion(andres);
        when(repo.buscar(7L)).thenReturn(Optional.of(alerta(7, AlertaSla.Nivel.AVISO, laura)));

        assertThatThrownBy(() -> service.atender(7L, null)).isInstanceOf(RecursoNoEncontradoException.class);
        verify(repo, never()).atender(any(), any(), any(), any());
    }

    @Test
    void unaAlertaInexistenteDaNoEncontrado() {
        sesion(admin);
        when(repo.buscar(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atender(99L, null)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void unaAlertaQueYaNoEstaPendienteSeRechaza() {
        sesion(admin);
        when(repo.buscar(7L)).thenReturn(Optional.of(alerta(7, AlertaSla.Nivel.AVISO, laura)));
        when(repo.atender(any(), any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.atender(7L, null))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya no está pendiente");
    }

    private static AlertaSlaDto alerta(long id, AlertaSla.Nivel nivel, Usuario responsable) {
        LocalDateTime t = LocalDateTime.of(2026, 11, 20, 10, 0);
        return new AlertaSlaDto(id, new Ref(7L, "Outlook no envía correos"), "Alta", "Abierto",
                AlertaSla.Tipo.RESOLUCION, nivel, t, t.minusHours(1), AlertaSla.Estado.PENDIENTE,
                new Ref(responsable.getId(), responsable.getNombre()), responsable.getNombre(), null, null, null);
    }
}
