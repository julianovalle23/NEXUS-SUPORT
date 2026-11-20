package com.nexus.support.config;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final String HEADER_INTERNO = "X-Internal-Token";

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    private final NexusProperties props;

    public WebConfig(NexusProperties props) {
        this.props = props;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Endpoints que solo usa n8n.
        registry.addInterceptor(new TokenInterceptor(HEADER_INTERNO, props.internalToken(), false,
                "Token interno inválido o no configurado")).addPathPatterns("/api/internal/**");
        // El resto de la API se protege con inicio de sesión y perfiles (security/SeguridadConfig).
    }

    /** CORS solo para abrir el frontend desde otro origen; normalmente Spring Boot lo sirve en el mismo origen. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(props.cors().origenes().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE");
    }

    /** Cliente HTTP del flujo RAG: espera corta, porque el usuario está aguardando la respuesta. */
    @Bean
    RestClient n8nRestClient() {
        return clienteN8n(props.rag().timeoutSegundos());
    }

    /**
     * Cliente HTTP del flujo de indexación: una reindexación completa puede durar minutos, así que
     * su espera es independiente de la del RAG y supera el executionTimeout del flujo en n8n.
     */
    @Bean
    RestClient n8nIndexacionRestClient() {
        return clienteN8n(props.n8n().indexacionTimeoutSegundos());
    }

    /**
     * Timeouts explícitos para no bloquear hilos indefinidamente. Los webhooks de n8n exigen el mismo
     * token interno que protege /api/internal/** (credencial Header Auth «NEXUS - Token interno»);
     * sin él n8n responde 403. El valor nunca se registra en logs.
     */
    private RestClient clienteN8n(int timeoutSegundos) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSegundos));
        RestClient.Builder builder = RestClient.builder().requestFactory(factory);
        String token = props.internalToken();
        if (token == null || token.isBlank()) {
            log.warn("INTERNAL_API_TOKEN no está configurado: n8n rechazará las llamadas de indexación y asistencia (403)");
        } else {
            builder.defaultHeader(HEADER_INTERNO, token);
        }
        return builder.build();
    }

    @Bean
    OpenAPI nexusOpenApi() {
        return new OpenAPI().info(new Info()
                .title("NEXUS Support API")
                .version("1.0.0")
                .description("Gestión de incidentes, base de conocimiento, asistencia RAG y análisis de datos."));
    }
}
