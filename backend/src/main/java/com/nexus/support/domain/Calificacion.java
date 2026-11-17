package com.nexus.support.domain;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "calificacion")
public class Calificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "respuesta_id", nullable = false, unique = true)
    private RespuestaIA respuesta;

    @Column(nullable = false)
    private Short puntuacion;

    @Column(length = 500)
    private String comentario;

    @Column(nullable = false)
    private LocalDateTime fecha;

    protected Calificacion() {
    }

    public Calificacion(RespuestaIA respuesta) {
        this.respuesta = respuesta;
    }

    @PrePersist
    @PreUpdate
    void alGuardar() {
        fecha = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public RespuestaIA getRespuesta() { return respuesta; }
    public Short getPuntuacion() { return puntuacion; }
    public void setPuntuacion(Short puntuacion) { this.puntuacion = puntuacion; }
    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }
    public LocalDateTime getFecha() { return fecha; }
}
