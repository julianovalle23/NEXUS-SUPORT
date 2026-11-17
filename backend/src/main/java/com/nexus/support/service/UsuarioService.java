package com.nexus.support.service;

import java.util.List;
import java.util.Locale;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.support.domain.Rol;
import com.nexus.support.domain.Usuario;
import com.nexus.support.dto.UsuarioDtos.UsuarioRequest;
import com.nexus.support.dto.UsuarioDtos.UsuarioResponse;
import com.nexus.support.exception.RecursoNoEncontradoException;
import com.nexus.support.exception.ReglaNegocioException;
import com.nexus.support.repository.UsuarioRepository;
import com.nexus.support.security.CuentaUsuario;
import com.nexus.support.security.SesionActual;

/** Administración de cuentas. Cambiar perfil, estado o contraseña cierra las sesiones abiertas de esa cuenta. */
@Service
@Transactional(readOnly = true)
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final SessionRegistry sesiones;

    public UsuarioService(UsuarioRepository usuarios, PasswordEncoder encoder, SessionRegistry sesiones) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.sesiones = sesiones;
    }

    public List<UsuarioResponse> listar() {
        return usuarios.findAllByOrderByNombre().stream().map(UsuarioResponse::de).toList();
    }

    @Transactional
    public UsuarioResponse crear(UsuarioRequest req) {
        String correo = normalizar(req.correo());
        if (usuarios.findByCorreoIgnoreCase(correo).isPresent()) {
            throw new ReglaNegocioException("Ya existe una cuenta con el correo " + correo);
        }
        Usuario u = new Usuario();
        u.setNombre(req.nombre().trim());
        u.setCorreo(correo);
        u.setArea(req.area().trim());
        u.setRol(req.rol());
        u.setActivo(req.activo() == null || req.activo());
        if (req.clave() != null && !req.clave().isBlank()) {
            u.setClaveHash(encoder.encode(req.clave()));
        }
        return UsuarioResponse.de(usuarios.save(u));
    }

    @Transactional
    public UsuarioResponse actualizar(Long id, UsuarioRequest req) {
        Usuario u = entidad(id);
        String correo = normalizar(req.correo());
        if (usuarios.existsByCorreoIgnoreCaseAndIdNot(correo, id)) {
            throw new ReglaNegocioException("Ya existe otra cuenta con el correo " + correo);
        }
        boolean activo = req.activo() == null ? u.isActivo() : req.activo();
        boolean cambiaAcceso = u.getRol() != req.rol() || u.isActivo() != activo || !u.getCorreo().equals(correo);
        if (cambiaAcceso && id.equals(SesionActual.cuenta().id())) {
            throw new ReglaNegocioException("No puede cambiar su propio perfil, correo ni estado; pídaselo a otro administrador");
        }
        boolean dejaDeSerAdmin = u.getRol() == Rol.ADMIN && u.isActivo() && (req.rol() != Rol.ADMIN || !activo);
        if (dejaDeSerAdmin && usuarios.countByRolAndActivoTrue(Rol.ADMIN) <= 1) {
            throw new ReglaNegocioException("Debe existir al menos un administrador activo");
        }
        u.setNombre(req.nombre().trim());
        u.setCorreo(correo);
        u.setArea(req.area().trim());
        u.setRol(req.rol());
        u.setActivo(activo);
        if (cambiaAcceso) {
            cerrarSesiones(u);
        }
        return UsuarioResponse.de(u);
    }

    /** Un administrador asigna o restablece la contraseña de otra cuenta. */
    @Transactional
    public UsuarioResponse asignarClave(Long id, String clave) {
        Usuario u = entidad(id);
        u.setClaveHash(encoder.encode(clave));
        if (!id.equals(SesionActual.cuenta().id())) {
            cerrarSesiones(u);
        }
        return UsuarioResponse.de(u);
    }

    /** Cualquier cuenta cambia su propia contraseña conociendo la actual. */
    @Transactional
    public void cambiarClavePropia(String actual, String nueva) {
        Usuario u = entidad(SesionActual.cuenta().id());
        if (u.getClaveHash() == null || !encoder.matches(actual, u.getClaveHash())) {
            throw new ReglaNegocioException("La contraseña actual no es correcta");
        }
        u.setClaveHash(encoder.encode(nueva));
    }

    private Usuario entidad(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
    }

    private void cerrarSesiones(Usuario u) {
        sesiones.getAllSessions(CuentaUsuario.de(u), false).forEach(SessionInformation::expireNow);
    }

    private static String normalizar(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }
}
