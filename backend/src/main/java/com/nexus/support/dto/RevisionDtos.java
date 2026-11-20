package com.nexus.support.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.nexus.support.domain.EstadoCausa;
import com.nexus.support.domain.EstadoProblema;
import com.nexus.support.domain.RevisionIncidente;
import com.nexus.support.dto.CatalogoDtos.Ref;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class RevisionDtos {

    private RevisionDtos() {
    }

    public record RevisionRequest(
            @NotBlank(message = "El impacto es obligatorio")
            @Size(max = 5000, message = "El impacto admite máximo 5000 caracteres") String impacto,
            @NotNull(message = "Indique si la causa está confirmada o pendiente") EstadoCausa estadoCausa,
            @Size(max = 5000, message = "La causa admite máximo 5000 caracteres") String causa,
            @Size(max = 5000, message = "La evidencia admite máximo 5000 caracteres") String evidenciaCausa,
            @Size(max = 5000, message = "La solución admite máximo 5000 caracteres") String solucion,
            @Size(max = 5000, message = "Las acciones preventivas admiten máximo 5000 caracteres") String accionesPreventivas) {
    }

    public record RevisionDto(Long id, String impacto, EstadoCausa estadoCausa, String causa, String evidenciaCausa,
                              String solucion, String accionesPreventivas, Ref autor, LocalDateTime fechaCreacion,
                              LocalDateTime fechaActualizacion) {
        public static RevisionDto de(RevisionIncidente r) {
            return r == null ? null : new RevisionDto(r.getId(), r.getImpacto(), r.getEstadoCausa(), r.getCausa(),
                    r.getEvidenciaCausa(), r.getSolucion(), r.getAccionesPreventivas(),
                    new Ref(r.getAutor().getId(), r.getAutor().getNombre()), r.getFechaCreacion(),
                    r.getFechaActualizacion());
        }
    }

    /**
     * Evento de la línea de tiempo, tomado de los datos guardados.
     *
     * @param tipo              CREACION, CAMBIO, MIGRACION, RESOLUCION o ASISTENCIA
     * @param fechaAproximada   true si la fecha no es la del hecho (fila MIGRACION: situación al crear el historial)
     */
    public record EventoLinea(LocalDateTime fecha, String tipo, String descripcion, boolean fechaAproximada) {
    }

    /** @param revision nula si aún no se ha registrado */
    public record RevisionCompleta(Long incidenteId, String titulo, String prioridad, boolean finalizado,
                                   Ref problema, EstadoProblema estadoProblema, RevisionDto revision,
                                   List<EventoLinea> lineaTiempo) {
    }

    /** @param motivo por qué se considera importante: prioridad alta o crítica, SLA de resolución incumplido */
    public record RevisionPendiente(Long incidenteId, String titulo, String prioridad, LocalDateTime fechaResolucion,
                                    String motivo) {
    }

    public record RevisionesPendientes(long total, int nivelMinimo, List<RevisionPendiente> ultimas) {
    }
}
