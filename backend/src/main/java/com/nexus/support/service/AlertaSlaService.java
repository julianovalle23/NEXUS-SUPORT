package com.nexus.support.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.support.domain.AlertaSla;
import com.nexus.support.domain.EstadoSla;
import com.nexus.support.domain.Rol;
import com.nexus.support.dto.AlertaSlaDtos.AlertaSlaDto;
import com.nexus.support.dto.AlertaSlaDtos.AlertasSla;
import com.nexus.support.dto.AlertaSlaDtos.RevisionAlertas;
import com.nexus.support.exception.RecursoNoEncontradoException;
import com.nexus.support.exception.ReglaNegocioException;
import com.nexus.support.repository.AlertaSlaRepository;
import com.nexus.support.security.CuentaUsuario;
import com.nexus.support.security.SesionActual;

/**
 * Alertas y escalamiento de SLA, calculados con reglas sobre los datos (sin IA).
 * <ul>
 *   <li>AVISO: al incidente abierto le queda como máximo {@link EstadoSla#FRACCION_ALERTA} del plazo de
 *       resolución o de primera respuesta. Lo ve el técnico responsable (y los administradores).</li>
 *   <li>ESCALAMIENTO: el plazo venció. Lo ven el técnico responsable y los administradores, y solo un
 *       administrador puede darlo por atendido.</li>
 * </ul>
 * La revisión no cambia el incidente (ni prioridad, ni responsable): reasignar o repriorizar es una decisión
 * de una persona. Atender una alerta tampoco detiene el SLA.
 */
@Service
public class AlertaSlaService {

    private static final Logger log = LoggerFactory.getLogger(AlertaSlaService.class);
    static final int LIMITE_LISTA = 200;

    private final AlertaSlaRepository repo;

    public AlertaSlaService(AlertaSlaRepository repo) {
        this.repo = repo;
    }

    /** Revisión periódica (nexus.sla.revision-intervalo). Un fallo se registra y se reintenta en la siguiente. */
    @Scheduled(initialDelayString = "${nexus.sla.revision-retraso-inicial:PT1M}",
            fixedDelayString = "${nexus.sla.revision-intervalo:PT5M}")
    public void revisarProgramado() {
        try {
            RevisionAlertas r = revisar();
            if (r.generadas() > 0 || r.superadas() > 0) {
                log.info("Revisión de SLA: {} alertas nuevas, {} superadas", r.generadas(), r.superadas());
            }
        } catch (RuntimeException e) {
            log.warn("Falló la revisión de alertas de SLA: {}", e.getMessage());
        }
    }

    /** Primero cierra las alertas que ya no aplican y luego registra las nuevas, en una sola transacción. */
    @Transactional
    public RevisionAlertas revisar() {
        LocalDateTime ahora = LocalDateTime.now();
        int superadas = repo.superar(ahora, EstadoSla.FRACCION_ALERTA);
        int generadas = repo.generar(ahora, EstadoSla.FRACCION_ALERTA);
        return new RevisionAlertas(ahora, generadas, superadas);
    }

    /** Administradores: todas. Técnicos: las de los incidentes de los que hoy son responsables. */
    @Transactional(readOnly = true)
    public AlertasSla listar(AlertaSla.Estado estado) {
        Long responsable = filtroResponsable(SesionActual.cuenta());
        long[] pendientes = repo.contarPendientes(responsable);
        List<AlertaSlaDto> alertas = repo.listar(estado, responsable, LIMITE_LISTA);
        return new AlertasSla(pendientes[0], pendientes[1], alertas);
    }

    @Transactional
    public AlertaSlaDto atender(Long id, String nota) {
        CuentaUsuario cuenta = SesionActual.cuenta();
        AlertaSlaDto alerta = repo.buscar(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Alerta de SLA", id));
        Long responsable = filtroResponsable(cuenta);
        if (responsable != null && !responsable.equals(alerta.responsable().id())) {
            throw new RecursoNoEncontradoException("Alerta de SLA", id);
        }
        if (cuenta.rol() != Rol.ADMIN && alerta.nivel() == AlertaSla.Nivel.ESCALAMIENTO) {
            throw new AccessDeniedException("Solo un administrador puede dar por atendido un escalamiento");
        }
        String texto = nota == null || nota.isBlank() ? null : nota.trim();
        if (!repo.atender(id, cuenta.id(), texto, LocalDateTime.now())) {
            throw new ReglaNegocioException("La alerta ya no está pendiente");
        }
        return repo.buscar(id).orElseThrow();
    }

    private static Long filtroResponsable(CuentaUsuario cuenta) {
        if (cuenta.rol() == Rol.SOLICITANTE) {
            throw new AccessDeniedException("Las alertas de SLA son solo para el personal de soporte");
        }
        return cuenta.rol() == Rol.ADMIN ? null : cuenta.id();
    }
}
