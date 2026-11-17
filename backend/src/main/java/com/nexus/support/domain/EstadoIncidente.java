package com.nexus.support.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "estado_incidente")
public class EstadoIncidente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String nombre;

    /** Resuelto y Cerrado son estados finales: fijan la fecha de resolución. */
    @Column(name = "es_final", nullable = false)
    private boolean esFinal;

    @Column(nullable = false)
    private Short orden;

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public boolean isEsFinal() { return esFinal; }
    public Short getOrden() { return orden; }
}
