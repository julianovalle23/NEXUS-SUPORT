package com.nexus.support.dto;

import java.util.List;

import com.nexus.support.domain.TipoElemento;

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

    /**
     * @param terminal true si no tiene transiciones normales de salida (Cerrado)
     * @param destinos estados a los que se puede pasar con una operación normal
     */
    public record EstadoDto(Long id, String nombre, boolean esFinal, Short orden, boolean terminal,
                            List<Long> destinos) {
    }

    public record UsuarioDto(Long id, String nombre, String correo, String area) {
    }

    public record ElementoDto(Long id, String nombre, TipoElemento tipo, String descripcion) {
    }

    /** @param elementos solo los activos (los que se pueden asignar a un incidente) */
    public record CatalogosDto(List<CategoriaDto> categorias, List<PrioridadDto> prioridades,
                               List<EstadoDto> estados, List<UsuarioDto> usuarios, List<ElementoDto> elementos) {
    }
}
