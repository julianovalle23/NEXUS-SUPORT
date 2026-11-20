package com.nexus.support.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import com.nexus.support.domain.Rol;
import com.nexus.support.domain.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    List<Usuario> findByActivoTrueAndRolOrderByNombre(Rol rol);

    List<Usuario> findAllByOrderByNombre();

    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    boolean existsByCorreoIgnoreCaseAndIdNot(String correo, Long id);

    long countByRolAndActivoTrue(Rol rol);

    long countByRolAndActivoTrueAndClaveHashIsNotNull(Rol rol);

    /**
     * Bloquea (SELECT ... FOR UPDATE) las cuentas de administración que pueden iniciar sesión: activas
     * y con contraseña. Siempre en el mismo orden (id) para no provocar interbloqueos. Una transacción
     * concurrente que también las bloquee espera, y al continuar PostgreSQL vuelve a evaluar el filtro
     * con los datos ya confirmados, así que ve el efecto del cambio anterior.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT u FROM Usuario u
            WHERE u.rol = com.nexus.support.domain.Rol.ADMIN AND u.activo = true AND u.claveHash IS NOT NULL
            ORDER BY u.id
            """)
    List<Usuario> bloquearAdministradoresUsables();

    /** Técnico activo con menos incidentes no finalizados (desempate por id). */
    @Query(value = """
            SELECT u.* FROM usuario u
            WHERE u.activo AND u.rol = 'TECNICO'
            ORDER BY (SELECT COUNT(*) FROM incidente i
                      JOIN estado_incidente e ON e.id = i.estado_id
                      WHERE i.usuario_id = u.id AND NOT e.es_final), u.id
            LIMIT 1
            """, nativeQuery = true)
    Optional<Usuario> tecnicoConMenosCarga();
}
