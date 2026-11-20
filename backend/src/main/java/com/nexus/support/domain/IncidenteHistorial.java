package com.nexus.support.domain;

import java.time.LocalDateTime;

import jakarta.persistence.*;

/**
 * Instantánea de estado, prioridad y responsable de un incidente tras su creación o un cambio.
 * Es de solo inserción: no tiene setters.
 */
@Entity
@Table(name = "incidente_historial")
public class IncidenteHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incidente_id", nullable = false)
    private Incidente incidente;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoHistorial tipo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estado_id", nullable = false)
    private EstadoIncidente estado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prioridad_id", nullable = false)
    private Prioridad prioridad;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsable_id", nullable = false)
    private Usuario responsable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private Usuario actor;

    /** Solo en REAPERTURA (obligatorio, ver ck_historial_motivo). */
    @Column(columnDefinition = "text")
    private String motivo;

    protected IncidenteHistorial() {
    }

    private IncidenteHistorial(Incidente i, TipoHistorial tipo, LocalDateTime fecha, Usuario actor) {
        this.incidente = i;
        this.tipo = tipo;
        this.fecha = fecha;
        this.estado = i.getEstado();
        this.prioridad = i.getPrioridad();
        this.responsable = i.getUsuario();
        this.actor = actor;
    }

    /** La fila de creación lleva la misma fecha que el incidente para que la línea de tiempo empiece en él. */
    public static IncidenteHistorial creacion(Incidente i, Usuario actor) {
        return new IncidenteHistorial(i, TipoHistorial.CREACION, i.getFechaCreacion(), actor);
    }

    public static IncidenteHistorial actualizacion(Incidente i, Usuario actor) {
        return new IncidenteHistorial(i, TipoHistorial.ACTUALIZACION, LocalDateTime.now(), actor);
    }

    /** Reapertura excepcional: queda registrado quién la hizo y por qué. */
    public static IncidenteHistorial reapertura(Incidente i, Usuario actor, String motivo) {
        IncidenteHistorial h = new IncidenteHistorial(i, TipoHistorial.REAPERTURA, LocalDateTime.now(), actor);
        h.motivo = motivo;
        return h;
    }

    public Long getId() { return id; }
    public Incidente getIncidente() { return incidente; }
    public LocalDateTime getFecha() { return fecha; }
    public TipoHistorial getTipo() { return tipo; }
    public EstadoIncidente getEstado() { return estado; }
    public Prioridad getPrioridad() { return prioridad; }
    public Usuario getResponsable() { return responsable; }
    public Usuario getActor() { return actor; }
    public String getMotivo() { return motivo; }
}
