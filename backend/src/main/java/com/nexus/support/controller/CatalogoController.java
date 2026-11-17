package com.nexus.support.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.support.dto.CatalogoDtos.CatalogosDto;
import com.nexus.support.service.CatalogoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/catalogos")
@Tag(name = "Catálogos", description = "Categorías, prioridades, estados y usuarios")
public class CatalogoController {

    private final CatalogoService service;

    public CatalogoController(CatalogoService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar todos los catálogos para formularios y filtros")
    public CatalogosDto todos() {
        return service.todos();
    }
}
