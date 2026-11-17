package com.nexus.support.dto;

import java.util.List;

/** DTOs de solo lectura para catálogos. */
public final class CatalogoDtos {

    private CatalogoDtos() {
    }

    /** Referencia compacta (id + nombre) usada dentro de otras respuestas. */
    public record Ref(Long id, String nombre) {
    }

    public record CategoriaDto(Long id, String nombre, String descripcion) {
    }

    public record PrioridadDto(Long id, String nombre, Short nivel, Integer slaHoras) {
    }

    public record EstadoDto(Long id, String nombre, boolean esFinal, Short orden) {
    }

    public record UsuarioDto(Long id, String nombre, String correo, String area) {
    }

    public record CatalogosDto(List<CategoriaDto> categorias, List<PrioridadDto> prioridades,
                               List<EstadoDto> estados, List<UsuarioDto> usuarios) {
    }
}
