package com.nexus.support.service;

import static com.nexus.support.service.Datos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.nexus.support.domain.*;
import com.nexus.support.dto.IncidenteDtos.IncidenteRequest;
import com.nexus.support.exception.ReglaNegocioException;
import com.nexus.support.repository.AnaliticaRepository;
import com.nexus.support.repository.IncidenteHistorialRepository;
import com.nexus.support.repository.IncidenteRepository;
import com.nexus.support.repository.UsuarioRepository;

/** Historial de cambios (etapa 1) y elemento afectado (etapa 4a). */
class IncidenteServiceTest {

    private final Categoria correo = categoria(5, "Correo y colaboración");
    private final Prioridad media = prioridad(2, "Media", 2, 24);
    private final Prioridad alta = prioridad(3, "Alta", 3, 8);
    private final EstadoIncidente abierto = estado(1, "Abierto", false);
    private final EstadoIncidente enProgreso = estado(2, "En progreso", false);
    private final Usuario laura = usuario(1, "Laura Gomez", Rol.TECNICO);
    private final Usuario andres = usuario(2, "Andres Martinez", Rol.TECNICO);
    private final Usuario admin = usuario(20, "Admin Nexus", Rol.ADMIN);

    private IncidenteRepository incidentes;
    private CatalogoService catalogos;
    private UsuarioRepository usuarios;
    private IncidenteHistorialRepository historial;
    private IncidenteService service;
    private Incidente existente;

    @BeforeEach
    void setUp() {
        incidentes = mock(IncidenteRepository.class);
        catalogos = mock(CatalogoService.class);
        usuarios = mock(UsuarioRepository.class);
        historial = mock(IncidenteHistorialRepository.class);
        service = new IncidenteService(incidentes, catalogos, usuarios, historial, mock(AnaliticaRepository.class));

        when(catalogos.categoria(5L)).thenReturn(correo);
        when(catalogos.prioridad(2L)).thenReturn(media);
        when(catalogos.prioridad(3L)).thenReturn(alta);
        when(catalogos.estado(1L)).thenReturn(abierto);
        when(catalogos.estado(2L)).thenReturn(enProgreso);
        when(catalogos.usuario(1L)).thenReturn(laura);
        when(catalogos.usuario(2L)).thenReturn(andres);
        when(usuarios.getReferenceById(20L)).thenReturn(admin);

        existente = incidente(7, correo, media, abierto, laura);
        when(incidentes.findById(7L)).thenReturn(Optional.of(existente));
        when(incidentes.findParaModificar(7L)).thenReturn(Optional.of(existente));
        when(catalogos.transicionPermitida(1L, 2L)).thenReturn(true); // Abierto -> En progreso
        when(incidentes.save(any())).thenAnswer(inv -> {
            Incidente i = inv.getArgument(0);
            ReflectionTestUtils.setField(i, "id", 99L);
            // Sin JPA no corre @PrePersist: se invoca a mano para fijar la fecha de creación
            ReflectionTestUtils.invokeMethod(i, "alCrear");
            return i;
        });
        sesion(admin);
    }

    @AfterEach
    void tearDown() {
        cerrarSesion();
    }

    @Test
    void crearRegistraFilaDeCreacionConLaSituacionInicialYElActor() {
        service.crear(request(2L, 1L, null, null));

        verify(historial).save(argThat(h -> h.getTipo() == TipoHistorial.CREACION
                && h.getEstado() == abierto && h.getPrioridad() == media && h.getResponsable() == laura
                && h.getActor() == admin && h.getFecha().equals(h.getIncidente().getFechaCreacion())));
    }

    @Test
    void cambiarEstadoRegistraUnaActualizacion() {
        service.cambiarEstado(7L, 2L);

        verify(historial).save(argThat(h -> h.getTipo() == TipoHistorial.ACTUALIZACION
                && h.getEstado() == enProgreso && h.getActor() == admin));
    }

    @Test
    void cambiarAlMismoEstadoNoRegistraFila() {
        service.cambiarEstado(7L, 1L);

        verify(historial, never()).save(any());
    }

    @Test
    void editarSoloTituloODescripcionNoRegistraFila() {
        IncidenteRequest req = new IncidenteRequest("Outlook no envía correos (actualizado)", "Otra descripción",
                5L, 2L, 1L, null, null, null);

        service.actualizar(7L, req);

        verify(historial, never()).save(any());
    }

    @Test
    void reasignarYCambiarPrioridadRegistraUnaSolaFilaConAmbosCambios() {
        service.actualizar(7L, request(3L, 2L, null, null));

        verify(historial, times(1)).save(argThat(h -> h.getTipo() == TipoHistorial.ACTUALIZACION
                && h.getResponsable() == andres && h.getPrioridad() == alta && h.getEstado() == abierto));
    }

    @Test
    void elementoInactivoNoSePuedeAsignar() {
        when(catalogos.elemento(30L)).thenReturn(elemento(30, "ERP Nómina", false));

        assertThatThrownBy(() -> service.actualizar(7L, request(2L, 1L, null, 30L)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("inactivo");
    }

    @Test
    void elementoActualSeConservaAunqueSeHayaDesactivado() {
        ElementoTi erp = elemento(30, "ERP Nómina", false);
        existente.setElemento(erp);

        service.actualizar(7L, request(2L, 1L, null, 30L));

        assertThat(existente.getElemento()).isSameAs(erp);
        verify(catalogos, never()).elemento(any());
    }

    @Test
    void sinElementoIdSeQuitaElElemento() {
        existente.setElemento(elemento(30, "ERP Nómina", true));

        service.actualizar(7L, request(2L, 1L, null, null));

        assertThat(existente.getElemento()).isNull();
    }

    // ---------- Ingreso guiado (etapa 5) ----------

    @Test
    void crearGuardaMensajeDeErrorYPasosIntentadosSinEspaciosSobrantes() {
        var r = service.crear(guiado("  0x800CCC0E  ", "\nReinicié Outlook\n"));

        assertThat(r.mensajeError()).isEqualTo("0x800CCC0E");
        assertThat(r.pasosIntentados()).isEqualTo("Reinicié Outlook");
    }

    @Test
    void actualizarConCamposGuiadosEnBlancoLosDejaVacios() {
        existente.setMensajeError("0x800CCC0E");
        existente.setPasosIntentados("Reinicié Outlook");

        service.actualizar(7L, guiado("   ", ""));

        assertThat(existente.getMensajeError()).isNull();
        assertThat(existente.getPasosIntentados()).isNull();
    }

    @Test
    void elConstructorSinIngresoGuiadoDejaAmbosCamposNulos() {
        IncidenteRequest req = request(2L, 1L, null, null);

        assertThat(req.mensajeError()).isNull();
        assertThat(req.pasosIntentados()).isNull();
    }

    private static IncidenteRequest guiado(String mensajeError, String pasosIntentados) {
        return new IncidenteRequest("Outlook no envía correos", "Los correos se quedan en la bandeja de salida.",
                5L, 2L, 1L, null, null, null, mensajeError, pasosIntentados);
    }

    private static IncidenteRequest request(Long prioridadId, Long usuarioId, Long estadoId, Long elementoId) {
        return new IncidenteRequest("Outlook no envía correos", "Los correos se quedan en la bandeja de salida.",
                5L, prioridadId, usuarioId, estadoId, null, elementoId);
    }
}
