package com.nexus.support.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexus.support.domain.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
}
