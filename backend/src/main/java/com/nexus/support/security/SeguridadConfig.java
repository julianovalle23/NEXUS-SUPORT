package com.nexus.support.security;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Inicio de sesión con cuentas de la tabla usuario (contraseñas BCrypt) y sesión HTTP.
 * Las escrituras exigen el token CSRF (cookie XSRF-TOKEN reenviada en la cabecera X-XSRF-TOKEN).
 * Los endpoints /api/internal/** (n8n) no usan sesión: los protege el token interno (TokenInterceptor).
 */
@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    private static final String[] PERSONAL = {"TECNICO", "ADMIN"};

    @Bean
    SecurityFilterChain seguridad(HttpSecurity http, CsrfTokenRepository csrf, SessionRegistry sesiones,
                                  SecurityContextRepository contexto) throws Exception {
        http
            .csrf(c -> c.csrfTokenRepository(csrf)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .ignoringRequestMatchers("/api/internal/**"))
            .cors(Customizer.withDefaults())
            .securityContext(s -> s.securityContextRepository(contexto))
            .sessionManagement(s -> s.maximumSessions(-1).sessionRegistry(sesiones)
                    .expiredSessionStrategy(e -> json(e.getResponse(), 401,
                            "La sesión se cerró porque la cuenta cambió; inicie sesión de nuevo")))
            .httpBasic(h -> h.disable())
            .formLogin(f -> f.disable())
            .logout(l -> l.logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_NO_CONTENT)))
            .exceptionHandling(e -> e
                    .authenticationEntryPoint((req, res, ex) -> json(res, 401, "Debe iniciar sesión"))
                    .accessDeniedHandler((req, res, ex) -> json(res, 403, "Su perfil no tiene permiso para esta acción")))
            .authorizeHttpRequests(a -> a
                    .requestMatchers("/api/internal/**").permitAll()
                    .requestMatchers("/api/auth/sesion", "/api/auth/login").permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").hasRole("ADMIN")
                    .requestMatchers("/api/usuarios/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/articulos/**").hasAnyRole(PERSONAL)
                    .requestMatchers("/api/articulos/**").hasRole("ADMIN")
                    .requestMatchers("/api/importaciones/**").hasRole("ADMIN")
                    .requestMatchers("/api/analitica/**", "/api/consultas/**", "/api/respuestas/**").hasAnyRole(PERSONAL)
                    .requestMatchers("/api/problemas/**").hasAnyRole(PERSONAL)
                    .requestMatchers(HttpMethod.POST, "/api/alertas-sla/revisar").hasRole("ADMIN")
                    .requestMatchers("/api/alertas-sla/**").hasAnyRole(PERSONAL)
                    .requestMatchers("/api/incidentes/*/revision", "/api/revisiones/**").hasAnyRole(PERSONAL)
                    .requestMatchers("/api/incidentes/*/asistencia", "/api/incidentes/*/consultas").hasAnyRole(PERSONAL)
                    // Reapertura excepcional de un incidente cerrado: solo administración (el servicio lo vuelve a comprobar).
                    .requestMatchers(HttpMethod.POST, "/api/incidentes/*/reapertura").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/incidentes/*").hasAnyRole(PERSONAL)
                    .requestMatchers(HttpMethod.PATCH, "/api/incidentes/*/estado").hasAnyRole(PERSONAL)
                    // Listar, ver y registrar incidentes: cualquier cuenta; el servicio limita al solicitante a los suyos.
                    .requestMatchers("/api/**").authenticated()
                    // Páginas y archivos estáticos: no contienen datos; cada página verifica la sesión al cargar.
                    .anyRequest().permitAll());
        return http.build();
    }

    private static void json(HttpServletResponse res, int estado, String mensaje) throws IOException {
        res.setStatus(estado);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"estado\":" + estado + ",\"mensaje\":\"" + mensaje + "\"}");
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(CuentaService cuentas, PasswordEncoder encoder) {
        DaoAuthenticationProvider proveedor = new DaoAuthenticationProvider(cuentas);
        proveedor.setPasswordEncoder(encoder);
        return new ProviderManager(proveedor);
    }

    /** Cookie legible por JavaScript para que el frontend reenvíe el token en la cabecera X-XSRF-TOKEN. */
    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Permite que el registro de sesiones se entere de las sesiones que terminan. */
    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
