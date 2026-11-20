package com.nexus.support.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Mantiene sincronizado Qdrant con la base de conocimiento invocando el flujo de indexación de n8n.
 *
 * <p>Solo se ejecuta una indexación a la vez (por instancia del backend). Las solicitudes que llegan
 * mientras otra está en curso no lanzan una segunda ejecución: se marcan como pendientes y se atienden
 * con una única ejecución adicional al terminar la actual («coalescencia»). Así diez artículos editados
 * seguidos no disparan diez indexaciones concurrentes contra n8n, Qdrant y el proveedor de embeddings.
 * Si alguna de las solicitudes pendientes pedía {@code forzar}, la ejecución adicional también fuerza.
 *
 * <p>Reintentos: si n8n responde con un fallo transitorio (resultado parcial o {@code reintentable: true}), se repite
 * hasta {@code nexus.indexacion.reintentos} veces con espera creciente (base, 2×base, 4×base…). La indexación es
 * idempotente (upsert por id y borrado solo tras confirmar todas las escrituras) y el reintento nunca fuerza: el hash
 * de contenido omite lo que ya quedó indexado, así que solo se vuelven a vectorizar los artículos que fallaron. Sin
 * respuesta de n8n (SIN_CONEXION, incluido el timeout) no se reintenta, porque la ejecución anterior podría seguir viva.
 */
@Service
public class IndexacionService {

    private static final Logger log = LoggerFactory.getLogger(IndexacionService.class);
    private static final int INTENTOS_INICIO = 12;
    private static final long ESPERA_MS = 10_000;

    /** Evento publicado por ArticuloService cuando cambia el contenido o el estado de un artículo. */
    public record ArticuloModificado(Long articuloId) {
    }

    /** Origen de una solicitud de indexación (solo informativo). */
    public enum Origen { ARRANQUE, MANUAL, ARTICULO }

    /**
     * Situación de la indexación.
     * <ul>
     *   <li>NUNCA: no se ha ejecutado desde que arrancó el backend.</li>
     *   <li>EN_CURSO: hay una ejecución en marcha.</li>
     *   <li>FINALIZADA: n8n respondió; el resultado real (OK, parcial o error por etapa) está en {@code resultado}.</li>
     *   <li>SIN_CONEXION: n8n no respondió, devolvió un error HTTP o superó la espera.</li>
     *   <li>ERROR: fallo inesperado en el backend.</li>
     * </ul>
     */
    public enum Situacion { NUNCA, EN_CURSO, FINALIZADA, SIN_CONEXION, ERROR }

    public record EstadoIndexacion(Situacion estado, Origen origen, boolean forzar, LocalDateTime inicio,
                                   LocalDateTime fin, boolean pendiente, Map<String, Object> resultado,
                                   String mensaje) {

        static EstadoIndexacion nunca() {
            return new EstadoIndexacion(Situacion.NUNCA, null, false, null, null, false, null, null);
        }

        static EstadoIndexacion enCurso(Origen origen, boolean forzar) {
            return new EstadoIndexacion(Situacion.EN_CURSO, origen, forzar, LocalDateTime.now(), null, false,
                    null, null);
        }

        EstadoIndexacion conMensaje(String nuevo) {
            return new EstadoIndexacion(estado, origen, forzar, inicio, fin, pendiente, resultado, nuevo);
        }

        EstadoIndexacion conPendiente() {
            return new EstadoIndexacion(estado, origen, forzar, inicio, fin, true, resultado, mensaje);
        }

        EstadoIndexacion terminar(Situacion situacion, Map<String, Object> resultado, String mensaje) {
            return new EstadoIndexacion(situacion, origen, forzar, inicio, LocalDateTime.now(), false, resultado,
                    mensaje);
        }
    }

    /** Máximo de reintentos admitido y tope de cada espera, para que una mala configuración no bloquee el turno. */
    static final int MAX_REINTENTOS = 5;
    static final long MAX_ESPERA_MS = 600_000;

    private final N8nClient n8n;
    private final Executor executor;
    private final int reintentos;
    private final long esperaBaseMs;
    /** Espera entre reintentos; devuelve false si el hilo se interrumpió. Reemplazable en pruebas. */
    Esperador esperador = ms -> {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    };

    @FunctionalInterface
    interface Esperador {
        boolean esperar(long ms);
    }

    // Estado protegido por el monitor de esta instancia.
    private boolean enCurso;
    private boolean pendiente;
    private boolean pendienteForzar;
    private Origen pendienteOrigen;
    private EstadoIndexacion estado = EstadoIndexacion.nunca();

    @Autowired
    public IndexacionService(N8nClient n8n, @Qualifier("applicationTaskExecutor") Executor executor,
                             @Value("${nexus.indexacion.reintentos:2}") int reintentos,
                             @Value("${nexus.indexacion.espera-reintento-segundos:30}") long esperaReintentoSegundos) {
        this.n8n = n8n;
        this.executor = executor;
        this.reintentos = Math.max(0, Math.min(reintentos, MAX_REINTENTOS));
        this.esperaBaseMs = Math.max(1_000, Math.min(esperaReintentoSegundos * 1_000, MAX_ESPERA_MS));
    }

    /** Sin reintentos. */
    IndexacionService(N8nClient n8n, Executor executor) {
        this(n8n, executor, 0, 30);
    }

    public synchronized EstadoIndexacion estado() {
        return estado;
    }

