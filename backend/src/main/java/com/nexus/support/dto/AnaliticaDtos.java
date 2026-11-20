package com.nexus.support.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.nexus.support.domain.EstadoPrimeraRespuesta;

public final class AnaliticaDtos {

    private AnaliticaDtos() {
    }

    /** Filtros comunes a todos los indicadores. 'hasta' es inclusivo. */
    public record Filtro(LocalDate desde, LocalDate hasta, Long categoriaId, Long prioridadId) {
    }

    public record Resumen(long totalIncidentes, long resueltos, long pendientes, BigDecimal tasaResolucion,
                          BigDecimal tiempoPromedioHoras, BigDecimal tiempoMedianaHoras,
                          BigDecimal cumplimientoSla, String categoriaMasRecurrente,
                          long incidentesCategoriaMasRecurrente) {
    }

    public record PorCategoria(Long categoriaId, String categoria, long total, BigDecimal porcentaje,
                               long resueltos, BigDecimal tasaResolucion, BigDecimal tiempoPromedioHoras) {
    }

    public record PorPrioridad(Long prioridadId, String prioridad, int nivel, int slaHoras, long total,
                               BigDecimal porcentaje, BigDecimal tiempoPromedioHoras, BigDecimal cumplimientoSla) {
    }

    public record PorEstado(Long estadoId, String estado, boolean esFinal, long total, BigDecimal porcentaje) {
    }

    public record PuntoTendencia(LocalDate periodo, long total, long resueltos) {
    }

    public record ProblemaRecurrente(String titulo, String categoria, long ocurrencias,
                                     BigDecimal tiempoPromedioHoras) {
    }

    public record AsistenciaStats(long totalConsultas, long respondidas, long sinResultados, long errores,
                                  long pendientes, BigDecimal calificacionPromedio, long calificadas) {
    }

    public record Dashboard(Filtro filtro, Resumen resumen, List<PorCategoria> porCategoria,
                            List<PorPrioridad> porPrioridad, List<PorEstado> porEstado,
                            List<PuntoTendencia> tendencia, String granularidad,
                            List<ProblemaRecurrente> recurrentes, AsistenciaStats asistencia,
                            SlaOperativo sla, List<PorElemento> porElemento) {
    }

    /**
     * @param elementoId nulo en la fila de incidentes sin elemento (siempre la primera)
     * @param cumplimientoSla % de resueltos dentro del SLA de resolución de su prioridad
     */
    public record PorElemento(Long elementoId, String elemento, String tipo, long total, long pendientes,
                              BigDecimal tiempoPromedioHoras, BigDecimal cumplimientoSla) {
    }

    // ---------- SLA operativo (incidentes no finalizados) ----------

    /**
     * @param fraccionAlerta un incidente está "por vencer" cuando le queda esta fracción del plazo o menos
     * @param criticos       vencidos y por vencer, del vencimiento más antiguo al más próximo
     */
    /** @param primeraRespuesta incluye también incidentes finalizados (CUMPLIDO/INCUMPLIDO) */
    public record SlaOperativo(long abiertos, long enPlazo, long porVencer, long vencidos, double fraccionAlerta,
                               List<IncidenteSla> criticos, PrimeraRespuestaResumen primeraRespuesta) {
    }

    /**
     * Primera respuesta de un incidente.
     *
     * @param vence             nulo si no hay objetivo
     * @param respondidoEn      nulo si sigue en el estado inicial o si no se sabe (SIN_DATOS)
     * @param minutosRestantes  solo para incidentes sin responder con objetivo (negativo = retraso)
     */
    public record PrimeraRespuesta(Integer objetivoMinutos, LocalDateTime vence, LocalDateTime respondidoEn,
                                   EstadoPrimeraRespuesta estado, BigDecimal minutosRestantes) {
    }

    /** @param cumplimiento % CUMPLIDO sobre (CUMPLIDO + INCUMPLIDO); nulo si no hay respondidos medibles */
    public record PrimeraRespuestaResumen(long enPlazo, long porVencer, long vencidos, long cumplidos,
                                          long incumplidos, long sinObjetivo, long sinDatos,
                                          BigDecimal cumplimiento) {
    }

    public record IncidenteSla(Long id, String titulo, String prioridad, int slaHoras, String responsable,
                               String estado, LocalDateTime vence, BigDecimal horasRestantes, String estadoSla) {
    }

    // ---------- Calidad de la base de conocimiento (historial RAG y calificaciones) ----------

    /** Datos observados en el historial y recomendaciones derivadas de ellos con reglas fijas. */
    public record Conocimiento(List<ArticuloUso> articulosUtiles, long consultasSinFuentes,
                               List<ConsultaSinFuentes> ultimasSinFuentes, List<CoberturaCategoria> cobertura,
                               Efectividad efectividad, List<Recomendacion> recomendaciones) {
    }

    /**
     * Resultado de las respuestas con fuentes (consultas RESPONDIDA).
     *
     * @param resolvio / noResolvio / sinIndicar lo declarado al calificar (calificacion.resolvio)
     * @param incidentesAsistidos      incidentes con al menos una consulta RESPONDIDA
     * @param reabiertosTrasAsistencia de esos, los que pasaron de un estado final a uno no final después de
     *                                 su primera consulta RESPONDIDA (según incidente_historial). Es una
     *                                 coincidencia en el tiempo, no prueba que la sugerencia fallara.
     */
    public record Efectividad(long respuestasConFuentes, long resolvio, long noResolvio, long sinIndicar,
                              long incidentesAsistidos, long reabiertosTrasAsistencia) {
    }

    /** Uso de un artículo como fuente de respuestas guardadas. */
    public record ArticuloUso(Long id, String titulo, String categoria, boolean fuenteValida, long usos,
                              BigDecimal similitudPromedio, BigDecimal calificacionPromedio, long calificaciones) {
    }

    public record ConsultaSinFuentes(Long consultaId, LocalDateTime fecha, Long incidenteId, String incidente,
                                     String categoria, String pregunta) {
    }

    public record CoberturaCategoria(Long categoriaId, String categoria, long articulosValidos, long consultas,
                                     long respondidas, long sinResultados, long malCalificadas,
                                     BigDecimal calificacionPromedio) {
    }

    /**
     * @param regla     identificador de la regla que la generó
     * @param evidencia datos observados que la sustentan
     */
    public record Recomendacion(String regla, String mensaje, String evidencia) {
    }
}
