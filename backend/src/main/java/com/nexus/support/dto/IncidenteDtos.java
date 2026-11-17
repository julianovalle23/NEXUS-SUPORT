package com.nexus.support.dto;

import java.time.Duration;
import java.time.LocalDateTime;

import com.nexus.support.domain.EstadoSla;
import com.nexus.support.domain.Incidente;
import com.nexus.support.dto.CatalogoDtos.Ref;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class IncidenteDtos {

    private IncidenteDtos() {
    }

    public record IncidenteRequest(
            @NotBlank(message = "El título es obligatorio")
            @Size(max = 200, message = "El título admite máximo 200 caracteres")
            String titulo,
            @NotBlank(message = "La descripción es obligatoria")
            @Size(max = 5000, message = "La descripción admite máximo 5000 caracteres")
            String descripcion,
            @NotNull(message = "La categoría es obligatoria") Long categoriaId,
            @NotNull(message = "La prioridad es obligatoria") Long prioridadId,
            /** Técnico responsable: obligatorio para técnicos y administradores; se asigna solo si registra un solicitante. */
            Long usuarioId,
            /** Opcional: por defecto el incidente se crea Abierto. */
            Long estadoId,
            /** Opcional: persona que reporta el incidente. */
            @Size(max = 120, message = "El solicitante admite máximo 120 caracteres")
            String solicitante) {
    }

    public record CambioEstadoRequest(@NotNull(message = "El estado es obligatorio") Long estadoId) {
    }

    /**
     * Situación frente al SLA de la prioridad.
     *
     * @param horasRestantes horas hasta el vencimiento (negativas si ya venció); null si el incidente está finalizado
     */
    public record SlaInfo(int horasSla, LocalDateTime vence, EstadoSla estado, Double horasRestantes) {

        static SlaInfo de(Incidente i, LocalDateTime ahora) {
            int horas = i.getPrioridad().getSlaHoras();
            LocalDateTime vence = EstadoSla.vencimiento(i.getFechaCreacion(), horas);
            boolean finalizado = i.getEstado().isEsFinal();
            EstadoSla estado = EstadoSla.calcular(i.getFechaCreacion(), i.getFechaResolucion(), finalizado, horas, ahora);
            Double restantes = finalizado ? null : redondear(Duration.between(ahora, vence).toMinutes() / 60.0);
            return new SlaInfo(horas, vence, estado, restantes);
        }
    }

    public record IncidenteResponse(Long id, String titulo, String descripcion, Ref categoria, Ref prioridad,
                                    Ref estado, Ref usuario, String solicitante,
                                    /** true si lo registró una cuenta de solicitante (el nombre no se edita). */
                                    boolean solicitanteConCuenta, LocalDateTime fechaCreacion,
                                    LocalDateTime fechaActualizacion, LocalDateTime fechaResolucion,
                                    Double horasResolucion, SlaInfo sla) {

        public static IncidenteResponse de(Incidente i) {
            Double horas = i.getFechaResolucion() == null ? null
                    : redondear(Duration.between(i.getFechaCreacion(), i.getFechaResolucion()).toMinutes() / 60.0);
            return new IncidenteResponse(i.getId(), i.getTitulo(), i.getDescripcion(),
                    new Ref(i.getCategoria().getId(), i.getCategoria().getNombre()),
                    new Ref(i.getPrioridad().getId(), i.getPrioridad().getNombre()),
                    new Ref(i.getEstado().getId(), i.getEstado().getNombre()),
                    new Ref(i.getUsuario().getId(), i.getUsuario().getNombre()),
                    i.getSolicitante(), i.getCuentaSolicitante() != null, i.getFechaCreacion(), i.getFechaActualizacion(), i.getFechaResolucion(),
                    horas, SlaInfo.de(i, LocalDateTime.now()));
        }
    }

    private static double redondear(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
