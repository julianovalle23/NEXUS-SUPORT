package com.nexus.support.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.support.domain.ElementoTi;
import com.nexus.support.domain.EstadoProblema;
import com.nexus.support.domain.Incidente;
import com.nexus.support.domain.Problema;
import com.nexus.support.dto.CatalogoDtos.Ref;
import com.nexus.support.dto.ProblemaDtos.*;
import com.nexus.support.exception.RecursoNoEncontradoException;
import com.nexus.support.exception.ReglaNegocioException;
import com.nexus.support.repository.IncidenteRepository;
import com.nexus.support.repository.ProblemaRepository;
import com.nexus.support.repository.UsuarioRepository;
import com.nexus.support.security.SesionActual;

/**
 * Problemas y su vínculo con incidentes. Reglas:
 * <ul>
 *   <li>CAUSA_CONFIRMADA exige causa y evidencia; sin evidencia la causa es solo una hipótesis.</li>
 *   <li>Un incidente pertenece como máximo a un problema; para moverlo, primero se desvincula.</li>
 *   <li>No se vinculan incidentes a un problema cerrado.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ProblemaService {

    private final ProblemaRepository problemas;
    private final IncidenteRepository incidentes;
    private final IncidenteService incidenteService;
    private final CatalogoService catalogos;
    private final UsuarioRepository usuarios;

    public ProblemaService(ProblemaRepository problemas, IncidenteRepository incidentes,
                           IncidenteService incidenteService, CatalogoService catalogos, UsuarioRepository usuarios) {
        this.problemas = problemas;
        this.incidentes = incidentes;
        this.incidenteService = incidenteService;
        this.catalogos = catalogos;
        this.usuarios = usuarios;
    }

    /** Ordenados por incidentes vinculados (más primero) y luego por fecha de creación. */
    public List<ProblemaResumen> listar(EstadoProblema estado) {
        Map<Long, long[]> conteos = new HashMap<>();
        for (Object[] fila : incidentes.conteoPorProblema()) {
            conteos.put((Long) fila[0], new long[]{((Number) fila[1]).longValue(), ((Number) fila[2]).longValue()});
        }
        List<Problema> lista = estado == null ? problemas.findAllByOrderByFechaCreacionDesc()
                : problemas.findByEstadoOrderByFechaCreacionDesc(estado);
        return lista.stream().map(p -> {
                    long[] c = conteos.getOrDefault(p.getId(), new long[]{0, 0});
                    return new ProblemaResumen(p.getId(), p.getTitulo(), p.getEstado(),
                            p.getElemento() == null ? null : new Ref(p.getElemento().getId(), p.getElemento().getNombre()),
                            c[0], c[1], p.getFechaCreacion(), p.getFechaCierre());
                })
                .sorted((a, b) -> Long.compare(b.incidentes(), a.incidentes()))
                .toList();
    }

    public ProblemaDetalle obtener(Long id) {
        return detalle(entidad(id));
    }

    @Transactional
    public ProblemaDetalle crear(ProblemaRequest req) {
        Problema p = new Problema(usuarios.getReferenceById(SesionActual.cuenta().id()));
        aplicar(p, req);
        return detalle(problemas.save(p));
    }

    @Transactional
    public ProblemaDetalle actualizar(Long id, ProblemaRequest req) {
        Problema p = entidad(id);
        aplicar(p, req);
        if (p.getEstado() == EstadoProblema.CAUSA_CONFIRMADA && !p.causaRespaldada()) {
            throw new ReglaNegocioException(
                    "La causa está confirmada: no se pueden dejar vacías la causa ni la evidencia. Cambie antes el estado");
        }
        return detalle(p);
    }

    @Transactional
    public ProblemaDetalle cambiarEstado(Long id, EstadoProblema nuevo) {
        Problema p = entidad(id);
        if (nuevo == EstadoProblema.CAUSA_CONFIRMADA && !p.causaRespaldada()) {
            throw new ReglaNegocioException("Para confirmar la causa registre la causa y la evidencia que la respalda");
        }
        p.cambiarEstado(nuevo);
        return detalle(p);
    }

    @Transactional
    public ProblemaDetalle vincular(Long problemaId, Long incidenteId) {
        Problema p = entidad(problemaId);
        if (p.getEstado() == EstadoProblema.CERRADO) {
            throw new ReglaNegocioException("El problema está cerrado; reábralo para vincular incidentes");
        }
        Incidente i = incidenteService.entidadParaModificar(incidenteId);
        if (i.getProblema() != null && !i.getProblema().getId().equals(problemaId)) {
            throw new ReglaNegocioException("El incidente #" + incidenteId + " ya pertenece al problema #"
                    + i.getProblema().getId() + "; desvincúlelo primero");
        }
        i.setProblema(p);
        return detalle(p);
    }

    @Transactional
    public ProblemaDetalle desvincular(Long problemaId, Long incidenteId) {
        Problema p = entidad(problemaId);
        Incidente i = incidenteService.entidadParaModificar(incidenteId);
        if (i.getProblema() == null || !i.getProblema().getId().equals(problemaId)) {
            throw new ReglaNegocioException("El incidente #" + incidenteId + " no pertenece a este problema");
        }
        i.setProblema(null);
        return detalle(p);
    }

    private Problema entidad(Long id) {
        return problemas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Problema", id));
    }

    /**
     * Hibernate vacía los cambios pendientes (FlushMode AUTO) antes de esta consulta, así que el
     * detalle ya refleja un vínculo recién creado o eliminado en la misma transacción.
     */
    private ProblemaDetalle detalle(Problema p) {
        return ProblemaDetalle.de(p, incidentes.findByProblemaIdOrderByFechaCreacionDesc(p.getId()));
    }

    private void aplicar(Problema p, ProblemaRequest req) {
        p.setTitulo(req.titulo().trim());
        p.setDescripcion(req.descripcion().trim());
        p.setElemento(elemento(p, req.elementoId()));
        p.setCausa(texto(req.causa()));
        p.setEvidenciaCausa(texto(req.evidenciaCausa()));
        p.setSolucionTemporal(texto(req.solucionTemporal()));
        p.setSolucionDefinitiva(texto(req.solucionDefinitiva()));
    }

    /** Igual que en incidentes: solo elementos activos, salvo el que ya tenía. */
    private ElementoTi elemento(Problema p, Long elementoId) {
        if (elementoId == null) {
            return null;
        }
        if (p.getElemento() != null && p.getElemento().getId().equals(elementoId)) {
            return p.getElemento();
        }
        ElementoTi e = catalogos.elemento(elementoId);
        if (!e.isActivo()) {
            throw new ReglaNegocioException("El elemento " + e.getNombre() + " está inactivo y no puede asignarse");
        }
        return e;
    }

    private static String texto(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
