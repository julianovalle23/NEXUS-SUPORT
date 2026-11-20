package com.nexus.support.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.nexus.support.domain.EstadoProblema;
import com.nexus.support.dto.ProblemaDtos.*;
import com.nexus.support.service.ProblemaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/problemas")
@Tag(name = "Problemas", description = "Causas comunes de incidentes repetidos")
public class ProblemaController {

    private final ProblemaService service;

    public ProblemaController(ProblemaService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar problemas (más incidentes vinculados primero)")
    public List<ProblemaResumen> listar(@RequestParam(required = false) EstadoProblema estado) {
        return service.listar(estado);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un problema con sus incidentes")
    public ProblemaDetalle obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    @Operation(summary = "Registrar un problema")
    public ResponseEntity<ProblemaDetalle> crear(@Valid @RequestBody ProblemaRequest req) {
        ProblemaDetalle creado = service.crear(req);
        return ResponseEntity.created(URI.create("/api/problemas/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar descripción, causa, evidencia y soluciones")
    public ProblemaDetalle actualizar(@PathVariable Long id, @Valid @RequestBody ProblemaRequest req) {
        return service.actualizar(id, req);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Cambiar el estado (CAUSA_CONFIRMADA exige causa y evidencia)")
    public ProblemaDetalle cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoProblemaRequest req) {
        return service.cambiarEstado(id, req.estado());
    }

    @PutMapping("/{id}/incidentes/{incidenteId}")
    @Operation(summary = "Vincular un incidente al problema")
    public ProblemaDetalle vincular(@PathVariable Long id, @PathVariable Long incidenteId) {
        return service.vincular(id, incidenteId);
    }

    @DeleteMapping("/{id}/incidentes/{incidenteId}")
    @Operation(summary = "Desvincular un incidente del problema")
    public ProblemaDetalle desvincular(@PathVariable Long id, @PathVariable Long incidenteId) {
        return service.desvincular(id, incidenteId);
    }
}
