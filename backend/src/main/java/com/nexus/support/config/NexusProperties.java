package com.nexus.support.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de la aplicación leída desde variables de entorno (ver .env.example).
 */
@ConfigurationProperties(prefix = "nexus")
public record NexusProperties(N8n n8n, String internalToken, Rag rag, Cors cors) {

    public record N8n(String webhookBaseUrl, String ragPath, String indexacionPath) {
        public String ragUrl() {
            return webhookBaseUrl + ragPath;
        }

        public String indexacionUrl() {
            return webhookBaseUrl + indexacionPath;
        }
    }

    /**
     * @param umbralSimilitud similitud coseno mínima para aceptar una fuente
     * @param topK            número máximo de artículos recuperados (3 a 5)
     * @param minResultados   fuentes mínimas para generar una respuesta
     * @param timeoutSegundos tiempo máximo de espera de n8n
     * @param permitirSimulacionFallos habilita el parámetro simularFalloEmbeddings (solo pruebas)
     */
    public record Rag(double umbralSimilitud, int topK, int minResultados, int timeoutSegundos,
                      boolean permitirSimulacionFallos) {
    }

    public record Cors(List<String> origenes) {
    }
}
