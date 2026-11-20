package com.nexus.support.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.*;

/**
 * Con @DynamicUpdate el UPDATE solo incluye las columnas modificadas, así una operación que solo
 * vincula un problema no puede reescribir el estado que otra transacción acaba de cambiar.
 */
@Entity
@DynamicUpdate
@Table(name = "incidente")
public class Incidente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(nullable = false, columnDefinition = "text")
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prioridad_id", nullable = false)
    private Prioridad prioridad;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estado_id", nullable = false)
    private EstadoIncidente estado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Persona que reporta el incidente (texto libre, opcional). El usuario es el técnico responsable. */
    @Column(length = 120)
    private String solicitante;

    /** Cuenta del solicitante que registró el incidente; nula si lo registró un técnico. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitante_id")
    private Usuario cuentaSolicitante;

    /** Equipo, aplicación o servicio afectado (opcional). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "elemento_id")
    private ElementoTi elemento;

    /** Ingreso guiado (opcional): mensaje de error exacto que ve el usuario. */
    @Column(name = "mensaje_error", length = 1000)
    private String mensajeError;

    /** Ingreso guiado (opcional): lo que ya se intentó para resolverlo. */
    @Column(name = "pasos_intentados", length = 2000)
    private String pasosIntentados;

    /** Problema al que pertenece (opcional); se gestiona desde ProblemaService. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problema_id")
    private Problema problema;

    /** Incidente cerrado del que este es seguimiento; el original no se modifica. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seguimiento_de_id", updatable = false)
    private Incidente seguimientoDe;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;

    @PrePersist
    void alCrear() {
        LocalDateTime ahora = LocalDateTime.now();
        if (fechaCreacion == null) fechaCreacion = ahora;
        // Un incidente creado directamente como resuelto no puede resolverse "antes" de crearse
        if (fechaResolucion != null && fechaResolucion.isBefore(fechaCreacion)) fechaResolucion = fechaCreacion;
        fechaActualizacion = ahora;
    }

    @PreUpdate
    void alActualizar() {
        fechaActualizacion = LocalDateTime.now();
    }

    /**
     * Cambia el estado manteniendo la coherencia de fechas: al pasar a un estado
     * final se registra la resolución; al reabrir se elimina.
     */
    public void cambiarEstado(EstadoIncidente nuevo) {
        if (nuevo.isEsFinal() && fechaResolucion == null) {
            fechaResolucion = LocalDateTime.now();
        } else if (!nuevo.isEsFinal()) {
            fechaResolucion = null;
        }
        this.estado = nuevo;
    }

    public Long getId() { return id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }
    public Prioridad getPrioridad() { return prioridad; }
    public void setPrioridad(Prioridad prioridad) { this.prioridad = prioridad; }
    public EstadoIncidente getEstado() { return estado; }
    public void setEstado(EstadoIncidente estado) { this.estado = estado; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public String getSolicitante() { return solicitante; }
    public void setSolicitante(String solicitante) { this.solicitante = solicitante; }
    public Usuario getCuentaSolicitante() { return cuentaSolicitante; }
    public void setCuentaSolicitante(Usuario cuentaSolicitante) { this.cuentaSolicitante = cuentaSolicitante; }
    public ElementoTi getElemento() { return elemento; }
    public void setElemento(ElementoTi elemento) { this.elemento = elemento; }
    public String getMensajeError() { return mensajeError; }
    public void setMensajeError(String mensajeError) { this.mensajeError = mensajeError; }
    public String getPasosIntentados() { return pasosIntentados; }
    public void setPasosIntentados(String pasosIntentados) { this.pasosIntentados = pasosIntentados; }
    public Problema getProblema() { return problema; }
    public void setProblema(Problema problema) { this.problema = problema; }
    public Incidente getSeguimientoDe() { return seguimientoDe; }
    public void setSeguimientoDe(Incidente seguimientoDe) { this.seguimientoDe = seguimientoDe; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public LocalDateTime getFechaResolucion() { return fechaResolucion; }
}
