package com.nexus.support.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexus.support.domain.ImportacionArticulo;

import jakarta.persistence.LockModeType;

public interface ImportacionRepository extends JpaRepository<ImportacionArticulo, Long> {

    /** Importación vigente del mismo archivo (las que terminaron en ERROR o DESCARTADO no cuentan). */
    Optional<ImportacionArticulo> findFirstByHashAndEstadoNotIn(String hash, Collection<ImportacionArticulo.Estado> estados);

    @EntityGraph(attributePaths = {"cargadoPor", "revisadoPor", "categoria"})
    List<ImportacionArticulo> findTop100ByOrderByFechaCargaDescIdDesc();

    /** Bloquea la importación para que dos confirmaciones simultáneas no creen los borradores dos veces. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ImportacionArticulo i WHERE i.id = :id")
    Optional<ImportacionArticulo> findParaModificar(@Param("id") Long id);
}
