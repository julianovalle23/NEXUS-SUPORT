package com.nexus.support.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.nexus.support.domain.RevisionIncidente;

public interface RevisionIncidenteRepository extends JpaRepository<RevisionIncidente, Long> {

    @EntityGraph(attributePaths = {"autor"})
    Optional<RevisionIncidente> findByIncidenteId(Long incidenteId);
}
