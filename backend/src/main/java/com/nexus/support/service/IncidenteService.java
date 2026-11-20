package com.nexus.support.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.support.domain.ElementoTi;
import com.nexus.support.domain.EstadoIncidente;
import com.nexus.support.domain.EstadoSla;
import com.nexus.support.domain.Incidente;
import com.nexus.support.domain.IncidenteHistorial;
import com.nexus.support.domain.Rol;
import com.nexus.support.domain.Usuario;
import com.nexus.support.dto.AnaliticaDtos.PrimeraRespuesta;
import com.nexus.support.dto.IncidenteDtos.HistorialDto;
import com.nexus.support.dto.IncidenteDtos.IncidenteRequest;
import com.nexus.support.dto.IncidenteDtos.IncidenteResponse;
import com.nexus.support.dto.PaginaDto;
import com.nexus.support.exception.RecursoNoEncontradoException;
import com.nexus.support.exception.ReglaNegocioException;
import com.nexus.support.repository.AnaliticaRepository;
import com.nexus.support.repository.IncidenteHistorialRepository;
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
    private final IncidenteHistorialRepository historial;
    private final AnaliticaRepository analitica;

    public IncidenteService(IncidenteRepository incidentes, CatalogoService catalogos, UsuarioRepository usuarios,
                            IncidenteHistorialRepository historial, AnaliticaRepository analitica) {
        this.incidentes = incidentes;
        this.catalogos = catalogos;
        this.usuarios = usuarios;
        this.historial = historial;
        this.analitica = analitica;
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
        return IncidenteResponse.de(visible(id));
    }

    /** A un solicitante, los incidentes ajenos se le presentan como inexistentes. */
    private Incidente visible(Long id) {
        Incidente i = entidad(id);
        if (SesionActual.esSolicitante() && (i.getCuentaSolicitante() == null
                || !i.getCuentaSolicitante().getId().equals(SesionActual.cuenta().id()))) {
            throw new RecursoNoEncontradoException("Incidente", id);
        }
        return i;
    }

    /** Historial cronológico; aplica la misma visibilidad que {@link #obtener(Long)}. */
    public List<HistorialDto> historial(Long id) {
        obtener(id);
        return historial.findByIncidenteIdOrderByFechaAscIdAsc(id).stream().map(HistorialDto::de).toList();
    }

    /** SLA de primera respuesta, calculado en SQL con la hora actual del servidor; misma visibilidad que obtener. */
    public PrimeraRespuesta primeraRespuesta(Long id) {
        obtener(id);
        return analitica.primeraRespuesta(id, LocalDateTime.now(), EstadoSla.FRACCION_ALERTA)
                .orElseThrow(() -> new RecursoNoEncontradoException("Incidente", id));
    }

    /** Uso interno de otros servicios que participan en la misma transacción (solo lectura). */
    Incidente entidad(Long id) {
        return incidentes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Incidente", id));
    }

    /**
     * Uso interno para modificar: bloquea la fila hasta el fin de la transacción, de modo que las
     * reglas se validan contra el estado vigente y no contra uno que otra petición está cambiando.
     */
    Incidente entidadParaModificar(Long id) {
        return incidentes.findParaModificar(id).orElseThrow(() -> new RecursoNoEncontradoException("Incidente", id));
    }

    @Transactional
    public IncidenteResponse crear(IncidenteRequest req) {
        return IncidenteResponse.de(registrar(req, null));
    }

    /**
     * Nuevo incidente que da seguimiento a uno cerrado. El original no se modifica: el vínculo
     * queda en el nuevo (seguimiento_de_id). Un solicitante solo puede hacerlo sobre los suyos.
     */
    @Transactional
    public IncidenteResponse registrarSeguimiento(Long originalId, IncidenteRequest req) {
        Incidente original = visible(originalId);
        if (!catalogos.esTerminal(original.getEstado().getId())) {
            throw new ReglaNegocioException("El incidente #" + originalId + " está en estado '"
                    + original.getEstado().getNombre() + "'; el seguimiento solo se registra sobre incidentes "
                    + "cerrados. Mientras siga en curso, actualícelo directamente");
        }
        return IncidenteResponse.de(registrar(req, original));
    }

    private Incidente registrar(IncidenteRequest req, Incidente seguimientoDe) {
        Incidente i = new Incidente();
        i.setSeguimientoDe(seguimientoDe);
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
            EstadoIncidente inicial = catalogos.estado(req.estadoId() == null ? ESTADO_ABIERTO : req.estadoId());
            if (catalogos.esTerminal(inicial.getId())) {
                throw new ReglaNegocioException("Un incidente no puede registrarse directamente en estado '"
                        + inicial.getNombre() + "'");
            }
            i.cambiarEstado(inicial);
        }
        Incidente guardado = incidentes.save(i);
        historial.save(IncidenteHistorial.creacion(guardado, actor()));
        return guardado;
    }

    @Transactional
    public IncidenteResponse actualizar(Long id, IncidenteRequest req) {
        Incidente i = entidadParaModificar(id);
        exigirNoTerminal(i);
        EstadoIncidente destino = req.estadoId() == null || req.estadoId().equals(i.getEstado().getId())
                ? null : catalogos.estado(req.estadoId());
        if (destino != null) {
            exigirTransicion(i, destino);
        }
        Situacion antes = Situacion.de(i);
        aplicar(i, req);
        if (destino != null) {
            i.cambiarEstado(destino);
        }
        registrarSiCambio(i, antes);
        return IncidenteResponse.de(i);
    }

    @Transactional
    public IncidenteResponse cambiarEstado(Long id, Long estadoId) {
        Incidente i = entidadParaModificar(id);
        EstadoIncidente destino = catalogos.estado(estadoId);
        if (destino.getId().equals(i.getEstado().getId())) {
            return IncidenteResponse.de(i); // sin cambio: no genera historial
        }
        exigirNoTerminal(i);
        exigirTransicion(i, destino);
        Situacion antes = Situacion.de(i);
        i.cambiarEstado(destino);
        registrarSiCambio(i, antes);
        return IncidenteResponse.de(i);
    }

    /**
     * Excepción controlada a la regla de estado terminal: solo ADMIN (también lo exige SeguridadConfig),
     * solo desde un estado terminal hacia uno no final, con motivo obligatorio. Queda una fila REAPERTURA
     * en el historial con quién, cuándo y por qué.
     */
    @Transactional
    public IncidenteResponse reabrir(Long id, Long estadoId, String motivo) {
        if (SesionActual.cuenta().rol() != Rol.ADMIN) {
            throw new AccessDeniedException("Solo un administrador puede reabrir un incidente cerrado");
        }
        String texto = motivo == null ? "" : motivo.trim();
        if (texto.length() < 10) {
            throw new ReglaNegocioException("El motivo de la reapertura es obligatorio (mínimo 10 caracteres)");
        }
        Incidente i = entidadParaModificar(id);
        if (!catalogos.esTerminal(i.getEstado().getId())) {
            throw new ReglaNegocioException("El incidente #" + id + " está en estado '" + i.getEstado().getNombre()
                    + "'; la reapertura excepcional solo aplica a incidentes cerrados");
        }
        EstadoIncidente destino = catalogos.estado(estadoId);
        if (destino.isEsFinal() || catalogos.esTerminal(destino.getId())) {
            throw new ReglaNegocioException("Un incidente reabierto debe quedar en un estado no final; '"
                    + destino.getNombre() + "' no lo es");
        }
        i.cambiarEstado(destino);
        historial.save(IncidenteHistorial.reapertura(i, actor(), texto));
        return IncidenteResponse.de(i);
    }

    private void exigirNoTerminal(Incidente i) {
        if (catalogos.esTerminal(i.getEstado().getId())) {
            throw new ReglaNegocioException("El incidente #" + i.getId() + " está en estado '"
                    + i.getEstado().getNombre() + "' y no admite cambios. Si el caso continúa, registre un "
                    + "incidente de seguimiento vinculado al original");
        }
    }

    private void exigirTransicion(Incidente i, EstadoIncidente destino) {
        if (!catalogos.transicionPermitida(i.getEstado().getId(), destino.getId())) {
            throw new ReglaNegocioException("No se permite pasar el incidente #" + i.getId() + " de '"
                    + i.getEstado().getNombre() + "' a '" + destino.getNombre() + "'");
        }
    }

    /** Lo que el historial registra; título, descripción o categoría no generan fila. */
    private record Situacion(Long estadoId, Long prioridadId, Long responsableId) {
        static Situacion de(Incidente i) {
            return new Situacion(i.getEstado().getId(), i.getPrioridad().getId(), i.getUsuario().getId());
        }
    }

    private void registrarSiCambio(Incidente i, Situacion antes) {
        if (!Objects.equals(antes, Situacion.de(i))) {
            historial.save(IncidenteHistorial.actualizacion(i, actor()));
        }
    }

    /** Referencia (sin consulta) a la cuenta que hace la petición. */
    private Usuario actor() {
        return usuarios.getReferenceById(SesionActual.cuenta().id());
    }

    private void datosBasicos(Incidente i, IncidenteRequest req) {
        i.setTitulo(req.titulo().trim());
        i.setDescripcion(req.descripcion().trim());
        i.setCategoria(catalogos.categoria(req.categoriaId()));
        i.setPrioridad(catalogos.prioridad(req.prioridadId()));
        i.setElemento(elemento(i, req.elementoId()));
        i.setMensajeError(textoOpcional(req.mensajeError()));
        i.setPasosIntentados(textoOpcional(req.pasosIntentados()));
    }

    private static String textoOpcional(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Como con el responsable: solo se asigna un elemento activo, pero el actual se conserva aunque se desactive. */
    private ElementoTi elemento(Incidente i, Long elementoId) {
        if (elementoId == null) {
            return null;
        }
        if (i.getElemento() != null && i.getElemento().getId().equals(elementoId)) {
            return i.getElemento();
        }
        ElementoTi e = catalogos.elemento(elementoId);
        if (!e.isActivo()) {
            throw new ReglaNegocioException("El elemento " + e.getNombre() + " está inactivo y no puede asignarse");
        }
        return e;
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
