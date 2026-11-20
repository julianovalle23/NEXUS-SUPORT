package com.nexus.support.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.nexus.support.domain.EstadoProblema;
import com.nexus.support.domain.Incidente;
import com.nexus.support.domain.Problema;
import com.nexus.support.dto.CatalogoDtos.Ref;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ProblemaDtos {

    private ProblemaDtos() {
    }

    /** El estado no se cambia aquí sino con {@link CambioEstadoProblemaRequest}. */
    public record ProblemaRequest(
            @NotBlank(message = "El título es obligatorio")
            @Size(max = 200, message = "El título admite máximo 200 caracteres") String titulo,
            @NotBlank(message = "La descripción es obligatoria")
            @Size(max = 5000, message = "La descripción admite máximo 5000 caracteres") String descripcion,
            Long elementoId,
            /** Hipótesis o causa; solo se considera confirmada con evidencia y estado CAUSA_CONFIRMADA. */
            @Size(max = 5000, message = "La causa admite máximo 5000 caracteres") String causa,
            /** Datos que respaldan la causa: pruebas, registros, incidentes reproducidos. */
            @Size(max = 5000, message = "La evidencia admite máximo 5000 caracteres") String evidenciaCausa,
            @Size(max = 5000, message = "La solución temporal admite máximo 5000 caracteres") String solucionTemporal,
            @Size(max = 5000, message = "La solución definitiva admite máximo 5000 caracteres") String solucionDefinitiva) {
    }

    public record CambioEstadoProblemaRequest(@NotNull(message = "El estado es obligatorio") EstadoProblema estado) {
    }

    /** Fila del listado; los conteos salen de los incidentes vinculados. */
    public record ProblemaResumen(Long id, String titulo, EstadoProblema estado, Ref elemento, long incidentes,
                                  long incidentesAbiertos, LocalDateTime fechaCreacion, LocalDateTime fechaCierre) {
    }

    public record IncidenteVinculado(Long id, String titulo, Ref estado, boolean finalizado, Ref prioridad,
                                     LocalDateTime fechaCreacion, LocalDateTime fechaResolucion) {
        public static IncidenteVinculado de(Incidente i) {
            return new IncidenteVinculado(i.getId(), i.getTitulo(),
                    new Ref(i.getEstado().getId(), i.getEstado().getNombre()), i.getEstado().isEsFinal(),
                    new Ref(i.getPrioridad().getId(), i.getPrioridad().getNombre()),
                    i.getFechaCreacion(), i.getFechaResolucion());
        }
    }

    /** @param causaRespaldada true si hay causa y evidencia (requisito para CAUSA_CONFIRMADA) */
    public record ProblemaDetalle(Long id, String titulo, String descripcion, EstadoProblema estado, Ref elemento,
                                  String causa, String evidenciaCausa, boolean causaRespaldada,
                                  String solucionTemporal, String solucionDefinitiva, Ref creador,
                                  LocalDateTime fechaCreacion, LocalDateTime fechaActualizacion,
                                  LocalDateTime fechaCierre, List<IncidenteVinculado> incidentes) {
        public static ProblemaDetalle de(Problema p, List<Incidente> incidentes) {
            return new ProblemaDetalle(p.getId(), p.getTitulo(), p.getDescripcion(), p.getEstado(),
                    p.getElemento() == null ? null : new Ref(p.getElemento().getId(), p.getElemento().getNombre()),
                    p.getCausa(), p.getEvidenciaCausa(), p.causaRespaldada(), p.getSolucionTemporal(),
                    p.getSolucionDefinitiva(), new Ref(p.getCreador().getId(), p.getCreador().getNombre()),
                    p.getFechaCreacion(), p.getFechaActualizacion(), p.getFechaCierre(),
                    incidentes.stream().map(IncidenteVinculado::de).toList());
        }
    }
}
