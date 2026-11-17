package com.nexus.support.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

import com.nexus.support.dto.UsuarioDtos.CambioClaveRequest;
import com.nexus.support.dto.UsuarioDtos.CuentaDto;
import com.nexus.support.dto.UsuarioDtos.LoginRequest;
import com.nexus.support.dto.UsuarioDtos.SesionDto;
import com.nexus.support.security.CuentaUsuario;
import com.nexus.support.service.UsuarioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** Inicio y estado de sesión. El cierre de sesión es POST /api/auth/logout (filtro de Spring Security). */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Sesión", description = "Inicio de sesión con cuentas de la base de datos")
public class AuthController {

    private final AuthenticationManager autenticacion;
    private final SecurityContextRepository contexto;
    private final SessionRegistry sesiones;
    private final CsrfTokenRepository csrf;
    private final UsuarioService usuarios;

    public AuthController(AuthenticationManager autenticacion, SecurityContextRepository contexto,
                          SessionRegistry sesiones, CsrfTokenRepository csrf, UsuarioService usuarios) {
        this.autenticacion = autenticacion;
        this.contexto = contexto;
        this.sesiones = sesiones;
        this.csrf = csrf;
        this.usuarios = usuarios;
    }

    @GetMapping("/sesion")
    @Operation(summary = "Cuenta de la sesión actual; también entrega la cookie XSRF-TOKEN")
    public SesionDto sesion(CsrfToken token) {
        token.getToken(); // fuerza la emisión de la cookie CSRF
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CuentaUsuario c
                ? new SesionDto(true, CuentaDto.de(c))
                : new SesionDto(false, null);
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión con correo y contraseña")
    public SesionDto login(@Valid @RequestBody LoginRequest req, HttpServletRequest request,
                           HttpServletResponse response) {
        Authentication auth;
        try {
            auth = autenticacion.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(req.correo().trim(), req.clave()));
        } catch (AuthenticationException e) {
            // Mismo mensaje para correo inexistente, contraseña errónea o cuenta inactiva.
            throw new BadCredentialsException("Correo o contraseña incorrectos, o la cuenta está inactiva");
        }
        if (request.getSession(false) != null) {
            request.changeSessionId(); // evita la fijación de sesión
        }
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        contexto.saveContext(ctx, request, response);
        sesiones.registerNewSession(request.getSession().getId(), auth.getPrincipal());
        // Token CSRF nuevo después de autenticarse
        csrf.saveToken(csrf.generateToken(request), request, response);
        return new SesionDto(true, CuentaDto.de((CuentaUsuario) auth.getPrincipal()));
    }

    @PutMapping("/clave")
    @Operation(summary = "Cambiar la contraseña propia")
    public ResponseEntity<Void> cambiarClave(@Valid @RequestBody CambioClaveRequest req) {
        usuarios.cambiarClavePropia(req.actual(), req.nueva());
        return ResponseEntity.noContent().build();
    }
}
