package com.nexus.support.security;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.nexus.support.domain.Rol;
import com.nexus.support.domain.Usuario;

/**
 * Cuenta autenticada guardada en la sesión. La igualdad es por id para poder
 * localizar y expirar las sesiones de un usuario cuando se desactiva o cambia su perfil.
 */
public final class CuentaUsuario implements UserDetails {

    private final Long id;
    private final String correo;
    private final String nombre;
    private final Rol rol;
    private final boolean activo;
    private final transient String claveHash;

    private CuentaUsuario(Long id, String correo, String nombre, Rol rol, boolean activo, String claveHash) {
        this.id = id;
        this.correo = correo;
        this.nombre = nombre;
        this.rol = rol;
        this.activo = activo;
        this.claveHash = claveHash;
    }

    public static CuentaUsuario de(Usuario u) {
        return new CuentaUsuario(u.getId(), u.getCorreo(), u.getNombre(), u.getRol(), u.isActivo(), u.getClaveHash());
    }

    public Long id() { return id; }
    public String correo() { return correo; }
    public String nombre() { return nombre; }
    public Rol rol() { return rol; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
    }

    @Override
    public String getPassword() { return claveHash; }

    @Override
    public String getUsername() { return correo; }

    @Override
    public boolean isEnabled() { return activo; }

    @Override
    public boolean equals(Object o) {
        return o instanceof CuentaUsuario c && Objects.equals(id, c.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
