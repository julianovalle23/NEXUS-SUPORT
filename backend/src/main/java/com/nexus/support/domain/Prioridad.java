package com.nexus.support.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "prioridad")
public class Prioridad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String nombre;

    @Column(nullable = false, unique = true)
    private Short nivel;

    @Column(name = "sla_horas", nullable = false)
    private Integer slaHoras;

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public Short getNivel() { return nivel; }
    public Integer getSlaHoras() { return slaHoras; }
}
