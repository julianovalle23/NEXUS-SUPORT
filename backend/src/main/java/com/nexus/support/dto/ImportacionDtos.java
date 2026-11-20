package com.nexus.support.dto;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.nexus.support.domain.Categoria;
import com.nexus.support.domain.FilaImportacion;
import com.nexus.support.domain.ImportacionArticulo;
import com.nexus.support.domain.Usuario;
import com.nexus.support.dto.CatalogoDtos.Ref;

public final class ImportacionDtos {

    private ImportacionDtos() {
    }

    /** {@code filas} = null: importar todos los registros válidos. */
    public record ConfirmarImportacionRequest(List<Long> filas) {
    }

    public record ImportacionResumen(Long id, String nombreArchivo, ImportacionArticulo.Formato formato,
                                     ImportacionArticulo.Estado estado, int tamanoBytes, int totalRegistros,
                                     Map<FilaImportacion.Estado, Long> registros, Ref categoria, String cargadoPor,
                                     LocalDateTime fechaCarga, String revisadoPor, LocalDateTime fechaRevision,
                                     String detalleError) {

        public static ImportacionResumen de(ImportacionArticulo i) {
            Map<FilaImportacion.Estado, Long> conteo = new EnumMap<>(FilaImportacion.Estado.class);
            for (FilaImportacion.Estado e : FilaImportacion.Estado.values()) {
                conteo.put(e, 0L);
            }
            i.getFilas().forEach(f -> conteo.merge(f.getEstado(), 1L, Long::sum));
            return new ImportacionResumen(i.getId(), i.getNombreArchivo(), i.getFormato(), i.getEstado(),
                    i.getTamanoBytes(), i.getTotalRegistros(), conteo, ref(i.getCategoria()), nombre(i.getCargadoPor()),
                    i.getFechaCarga(), nombre(i.getRevisadoPor()), i.getFechaRevision(), i.getDetalleError());
        }
    }

    public record FilaDto(Long id, int numero, String titulo, String problema, String sintomas, String causa,
                          String solucion, Ref categoria, FilaImportacion.Estado estado, List<String> errores,
                          Long articuloId) {

        public static FilaDto de(FilaImportacion f) {
            return new FilaDto(f.getId(), f.getNumero(), f.getTitulo(), f.getProblema(), f.getSintomas(),
                    f.getCausa(), f.getSolucion(), ref(f.getCategoria()), f.getEstado(), f.getErrores(),
                    f.getArticulo() == null ? null : f.getArticulo().getId());
        }
    }

    public record ImportacionDetalle(ImportacionResumen resumen, List<FilaDto> filas) {
        public static ImportacionDetalle de(ImportacionArticulo i) {
            return new ImportacionDetalle(ImportacionResumen.de(i), i.getFilas().stream().map(FilaDto::de).toList());
        }
    }

    private static Ref ref(Categoria c) {
        return c == null ? null : new Ref(c.getId(), c.getNombre());
    }

    private static String nombre(Usuario u) {
        return u == null ? null : u.getNombre();
    }
}
