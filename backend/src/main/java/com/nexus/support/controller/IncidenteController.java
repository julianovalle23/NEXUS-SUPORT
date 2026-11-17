package com.nexus.support.controller;

import java.net.URI;
import java.time.LocalDate;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.nexus.support.dto.IncidenteDtos.CambioEstadoRequest;
import com.nexus.support.dto.IncidenteDtos.IncidenteRequest;
import com.nexus.support.dto.IncidenteDtos.IncidenteResponse;
import com.nexus.support.dto.PaginaDto;
import com.nexus.support.service.IncidenteService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/incidentes")
@Tag(name = "Incidentes", description = "RF01 - Gestión de incidentes")
public class IncidenteController {

    private final IncidenteService service;

    public IncidenteController(IncidenteService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Consultar y filtrar incidentes (paginado, más recientes primero)")
    public PaginaDto<IncidenteResponse> listar(
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Long prioridadId,
            @RequestParam(required = false) Long estadoId,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String texto,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        var pageable = PageRequest.of(Math.max(pagina, 0), Math.clamp(tamano, 1, 200),
                Sort.by(Sort.Direction.DESC, "fechaCreacion"));
        return service.buscar(categoriaId, prioridadId, estadoId, usuarioId, desde, hasta, texto, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un incidente")
    public IncidenteResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    @Operation(summary = "Registrar un incidente")
    public ResponseEntity<IncidenteResponse> crear(@Valid @RequestBody IncidenteRequest req) {
        IncidenteResponse creado = service.crear(req);
        return ResponseEntity.created(URI.create("/api/incidentes/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar un incidente")
    public IncidenteResponse actualizar(@PathVariable Long id, @Valid @RequestBody IncidenteRequest req) {
        return service.actualizar(id, req);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Actualizar el estado de un incidente (registra la fecha de resolución si es final)")
    public IncidenteResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoRequest req) {
        return service.cambiarEstado(id, req.estadoId());
    }
}
