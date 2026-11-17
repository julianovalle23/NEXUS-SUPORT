package com.nexus.support.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexus.support.domain.EstadoIncidente;

public interface EstadoIncidenteRepository extends JpaRepository<EstadoIncidente, Long> {
}