    /**
     * Solicita una indexación sin esperar a que termine. Si ya hay una en curso, la solicitud queda
     * pendiente y se atiende al finalizar la actual.
     *
     * @return el estado inmediatamente después de registrar la solicitud
     */
    public EstadoIndexacion solicitar(boolean forzar, Origen origen) {
        if (!reservar(forzar, origen)) {
            return estado();
        }
        try {
            executor.execute(() -> ciclo(forzar, origen));
        } catch (RejectedExecutionException e) {
            synchronized (this) {
                enCurso = false;
                pendiente = false;
                pendienteForzar = false;
                estado = estado.terminar(Situacion.ERROR, null, "No hay hilos disponibles para indexar; reintente");
            }
        }
        return estado();
    }

    /** Se ejecuta solo si la transacción del artículo se confirmó; no bloquea al hilo que guardó. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alModificarArticulo(ArticuloModificado evento) {
        // La validación relacional en el flujo RAG impide usar artículos desactivados aunque esto falle.
        EstadoIndexacion e = solicitar(false, Origen.ARTICULO);
        log.info("Artículo {} modificado; indexación {}{}", evento.articuloId(), e.estado(),
                e.pendiente() ? " (pendiente tras la actual)" : "");
    }

    /** Al arrancar, n8n puede no estar listo todavía: se reintenta mientras no haya conexión. */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void indexacionInicial() {
        for (int intento = 1; intento <= INTENTOS_INICIO; intento++) {
            if (!reservar(false, Origen.ARRANQUE)) {
                log.info("Indexación inicial omitida: ya hay una indexación en curso");
                return;
            }
            ciclo(false, Origen.ARRANQUE);
            EstadoIndexacion e = estado();
            if (e.estado() != Situacion.SIN_CONEXION) {
                log.info("Indexación inicial {}: {}", e.estado(), e.resultado());
                return;
            }
            log.info("Indexación inicial pendiente (intento {}/{}): {}", intento, INTENTOS_INICIO, e.mensaje());
            try {
                Thread.sleep(ESPERA_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("No fue posible completar la indexación inicial; use POST /api/articulos/reindexar");
    }

    /** Toma el turno de ejecución, o registra la solicitud como pendiente si otro lo tiene. */
    private synchronized boolean reservar(boolean forzar, Origen origen) {
        if (enCurso) {
            pendiente = true;
            pendienteForzar |= forzar;
            pendienteOrigen = origen;
            estado = estado.conPendiente();
            return false;
        }
        enCurso = true;
        estado = EstadoIndexacion.enCurso(origen, forzar);
        return true;
    }

    /** Ejecuta la indexación y, mientras queden solicitudes pendientes, una única ejecución más. */
    private void ciclo(boolean forzar, Origen origen) {
        boolean f = forzar;
        Origen o = origen;
        while (true) {
            EstadoIndexacion terminado = ejecutarConReintentos(f);
            synchronized (this) {
                if (!pendiente) {
                    estado = terminado;
                    enCurso = false;
                    return;
                }
                f = pendienteForzar;
                o = pendienteOrigen;
                pendiente = false;
                pendienteForzar = false;
                estado = EstadoIndexacion.enCurso(o, f);
            }
        }
    }

    private EstadoIndexacion ejecutarConReintentos(boolean forzar) {
        EstadoIndexacion r = ejecutar(forzar);
        for (int intento = 1; intento <= reintentos && reintentable(r); intento++) {
            long espera = Math.min(esperaBaseMs << (intento - 1), MAX_ESPERA_MS);
            String motivo = r.resultado().get("etapa") + "/" + r.resultado().getOrDefault("tipoError", "sin tipo");
            log.info("Indexación con fallo transitorio ({}); reintento {}/{} en {} s, sin forzar", motivo, intento,
                    reintentos, espera / 1_000);
            synchronized (this) {
                estado = estado.conMensaje("Reintento " + intento + " de " + reintentos + " en " + espera / 1_000
                        + " s tras " + motivo);
            }
            if (!esperador.esperar(espera)) {
                return r;
            }
            // Nunca forzar: así solo se vuelve a vectorizar lo que falló
            r = ejecutar(false);
        }
        return r;
    }

    /**
     * Fallo transitorio informado por n8n. Con {@code reintentable} (flujo con errores tipados) se usa ese valor; el
     * flujo anterior no lo envía y entonces solo se reintenta un resultado parcial.
     */
    static boolean reintentable(EstadoIndexacion e) {
        Map<String, Object> r = e.resultado();
        if (e.estado() != Situacion.FINALIZADA || r == null || !"ERROR".equals(r.get("estado"))) {
            return false;
        }
        Object marca = r.get("reintentable");
        return marca instanceof Boolean b ? b : "INDEXACION_PARCIAL".equals(r.get("etapa"));
    }

    private EstadoIndexacion ejecutar(boolean forzar) {
        EstadoIndexacion inicio = estado();
        try {
            return inicio.terminar(Situacion.FINALIZADA, n8n.indexar(forzar), null);
        } catch (N8nClient.N8nException e) {
            log.warn("Indexación sin respuesta válida de n8n: {}", e.getMessage());
            return inicio.terminar(Situacion.SIN_CONEXION, null, e.getMessage());
        } catch (RuntimeException e) {
            // Sin esto un fallo inesperado dejaría enCurso=true para siempre.
            log.error("Fallo inesperado durante la indexación", e);
            return inicio.terminar(Situacion.ERROR, null, "Fallo inesperado en el backend durante la indexación");
        }
    }
}
