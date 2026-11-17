package com.nexus.support.service;

import java.time.LocalDate;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.support.domain.Incidente;
import com.nexus.support.domain.Rol;
import com.nexus.support.domain.Usuario;
import com.nexus.support.dto.IncidenteDtos.IncidenteRequest;
import com.nexus.support.dto.IncidenteDtos.IncidenteResponse;
import com.nexus.support.dto.PaginaDto;
import com.nexus.support.exception.RecursoNoEncontradoException;
import com.nexus.support.exception.ReglaNegocioException;
import com.nexus.support.repository.IncidenteRepository;
import com.nexus.support.repository.UsuarioRepository;
import com.nexus.support.security.SesionActual;

/**
 * Los métodos públicos devuelven DTOs construidos dentro de la transacción
 * (open-in-view está deshabilitado, así que las relaciones LAZY deben leerse aquí).
 */
@Service
@Transactional(readOnly = true)
public class IncidenteService {

    /** Estado con el que nace todo incidente si no se indica otro. */
    static final long ESTADO_ABIERTO = 1L;

    private final IncidenteRepository incidentes;
    private final CatalogoService catalogos;
    private final UsuarioRepository usuarios;

    public IncidenteService(IncidenteRepository incidentes, CatalogoService catalogos, UsuarioRepository usuarios) {
        this.incidentes = incidentes;
        this.catalogos = catalogos;
        this.usuarios = usuarios;
    }

    public PaginaDto<IncidenteResponse> buscar(Long categoriaId, Long prioridadId, Long estadoId, Long usuarioId,
                                               LocalDate desde, LocalDate hasta, String texto, Pageable pageable) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new ReglaNegocioException("La fecha 'hasta' no puede ser anterior a 'desde'");
        }
        String patron = (texto == null || texto.isBlank()) ? null : "%" + texto.trim().toLowerCase() + "%";
        // Un solicitante solo ve los incidentes que registró
        Long solicitanteId = SesionActual.esSolicitante() ? SesionActual.cuenta().id() : null;
        return PaginaDto.de(incidentes.buscar(categoriaId, prioridadId, estadoId, usuarioId, solicitanteId,
                desde == null ? null : desde.atStartOfDay(),
                // 'hasta' es inclusivo: se compara contra el inicio del día siguiente
                hasta == null ? null : hasta.plusDays(1).atStartOfDay(),
                patron, pageable), IncidenteResponse::de);
    }

    public IncidenteResponse obtener(Long id) {
        Incidente i = entidad(id);
        // A un solicitante, los incidentes ajenos se le presentan como inexistentes
        if (SesionActual.esSolicitante() && (i.getCuentaSolicitante() == null
                || !i.getCuentaSolicitante().getId().equals(SesionActual.cuenta().id()))) {
            throw new RecursoNoEncontradoException("Incidente", id);
        }
        return IncidenteResponse.de(i);
    }

    /** Uso interno de otros servicios que participan en la misma transacción. */
    Incidente entidad(Long id) {
        return incidentes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Incidente", id));
    }

    @Transactional
    public IncidenteResponse crear(IncidenteRequest req) {
        Incidente i = new Incidente();
        if (SesionActual.esSolicitante()) {
            // El solicitante no elige técnico ni estado: nace Abierto y se asigna al técnico con menos carga.
            Usuario yo = catalogos.usuario(SesionActual.cuenta().id());
            datosBasicos(i, req);
            i.setUsuario(usuarios.tecnicoConMenosCarga().orElseThrow(() ->
                    new ReglaNegocioException("No hay técnicos activos para asignar el incidente")));
            i.setSolicitante(yo.getNombre());
            i.setCuentaSolicitante(yo);
            i.cambiarEstado(catalogos.estado(ESTADO_ABIERTO));
        } else {
            aplicar(i, req);
            i.cambiarEstado(catalogos.estado(req.estadoId() == null ? ESTADO_ABIERTO : req.estadoId()));
        }
        return IncidenteResponse.de(incidentes.save(i));
    }

    @Transactional
    public IncidenteResponse actualizar(Long id, IncidenteRequest req) {
        Incidente i = entidad(id);
        aplicar(i, req);
        if (req.estadoId() != null && !req.estadoId().equals(i.getEstado().getId())) {
            i.cambiarEstado(catalogos.estado(req.estadoId()));
        }
        return IncidenteResponse.de(i);
    }

    @Transactional
    public IncidenteResponse cambiarEstado(Long id, Long estadoId) {
        Incidente i = entidad(id);
        i.cambiarEstado(catalogos.estado(estadoId));
        return IncidenteResponse.de(i);
    }

    private void datosBasicos(Incidente i, IncidenteRequest req) {
        i.setTitulo(req.titulo().trim());
        i.setDescripcion(req.descripcion().trim());
        i.setCategoria(catalogos.categoria(req.categoriaId()));
        i.setPrioridad(catalogos.prioridad(req.prioridadId()));
    }

    private void aplicar(Incidente i, IncidenteRequest req) {
        datosBasicos(i, req);
        if (req.usuarioId() == null) {
            throw new ReglaNegocioException("El técnico responsable es obligatorio");
        }
        i.setUsuario(responsable(i, req.usuarioId()));
        if (i.getCuentaSolicitante() != null) {
            return; // registrado por una cuenta de solicitante: el nombre queda ligado a esa cuenta
        }
        i.setSolicitante(req.solicitante() == null || req.solicitante().isBlank() ? null : req.solicitante().trim());
    }

    /**
     * Solo se puede asignar a una cuenta activa con perfil técnico; si ya era el responsable
     * se conserva aunque se haya desactivado o cambiado de perfil.
     */
    private Usuario responsable(Incidente i, Long usuarioId) {
        Usuario u = catalogos.usuario(usuarioId);
        boolean esElActual = i.getUsuario() != null && i.getUsuario().getId().equals(u.getId());
        if (esElActual) {
            return u;
        }
        if (u.getRol() != Rol.TECNICO) {
            throw new ReglaNegocioException(u.getNombre() + " no tiene perfil de técnico y no puede recibir incidentes");
        }
        if (!u.isActivo()) {
            throw new ReglaNegocioException("El técnico " + u.getNombre() + " está inactivo y no puede recibir incidentes");
        }
        return u;
    }
}
