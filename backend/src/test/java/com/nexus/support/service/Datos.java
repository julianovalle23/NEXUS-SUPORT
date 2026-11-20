package com.nexus.support.service;

import java.time.LocalDateTime;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.nexus.support.domain.*;
import com.nexus.support.security.CuentaUsuario;

/** Datos de prueba en memoria (sin base) y sesión simulada para las pruebas de servicios. */
final class Datos {

    private Datos() {
    }

    static Usuario usuario(long id, String nombre, Rol rol) {
        Usuario u = new Usuario();
        ReflectionTestUtils.setField(u, "id", id);
        u.setNombre(nombre);
        u.setCorreo(nombre.toLowerCase().replace(' ', '.') + "@nexus.local");
        u.setRol(rol);
        u.setActivo(true);
        return u;
    }

    static EstadoIncidente estado(long id, String nombre, boolean esFinal) {
        EstadoIncidente e = new EstadoIncidente();
        ReflectionTestUtils.setField(e, "id", id);
        ReflectionTestUtils.setField(e, "nombre", nombre);
        ReflectionTestUtils.setField(e, "esFinal", esFinal);
        ReflectionTestUtils.setField(e, "orden", (short) id);
        return e;
    }

    static Prioridad prioridad(long id, String nombre, int nivel, int slaHoras) {
        Prioridad p = new Prioridad();
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "nombre", nombre);
        ReflectionTestUtils.setField(p, "nivel", (short) nivel);
        ReflectionTestUtils.setField(p, "slaHoras", slaHoras);
        return p;
    }

    static Categoria categoria(long id, String nombre) {
        Categoria c = new Categoria();
        ReflectionTestUtils.setField(c, "id", id);
        c.setNombre(nombre);
        c.setDescripcion(nombre);
        return c;
    }

    static ElementoTi elemento(long id, String nombre, boolean activo) {
        ElementoTi e = new ElementoTi();
        ReflectionTestUtils.setField(e, "id", id);
        ReflectionTestUtils.setField(e, "nombre", nombre);
        ReflectionTestUtils.setField(e, "tipo", TipoElemento.APLICACION);
        ReflectionTestUtils.setField(e, "activo", activo);
        return e;
    }

    static Incidente incidente(long id, Categoria c, Prioridad p, EstadoIncidente e, Usuario responsable) {
        Incidente i = new Incidente();
        ReflectionTestUtils.setField(i, "id", id);
        i.setTitulo("Outlook no envía correos");
        i.setDescripcion("Los correos se quedan en la bandeja de salida.");
        i.setCategoria(c);
        i.setPrioridad(p);
        i.setUsuario(responsable);
        i.cambiarEstado(e);
        ReflectionTestUtils.setField(i, "fechaCreacion", LocalDateTime.of(2026, 11, 2, 8, 0));
        ReflectionTestUtils.setField(i, "fechaActualizacion", LocalDateTime.of(2026, 11, 2, 8, 0));
        return i;
    }

    /** Deja a la cuenta como usuario autenticado de la petición (lo que lee SesionActual). */
    static void sesion(Usuario u) {
        CuentaUsuario c = CuentaUsuario.de(u);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(c, null, c.getAuthorities()));
    }

    static void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }
}
