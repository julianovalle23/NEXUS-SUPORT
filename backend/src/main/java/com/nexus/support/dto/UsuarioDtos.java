package com.nexus.support.dto;

import java.time.LocalDateTime;

import com.nexus.support.domain.AuditoriaUsuario;
import com.nexus.support.domain.Rol;
import com.nexus.support.domain.Usuario;
import com.nexus.support.security.CuentaUsuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class UsuarioDtos {

    /** BCrypt solo considera los primeros 72 bytes. */
    private static final int CLAVE_MIN = 8;
    private static final int CLAVE_MAX = 72;

    private UsuarioDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "El correo es obligatorio") String correo,
            @NotBlank(message = "La contraseña es obligatoria") String clave) {
    }

    public record CuentaDto(Long id, String nombre, String correo, Rol rol) {
        public static CuentaDto de(CuentaUsuario c) {
            return new CuentaDto(c.id(), c.nombre(), c.correo(), c.rol());
        }
    }

    public record SesionDto(boolean autenticado, CuentaDto usuario) {
    }

    public record CambioClaveRequest(
            @NotBlank(message = "La contraseña actual es obligatoria") String actual,
            @NotBlank(message = "La nueva contraseña es obligatoria")
            @Size(min = CLAVE_MIN, max = CLAVE_MAX, message = "La contraseña debe tener entre 8 y 72 caracteres")
            String nueva) {
    }

    public record AsignarClaveRequest(
            @NotBlank(message = "La contraseña es obligatoria")
            @Size(min = CLAVE_MIN, max = CLAVE_MAX, message = "La contraseña debe tener entre 8 y 72 caracteres")
            String clave) {
    }

    public record UsuarioRequest(
            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 120, message = "El nombre admite máximo 120 caracteres") String nombre,
            @NotBlank(message = "El correo es obligatorio") @Email(message = "El correo no es válido")
            @Size(max = 160, message = "El correo admite máximo 160 caracteres") String correo,
            @NotBlank(message = "El área es obligatoria")
            @Size(max = 80, message = "El área admite máximo 80 caracteres") String area,
            @NotNull(message = "El perfil es obligatorio") Rol rol,
            /** Opcional; por defecto activo. */
            Boolean activo,
            /** Opcional al crear: sin contraseña la cuenta no puede iniciar sesión. Se ignora al editar. */
            @Size(min = CLAVE_MIN, max = CLAVE_MAX, message = "La contraseña debe tener entre 8 y 72 caracteres")
            String clave) {
    }

    public record UsuarioResponse(Long id, String nombre, String correo, String area, Rol rol, boolean activo,
                                  boolean tieneClave) {
        public static UsuarioResponse de(Usuario u) {
            return new UsuarioResponse(u.getId(), u.getNombre(), u.getCorreo(), u.getArea(), u.getRol(),
                    u.isActivo(), u.getClaveHash() != null);
        }
    }

    /** actor nulo = cambio hecho al arrancar la aplicación (cuenta de administración inicial). */
    public record AuditoriaDto(Long id, LocalDateTime fecha, AuditoriaUsuario.Origen origen, String actor,
                               Long usuarioId, String usuario, Rol rolAnterior, Rol rolNuevo,
                               Boolean activoAnterior, boolean activoNuevo) {
        public static AuditoriaDto de(AuditoriaUsuario a) {
            return new AuditoriaDto(a.getId(), a.getFecha(), a.getOrigen(),
                    a.getActor() == null ? null : a.getActor().getNombre(),
                    a.getUsuario().getId(), a.getUsuario().getNombre(), a.getRolAnterior(), a.getRolNuevo(),
                    a.getActivoAnterior(), a.isActivoNuevo());
        }
    }
}
