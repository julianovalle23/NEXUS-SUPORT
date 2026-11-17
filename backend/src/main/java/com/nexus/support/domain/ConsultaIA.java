package com.nexus.support.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "consulta_ia")
public class ConsultaIA {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incidente_id", nullable = false)
    private Incidente incidente;

    @Column(nullable = false, columnDefinition = "text")
    private String pregunta;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoConsulta estado = EstadoConsulta.PENDIENTE;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal umbral;

    @Column(name = "detalle_error", columnDefinition = "text")
    private String detalleError;

    @OneToOne(mappedBy = "consulta", fetch = FetchType.LAZY)
    private RespuestaIA respuesta;

    protected ConsultaIA() {
    }

    public ConsultaIA(Incidente incidente, String pregunta, BigDecimal umbral) {
        this.incidente = incidente;
        this.pregunta = pregunta;
        this.umbral = umbral;
    }

    @PrePersist
    void alCrear() {
        if (fecha == null) fecha = LocalDateTime.now();
    }

    public void marcarError(String detalle) {
        this.estado = EstadoConsulta.ERROR;
        this.detalleError = detalle;
    }

    public Long getId() { return id; }
    public Incidente getIncidente() { return incidente; }
    public String getPregunta() { return pregunta; }
    public LocalDateTime getFecha() { return fecha; }
    public EstadoConsulta getEstado() { return estado; }
    public void setEstado(EstadoConsulta estado) { this.estado = estado; }
    public BigDecimal getUmbral() { return umbral; }
    public String getDetalleError() { return detalleError; }
    public RespuestaIA getRespuesta() { return respuesta; }
    public void setRespuesta(RespuestaIA respuesta) { this.respuesta = respuesta; }
}
