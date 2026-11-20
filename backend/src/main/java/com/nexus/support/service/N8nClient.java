package com.nexus.support.service;

import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final RestClient httpIndexacion;
    private final NexusProperties props;

    public N8nClient(@Qualifier("n8nRestClient") RestClient n8nRestClient,
                     @Qualifier("n8nIndexacionRestClient") RestClient n8nIndexacionRestClient,
                     NexusProperties props) {
        this.http = n8nRestClient;
        this.httpIndexacion = n8nIndexacionRestClient;
        this.props = props;
    }

    /**
     * Ejecuta el flujo RAG.
     *
     * @throws N8nException si n8n no responde, hay timeout o la respuesta no es interpretable
     */
    public RagResultado consultarRag(RagSolicitud solicitud) {
        String correlacion = idSeguro(solicitud.correlationId()) == null ? UUID.randomUUID().toString()
                : solicitud.correlationId();
        long inicio = System.nanoTime();
        try {
            RagResultado r = http.post()
                    .uri(props.n8n().ragUrl())
                    .header(HEADER_CORRELACION, correlacion)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(solicitud)
                    .retrieve()
                    .body(RagResultado.class);
            if (r == null || r.estado() == null) {
                throw new N8nException("n8n devolvió una respuesta vacía o sin estado");
            }
            // Solo metadatos: nunca el texto del incidente ni de la respuesta
            log.info("n8n RAG correlationId={} ejecucion={} consulta={} estado={} etapa={} fuentes={} duracionMs={}",
                    correlacion, idSeguro(r.ejecucionId()), solicitud.consultaId(), r.estado(), r.etapa(),
                    r.fuentes() == null ? 0 : r.fuentes().size(), milisegundos(inicio));
            avisarSiNoCoincide(correlacion, r.correlationId());
            return r;
        } catch (RestClientException e) {
            log.warn("n8n RAG correlationId={} consulta={} fallo tras {} ms: {}", correlacion, solicitud.consultaId(),
                    milisegundos(inicio), e.getMessage());
            throw new N8nException("No fue posible comunicarse con el servicio de asistencia (n8n): " + e.getMessage(), e);
        }
    }

    /** Ejecuta el flujo de indexación y devuelve el resumen que genera n8n. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> indexar(boolean forzar) {
        String correlacion = UUID.randomUUID().toString();
        long inicio = System.nanoTime();
        try {
            Map<String, Object> r = httpIndexacion.post()
                    .uri(props.n8n().indexacionUrl())
                    .header(HEADER_CORRELACION, correlacion)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("forzar", forzar))
                    .retrieve()
                    .body(Map.class);
            if (r == null) {
                throw new N8nException("n8n devolvió una respuesta vacía en la indexación");
            }
            Map<String, Object> resultado = new LinkedHashMap<>(r);
            Object eco = resultado.putIfAbsent("correlationId", correlacion);
            log.info("n8n indexación correlationId={} ejecucion={} estado={} etapa={} resumen={} duracionMs={}",
                    correlacion, idSeguro(resultado.get("ejecucionId")), resultado.get("estado"),
                    resultado.get("etapa"), resultado.get("resumen"), milisegundos(inicio));
            avisarSiNoCoincide(correlacion, eco);
            return resultado;
        } catch (RestClientException e) {
            log.warn("n8n indexación correlationId={} fallo tras {} ms: {}", correlacion, milisegundos(inicio),
                    e.getMessage());
            throw new N8nException("No fue posible ejecutar la indexación en n8n: " + e.getMessage(), e);
        }
    }

    /** Cabecera con la que se sigue cada solicitud de Spring Boot en la ejecución de n8n y en su respuesta. */
    public static final String HEADER_CORRELACION = "X-Correlation-Id";

    private static final java.util.regex.Pattern ID_SEGURO = java.util.regex.Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /** Identificador recibido de n8n o del cliente, solo si tiene un formato seguro para guardar y registrar. */
    static String idSeguro(Object valor) {
        String v = valor == null ? null : String.valueOf(valor);
        return v != null && ID_SEGURO.matcher(v).matches() ? v : null;
    }

    private static void avisarSiNoCoincide(String enviado, Object recibido) {
        if (recibido != null && !enviado.equals(String.valueOf(recibido))) {
            log.warn("n8n devolvió correlationId {} para la solicitud {}", idSeguro(recibido), enviado);
        }
    }

    private static long milisegundos(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
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
