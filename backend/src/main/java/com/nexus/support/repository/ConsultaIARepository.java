package com.nexus.support.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.nexus.support.domain.ConsultaIA;
import com.nexus.support.domain.EstadoConsulta;

public interface ConsultaIARepository extends JpaRepository<ConsultaIA, Long> {

    @EntityGraph(attributePaths = {"incidente", "respuesta"})
    List<ConsultaIA> findByIncidenteIdOrderByFechaDesc(Long incidenteId);

    @EntityGraph(attributePaths = {"incidente", "respuesta"})
    Page<ConsultaIA> findAllByOrderByFechaDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"incidente", "respuesta"})
    Page<ConsultaIA> findByEstadoOrderByFechaDesc(EstadoConsulta estado, Pageable pageable);
}
