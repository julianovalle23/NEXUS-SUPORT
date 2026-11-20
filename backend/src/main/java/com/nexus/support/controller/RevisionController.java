package com.nexus.support.controller;

import org.springframework.web.bind.annotation.*;

import com.nexus.support.dto.RevisionDtos.*;
import com.nexus.support.service.RevisionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@Tag(name = "Revisiones posteriores", description = "Impacto, línea de tiempo, causa y acciones preventivas")
public class RevisionController {

    private final RevisionService service;

    public RevisionController(RevisionService service) {
        this.service = service;
    }

    @GetMapping("/incidentes/{id}/revision")
    @Operation(summary = "Revisión del incidente (nula si no existe) y línea de tiempo calculada con los datos guardados")
    public RevisionCompleta obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PutMapping("/incidentes/{id}/revision")
    @Operation(summary = "Crear o actualizar la revisión (solo incidentes finalizados; causa confirmada exige evidencia)")
    public RevisionCompleta guardar(@PathVariable Long id, @Valid @RequestBody RevisionRequest req) {
        return service.guardar(id, req);
    }

    @GetMapping("/revisiones/pendientes")
    @Operation(summary = "Incidentes importantes finalizados sin revisión (prioridad Alta o más, o SLA incumplido)")
    public RevisionesPendientes pendientes() {
        return service.pendientes();
    }
}
