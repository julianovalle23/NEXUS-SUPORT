package com.nexus.support.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import com.nexus.support.domain.Calificacion;
import com.nexus.support.domain.ConsultaIA;
import com.nexus.support.domain.EstadoConsulta;
import com.nexus.support.domain.RespuestaIA;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AsistenciaDtos {

    private AsistenciaDtos() {
    }

    // ---------- Entrada ----------

    /** La pregunta es opcional: si no se envía se usa la descripción del incidente. */
    public record AsistenciaRequest(@Size(max = 5000) String pregunta) {
    }

    public record CalificacionRequest(
            @NotNull(message = "La puntuación es obligatoria")
            @Min(value = 1, message = "La puntuación mínima es 1")
            @Max(value = 5, message = "La puntuación máxima es 5")
            Integer puntuacion,
            @Size(max = 500, message = "El comentario admite máximo 500 caracteres") String comentario) {
    }

    // ---------- Contrato con n8n ----------

    public record RagSolicitud(Long consultaId, String pregunta, IncidenteRag incidente, double umbral, int topK,
                               int minResultados, boolean simularFalloEmbeddings) {
    }

    public record IncidenteRag(String titulo, String categoria, String prioridad) {
    }

    public record RagResultado(String estado, String respuesta, List<String> pasos, List<FuenteRag> fuentes,
                               Double mejorSimilitud, String modelo, String etapa, String mensaje) {
    }

    public record FuenteRag(Long articuloId, String titulo, String categoria, Double similitud, Boolean usada) {
    }

    // ---------- Salida ----------

    public record FuenteDto(Long articuloId, String titulo, BigDecimal similitud) {
    }

    public record CalificacionDto(Long id, Long respuestaId, Short puntuacion, String comentario,
                                  LocalDateTime fecha) {
        public static CalificacionDto de(Calificacion c) {
            return c == null ? null
                    : new CalificacionDto(c.getId(), c.getRespuesta().getId(), c.getPuntuacion(), c.getComentario(),
                    c.getFecha());
        }
    }

    public record RespuestaDto(Long id, String respuesta, List<String> pasos, String modelo, LocalDateTime fecha,
                               List<FuenteDto> fuentes, CalificacionDto calificacion) {
        public static RespuestaDto de(RespuestaIA r) {
            if (r == null) return null;
            List<String> pasos = r.getPasos() == null || r.getPasos().isBlank() ? List.of()
                    : Arrays.asList(r.getPasos().split("\n"));
            return new RespuestaDto(r.getId(), r.getRespuesta(), pasos, r.getModelo(), r.getFecha(),
                    r.getFuentes().stream()
                            .map(f -> new FuenteDto(f.getArticulo().getId(), f.getArticulo().getTitulo(), f.getSimilitud()))
                            .toList(),
                    CalificacionDto.de(r.getCalificacion()));
        }
    }

    public record ConsultaDto(Long id, Long incidenteId, String incidenteTitulo, String pregunta,
                              LocalDateTime fecha, EstadoConsulta estado, BigDecimal umbral, String detalleError,
                              RespuestaDto respuesta) {
        public static ConsultaDto de(ConsultaIA c) {
            return new ConsultaDto(c.getId(), c.getIncidente().getId(), c.getIncidente().getTitulo(), c.getPregunta(),
                    c.getFecha(), c.getEstado(), c.getUmbral(), c.getDetalleError(), RespuestaDto.de(c.getRespuesta()));
        }
    }
}
