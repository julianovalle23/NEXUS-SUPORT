package com.nexus.support.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Exige un token compartido en una cabecera. Si el token no está configurado, rechaza todas las
 * solicitudes protegidas (falla cerrado). No identifica personas: solo verifica que quien llama
 * conoce el secreto.
 */
public class TokenInterceptor implements HandlerInterceptor {

    private static final Set<String> LECTURA = Set.of("GET", "HEAD", "OPTIONS");

    private final String header;
    private final byte[] esperado;
    private final boolean soloEscritura;
    private final String mensaje;

    /**
     * @param soloEscritura si es true, las solicitudes GET/HEAD/OPTIONS no requieren el token
     */
    public TokenInterceptor(String header, String token, boolean soloEscritura, String mensaje) {
        this.header = header;
        this.esperado = (token == null || token.isBlank()) ? null : token.getBytes(StandardCharsets.UTF_8);
        this.soloEscritura = soloEscritura;
        this.mensaje = mensaje;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (soloEscritura && LECTURA.contains(request.getMethod())) return true;
        String recibido = request.getHeader(header);
        boolean valido = esperado != null && recibido != null
                && MessageDigest.isEqual(esperado, recibido.getBytes(StandardCharsets.UTF_8));
        if (!valido) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"estado\":401,\"mensaje\":\"" + mensaje + "\",\"cabecera\":\"" + header + "\"}");
            return false;
        }
        return true;
    }
}
