package com.nexus.support.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.nexus.support.domain.AuditoriaUsuario;

public interface AuditoriaUsuarioRepository extends JpaRepository<AuditoriaUsuario, Long> {

    @EntityGraph(attributePaths = {"actor", "usuario"})
    List<AuditoriaUsuario> findTop200ByOrderByFechaDescIdDesc();
}
