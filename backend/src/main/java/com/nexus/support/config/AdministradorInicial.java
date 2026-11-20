package com.nexus.support.config;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.nexus.support.domain.AuditoriaUsuario;
import com.nexus.support.domain.Rol;
import com.nexus.support.domain.Usuario;
import com.nexus.support.repository.AuditoriaUsuarioRepository;
import com.nexus.support.repository.UsuarioRepository;

/**
 * Crea la primera cuenta de administración con NEXUS_ADMIN_CORREO y NEXUS_ADMIN_CLAVE
 * solo cuando no existe ningún administrador activo con contraseña. Si ya existe, no cambia nada.
 * El cambio queda en auditoria_usuario con origen ARRANQUE y sin actor.
 */
@Component
class AdministradorInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdministradorInicial.class);

    private final UsuarioRepository usuarios;
    private final AuditoriaUsuarioRepository auditoria;
    private final PasswordEncoder encoder;
    private final String correo;
    private final String clave;

    AdministradorInicial(UsuarioRepository usuarios, AuditoriaUsuarioRepository auditoria, PasswordEncoder encoder,
                         @Value("${nexus.admin-inicial.correo:}") String correo,
                         @Value("${nexus.admin-inicial.clave:}") String clave) {
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.encoder = encoder;
        this.correo = correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
        this.clave = clave == null ? "" : clave;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (usuarios.countByRolAndActivoTrueAndClaveHashIsNotNull(Rol.ADMIN) > 0) {
            return;
        }
        if (correo.isBlank() || clave.length() < 8) {
            log.warn("No hay ningún administrador con contraseña. Defina NEXUS_ADMIN_CORREO y NEXUS_ADMIN_CLAVE "
                    + "(mínimo 8 caracteres) en .env y reinicie para crear el primero.");
            return;
        }
        Usuario u = usuarios.findByCorreoIgnoreCase(correo).orElseGet(() -> {
            Usuario nuevo = new Usuario();
            nuevo.setNombre("Administrador");
            nuevo.setCorreo(correo);
            nuevo.setArea("Mesa de servicio");
            return nuevo;
        });
        boolean existia = u.getId() != null;
        Rol rolAnterior = existia ? u.getRol() : null;
        Boolean activoAnterior = existia ? u.isActivo() : null;
        u.setRol(Rol.ADMIN);
        u.setActivo(true);
        u.setClaveHash(encoder.encode(clave));
        Usuario guardado = usuarios.save(u);
        if (!existia || rolAnterior != Rol.ADMIN || !activoAnterior) {
            auditoria.save(new AuditoriaUsuario(AuditoriaUsuario.Origen.ARRANQUE, null, guardado, rolAnterior,
                    activoAnterior));
        }
        log.info("Cuenta de administración inicial lista para {}", correo);
    }
}
