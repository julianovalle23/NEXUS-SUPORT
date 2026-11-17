package com.nexus.support.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexus.support.domain.ArticuloConocimiento;

public interface ArticuloRepository extends JpaRepository<ArticuloConocimiento, Long> {

    @EntityGraph(attributePaths = "categoria")
    @Query("""
            SELECT a FROM ArticuloConocimiento a
            WHERE (:activo IS NULL OR a.activo = :activo)
              AND (:categoriaId IS NULL OR a.categoria.id = :categoriaId)
            ORDER BY a.id
            """)
    List<ArticuloConocimiento> buscar(@Param("activo") Boolean activo, @Param("categoriaId") Long categoriaId);

    /** Fuentes válidas para el RAG: existen, están activas y verificadas. */
    @Query("SELECT a FROM ArticuloConocimiento a WHERE a.id IN :ids AND a.activo = true AND a.verificado = true")
    List<ArticuloConocimiento> findFuentesValidas(@Param("ids") Collection<Long> ids);

    boolean existsByTituloIgnoreCase(String titulo);

    boolean existsByTituloIgnoreCaseAndIdNot(String titulo, Long id);
}
