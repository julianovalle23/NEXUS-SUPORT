package com.nexus.support.domain;

import java.math.BigDecimal;

import jakarta.persistence.*;

@Entity
@Table(name = "fuente_respuesta",
        uniqueConstraints = @UniqueConstraint(name = "uk_fuente", columnNames = {"respuesta_id", "articulo_id"}))
public class FuenteRespuesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "respuesta_id", nullable = false)
    private RespuestaIA respuesta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "articulo_id", nullable = false)
    private ArticuloConocimiento articulo;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal similitud;

    /**
     * true: el modelo citó el artículo como base de la respuesta (evidencia).
     * false: fue recuperado y entregado al modelo como contexto, pero no lo citó (candidato, solo auditoría).
     */
    @Column(nullable = false)
    private boolean usada = true;

    protected FuenteRespuesta() {
    }

    FuenteRespuesta(RespuestaIA respuesta, ArticuloConocimiento articulo, BigDecimal similitud, boolean usada) {
        this.respuesta = respuesta;
        this.articulo = articulo;
        this.similitud = similitud;
        this.usada = usada;
    }

    public Long getId() { return id; }
    public RespuestaIA getRespuesta() { return respuesta; }
    public ArticuloConocimiento getArticulo() { return articulo; }
    public BigDecimal getSimilitud() { return similitud; }
    public boolean isUsada() { return usada; }
}
