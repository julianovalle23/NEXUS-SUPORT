package com.nexus.support.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.nexus.support.dto.UsuarioDtos.AsignarClaveRequest;
import com.nexus.support.dto.UsuarioDtos.AuditoriaDto;
import com.nexus.support.dto.UsuarioDtos.UsuarioRequest;
import com.nexus.support.dto.UsuarioDtos.UsuarioResponse;
import com.nexus.support.service.UsuarioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Solo administradores (ver SeguridadConfig). */
@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuarios", description = "Cuentas y perfiles (solo administradores)")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar todas las cuentas")
    public List<UsuarioResponse> listar() {
        return service.listar();
    }

    @GetMapping("/auditoria")
    @Operation(summary = "Últimos cambios de perfil o estado de las cuentas (quién, a quién, cuándo, qué)")
    public List<AuditoriaDto> auditoria() {
        return service.auditoria();
    }

    @PostMapping
    @Operation(summary = "Crear una cuenta")
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody UsuarioRequest req) {
        UsuarioResponse creado = service.crear(req);
        return ResponseEntity.created(URI.create("/api/usuarios/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar nombre, correo, área, perfil y estado")
    public UsuarioResponse actualizar(@PathVariable Long id, @Valid @RequestBody UsuarioRequest req) {
        return service.actualizar(id, req);
    }

    @PutMapping("/{id}/clave")
    @Operation(summary = "Asignar o restablecer la contraseña de una cuenta")
    public UsuarioResponse asignarClave(@PathVariable Long id, @Valid @RequestBody AsignarClaveRequest req) {
        return service.asignarClave(id, req.clave());
    }
}
