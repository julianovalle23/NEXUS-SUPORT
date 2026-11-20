package com.nexus.support.domain;

import java.time.LocalDateTime;

import jakarta.persistence.*;

/**
 * Causa común de varios incidentes. La causa sin evidencia es solo una hipótesis: el estado
 * CAUSA_CONFIRMADA exige ambas (lo comprueba el servicio y, además, la base).
 */
@Entity
@Table(name = "problema")
public class Problema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(nullable = false, columnDefinition = "text")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoProblema estado = EstadoProblema.ABIERTO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "elemento_id")
    private ElementoTi elemento;

    @Column(columnDefinition = "text")
    private String causa;

    @Column(name = "evidencia_causa", columnDefinition = "text")
    private String evidenciaCausa;

    @Column(name = "solucion_temporal", columnDefinition = "text")
    private String solucionTemporal;

    @Column(name = "solucion_definitiva", columnDefinition = "text")
    private String solucionDefinitiva;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creador_id", nullable = false)
    private Usuario creador;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    protected Problema() {
    }

    public Problema(Usuario creador) {
        this.creador = creador;
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

    /** Al cerrar se registra la fecha de cierre; al reabrir se elimina. */
    public void cambiarEstado(EstadoProblema nuevo) {
        if (nuevo == EstadoProblema.CERRADO && fechaCierre == null) {
            fechaCierre = LocalDateTime.now();
        } else if (nuevo != EstadoProblema.CERRADO) {
            fechaCierre = null;
        }
        estado = nuevo;
    }

    /** true si hay causa y evidencia con contenido. */
    public boolean causaRespaldada() {
        return causa != null && !causa.isBlank() && evidenciaCausa != null && !evidenciaCausa.isBlank();
    }

    public Long getId() { return id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public EstadoProblema getEstado() { return estado; }
    public ElementoTi getElemento() { return elemento; }
    public void setElemento(ElementoTi elemento) { this.elemento = elemento; }
    public String getCausa() { return causa; }
    public void setCausa(String causa) { this.causa = causa; }
    public String getEvidenciaCausa() { return evidenciaCausa; }
    public void setEvidenciaCausa(String evidenciaCausa) { this.evidenciaCausa = evidenciaCausa; }
    public String getSolucionTemporal() { return solucionTemporal; }
    public void setSolucionTemporal(String solucionTemporal) { this.solucionTemporal = solucionTemporal; }
    public String getSolucionDefinitiva() { return solucionDefinitiva; }
    public void setSolucionDefinitiva(String solucionDefinitiva) { this.solucionDefinitiva = solucionDefinitiva; }
    public Usuario getCreador() { return creador; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }
}
