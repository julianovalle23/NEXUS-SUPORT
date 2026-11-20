package com.nexus.support.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.nexus.support.domain.AlertaSla;
import com.nexus.support.dto.CatalogoDtos.Ref;

import jakarta.validation.constraints.Size;

public final class AlertaSlaDtos {

    private AlertaSlaDtos() {
    }

    /**
     * Alerta de SLA.
     *
     * @param responsable          técnico responsable ACTUAL del incidente
     * @param responsableAlGenerar técnico responsable cuando se generó la alerta (a quien se avisó)
     */
    public record AlertaSlaDto(Long id, Ref incidente, String prioridad, String estadoIncidente,
                               AlertaSla.Tipo tipo, AlertaSla.Nivel nivel, LocalDateTime vence,
                               LocalDateTime generadaEn, AlertaSla.Estado estado, Ref responsable,
                               String responsableAlGenerar, String atendidaPor, LocalDateTime fechaCierre,
                               String nota) {
    }

    public record AlertasSla(long pendientes, long escalamientosPendientes, List<AlertaSlaDto> alertas) {
    }

    public record AtenderAlertaRequest(
            @Size(max = 500, message = "La nota admite máximo 500 caracteres") String nota) {
    }

    /** Resultado de una revisión: alertas nuevas (o reactivadas) y alertas que dejaron de aplicar. */
    public record RevisionAlertas(LocalDateTime fecha, int generadas, int superadas) {
    }
}
