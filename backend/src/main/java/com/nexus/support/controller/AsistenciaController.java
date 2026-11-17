package com.nexus.support.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.nexus.support.domain.EstadoConsulta;
import com.nexus.support.dto.AsistenciaDtos.*;
import com.nexus.support.dto.PaginaDto;
import com.nexus.support.service.AsistenciaService;
import com.nexus.support.service.CalificacionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@Tag(name = "Asistencia inteligente", description = "RF04/RF05 - RAG · RF08 - Historial y evaluación")
public class AsistenciaController {

    private final AsistenciaService asistencia;
    private final CalificacionService calificaciones;

    public AsistenciaController(AsistenciaService asistencia, CalificacionService calificaciones) {
        this.asistencia = asistencia;
        this.calificaciones = calificaciones;
    }

    @PostMapping("/incidentes/{id}/asistencia")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Solicitar asistencia RAG para un incidente",
            description = "Usa la descripción del incidente (o la pregunta enviada). La consulta queda registrada "
                    + "con estado RESPONDIDA, SIN_RESULTADOS o ERROR.")
    public ConsultaDto solicitar(@PathVariable Long id,
                                 @Valid @RequestBody(required = false) AsistenciaRequest req,
                                 @RequestParam(defaultValue = "false") boolean simularFalloEmbeddings) {
        return asistencia.consultar(id, req, simularFalloEmbeddings);
    }

    @GetMapping("/incidentes/{id}/consultas")
    @Operation(summary = "Historial de asistencias generadas para un incidente")
    public List<ConsultaDto> historialIncidente(@PathVariable Long id) {
        return asistencia.historialIncidente(id);
    }

    @GetMapping("/consultas")
    @Operation(summary = "Historial global de consultas inteligentes")
    public PaginaDto<ConsultaDto> historial(@RequestParam(required = false) EstadoConsulta estado,
                                            @RequestParam(defaultValue = "0") int pagina,
                                            @RequestParam(defaultValue = "20") int tamano) {
        return asistencia.historial(estado, pagina, tamano);
    }

    @GetMapping("/consultas/{id}")
    @Operation(summary = "Detalle de una consulta con su respuesta, fuentes y calificación")
    public ConsultaDto consulta(@PathVariable Long id) {
        return asistencia.obtener(id);
    }

    @PutMapping("/respuestas/{id}/calificacion")
    @Operation(summary = "Calificar la utilidad de una respuesta (1 a 5)")
    public CalificacionDto calificar(@PathVariable Long id, @Valid @RequestBody CalificacionRequest req) {
        return calificaciones.calificar(id, req);
    }

    @GetMapping("/respuestas/{id}/calificacion")
    @Operation(summary = "Consultar la calificación de una respuesta")
    public CalificacionDto calificacion(@PathVariable Long id) {
        return calificaciones.obtener(id);
    }
}
