package com.nexus.support.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.*;

import com.nexus.support.dto.AnaliticaDtos.*;
import com.nexus.support.service.AnaliticaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * RF06/RF07. Todos los endpoints aceptan los filtros desde, hasta (inclusivo,
 * formato AAAA-MM-DD), categoriaId y prioridadId.
 */
@RestController
@RequestMapping("/api/analitica")
@Tag(name = "Análisis de datos", description = "RF06 - Indicadores calculados con SQL sobre los incidentes")
public class AnaliticaController {

    private final AnaliticaService service;

    public AnaliticaController(AnaliticaService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Todos los indicadores del dashboard con los mismos filtros")
    public Dashboard dashboard(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate desde,
                               @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate hasta,
                               @RequestParam(required = false) Long categoriaId,
                               @RequestParam(required = false) Long prioridadId,
                               @RequestParam(defaultValue = "semana") String granularidad) {
        return service.dashboard(service.filtro(desde, hasta, categoriaId, prioridadId), granularidad);
    }

    @GetMapping("/resumen")
    @Operation(summary = "Total, tasa de resolución, tiempos promedio/mediana, cumplimiento de SLA y categoría más recurrente")
    public Resumen resumen(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate desde,
                           @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate hasta,
                           @RequestParam(required = false) Long categoriaId,
                           @RequestParam(required = false) Long prioridadId) {
        return service.resumen(service.filtro(desde, hasta, categoriaId, prioridadId));
    }

    @GetMapping("/categorias")
    @Operation(summary = "Incidentes por categoría: frecuencia, porcentaje, tasa de resolución y tiempo promedio")
    public List<PorCategoria> categorias(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate desde,
                                         @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate hasta,
                                         @RequestParam(required = false) Long categoriaId,
                                         @RequestParam(required = false) Long prioridadId) {
        return service.porCategoria(service.filtro(desde, hasta, categoriaId, prioridadId));
    }

    @GetMapping("/prioridades")
    @Operation(summary = "Distribución por nivel de urgencia y cumplimiento de SLA")
    public List<PorPrioridad> prioridades(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate desde,
                                          @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate hasta,
                                          @RequestParam(required = false) Long categoriaId,
                                          @RequestParam(required = false) Long prioridadId) {
        return service.porPrioridad(service.filtro(desde, hasta, categoriaId, prioridadId));
    }

    @GetMapping("/estados")
    @Operation(summary = "Distribución por estado")
    public List<PorEstado> estados(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate desde,
                                   @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate hasta,
                                   @RequestParam(required = false) Long categoriaId,
                                   @RequestParam(required = false) Long prioridadId) {
        return service.porEstado(service.filtro(desde, hasta, categoriaId, prioridadId));
    }

    @GetMapping("/tendencia")
    @Operation(summary = "Evolución diaria o semanal (granularidad = dia | semana)")
    public List<PuntoTendencia> tendencia(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate desde,
                                          @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate hasta,
                                          @RequestParam(required = false) Long categoriaId,
                                          @RequestParam(required = false) Long prioridadId,
                                          @RequestParam(defaultValue = "semana") String granularidad) {
        return service.tendencia(service.filtro(desde, hasta, categoriaId, prioridadId), granularidad);
    }

    @GetMapping("/recurrentes")
    @Operation(summary = "Problemas más frecuentes (títulos repetidos)")
    public List<ProblemaRecurrente> recurrentes(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate desde,
                                                @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate hasta,
                                                @RequestParam(required = false) Long categoriaId,
                                                @RequestParam(required = false) Long prioridadId,
                                                @RequestParam(defaultValue = "10") int limite) {
        return service.recurrentes(service.filtro(desde, hasta, categoriaId, prioridadId), limite);
    }

    @GetMapping("/asistencia")
    @Operation(summary = "Uso del asistente RAG: consultas por estado y calificación promedio")
    public AsistenciaStats asistencia(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate desde,
                                      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate hasta,
                                      @RequestParam(required = false) Long categoriaId,
                                      @RequestParam(required = false) Long prioridadId) {
        return service.asistencia(service.filtro(desde, hasta, categoriaId, prioridadId));
    }

    @GetMapping("/sla")
    @Operation(summary = "Incidentes no finalizados frente al SLA de su prioridad: en plazo, por vencer y vencidos")
    public SlaOperativo sla(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate desde,
                            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate hasta,
                            @RequestParam(required = false) Long categoriaId,
                            @RequestParam(required = false) Long prioridadId) {
        return service.slaOperativo(service.filtro(desde, hasta, categoriaId, prioridadId));
    }

    @GetMapping("/conocimiento")
    @Operation(summary = "Calidad de la base de conocimiento: artículos más útiles, consultas sin fuentes, "
            + "cobertura por categoría y recomendaciones por reglas (filtra por la fecha de la consulta)")
    public Conocimiento conocimiento(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate desde,
                                     @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate hasta,
                                     @RequestParam(required = false) Long categoriaId,
                                     @RequestParam(required = false) Long prioridadId) {
        return service.conocimiento(service.filtro(desde, hasta, categoriaId, prioridadId));
    }
}
