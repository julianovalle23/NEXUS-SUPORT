package com.nexus.support.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

import com.nexus.support.domain.ArticuloConocimiento;
import com.nexus.support.dto.CatalogoDtos.Ref;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ArticuloDtos {

    private ArticuloDtos() {
    }

    public record ArticuloRequest(
            @NotBlank(message = "El título es obligatorio") @Size(max = 200) String titulo,
            @NotBlank(message = "La descripción del problema es obligatoria") String problema,
            @NotBlank(message = "Los síntomas son obligatorios") String sintomas,
            @NotBlank(message = "La causa es obligatoria") String causa,
            @NotBlank(message = "El procedimiento de solución es obligatorio") String solucion,
            Long categoriaId,
            Boolean verificado) {
    }

    public record EstadoArticuloRequest(@NotNull Boolean activo, @NotNull Boolean verificado) {
    }

    public record ArticuloResponse(Long id, String titulo, String problema, String sintomas, String causa,
                                   String solucion, Ref categoria, boolean activo, boolean verificado,
                                   boolean fuenteValida, LocalDateTime fechaCreacion,
                                   LocalDateTime fechaActualizacion) {

        public static ArticuloResponse de(ArticuloConocimiento a) {
            return new ArticuloResponse(a.getId(), a.getTitulo(), a.getProblema(), a.getSintomas(), a.getCausa(),
                    a.getSolucion(), ref(a), a.isActivo(), a.isVerificado(), a.esFuenteValida(),
                    a.getFechaCreacion(), a.getFechaActualizacion());
        }
    }

    /**
     * Representación que consume n8n. El texto y su hash se calculan aquí para
     * que exista una sola definición de "qué se vectoriza" y el flujo pueda
     * omitir artículos sin cambios (evita duplicados y re-embeddings innecesarios).
     */
    public record ArticuloIndexable(Long id, String titulo, String categoria, String texto, String hash,
                                    boolean fuenteValida, LocalDateTime fechaActualizacion) {

        public static ArticuloIndexable de(ArticuloConocimiento a) {
            String texto = "Título: " + a.getTitulo()
                    + "\nCategoría: " + (a.getCategoria() == null ? "General" : a.getCategoria().getNombre())
                    + "\nProblema: " + a.getProblema()
                    + "\nSíntomas: " + a.getSintomas()
                    + "\nCausa: " + a.getCausa()
                    + "\nSolución: " + a.getSolucion();
            Ref cat = ref(a);
            return new ArticuloIndexable(a.getId(), a.getTitulo(), cat == null ? null : cat.nombre(), texto,
                    sha256(texto), a.esFuenteValida(), a.getFechaActualizacion());
        }
    }

    /** Una lista vacía es válida: n8n la envía cuando ningún candidato supera el umbral. */
    public record ValidarFuentesRequest(@NotNull List<Long> ids) {
    }

    /*
     * Las respuestas internas se envuelven en un objeto porque el nodo HTTP de n8n
     * convierte un arreglo JSON en varios ítems y un arreglo vacío detendría el flujo.
     */
    public record ArticulosIndexablesResponse(int total, List<ArticuloIndexable> articulos) {
    }

    public record FuentesValidasResponse(int total, List<FuenteContexto> fuentes) {
    }

    /** Fuente confirmada contra la base relacional, con el contenido que se envía al modelo. */
    public record FuenteContexto(Long id, String titulo, String problema, String sintomas, String causa,
                                 String solucion) {

        public static FuenteContexto de(ArticuloConocimiento a) {
            return new FuenteContexto(a.getId(), a.getTitulo(), a.getProblema(), a.getSintomas(), a.getCausa(),
                    a.getSolucion());
        }
    }

    private static Ref ref(ArticuloConocimiento a) {
        return a.getCategoria() == null ? null : new Ref(a.getCategoria().getId(), a.getCategoria().getNombre());
    }

    static String sha256(String texto) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
