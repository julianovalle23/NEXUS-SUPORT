package com.nexus.support.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.nexus.support.dto.ArticuloDtos.ArticuloIndexable;
import com.nexus.support.dto.ArticuloDtos.ArticulosIndexablesResponse;
import com.nexus.support.dto.ArticuloDtos.FuenteContexto;
import com.nexus.support.dto.ArticuloDtos.FuentesValidasResponse;
import com.nexus.support.dto.ArticuloDtos.ValidarFuentesRequest;
import com.nexus.support.service.ArticuloService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Endpoints consumidos únicamente por n8n. Protegidos con el encabezado
 * X-Internal-Token (ver {@link com.nexus.support.config.WebConfig}).
 */
@RestController
@RequestMapping("/api/internal")
@Tag(name = "Interno (n8n)", description = "Requiere encabezado X-Internal-Token")
public class InternalController {

    private final ArticuloService articulos;

    public InternalController(ArticuloService articulos) {
        this.articulos = articulos;
    }

    @GetMapping("/articulos")
    @Operation(summary = "Artículos con texto y hash para el flujo de indexación")
    public ArticulosIndexablesResponse articulos() {
        List<ArticuloIndexable> lista = articulos.indexables();
        return new ArticulosIndexablesResponse(lista.size(), lista);
    }

    @PostMapping("/articulos/validar")
    @Operation(summary = "Confirma qué artículos recuperados existen, están activos y verificados")
    public FuentesValidasResponse validar(@Valid @RequestBody ValidarFuentesRequest req) {
        List<FuenteContexto> fuentes = req.ids().isEmpty() ? List.of() : articulos.fuentesValidas(req.ids());
        return new FuentesValidasResponse(fuentes.size(), fuentes);
    }
}
