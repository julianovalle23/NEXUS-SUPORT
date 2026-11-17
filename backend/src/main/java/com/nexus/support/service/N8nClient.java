package com.nexus.support.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.nexus.support.config.NexusProperties;
import com.nexus.support.dto.AsistenciaDtos.RagResultado;
import com.nexus.support.dto.AsistenciaDtos.RagSolicitud;

/** Único punto de comunicación con n8n. El frontend nunca llama a n8n directamente. */
@Component
public class N8nClient {

    private static final Logger log = LoggerFactory.getLogger(N8nClient.class);

    private final RestClient http;
    private final NexusProperties props;

    public N8nClient(RestClient n8nRestClient, NexusProperties props) {
        this.http = n8nRestClient;
        this.props = props;
    }

    /**
     * Ejecuta el flujo RAG.
     *
     * @throws N8nException si n8n no responde, hay timeout o la respuesta no es interpretable
     */
    public RagResultado consultarRag(RagSolicitud solicitud) {
        try {
            RagResultado r = http.post()
                    .uri(props.n8n().ragUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(solicitud)
                    .retrieve()
                    .body(RagResultado.class);
            if (r == null || r.estado() == null) {
                throw new N8nException("n8n devolvió una respuesta vacía o sin estado");
            }
            return r;
        } catch (RestClientException e) {
            log.warn("Fallo invocando el flujo RAG de n8n: {}", e.getMessage());
            throw new N8nException("No fue posible comunicarse con el servicio de asistencia (n8n): " + e.getMessage(), e);
        }
    }

    /** Ejecuta el flujo de indexación y devuelve el resumen que genera n8n. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> indexar(boolean forzar) {
        try {
            return http.post()
                    .uri(props.n8n().indexacionUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("forzar", forzar))
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            throw new N8nException("No fue posible ejecutar la indexación en n8n: " + e.getMessage(), e);
        }
    }

    public static class N8nException extends RuntimeException {
        public N8nException(String mensaje) {
            super(mensaje);
        }

        public N8nException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
