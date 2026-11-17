package com.nexus.support.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexus.support.domain.Incidente;

public interface IncidenteRepository extends JpaRepository<Incidente, Long> {

    @EntityGraph(attributePaths = {"categoria", "prioridad", "estado", "usuario"})
    @Query("""
            SELECT i FROM Incidente i
            WHERE (:categoriaId IS NULL OR i.categoria.id = :categoriaId)
              AND (:prioridadId IS NULL OR i.prioridad.id = :prioridadId)
              AND (:estadoId    IS NULL OR i.estado.id    = :estadoId)
              AND (:usuarioId   IS NULL OR i.usuario.id   = :usuarioId)
              AND (:solicitanteId IS NULL OR i.cuentaSolicitante.id = :solicitanteId)
              AND (CAST(:desde AS timestamp) IS NULL OR i.fechaCreacion >= :desde)
              AND (CAST(:hasta AS timestamp) IS NULL OR i.fechaCreacion <  :hasta)
              AND (:texto IS NULL OR LOWER(i.titulo) LIKE :texto OR LOWER(i.descripcion) LIKE :texto)
            """)
    Page<Incidente> buscar(@Param("categoriaId") Long categoriaId,
                           @Param("prioridadId") Long prioridadId,
                           @Param("estadoId") Long estadoId,
                           @Param("usuarioId") Long usuarioId,
                           @Param("solicitanteId") Long solicitanteId,
                           @Param("desde") LocalDateTime desde,
                           @Param("hasta") LocalDateTime hasta,
                           @Param("texto") String texto,
                           Pageable pageable);
}
