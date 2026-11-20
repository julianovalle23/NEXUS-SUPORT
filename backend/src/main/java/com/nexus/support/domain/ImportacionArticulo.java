package com.nexus.support.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

/**
 * Un archivo TXT, MD o CSV cargado para crear artículos de conocimiento.
 *
 * <p>Ciclo de vida: RECIBIDO → PROCESANDO → PENDIENTE_REVISION (hay al menos un registro válido) o ERROR
 * (formato ilegible o ningún registro válido). Desde PENDIENTE_REVISION, quien administra la base de
 * conocimiento confirma (IMPORTADO: se crean borradores inactivos y sin verificar) o descarta (DESCARTADO).
 */
@Entity
@Table(name = "importacion_articulo")
public class ImportacionArticulo {

    public enum Formato { TXT, MD, CSV }

    public enum Estado { RECIBIDO, PROCESANDO, PENDIENTE_REVISION, IMPORTADO, ERROR, DESCARTADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Formato formato;

    /** SHA-256 del contenido del archivo, para no procesar dos veces el mismo. */
    @Column(nullable = false, length = 64)
    private String hash;

    @Column(name = "tamano_bytes", nullable = false)
    private int tamanoBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado = Estado.RECIBIDO;

    @Column(name = "total_registros", nullable = false)
    private int totalRegistros;

    /** Motivo por el que el archivo no pudo procesarse. No incluye el contenido del archivo. */
    @Column(name = "detalle_error", length = 1000)
    private String detalleError;

    /** Categoría que se asigna a los registros que no indican una. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cargado_por_id", nullable = false)
    private Usuario cargadoPor;

    @Column(name = "fecha_carga", nullable = false)
    private LocalDateTime fechaCarga;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revisado_por_id")
    private Usuario revisadoPor;

    @Column(name = "fecha_revision")
    private LocalDateTime fechaRevision;

    @OneToMany(mappedBy = "importacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numero")
    private List<FilaImportacion> filas = new ArrayList<>();

    protected ImportacionArticulo() {
    }

    public ImportacionArticulo(String nombreArchivo, Formato formato, String hash, int tamanoBytes,
                               Categoria categoria, Usuario cargadoPor) {
        this.nombreArchivo = nombreArchivo;
        this.formato = formato;
        this.hash = hash;
        this.tamanoBytes = tamanoBytes;
        this.categoria = categoria;
        this.cargadoPor = cargadoPor;
    }

    @PrePersist
    void alCrear() {
        fechaCarga = LocalDateTime.now();
    }

    public void agregarFila(FilaImportacion fila) {
        fila.asignar(this);
        filas.add(fila);
    }

    public void marcarProcesando() {
        estado = Estado.PROCESANDO;
    }

    /** Fin del procesamiento: revisable si hay al menos un registro válido; si no, ERROR con el motivo. */
    public void terminarProcesamiento() {
        totalRegistros = filas.size();
        boolean hayValidas = filas.stream().anyMatch(f -> f.getEstado() == FilaImportacion.Estado.VALIDA);
        estado = hayValidas ? Estado.PENDIENTE_REVISION : Estado.ERROR;
        detalleError = hayValidas ? null : "Ningún registro del archivo es válido; revise los errores de cada registro";
    }

    public void fallar(String motivo) {
        estado = Estado.ERROR;
        detalleError = motivo == null ? null : motivo.substring(0, Math.min(motivo.length(), 1000));
    }

    public void revisar(Estado resultado, Usuario revisor) {
        estado = resultado;
        revisadoPor = revisor;
        fechaRevision = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getNombreArchivo() { return nombreArchivo; }
    public Formato getFormato() { return formato; }
    public String getHash() { return hash; }
    public int getTamanoBytes() { return tamanoBytes; }
    public Estado getEstado() { return estado; }
    public int getTotalRegistros() { return totalRegistros; }
    public String getDetalleError() { return detalleError; }
    public Categoria getCategoria() { return categoria; }
    public Usuario getCargadoPor() { return cargadoPor; }
    public LocalDateTime getFechaCarga() { return fechaCarga; }
    public Usuario getRevisadoPor() { return revisadoPor; }
    public LocalDateTime getFechaRevision() { return fechaRevision; }
    public List<FilaImportacion> getFilas() { return filas; }
}
