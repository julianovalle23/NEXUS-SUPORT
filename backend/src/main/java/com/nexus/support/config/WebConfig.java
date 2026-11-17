package com.nexus.support.config;

import java.time.Duration;

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

    /** Cliente HTTP hacia n8n con timeouts explícitos para no bloquear hilos indefinidamente. */
    @Bean
    RestClient n8nRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(props.rag().timeoutSegundos()));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Bean
    OpenAPI nexusOpenApi() {
        return new OpenAPI().info(new Info()
                .title("NEXUS Support API")
                .version("1.0.0")
                .description("Gestión de incidentes, base de conocimiento, asistencia RAG y análisis de datos."));
    }
}
