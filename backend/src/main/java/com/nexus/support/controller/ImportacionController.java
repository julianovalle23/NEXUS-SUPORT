package com.nexus.support.controller;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.nexus.support.dto.ImportacionDtos.ConfirmarImportacionRequest;
import com.nexus.support.dto.ImportacionDtos.ImportacionDetalle;
import com.nexus.support.dto.ImportacionDtos.ImportacionResumen;
import com.nexus.support.service.ImportacionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/importaciones")
@Tag(name = "Importación de artículos", description = "Carga de archivos TXT, MD o CSV como borradores en revisión")
public class ImportacionController {

    private final ImportacionService service;

    public ImportacionController(ImportacionService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Cargar un archivo de artículos y obtener la vista previa",
            description = "Máximo 1 MB y 200 artículos. No crea artículos: deja la importación en PENDIENTE_REVISION "
                    + "(o ERROR si nada es válido). Rechaza un archivo idéntico a otro con importación vigente.")
    public ResponseEntity<ImportacionDetalle> cargar(@RequestParam("archivo") MultipartFile archivo,
                                                     @RequestParam(required = false) Long categoriaId)
            throws IOException {
        ImportacionDetalle d = service.cargar(archivo.getOriginalFilename(), archivo.getBytes(), categoriaId);
        return ResponseEntity.created(URI.create("/api/importaciones/" + d.resumen().id())).body(d);
    }

    @GetMapping
    @Operation(summary = "Últimas 100 importaciones")
    public List<ImportacionResumen> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Importación con la vista previa de sus registros")
    public ImportacionDetalle detalle(@PathVariable Long id) {
        return service.detalle(id);
    }

    @PostMapping("/{id}/confirmar")
    @Operation(summary = "Crear borradores inactivos y sin verificar con los registros seleccionados",
            description = "Sin cuerpo, o con filas = null, importa todos los registros válidos.")
    public ImportacionDetalle confirmar(@PathVariable Long id,
                                        @RequestBody(required = false) ConfirmarImportacionRequest req) {
        return service.confirmar(id, req == null ? null : req.filas());
    }

    @PostMapping("/{id}/descartar")
    @Operation(summary = "Descartar la importación sin crear artículos")
    public ImportacionDetalle descartar(@PathVariable Long id) {
        return service.descartar(id);
    }
}
