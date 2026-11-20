package com.nexus.support.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

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
            @Size(max = 500, message = "El comentario admite máximo 500 caracteres") String comentario,
            /** Opcional: si la solución sugerida resolvió el incidente; solo para respuestas con fuentes. */
            Boolean resolvio) {
    }

    // ---------- Contrato con n8n ----------

    /**
     * Cuerpo enviado al flujo RAG. {@code correlationId} no forma parte del cuerpo: viaja en la cabecera
     * X-Correlation-Id, que el flujo publicado anterior ignora (su validación rechaza claves desconocidas del cuerpo).
     */
    public record RagSolicitud(Long consultaId, String pregunta, IncidenteRag incidente, double umbral, int topK,
                               int minResultados, boolean simularFalloEmbeddings, @JsonIgnore String correlationId) {

        public RagSolicitud(Long consultaId, String pregunta, IncidenteRag incidente, double umbral, int topK,
                            int minResultados, boolean simularFalloEmbeddings) {
            this(consultaId, pregunta, incidente, umbral, topK, minResultados, simularFalloEmbeddings, null);
        }
    }

    /**
     * Datos del incidente para el flujo RAG. Los campos nulos no se envían: el flujo publicado anterior solo admite
     * titulo, categoria y prioridad, y rechaza cualquier otra clave aunque sea null.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IncidenteRag(String titulo, String categoria, String prioridad, String elemento,
                               String mensajeError, String pasosIntentados) {

        public IncidenteRag(String titulo, String categoria, String prioridad) {
            this(titulo, categoria, prioridad, null, null, null);
        }
    }

    /** {@code auditoria} es opcional: el backend la usa solo para registrar los candidatos no citados. */
    /**
     * {@code correlationId} y {@code ejecucionId} los devuelve el flujo con trazabilidad (etapa A); el publicado
     * anterior no los envía y quedan en null.
     */
    public record RagResultado(String estado, String respuesta, List<String> pasos, List<FuenteRag> fuentes,
                               Double mejorSimilitud, String modelo, String etapa, String mensaje,
                               AuditoriaRag auditoria, String correlationId, String ejecucionId) {

        public RagResultado(String estado, String respuesta, List<String> pasos, List<FuenteRag> fuentes,
                            Double mejorSimilitud, String modelo, String etapa, String mensaje, AuditoriaRag auditoria) {
            this(estado, respuesta, pasos, fuentes, mejorSimilitud, modelo, etapa, mensaje, auditoria, null, null);
        }
    }

    /** Subconjunto de la auditoría del flujo RAG; el resto de campos se ignora. */
    public record AuditoriaRag(List<CandidataRag> candidatas) {
    }

    /** Artículo entregado al modelo como contexto; {@code citada} indica si lo usó en la respuesta. */
    public record CandidataRag(Long articuloId, Double similitud, Boolean citada) {
    }

    public record FuenteRag(Long articuloId, String titulo, String categoria, Double similitud, Boolean usada) {
    }

    // ---------- Salida ----------

    /** {@code usada} = false: candidato recuperado que el modelo no citó (no es evidencia de la respuesta). */
    public record FuenteDto(Long articuloId, String titulo, BigDecimal similitud, boolean usada) {
    }

    public record CalificacionDto(Long id, Long respuestaId, Short puntuacion, String comentario,
                                  Boolean resolvio, LocalDateTime fecha) {
        public static CalificacionDto de(Calificacion c) {
            return c == null ? null
                    : new CalificacionDto(c.getId(), c.getRespuesta().getId(), c.getPuntuacion(), c.getComentario(),
                    c.getResolvio(), c.getFecha());
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
                            .map(f -> new FuenteDto(f.getArticulo().getId(), f.getArticulo().getTitulo(), f.getSimilitud(),
                                    f.isUsada()))
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
