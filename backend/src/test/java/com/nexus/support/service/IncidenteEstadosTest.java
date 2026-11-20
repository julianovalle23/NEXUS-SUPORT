package com.nexus.support.service;

import static com.nexus.support.service.Datos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.nexus.support.domain.*;
import com.nexus.support.dto.IncidenteDtos.IncidenteRequest;
import com.nexus.support.exception.RecursoNoEncontradoException;
import com.nexus.support.exception.ReglaNegocioException;
import com.nexus.support.repository.AnaliticaRepository;
import com.nexus.support.repository.IncidenteHistorialRepository;
import com.nexus.support.repository.IncidenteRepository;
import com.nexus.support.repository.UsuarioRepository;

/**
 * Máquina de estados de incidentes: Cerrado terminal, transiciones permitidas, reapertura
 * excepcional y seguimiento. Las transiciones simuladas son las sembradas en 05-mejoras.sql.
 */
class IncidenteEstadosTest {

    private static final String MOTIVO = "El usuario reporta que el fallo volvió a ocurrir con la misma causa";
    /** Pares origen-destino de transicion_estado. */
    private static final Set<String> TRANSICIONES = Set.of("1-2", "1-3", "2-1", "2-3", "3-2", "3-4");

    private final Categoria correo = categoria(5, "Correo y colaboración");
    private final Prioridad media = prioridad(2, "Media", 2, 24);
    private final EstadoIncidente abierto = estado(1, "Abierto", false);
    private final EstadoIncidente enProgreso = estado(2, "En progreso", false);
    private final EstadoIncidente resuelto = estado(3, "Resuelto", true);
    private final EstadoIncidente cerrado = estado(4, "Cerrado", true);
    private final Usuario laura = usuario(1, "Laura Gomez", Rol.TECNICO);
    private final Usuario admin = usuario(20, "Admin Nexus", Rol.ADMIN);
    private final Usuario sofia = usuario(30, "Sofia Solicitante", Rol.SOLICITANTE);

    private IncidenteRepository incidentes;
    private CatalogoService catalogos;
    private IncidenteHistorialRepository historial;
    private IncidenteService service;
    private Incidente incidente;

