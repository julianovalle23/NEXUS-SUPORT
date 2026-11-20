package com.nexus.support.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.nexus.support.dto.ArticuloDtos.ArticuloRequest;
import com.nexus.support.dto.ArticuloDtos.ArticuloResponse;
import com.nexus.support.dto.ArticuloDtos.EstadoArticuloRequest;
import com.nexus.support.service.ArticuloService;
import com.nexus.support.service.IndexacionService;
import com.nexus.support.service.IndexacionService.EstadoIndexacion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/articulos")
@Tag(name = "Base de conocimiento", description = "RF02 - Artículos técnicos")
public class ArticuloController {

    private final ArticuloService service;
    private final IndexacionService indexacion;

    public ArticuloController(ArticuloService service, IndexacionService indexacion) {
        this.service = service;
        this.indexacion = indexacion;
    }

    @GetMapping
    @Operation(summary = "Listar artículos, opcionalmente por estado y categoría")
    public List<ArticuloResponse> listar(@RequestParam(required = false) Boolean activo,
                                         @RequestParam(required = false) Long categoriaId) {
        return service.listar(activo, categoriaId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un artículo")
    public ArticuloResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    @Operation(summary = "Crear un artículo técnico")
    public ResponseEntity<ArticuloResponse> crear(@Valid @RequestBody ArticuloRequest req) {
        ArticuloResponse creado = service.crear(req);
        return ResponseEntity.created(URI.create("/api/articulos/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar el contenido de un artículo")
    public ArticuloResponse actualizar(@PathVariable Long id, @Valid @RequestBody ArticuloRequest req) {
        return service.actualizar(id, req);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Activar/desactivar o verificar un artículo")
    public ArticuloResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoArticuloRequest req) {
        return service.cambiarEstado(id, req);
    }

    @PostMapping("/reindexar")
    @Operation(summary = "Solicitar el flujo de indexación de n8n (RF03)",
            description = "Responde 202 sin esperar a n8n. Si ya hay una indexación en curso, la solicitud queda "
                    + "pendiente y se atiende al terminar. Con forzar=true vuelve a generar todos los embeddings. "
                    + "El resultado se consulta en GET /api/articulos/indexacion.")
    public ResponseEntity<EstadoIndexacion> reindexar(@RequestParam(defaultValue = "false") boolean forzar) {
        return ResponseEntity.accepted().body(indexacion.solicitar(forzar, IndexacionService.Origen.MANUAL));
    }

    @GetMapping("/indexacion")
    @Operation(summary = "Estado de la última indexación o de la que está en curso")
    public EstadoIndexacion estadoIndexacion() {
        return indexacion.estado();
    }
}
