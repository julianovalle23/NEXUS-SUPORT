package com.nexus.support.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.support.domain.ConsultaIA;
import com.nexus.support.domain.EstadoCausa;
import com.nexus.support.domain.Incidente;
import com.nexus.support.domain.IncidenteHistorial;
import com.nexus.support.domain.RevisionIncidente;
import com.nexus.support.domain.TipoHistorial;
import com.nexus.support.dto.CatalogoDtos.Ref;
import com.nexus.support.dto.RevisionDtos.*;
import com.nexus.support.exception.ReglaNegocioException;
import com.nexus.support.repository.AnaliticaRepository;
import com.nexus.support.repository.ConsultaIARepository;
import com.nexus.support.repository.IncidenteHistorialRepository;
import com.nexus.support.repository.RevisionIncidenteRepository;
import com.nexus.support.repository.UsuarioRepository;
import com.nexus.support.security.SesionActual;

/**
 * Revisión posterior de incidentes. La línea de tiempo sale solo de datos guardados (historial y
 * consultas al asistente); la causa solo figura como confirmada si hay evidencia registrada.
 */
@Service
@Transactional(readOnly = true)
public class RevisionService {

    /** Nivel de prioridad desde el que un incidente finalizado se considera importante (3 = Alta). */
    static final int NIVEL_IMPORTANTE = 3;
    private static final int LIMITE_PENDIENTES = 10;

    private final IncidenteService incidentes;
    private final RevisionIncidenteRepository revisiones;
    private final IncidenteHistorialRepository historial;
    private final ConsultaIARepository consultas;
    private final AnaliticaRepository analitica;
    private final UsuarioRepository usuarios;

    public RevisionService(IncidenteService incidentes, RevisionIncidenteRepository revisiones,
                           IncidenteHistorialRepository historial, ConsultaIARepository consultas,
                           AnaliticaRepository analitica, UsuarioRepository usuarios) {
        this.incidentes = incidentes;
        this.revisiones = revisiones;
        this.historial = historial;
        this.consultas = consultas;
        this.analitica = analitica;
        this.usuarios = usuarios;
    }

    public RevisionCompleta obtener(Long incidenteId) {
        Incidente i = incidentes.entidad(incidenteId);
        return completa(i, revisiones.findByIncidenteId(incidenteId).orElse(null));
    }

    /** Crea o reemplaza la revisión. Solo para incidentes finalizados. */
    @Transactional
    public RevisionCompleta guardar(Long incidenteId, RevisionRequest req) {
        Incidente i = incidentes.entidad(incidenteId);
        if (!i.getEstado().isEsFinal()) {
            throw new ReglaNegocioException("La revisión posterior solo se registra cuando el incidente está resuelto o cerrado");
        }
        String causa = texto(req.causa());
        String evidencia = texto(req.evidenciaCausa());
        if (req.estadoCausa() == EstadoCausa.CONFIRMADA && (causa == null || evidencia == null)) {
            throw new ReglaNegocioException("Para marcar la causa como confirmada registre la causa y su evidencia");
        }
        RevisionIncidente r = revisiones.findByIncidenteId(incidenteId).orElseGet(() ->
                new RevisionIncidente(i, usuarios.getReferenceById(SesionActual.cuenta().id())));
        r.setImpacto(req.impacto().trim());
        r.setEstadoCausa(req.estadoCausa());
        r.setCausa(causa);
        r.setEvidenciaCausa(evidencia);
        r.setSolucion(texto(req.solucion()));
        r.setAccionesPreventivas(texto(req.accionesPreventivas()));
        return completa(i, revisiones.saveAndFlush(r));
    }

    public RevisionesPendientes pendientes() {
        return analitica.revisionesPendientes(NIVEL_IMPORTANTE, LIMITE_PENDIENTES);
    }

    private RevisionCompleta completa(Incidente i, RevisionIncidente r) {
        return new RevisionCompleta(i.getId(), i.getTitulo(), i.getPrioridad().getNombre(), i.getEstado().isEsFinal(),
                i.getProblema() == null ? null : new Ref(i.getProblema().getId(), i.getProblema().getTitulo()),
                i.getProblema() == null ? null : i.getProblema().getEstado(),
                RevisionDto.de(r), lineaTiempo(i));
    }

