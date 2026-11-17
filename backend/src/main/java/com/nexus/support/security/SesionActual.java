package com.nexus.support.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nexus.support.domain.Rol;

/** Acceso a la cuenta de la petición en curso. */
public final class SesionActual {

    private SesionActual() {
    }

    public static CuentaUsuario cuenta() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CuentaUsuario c) {
            return c;
        }
        throw new AccessDeniedException("Debe iniciar sesión");
    }

    public static boolean esSolicitante() {
        return cuenta().rol() == Rol.SOLICITANTE;
    }
}
