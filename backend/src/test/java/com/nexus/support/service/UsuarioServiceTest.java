package com.nexus.support.service;

import static com.nexus.support.service.Datos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.nexus.support.domain.AuditoriaUsuario;
import com.nexus.support.domain.Rol;
import com.nexus.support.domain.Usuario;
import com.nexus.support.dto.UsuarioDtos.UsuarioRequest;
import com.nexus.support.exception.ReglaNegocioException;
import com.nexus.support.repository.AuditoriaUsuarioRepository;
import com.nexus.support.repository.UsuarioRepository;

/**
 * Protección de cuentas de administración y auditoría de cambios de perfil o estado.
 * El bloqueo real (SELECT ... FOR UPDATE) solo se comprueba con PostgreSQL; aquí se simula lo que
 * devuelve la consulta bloqueante una vez obtenido el bloqueo.
 */
class UsuarioServiceTest {

    private final Usuario ana = conClave(usuario(20, "Ana Admin", Rol.ADMIN));
    private final Usuario bruno = conClave(usuario(21, "Bruno Admin", Rol.ADMIN));
    private final Usuario carla = usuario(22, "Carla Sin Clave", Rol.ADMIN); // ADMIN sin contraseña: no utilizable

    private UsuarioRepository usuarios;
    private AuditoriaUsuarioRepository auditoria;
    private UsuarioService service;

    @BeforeEach
    void setUp() {
        usuarios = mock(UsuarioRepository.class);
        auditoria = mock(AuditoriaUsuarioRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("hash");
        service = new UsuarioService(usuarios, auditoria, encoder, mock(SessionRegistry.class));

        for (Usuario u : List.of(ana, bruno, carla)) {
            when(usuarios.findById(u.getId())).thenReturn(Optional.of(u));
            when(usuarios.getReferenceById(u.getId())).thenReturn(u);
        }
        when(usuarios.findByCorreoIgnoreCase(any())).thenReturn(Optional.empty());
        when(usuarios.save(any())).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 99L);
            return u;
        });
        sesion(ana);
    }

    @AfterEach
    void tearDown() {
        cerrarSesion();
    }

    @Test
    void quitarElPerfilALaUltimaCuentaDeAdministracionUtilizableSeRechaza() {
        // Tras obtener el bloqueo solo queda Bruno como administrador utilizable
        // (p. ej., otra transacción ya confirmó que Ana dejó de serlo).
        when(usuarios.bloquearAdministradoresUsables()).thenReturn(List.of(bruno));

        assertThatThrownBy(() -> service.actualizar(21L, request(bruno, Rol.TECNICO, true)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("última cuenta de administración");
        assertThat(bruno.getRol()).isEqualTo(Rol.ADMIN);
        verify(auditoria, never()).save(any());
    }

    @Test
    void desactivarLaUltimaCuentaDeAdministracionUtilizableSeRechaza() {
        when(usuarios.bloquearAdministradoresUsables()).thenReturn(List.of(bruno));

        assertThatThrownBy(() -> service.actualizar(21L, request(bruno, Rol.ADMIN, false)))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(bruno.isActivo()).isTrue();
    }

    @Test
    void unAdministradorSinContrasenaNoCuentaComoRespaldo() {
        // Carla es ADMIN activa pero sin contraseña: no aparece en la consulta bloqueante.
        when(usuarios.bloquearAdministradoresUsables()).thenReturn(List.of(bruno));

        assertThatThrownBy(() -> service.actualizar(21L, request(bruno, Rol.SOLICITANTE, true)))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void nadiePuedeCambiarSuPropioPerfil() {
        when(usuarios.bloquearAdministradoresUsables()).thenReturn(List.of(ana, bruno));

        assertThatThrownBy(() -> service.actualizar(20L, request(ana, Rol.TECNICO, true)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("propio perfil");
        assertThat(ana.getRol()).isEqualTo(Rol.ADMIN);
        verify(auditoria, never()).save(any());
    }

    @Test
    void nadiePuedeDesactivarSuPropiaCuenta() {
        when(usuarios.bloquearAdministradoresUsables()).thenReturn(List.of(ana, bruno));

        assertThatThrownBy(() -> service.actualizar(20L, request(ana, Rol.ADMIN, false)))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void conOtroAdministradorUtilizableElCambioSeAplicaYSeAudita() {
        when(usuarios.bloquearAdministradoresUsables()).thenReturn(List.of(ana, bruno));

        service.actualizar(21L, request(bruno, Rol.TECNICO, true));

        assertThat(bruno.getRol()).isEqualTo(Rol.TECNICO);
        verify(usuarios).bloquearAdministradoresUsables();
        verify(auditoria).save(argThat(a -> a.getOrigen() == AuditoriaUsuario.Origen.API
                && a.getActor() == ana && a.getUsuario() == bruno
                && a.getRolAnterior() == Rol.ADMIN && a.getRolNuevo() == Rol.TECNICO
                && Boolean.TRUE.equals(a.getActivoAnterior()) && a.isActivoNuevo()
                && a.getFecha() != null));
    }

    @Test
    void degradarUnAdministradorSinContrasenaNoRequiereRespaldo() {
        when(usuarios.bloquearAdministradoresUsables()).thenReturn(List.of(ana));

        service.actualizar(22L, request(carla, Rol.TECNICO, true));

        assertThat(carla.getRol()).isEqualTo(Rol.TECNICO);
        verify(auditoria).save(any());
    }

    @Test
    void editarSoloElNombreNoGeneraAuditoria() {
        when(usuarios.bloquearAdministradoresUsables()).thenReturn(List.of(ana, bruno));
        UsuarioRequest req = new UsuarioRequest("Bruno Administrador", bruno.getCorreo(), "TI", Rol.ADMIN, true, null);

        service.actualizar(21L, req);

        assertThat(bruno.getNombre()).isEqualTo("Bruno Administrador");
        verify(auditoria, never()).save(any());
    }

    @Test
    void crearUnaCuentaQuedaAuditadaSinValoresAnteriores() {
        service.crear(new UsuarioRequest("Diego Tecnico", "diego@nexus.local", "TI", Rol.TECNICO, null, null));

        verify(auditoria).save(argThat(a -> a.getActor() == ana && a.getUsuario().getId() == 99L
                && a.getRolAnterior() == null && a.getActivoAnterior() == null
                && a.getRolNuevo() == Rol.TECNICO && a.isActivoNuevo()));
    }

    private static Usuario conClave(Usuario u) {
        u.setClaveHash("hash");
        return u;
    }

    private static UsuarioRequest request(Usuario u, Rol rol, boolean activo) {
        return new UsuarioRequest(u.getNombre(), u.getCorreo(), "TI", rol, activo, null);
    }
}
