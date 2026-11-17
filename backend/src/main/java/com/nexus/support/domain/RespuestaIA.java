package com.nexus.support.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

@Entity
@Table(name = "respuesta_ia")
public class RespuestaIA {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consulta_id", nullable = false, unique = true)
    private ConsultaIA consulta;

    @Column(nullable = false, columnDefinition = "text")
    private String respuesta;

    @Column(columnDefinition = "text")
    private String pasos;

    @Column(length = 120)
    private String modelo;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @OneToMany(mappedBy = "respuesta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("similitud DESC")
    private List<FuenteRespuesta> fuentes = new ArrayList<>();

    @OneToOne(mappedBy = "respuesta", fetch = FetchType.LAZY)
    private Calificacion calificacion;

    protected RespuestaIA() {
    }

    public RespuestaIA(ConsultaIA consulta, String respuesta, String pasos, String modelo) {
        this.consulta = consulta;
        this.respuesta = respuesta;
        this.pasos = pasos;
        this.modelo = modelo;
    }

    @PrePersist
    void alCrear() {
        if (fecha == null) fecha = LocalDateTime.now();
    }

    public void agregarFuente(ArticuloConocimiento articulo, BigDecimal similitud) {
        fuentes.add(new FuenteRespuesta(this, articulo, similitud));
    }

    public Long getId() { return id; }
    public ConsultaIA getConsulta() { return consulta; }
    public String getRespuesta() { return respuesta; }
    public String getPasos() { return pasos; }
    public String getModelo() { return modelo; }
    public LocalDateTime getFecha() { return fecha; }
    public List<FuenteRespuesta> getFuentes() { return fuentes; }
    public Calificacion getCalificacion() { return calificacion; }
}
