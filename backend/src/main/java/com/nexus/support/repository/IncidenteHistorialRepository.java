package com.nexus.support.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.nexus.support.domain.IncidenteHistorial;

public interface IncidenteHistorialRepository extends JpaRepository<IncidenteHistorial, Long> {

    @EntityGraph(attributePaths = {"estado", "prioridad", "responsable", "actor"})
    List<IncidenteHistorial> findByIncidenteIdOrderByFechaAscIdAsc(Long incidenteId);
}