    /** Historial de estado/prioridad/responsable + consultas al asistente, en orden cronológico. */
    List<EventoLinea> lineaTiempo(Incidente i) {
        List<EventoLinea> eventos = new ArrayList<>();
        List<IncidenteHistorial> filas = historial.findByIncidenteIdOrderByFechaAscIdAsc(i.getId());
        boolean hayCreacion = filas.stream().anyMatch(h -> h.getTipo() == TipoHistorial.CREACION);
        if (!hayCreacion) {
            eventos.add(new EventoLinea(i.getFechaCreacion(), "CREACION", "Incidente registrado", false));
        }
        IncidenteHistorial anterior = null;
        for (IncidenteHistorial h : filas) {
            eventos.add(switch (h.getTipo()) {
                case CREACION -> new EventoLinea(h.getFecha(), "CREACION", "Incidente registrado: estado "
                        + h.getEstado().getNombre() + ", prioridad " + h.getPrioridad().getNombre()
                        + ", responsable " + h.getResponsable().getNombre(), false);
                case MIGRACION -> new EventoLinea(h.getFecha(), "MIGRACION", "Situación conocida al crear el historial: "
                        + h.getEstado().getNombre() + ", prioridad " + h.getPrioridad().getNombre()
                        + ", responsable " + h.getResponsable().getNombre() + " (los cambios anteriores no se registraron)", true);
                case ACTUALIZACION -> new EventoLinea(h.getFecha(), "CAMBIO", cambios(anterior, h), false);
                case REAPERTURA -> new EventoLinea(h.getFecha(), "REAPERTURA", "Reapertura excepcional: "
                        + cambios(anterior, h) + ". Motivo: " + h.getMotivo(), false);
            });
            anterior = h;
        }
        // Sin cambios registrados, la resolución solo se conoce por la fecha guardada en el incidente
        boolean hayCambios = filas.stream()
                .anyMatch(h -> h.getTipo() == TipoHistorial.ACTUALIZACION || h.getTipo() == TipoHistorial.REAPERTURA);
        if (!hayCambios && i.getFechaResolucion() != null) {
            eventos.add(new EventoLinea(i.getFechaResolucion(), "RESOLUCION", "Fecha de resolución registrada", false));
        }
        for (ConsultaIA c : consultas.findByIncidenteIdOrderByFechaDesc(i.getId())) {
            eventos.add(new EventoLinea(c.getFecha(), "ASISTENCIA", asistencia(c), false));
        }
        eventos.sort(Comparator.comparing(EventoLinea::fecha));
        return eventos;
    }

    private static String cambios(IncidenteHistorial antes, IncidenteHistorial h) {
        if (antes == null) {
            return "Estado " + h.getEstado().getNombre();
        }
        List<String> partes = new ArrayList<>();
        if (!Objects.equals(antes.getEstado().getId(), h.getEstado().getId())) {
            partes.add("Estado: " + antes.getEstado().getNombre() + " → " + h.getEstado().getNombre());
        }
        if (!Objects.equals(antes.getPrioridad().getId(), h.getPrioridad().getId())) {
            partes.add("Prioridad: " + antes.getPrioridad().getNombre() + " → " + h.getPrioridad().getNombre());
        }
        if (!Objects.equals(antes.getResponsable().getId(), h.getResponsable().getId())) {
            partes.add("Responsable: " + antes.getResponsable().getNombre() + " → " + h.getResponsable().getNombre());
        }
        String texto = String.join(" · ", partes);
        return (texto.isEmpty() ? "Cambio registrado" : texto)
                + (h.getActor() == null ? "" : " (por " + h.getActor().getNombre() + ")");
    }

    private static String asistencia(ConsultaIA c) {
        StringBuilder s = new StringBuilder("Consulta al asistente #" + c.getId() + ": " + switch (c.getEstado()) {
            case RESPONDIDA -> "respondida con " + c.getRespuesta().getFuentesUsadas().size() + " fuente(s)";
            case SIN_RESULTADOS -> "sin evidencia suficiente en la base de conocimiento";
            case ERROR -> "error en el flujo";
            case PENDIENTE -> "pendiente";
        });
        var k = c.getRespuesta() == null ? null : c.getRespuesta().getCalificacion();
        if (k != null) {
            s.append(" · calificada ").append(k.getPuntuacion()).append("/5");
            if (k.getResolvio() != null) s.append(k.getResolvio() ? " · resolvió el incidente" : " · no lo resolvió");
        }
        return s.toString();
    }

    private static String texto(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
