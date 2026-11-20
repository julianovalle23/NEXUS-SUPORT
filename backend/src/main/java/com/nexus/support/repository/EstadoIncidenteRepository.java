package com.nexus.support.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexus.support.domain.EstadoIncidente;

/** Las transiciones permitidas viven en la tabla transicion_estado (ver 05-mejoras.sql). */
public interface EstadoIncidenteRepository extends JpaRepository<EstadoIncidente, Long> {

    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM transicion_estado
                           WHERE estado_origen_id = :origen AND estado_destino_id = :destino)
            """, nativeQuery = true)
    boolean transicionPermitida(@Param("origen") Long origen, @Param("destino") Long destino);

    /** Terminal = sin transiciones normales de salida (hoy, Cerrado). */
    @Query(value = "SELECT NOT EXISTS (SELECT 1 FROM transicion_estado WHERE estado_origen_id = :id)",
            nativeQuery = true)
    boolean esTerminal(@Param("id") Long id);

    /** Pares [origen, destino]. */
    @Query(value = "SELECT estado_origen_id, estado_destino_id FROM transicion_estado ORDER BY 1, 2",
            nativeQuery = true)
    List<Object[]> transiciones();
}
