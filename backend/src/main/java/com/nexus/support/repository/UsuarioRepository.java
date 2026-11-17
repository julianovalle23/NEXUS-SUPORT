package com.nexus.support.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nexus.support.domain.Rol;
import com.nexus.support.domain.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    List<Usuario> findByActivoTrueAndRolOrderByNombre(Rol rol);

    List<Usuario> findAllByOrderByNombre();

    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    boolean existsByCorreoIgnoreCaseAndIdNot(String correo, Long id);

    long countByRolAndActivoTrue(Rol rol);

    long countByRolAndActivoTrueAndClaveHashIsNotNull(Rol rol);

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
