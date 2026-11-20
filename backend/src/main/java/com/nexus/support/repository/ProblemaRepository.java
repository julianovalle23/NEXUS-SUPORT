package com.nexus.support.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.nexus.support.domain.EstadoProblema;
import com.nexus.support.domain.Problema;

public interface ProblemaRepository extends JpaRepository<Problema, Long> {

    @EntityGraph(attributePaths = {"elemento"})
    List<Problema> findAllByOrderByFechaCreacionDesc();

    @EntityGraph(attributePaths = {"elemento"})
    List<Problema> findByEstadoOrderByFechaCreacionDesc(EstadoProblema estado);
}
