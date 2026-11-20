package com.nexus.support.service;

import static com.nexus.support.service.Datos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.nexus.support.domain.*;
import com.nexus.support.dto.RevisionDtos.EventoLinea;
import com.nexus.support.dto.RevisionDtos.RevisionRequest;
import com.nexus.support.exception.ReglaNegocioException;
import com.nexus.support.repository.*;

/** Revisión posterior y línea de tiempo (etapa 5). */
class RevisionServiceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 2, 8, 0);

    private final EstadoIncidente abierto = estado(1, "Abierto", false);
    private final EstadoIncidente enProgreso = estado(2, "En progreso", false);
    private final EstadoIncidente resuelto = estado(3, "Resuelto", true);
    private final Usuario laura = usuario(1, "Laura Gomez", Rol.TECNICO);
    private final Usuario andres = usuario(2, "Andres Martinez", Rol.TECNICO);
    private final Usuario admin = usuario(20, "Admin Nexus", Rol.ADMIN);

    private IncidenteService incidentes;
    private RevisionIncidenteRepository revisiones;
    private IncidenteHistorialRepository historial;
    private ConsultaIARepository consultas;
    private RevisionService service;
    private Incidente incidente;

    @BeforeEach
    void setUp() {
        incidentes = mock(IncidenteService.class);
        revisiones = mock(RevisionIncidenteRepository.class);
        historial = mock(IncidenteHistorialRepository.class);
        consultas = mock(ConsultaIARepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        service = new RevisionService(incidentes, revisiones, historial, consultas, mock(AnaliticaRepository.class),
                usuarios);

        incidente = incidente(7, categoria(5, "Correo"), prioridad(3, "Alta", 3, 8), resuelto, laura);
        when(incidentes.entidad(7L)).thenReturn(incidente);
        when(revisiones.findByIncidenteId(7L)).thenReturn(Optional.empty());
        when(revisiones.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historial.findByIncidenteIdOrderByFechaAscIdAsc(7L)).thenReturn(List.of());
        when(consultas.findByIncidenteIdOrderByFechaDesc(7L)).thenReturn(List.of());
        when(usuarios.getReferenceById(20L)).thenReturn(admin);
        sesion(admin);
    }

    @AfterEach
    void tearDown() {
        cerrarSesion();
    }

    @Test
    void noSeRegistraRevisionDeUnIncidenteAbierto() {
        incidente.cambiarEstado(enProgreso);

        assertThatThrownBy(() -> service.guardar(7L, request(EstadoCausa.PENDIENTE, null, null)))
                .isInstanceOf(ReglaNegocioException.class);
        verify(revisiones, never()).saveAndFlush(any());
    }

    @Test
    void causaConfirmadaSinEvidenciaSeRechaza() {
        assertThatThrownBy(() -> service.guardar(7L, request(EstadoCausa.CONFIRMADA, "Certificado vencido", "  ")))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void causaPendienteSinTextoSeAcepta() {
        var r = service.guardar(7L, request(EstadoCausa.PENDIENTE, null, null));

        assertThat(r.revision().estadoCausa()).isEqualTo(EstadoCausa.PENDIENTE);
        assertThat(r.revision().autor().id()).isEqualTo(20L);
    }

    @Test
    void guardarDeNuevoActualizaLaMismaRevision() {
        RevisionIncidente existente = new RevisionIncidente(incidente, laura);
        when(revisiones.findByIncidenteId(7L)).thenReturn(Optional.of(existente));

        service.guardar(7L, request(EstadoCausa.CONFIRMADA, "Certificado vencido", "Registro del servidor"));

        verify(revisiones).saveAndFlush(same(existente));
        assertThat(existente.getEstadoCausa()).isEqualTo(EstadoCausa.CONFIRMADA);
        assertThat(existente.getAutor()).isSameAs(laura); // el autor original se conserva
    }

    @Test
    void lineaDeTiempoOrdenaCambiosYConsultasYDescribeQueCambio() {
        incidente.cambiarEstado(abierto);
        IncidenteHistorial creacion = IncidenteHistorial.creacion(incidente, admin);
        incidente.cambiarEstado(enProgreso);
        incidente.setUsuario(andres);
        IncidenteHistorial reasignacion = actualizacion(T0.plusHours(1));
        incidente.cambiarEstado(resuelto);
        IncidenteHistorial resolucion = actualizacion(T0.plusHours(5));
        when(historial.findByIncidenteIdOrderByFechaAscIdAsc(7L)).thenReturn(List.of(creacion, reasignacion, resolucion));
        when(consultas.findByIncidenteIdOrderByFechaDesc(7L)).thenReturn(List.of(consultaSinResultados(T0.plusHours(2))));

        List<EventoLinea> linea = service.lineaTiempo(incidente);

        assertThat(linea).extracting(EventoLinea::tipo).containsExactly("CREACION", "CAMBIO", "ASISTENCIA", "CAMBIO");
        assertThat(linea.get(1).descripcion())
                .contains("Estado: Abierto → En progreso")
                .contains("Responsable: Laura Gomez → Andres Martinez")
                .contains("por Admin Nexus");
        assertThat(linea.get(2).descripcion()).contains("sin evidencia suficiente");
        assertThat(linea.get(3).descripcion()).contains("Estado: En progreso → Resuelto");
        assertThat(linea).noneMatch(EventoLinea::fechaAproximada);
    }

    @Test
    void filaMigracionSeMarcaComoAproximadaYSeUsaLaFechaDeResolucionGuardada() {
        IncidenteHistorial migracion = BeanUtils.instantiateClass(IncidenteHistorial.class);
        ReflectionTestUtils.setField(migracion, "tipo", TipoHistorial.MIGRACION);
        ReflectionTestUtils.setField(migracion, "fecha", T0.plusHours(6));
        ReflectionTestUtils.setField(migracion, "estado", resuelto);
        ReflectionTestUtils.setField(migracion, "prioridad", incidente.getPrioridad());
        ReflectionTestUtils.setField(migracion, "responsable", laura);
        ReflectionTestUtils.setField(incidente, "fechaResolucion", T0.plusHours(6));
        when(historial.findByIncidenteIdOrderByFechaAscIdAsc(7L)).thenReturn(List.of(migracion));

        List<EventoLinea> linea = service.lineaTiempo(incidente);

        assertThat(linea).extracting(EventoLinea::tipo).containsExactly("CREACION", "MIGRACION", "RESOLUCION");
        assertThat(linea.get(1).fechaAproximada()).isTrue();
        assertThat(linea.get(0).fechaAproximada()).isFalse();
    }

    private IncidenteHistorial actualizacion(LocalDateTime fecha) {
        IncidenteHistorial h = IncidenteHistorial.actualizacion(incidente, admin);
        ReflectionTestUtils.setField(h, "fecha", fecha);
        return h;
    }

    private ConsultaIA consultaSinResultados(LocalDateTime fecha) {
        ConsultaIA c = new ConsultaIA(incidente, "No envía correos", new BigDecimal("0.450"));
        ReflectionTestUtils.setField(c, "id", 50L);
        ReflectionTestUtils.setField(c, "fecha", fecha);
        c.setEstado(EstadoConsulta.SIN_RESULTADOS);
        return c;
    }

    private static RevisionRequest request(EstadoCausa estado, String causa, String evidencia) {
        return new RevisionRequest("Diez usuarios sin correo saliente durante cinco horas", estado, causa, evidencia,
                "Renovación del certificado", "Alerta 30 días antes del vencimiento");
    }
}
