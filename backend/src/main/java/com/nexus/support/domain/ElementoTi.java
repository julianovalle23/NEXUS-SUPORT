package com.nexus.support.domain;

import jakarta.persistence.*;

/** Equipo, aplicación o servicio de TI (catálogo administrado con SQL, como las categorías). */
@Entity
@Table(name = "elemento_ti")
public class ElementoTi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoElemento tipo;

    @Column(length = 300)
    private String descripcion;

    @Column(nullable = false)
    private boolean activo;

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public TipoElemento getTipo() { return tipo; }
    public String getDescripcion() { return descripcion; }
    public boolean isActivo() { return activo; }
}
