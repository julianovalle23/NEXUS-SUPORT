package com.nexus.support.controller;

import org.springframework.web.bind.annotation.*;

import com.nexus.support.domain.AlertaSla;
import com.nexus.support.dto.AlertaSlaDtos.AlertaSlaDto;
import com.nexus.support.dto.AlertaSlaDtos.AlertasSla;
import com.nexus.support.dto.AlertaSlaDtos.AtenderAlertaRequest;
import com.nexus.support.dto.AlertaSlaDtos.RevisionAlertas;
import com.nexus.support.service.AlertaSlaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/alertas-sla")
@Tag(name = "Alertas de SLA", description = "Avisos antes del vencimiento y escalamientos después, por reglas")
public class AlertaSlaController {

    private final AlertaSlaService service;

    public AlertaSlaController(AlertaSlaService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Alertas de SLA (máximo 200)",
            description = "Administradores ven todas; técnicos, las de sus incidentes. Sin estado: todas.")
    public AlertasSla listar(@RequestParam(required = false) AlertaSla.Estado estado) {
        return service.listar(estado);
    }

    @PostMapping("/{id}/atender")
    @Operation(summary = "Dar por atendida una alerta pendiente",
            description = "Un escalamiento solo lo atiende un administrador. No modifica el incidente ni el SLA.")
    public AlertaSlaDto atender(@PathVariable Long id, @Valid @RequestBody(required = false) AtenderAlertaRequest req) {
        return service.atender(id, req == null ? null : req.nota());
    }

    @PostMapping("/revisar")
    @Operation(summary = "Ejecutar ahora la revisión de alertas (también corre cada nexus.sla.revision-intervalo)")
    public RevisionAlertas revisar() {
        return service.revisar();
    }
}