    @BeforeEach
    void setUp() {
        incidentes = mock(IncidenteRepository.class);
        catalogos = mock(CatalogoService.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        historial = mock(IncidenteHistorialRepository.class);
        service = new IncidenteService(incidentes, catalogos, usuarios, historial, mock(AnaliticaRepository.class));

        when(catalogos.categoria(5L)).thenReturn(correo);
        when(catalogos.prioridad(2L)).thenReturn(media);
        for (EstadoIncidente e : new EstadoIncidente[] {abierto, enProgreso, resuelto, cerrado}) {
            when(catalogos.estado(e.getId())).thenReturn(e);
        }
        when(catalogos.esTerminal(anyLong())).thenAnswer(inv -> inv.getArgument(0, Long.class) == 4L);
        when(catalogos.transicionPermitida(anyLong(), anyLong())).thenAnswer(inv ->
                TRANSICIONES.contains(inv.getArgument(0) + "-" + inv.getArgument(1)));
        when(catalogos.usuario(1L)).thenReturn(laura);
        when(catalogos.usuario(30L)).thenReturn(sofia);
        when(usuarios.getReferenceById(1L)).thenReturn(laura);
        when(usuarios.getReferenceById(20L)).thenReturn(admin);
        when(usuarios.getReferenceById(30L)).thenReturn(sofia);
        when(usuarios.tecnicoConMenosCarga()).thenReturn(Optional.of(laura));

        incidente = incidente(7, correo, media, abierto, laura);
        when(incidentes.findById(7L)).thenReturn(Optional.of(incidente));
        when(incidentes.findParaModificar(7L)).thenReturn(Optional.of(incidente));
        when(incidentes.save(any())).thenAnswer(inv -> {
            Incidente i = inv.getArgument(0);
            ReflectionTestUtils.setField(i, "id", 99L);
            ReflectionTestUtils.invokeMethod(i, "alCrear");
            return i;
        });
        sesion(laura);
    }

    @AfterEach
    void tearDown() {
        cerrarSesion();
    }

    // ---------- Estado terminal ----------

    @Test
    void unIncidenteCerradoNoSeReabrePorCambioDeEstado() {
        cerrar();

        assertThatThrownBy(() -> service.cambiarEstado(7L, 1L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no admite cambios");
        assertThat(incidente.getEstado()).isSameAs(cerrado);
        verify(historial, never()).save(any());
    }

    @Test
    void unAdministradorTampocoReabrePorLaViaNormal() {
        cerrar();
        sesion(admin);

        assertThatThrownBy(() -> service.cambiarEstado(7L, 2L)).isInstanceOf(ReglaNegocioException.class);
        assertThat(incidente.getEstado()).isSameAs(cerrado);
    }

    @Test
    void unIncidenteCerradoNoSeEditaNiSiquieraElTitulo() {
        cerrar();
        IncidenteRequest req = new IncidenteRequest("Título nuevo", incidente.getDescripcion(), 5L, 2L, 1L, null,
                null, null);

        assertThatThrownBy(() -> service.actualizar(7L, req)).isInstanceOf(ReglaNegocioException.class);
        assertThat(incidente.getTitulo()).isEqualTo("Outlook no envía correos");
    }

    @Test
    void unIncidenteCerradoNoSeReabrePorEdicionConEstadoId() {
        cerrar();

        assertThatThrownBy(() -> service.actualizar(7L, request(1L))).isInstanceOf(ReglaNegocioException.class);
        assertThat(incidente.getEstado()).isSameAs(cerrado);
    }

    @Test
    void noSeRegistraUnIncidenteDirectamenteCerrado() {
        assertThatThrownBy(() -> service.crear(request(4L))).isInstanceOf(ReglaNegocioException.class);
        verify(incidentes, never()).save(any());
    }

    // ---------- Transiciones ----------

    @Test
    void unaTransicionNoPermitidaSeRechaza() {
        assertThatThrownBy(() -> service.cambiarEstado(7L, 4L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("de 'Abierto' a 'Cerrado'");
        assertThat(incidente.getEstado()).isSameAs(abierto);
    }

    @Test
    void unaTransicionNoPermitidaTambienSeRechazaAlEditar() {
        assertThatThrownBy(() -> service.actualizar(7L, request(4L))).isInstanceOf(ReglaNegocioException.class);
        assertThat(incidente.getEstado()).isSameAs(abierto);
    }

    @Test
    void resueltoPasaACerradoConservandoLaFechaDeResolucion() {
        incidente.cambiarEstado(resuelto);
        LocalDateTime resolucion = incidente.getFechaResolucion();

        service.cambiarEstado(7L, 4L);

        assertThat(incidente.getEstado()).isSameAs(cerrado);
        assertThat(incidente.getFechaResolucion()).isEqualTo(resolucion);
    }

    @Test
    void elCambioDeEstadoTrabajaSobreLaFilaBloqueada() {
        service.cambiarEstado(7L, 2L);

        verify(incidentes).findParaModificar(7L);
        verify(incidentes, never()).findById(7L);
    }

    // ---------- Reapertura excepcional ----------

    @Test
    void unTecnicoNoPuedeUsarLaReapertura() {
        cerrar();

        assertThatThrownBy(() -> service.reabrir(7L, 2L, MOTIVO)).isInstanceOf(AccessDeniedException.class);
        assertThat(incidente.getEstado()).isSameAs(cerrado);
        verify(historial, never()).save(any());
    }

    @Test
    void laReaperturaSinMotivoSuficienteSeRechaza() {
        cerrar();
        sesion(admin);

        assertThatThrownBy(() -> service.reabrir(7L, 2L, "  corto  ")).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> service.reabrir(7L, 2L, null)).isInstanceOf(ReglaNegocioException.class);
        assertThat(incidente.getEstado()).isSameAs(cerrado);
        verify(historial, never()).save(any());
    }

    @Test
    void laReaperturaSoloAplicaAIncidentesCerrados() {
        sesion(admin);

        assertThatThrownBy(() -> service.reabrir(7L, 2L, MOTIVO)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void laReaperturaDebeDejarElIncidenteEnUnEstadoNoFinal() {
        cerrar();
        sesion(admin);

        assertThatThrownBy(() -> service.reabrir(7L, 3L, MOTIVO)).isInstanceOf(ReglaNegocioException.class);
        assertThat(incidente.getEstado()).isSameAs(cerrado);
    }

    @Test
    void laReaperturaAutorizadaQuedaEnElHistorialConActorYMotivo() {
        cerrar();
        sesion(admin);

        service.reabrir(7L, 2L, "  " + MOTIVO + "  ");

        assertThat(incidente.getEstado()).isSameAs(enProgreso);
        assertThat(incidente.getFechaResolucion()).isNull();
        verify(historial).save(argThat(h -> h.getTipo() == TipoHistorial.REAPERTURA && h.getActor() == admin
                && h.getEstado() == enProgreso && MOTIVO.equals(h.getMotivo()) && h.getFecha() != null));
    }

    // ---------- Seguimiento ----------

    @Test
    void elSeguimientoCreaUnIncidenteNuevoVinculadoSinTocarElOriginal() {
        cerrar();

        var nuevo = service.registrarSeguimiento(7L, request(null));

        assertThat(nuevo.id()).isEqualTo(99L);
        assertThat(nuevo.seguimientoDe().id()).isEqualTo(7L);
        assertThat(incidente.getEstado()).isSameAs(cerrado);
        verify(historial, times(1)).save(argThat(h -> h.getTipo() == TipoHistorial.CREACION
                && h.getIncidente().getId() == 99L));
    }

    @Test
    void elSeguimientoDeUnIncidenteEnCursoSeRechaza() {
        assertThatThrownBy(() -> service.registrarSeguimiento(7L, request(null)))
                .isInstanceOf(ReglaNegocioException.class);
        verify(incidentes, never()).save(any());
    }

    @Test
    void unSolicitantePuedeDarSeguimientoASuPropioIncidente() {
        cerrar();
        incidente.setCuentaSolicitante(sofia);
        sesion(sofia);

        var nuevo = service.registrarSeguimiento(7L, request(null));

        assertThat(nuevo.seguimientoDe().id()).isEqualTo(7L);
        assertThat(nuevo.estado().id()).isEqualTo(1L);
    }

    @Test
    void unSolicitanteNoVeNiDaSeguimientoAIncidentesAjenos() {
        cerrar();
        sesion(sofia);

        assertThatThrownBy(() -> service.registrarSeguimiento(7L, request(null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(incidentes, never()).save(any());
    }

    private void cerrar() {
        incidente.cambiarEstado(cerrado);
    }

    private static IncidenteRequest request(Long estadoId) {
        return new IncidenteRequest("Outlook no envía correos", "Los correos se quedan en la bandeja de salida.",
                5L, 2L, 1L, estadoId, null, null);
    }
}
