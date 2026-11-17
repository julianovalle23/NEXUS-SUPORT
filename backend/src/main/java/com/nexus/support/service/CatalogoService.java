package com.nexus.support.service;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.support.domain.Categoria;
import com.nexus.support.domain.EstadoIncidente;
import com.nexus.support.domain.Prioridad;
import com.nexus.support.domain.Rol;
import com.nexus.support.domain.Usuario;
import com.nexus.support.dto.CatalogoDtos.*;
import com.nexus.support.exception.RecursoNoEncontradoException;
import com.nexus.support.repository.*;

@Service
@Transactional(readOnly = true)
public class CatalogoService {

    private final CategoriaRepository categorias;
    private final PrioridadRepository prioridades;
    private final EstadoIncidenteRepository estados;
    private final UsuarioRepository usuarios;

    public CatalogoService(CategoriaRepository categorias, PrioridadRepository prioridades,
                           EstadoIncidenteRepository estados, UsuarioRepository usuarios) {
        this.categorias = categorias;
        this.prioridades = prioridades;
        this.estados = estados;
        this.usuarios = usuarios;
    }

    public CatalogosDto todos() {
        return new CatalogosDto(
                categorias.findAll(Sort.by("id")).stream()
                        .map(c -> new CategoriaDto(c.getId(), c.getNombre(), c.getDescripcion())).toList(),
                prioridades.findAll(Sort.by("nivel")).stream()
                        .map(p -> new PrioridadDto(p.getId(), p.getNombre(), p.getNivel(), p.getSlaHoras())).toList(),
                estados.findAll(Sort.by("orden")).stream()
                        .map(e -> new EstadoDto(e.getId(), e.getNombre(), e.isEsFinal(), e.getOrden())).toList(),
                usuarios.findByActivoTrueAndRolOrderByNombre(Rol.TECNICO).stream()
                        .map(u -> new UsuarioDto(u.getId(), u.getNombre(), u.getCorreo(), u.getArea())).toList());
    }

    public Categoria categoria(Long id) {
        return categorias.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Categoría", id));
    }

    public Prioridad prioridad(Long id) {
        return prioridades.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Prioridad", id));
    }

    public EstadoIncidente estado(Long id) {
        return estados.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Estado", id));
    }

    public Usuario usuario(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
    }
}
