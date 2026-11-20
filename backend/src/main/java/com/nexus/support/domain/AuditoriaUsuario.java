package com.nexus.support.domain;

import java.time.LocalDateTime;

import jakarta.persistence.*;

/** Cambio de perfil o de estado de una cuenta (solo inserción). */
@Entity
@Table(name = "auditoria_usuario")
public class AuditoriaUsuario {

    /** API = lo hizo un administrador; ARRANQUE = cuenta de administración inicial (sin actor). */
    public enum Origen { API, ARRANQUE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Origen origen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private Usuario actor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_anterior", length = 20)
    private Rol rolAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_nuevo", nullable = false, length = 20)
    private Rol rolNuevo;

    @Column(name = "activo_anterior")
    private Boolean activoAnterior;

    @Column(name = "activo_nuevo", nullable = false)
    private boolean activoNuevo;

    protected AuditoriaUsuario() {
    }

    /**
     * @param rolAnterior    nulo si la cuenta se acaba de crear
     * @param activoAnterior nulo si la cuenta se acaba de crear
     */
    public AuditoriaUsuario(Origen origen, Usuario actor, Usuario usuario, Rol rolAnterior, Boolean activoAnterior) {
        this.fecha = LocalDateTime.now();
        this.origen = origen;
        this.actor = actor;
        this.usuario = usuario;
        this.rolAnterior = rolAnterior;
        this.activoAnterior = activoAnterior;
        this.rolNuevo = usuario.getRol();
        this.activoNuevo = usuario.isActivo();
    }

    public Long getId() { return id; }
    public LocalDateTime getFecha() { return fecha; }
    public Origen getOrigen() { return origen; }
    public Usuario getActor() { return actor; }
    public Usuario getUsuario() { return usuario; }
    public Rol getRolAnterior() { return rolAnterior; }
    public Rol getRolNuevo() { return rolNuevo; }
    public Boolean getActivoAnterior() { return activoAnterior; }
    public boolean isActivoNuevo() { return activoNuevo; }
}
