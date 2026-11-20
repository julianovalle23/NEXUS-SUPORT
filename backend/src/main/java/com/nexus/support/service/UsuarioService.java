package com.nexus.support.service;

import java.util.List;
import java.util.Locale;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.support.domain.AuditoriaUsuario;
import com.nexus.support.domain.AuditoriaUsuario.Origen;
import com.nexus.support.domain.Rol;
import com.nexus.support.domain.Usuario;
import com.nexus.support.dto.UsuarioDtos.AuditoriaDto;
import com.nexus.support.dto.UsuarioDtos.UsuarioRequest;
import com.nexus.support.dto.UsuarioDtos.UsuarioResponse;
import com.nexus.support.exception.RecursoNoEncontradoException;
import com.nexus.support.exception.ReglaNegocioException;
import com.nexus.support.repository.AuditoriaUsuarioRepository;
import com.nexus.support.repository.UsuarioRepository;
import com.nexus.support.security.CuentaUsuario;
import com.nexus.support.security.SesionActual;

/**
 * Administración de cuentas (el acceso a /api/usuarios/** ya exige ADMIN en SeguridadConfig).
 * Cambiar perfil, estado o contraseña cierra las sesiones abiertas de esa cuenta.
 * Todo alta y todo cambio de perfil o estado queda en auditoria_usuario.
 */
@Service
@Transactional(readOnly = true)
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final AuditoriaUsuarioRepository auditoria;
    private final PasswordEncoder encoder;
    private final SessionRegistry sesiones;

    public UsuarioService(UsuarioRepository usuarios, AuditoriaUsuarioRepository auditoria, PasswordEncoder encoder,
                          SessionRegistry sesiones) {
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.encoder = encoder;
        this.sesiones = sesiones;
    }

    public List<UsuarioResponse> listar() {
        return usuarios.findAllByOrderByNombre().stream().map(UsuarioResponse::de).toList();
    }

    /** Últimos 200 cambios de perfil o estado, del más reciente al más antiguo. */
    public List<AuditoriaDto> auditoria() {
        return auditoria.findTop200ByOrderByFechaDescIdDesc().stream().map(AuditoriaDto::de).toList();
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
        Usuario guardado = usuarios.save(u);
        auditoria.save(new AuditoriaUsuario(Origen.API, actor(), guardado, null, null));
        return UsuarioResponse.de(guardado);
    }

    @Transactional
    public UsuarioResponse actualizar(Long id, UsuarioRequest req) {
        // 1) Bloquear primero, y siempre en el mismo orden, las cuentas de administración utilizables.
        //    Dos administradores que se quitan el perfil a la vez quedan en serie: el segundo espera
        //    y, al continuar, ya ve el cambio confirmado del primero.
        List<Usuario> administradores = usuarios.bloquearAdministradoresUsables();
        Usuario u = entidad(id);
        String correo = normalizar(req.correo());
        if (usuarios.existsByCorreoIgnoreCaseAndIdNot(correo, id)) {
            throw new ReglaNegocioException("Ya existe otra cuenta con el correo " + correo);
        }
        boolean activo = req.activo() == null ? u.isActivo() : req.activo();
        Rol rolAnterior = u.getRol();
        boolean activoAnterior = u.isActivo();
        boolean cambiaPerfilOEstado = rolAnterior != req.rol() || activoAnterior != activo;
        boolean cambiaAcceso = cambiaPerfilOEstado || !u.getCorreo().equals(correo);
        if (cambiaAcceso && id.equals(SesionActual.cuenta().id())) {
            throw new ReglaNegocioException(
                    "No puede cambiar su propio perfil, correo ni estado; pídaselo a otro administrador");
        }
        // 2) Con el bloqueo tomado, la cuenta no puede dejar de ser administradora utilizable
        //    si no queda otra que sí lo sea.
        boolean eraAdminUsable = administradores.stream().anyMatch(a -> a.getId().equals(id));
        boolean seguiraSiendoAdmin = req.rol() == Rol.ADMIN && activo;
        if (eraAdminUsable && !seguiraSiendoAdmin && administradores.size() <= 1) {
            throw new ReglaNegocioException("No se puede quitar el perfil de administración ni desactivar la última "
                    + "cuenta de administración activa con contraseña; primero habilite otra");
        }
        u.setNombre(req.nombre().trim());
        u.setCorreo(correo);
        u.setArea(req.area().trim());
        u.setRol(req.rol());
        u.setActivo(activo);
        if (cambiaPerfilOEstado) {
            auditoria.save(new AuditoriaUsuario(Origen.API, actor(), u, rolAnterior, activoAnterior));
        }
        if (cambiaAcceso) {
            cerrarSesiones(u);
        }
        return UsuarioResponse.de(u);
    }

    /** Un administrador asigna o restablece la contraseña de otra cuenta (nunca la quita). */
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

    private Usuario actor() {
        return usuarios.getReferenceById(SesionActual.cuenta().id());
    }

    private void cerrarSesiones(Usuario u) {
        sesiones.getAllSessions(CuentaUsuario.de(u), false).forEach(SessionInformation::expireNow);
    }

    private static String normalizar(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }
}
