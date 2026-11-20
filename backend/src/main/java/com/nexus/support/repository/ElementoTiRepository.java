package com.nexus.support.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexus.support.domain.ElementoTi;

public interface ElementoTiRepository extends JpaRepository<ElementoTi, Long> {

    List<ElementoTi> findByActivoTrueOrderByTipoAscNombreAsc();
}
