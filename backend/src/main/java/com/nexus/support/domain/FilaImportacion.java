package com.nexus.support.domain;

import java.util.List;

import jakarta.persistence.*;

/** Un registro (artículo candidato) leído de un archivo de importación, con su resultado de validación. */
@Entity
@Table(name = "importacion_fila")
public class FilaImportacion {

    /**
     * VALIDA: se puede importar. INVALIDA: le faltan campos o no cumple las reglas. DUPLICADA: su título ya
     * existe en la base de conocimiento o se repite en el archivo. IMPORTADA: se creó el borrador.
     * DESCARTADA: era válida, pero no se seleccionó al confirmar o la importación se descartó.
     */
    public enum Estado { VALIDA, INVALIDA, DUPLICADA, IMPORTADA, DESCARTADA }

    private static final String SEPARADOR = "\n";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "importacion_id", nullable = false)
    private ImportacionArticulo importacion;

    /** Línea del archivo (CSV y TXT) o número de artículo (MD) donde empieza el registro. */
    @Column(nullable = false)
    private int numero;

    @Column(length = 300)
    private String titulo;

    @Column(columnDefinition = "text")
    private String problema;

    @Column(columnDefinition = "text")
    private String sintomas;

    @Column(columnDefinition = "text")
    private String causa;

    @Column(columnDefinition = "text")
    private String solucion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(length = 2000)
    private String errores;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "articulo_id")
    private ArticuloConocimiento articulo;

    protected FilaImportacion() {
    }

    public FilaImportacion(int numero, String titulo, String problema, String sintomas, String causa,
                           String solucion, Categoria categoria, Estado estado, List<String> errores) {
        this.numero = numero;
        this.titulo = titulo;
        this.problema = problema;
        this.sintomas = sintomas;
        this.causa = causa;
        this.solucion = solucion;
        this.categoria = categoria;
        this.estado = estado;
        setErrores(errores);
    }

    void asignar(ImportacionArticulo importacion) {
        this.importacion = importacion;
    }

    public void marcarImportada(ArticuloConocimiento articulo) {
        this.articulo = articulo;
        this.estado = Estado.IMPORTADA;
    }

    public void marcar(Estado estado, List<String> errores) {
        this.estado = estado;
        setErrores(errores);
    }

    private void setErrores(List<String> lista) {
        String texto = lista == null || lista.isEmpty() ? null : String.join(SEPARADOR, lista);
        this.errores = texto == null ? null : texto.substring(0, Math.min(texto.length(), 2000));
    }

    public List<String> getErrores() {
        return errores == null ? List.of() : List.of(errores.split(SEPARADOR));
    }

    public Long getId() { return id; }
    public ImportacionArticulo getImportacion() { return importacion; }
    public int getNumero() { return numero; }
    public String getTitulo() { return titulo; }
    public String getProblema() { return problema; }
    public String getSintomas() { return sintomas; }
    public String getCausa() { return causa; }
    public String getSolucion() { return solucion; }
    public Categoria getCategoria() { return categoria; }
    public Estado getEstado() { return estado; }
    public ArticuloConocimiento getArticulo() { return articulo; }
}
