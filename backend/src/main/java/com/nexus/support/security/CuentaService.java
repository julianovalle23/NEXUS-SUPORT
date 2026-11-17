package com.nexus.support.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.support.repository.UsuarioRepository;

/** Busca la cuenta por correo (sin distinguir mayúsculas). Las cuentas sin contraseña no pueden iniciar sesión. */
@Service
public class CuentaService implements UserDetailsService {

    private final UsuarioRepository usuarios;

    public CuentaService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String correo) {
        return usuarios.findByCorreoIgnoreCase(correo == null ? "" : correo.trim())
                .filter(u -> u.getClaveHash() != null)
                .map(CuentaUsuario::de)
                .orElseThrow(() -> new UsernameNotFoundException("Cuenta no encontrada"));
    }
}
