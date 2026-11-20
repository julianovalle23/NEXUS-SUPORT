package com.nexus.support.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexus.support.domain.Incidente;

import jakarta.persistence.LockModeType;

public interface IncidenteRepository extends JpaRepository<Incidente, Long> {

    @EntityGraph(attributePaths = {"categoria", "prioridad", "estado", "usuario", "elemento", "problema", "seguimientoDe"})
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

    @EntityGraph(attributePaths = {"estado", "prioridad"})
    List<Incidente> findByProblemaIdOrderByFechaCreacionDesc(Long problemaId);

    /** Por problema: [problemaId, incidentes, incidentes no finalizados]. */
    @Query("""
            SELECT i.problema.id, COUNT(i), SUM(CASE WHEN i.estado.esFinal = false THEN 1 ELSE 0 END)
            FROM Incidente i
            WHERE i.problema IS NOT NULL
            GROUP BY i.problema.id
            """)
    List<Object[]> conteoPorProblema();

    /**
     * SELECT ... FOR UPDATE: dos cambios simultáneos sobre el mismo incidente se ejecutan en serie y el
     * segundo valida la transición contra el estado que dejó el primero.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Incidente i WHERE i.id = :id")
    Optional<Incidente> findParaModificar(@Param("id") Long id);
}
