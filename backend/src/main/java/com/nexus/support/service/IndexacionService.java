package com.nexus.support.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Mantiene sincronizado Qdrant con la base de conocimiento invocando el Flujo 1 de n8n.
 */
@Service
public class IndexacionService {

    private static final Logger log = LoggerFactory.getLogger(IndexacionService.class);
    private static final int INTENTOS_INICIO = 12;
    private static final long ESPERA_MS = 10_000;

    /** Evento publicado por ArticuloService cuando cambia el contenido o el estado de un artículo. */
    public record ArticuloModificado(Long articuloId) {
    }

    private final N8nClient n8n;

    public IndexacionService(N8nClient n8n) {
        this.n8n = n8n;
    }

    /** Indexación manual (síncrona) para el endpoint de administración. */
    public Map<String, Object> indexarAhora(boolean forzar) {
        return n8n.indexar(forzar);
    }

    /** Se ejecuta solo si la transacción del artículo se confirmó. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alModificarArticulo(ArticuloModificado evento) {
        try {
            log.info("Artículo {} modificado; sincronizando Qdrant: {}", evento.articuloId(), n8n.indexar(false));
        } catch (N8nClient.N8nException e) {
            // La validación relacional en el flujo RAG impide usar artículos desactivados aunque esto falle.
            log.warn("No se pudo reindexar tras modificar el artículo {}: {}", evento.articuloId(), e.getMessage());
        }
    }

    /** Al arrancar, n8n puede no estar listo todavía: se reintenta varias veces. */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void indexacionInicial() {
        for (int intento = 1; intento <= INTENTOS_INICIO; intento++) {
            try {
                log.info("Indexación inicial completada: {}", n8n.indexar(false));
                return;
            } catch (N8nClient.N8nException e) {
                log.info("Indexación inicial pendiente (intento {}/{}): {}", intento, INTENTOS_INICIO, e.getMessage());
                try {
                    Thread.sleep(ESPERA_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.warn("No fue posible completar la indexación inicial; use POST /api/articulos/reindexar");
    }
}
