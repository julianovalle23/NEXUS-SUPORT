package com.nexus.support.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.support.domain.Categoria;
import com.nexus.support.domain.ElementoTi;
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
    private final ElementoTiRepository elementos;

    public CatalogoService(CategoriaRepository categorias, PrioridadRepository prioridades,
                           EstadoIncidenteRepository estados, UsuarioRepository usuarios,
                           ElementoTiRepository elementos) {
        this.categorias = categorias;
        this.prioridades = prioridades;
        this.estados = estados;
        this.usuarios = usuarios;
        this.elementos = elementos;
    }

    public CatalogosDto todos() {
        Map<Long, List<Long>> destinos = new TreeMap<>();
        for (Object[] t : estados.transiciones()) {
            destinos.computeIfAbsent(((Number) t[0]).longValue(), k -> new ArrayList<>()).add(((Number) t[1]).longValue());
        }
        return new CatalogosDto(
                categorias.findAll(Sort.by("id")).stream()
                        .map(c -> new CategoriaDto(c.getId(), c.getNombre(), c.getDescripcion())).toList(),
                prioridades.findAll(Sort.by("nivel")).stream()
                        .map(p -> new PrioridadDto(p.getId(), p.getNombre(), p.getNivel(), p.getSlaHoras())).toList(),
                estados.findAll(Sort.by("orden")).stream()
                        .map(e -> {
                            List<Long> d = destinos.getOrDefault(e.getId(), List.of());
                            return new EstadoDto(e.getId(), e.getNombre(), e.isEsFinal(), e.getOrden(), d.isEmpty(), d);
                        }).toList(),
                usuarios.findByActivoTrueAndRolOrderByNombre(Rol.TECNICO).stream()
                        .map(u -> new UsuarioDto(u.getId(), u.getNombre(), u.getCorreo(), u.getArea())).toList(),
                elementos.findByActivoTrueOrderByTipoAscNombreAsc().stream()
                        .map(e -> new ElementoDto(e.getId(), e.getNombre(), e.getTipo(), e.getDescripcion())).toList());
    }

    public ElementoTi elemento(Long id) {
        return elementos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Elemento de TI", id));
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

    /** Cambio permitido por una operación normal (tabla transicion_estado). */
    public boolean transicionPermitida(Long origen, Long destino) {
        return estados.transicionPermitida(origen, destino);
    }

    /** Estado sin transiciones normales de salida (Cerrado). */
    public boolean esTerminal(Long estadoId) {
        return estados.esTerminal(estadoId);
    }

    public Usuario usuario(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
    }
}
