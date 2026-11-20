package com.nexus.support.domain;

import java.time.LocalDateTime;

import jakarta.persistence.*;

/** Revisión posterior de un incidente finalizado. La línea de tiempo se calcula, no se guarda. */
@Entity
@Table(name = "revision_incidente")
public class RevisionIncidente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incidente_id", nullable = false, unique = true)
    private Incidente incidente;

    @Column(nullable = false, columnDefinition = "text")
    private String impacto;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_causa", nullable = false, length = 20)
    private EstadoCausa estadoCausa = EstadoCausa.PENDIENTE;

    @Column(columnDefinition = "text")
    private String causa;

    @Column(name = "evidencia_causa", columnDefinition = "text")
    private String evidenciaCausa;

    @Column(columnDefinition = "text")
    private String solucion;

    @Column(name = "acciones_preventivas", columnDefinition = "text")
    private String accionesPreventivas;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected RevisionIncidente() {
    }

    public RevisionIncidente(Incidente incidente, Usuario autor) {
        this.incidente = incidente;
        this.autor = autor;
    }

    @PrePersist
    void alCrear() {
        fechaCreacion = LocalDateTime.now();
        fechaActualizacion = fechaCreacion;
    }

    @PreUpdate
    void alActualizar() {
        fechaActualizacion = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Incidente getIncidente() { return incidente; }
    public String getImpacto() { return impacto; }
    public void setImpacto(String impacto) { this.impacto = impacto; }
    public EstadoCausa getEstadoCausa() { return estadoCausa; }
    public void setEstadoCausa(EstadoCausa estadoCausa) { this.estadoCausa = estadoCausa; }
    public String getCausa() { return causa; }
    public void setCausa(String causa) { this.causa = causa; }
    public String getEvidenciaCausa() { return evidenciaCausa; }
    public void setEvidenciaCausa(String evidenciaCausa) { this.evidenciaCausa = evidenciaCausa; }
    public String getSolucion() { return solucion; }
    public void setSolucion(String solucion) { this.solucion = solucion; }
    public String getAccionesPreventivas() { return accionesPreventivas; }
    public void setAccionesPreventivas(String accionesPreventivas) { this.accionesPreventivas = accionesPreventivas; }
    public Usuario getAutor() { return autor; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
}
