package com.nexus.support.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexus.support.domain.ArticuloConocimiento;
import com.nexus.support.domain.Categoria;
import com.nexus.support.domain.FilaImportacion;
import com.nexus.support.domain.ImportacionArticulo;
import com.nexus.support.domain.ImportacionArticulo.Estado;
import com.nexus.support.domain.ImportacionArticulo.Formato;
import com.nexus.support.dto.ImportacionDtos.ImportacionDetalle;
import com.nexus.support.dto.ImportacionDtos.ImportacionResumen;
import com.nexus.support.exception.RecursoNoEncontradoException;
import com.nexus.support.exception.ReglaNegocioException;
import com.nexus.support.repository.ArticuloRepository;
import com.nexus.support.repository.CategoriaRepository;
import com.nexus.support.repository.ImportacionRepository;
import com.nexus.support.repository.UsuarioRepository;
import com.nexus.support.security.SesionActual;
import com.nexus.support.service.LectorArchivoArticulos.Campo;
import com.nexus.support.service.LectorArchivoArticulos.FormatoInvalidoException;
import com.nexus.support.service.LectorArchivoArticulos.Registro;

/**
 * Carga de artículos desde archivos TXT, MD o CSV, sin pasar por n8n.
 *
 * <ol>
 *   <li>Se registra la importación (RECIBIDO) con el SHA-256 del archivo: el mismo archivo no puede tener
 *       dos importaciones vigentes.</li>
 *   <li>Se procesa (PROCESANDO): se lee cada registro y se valida como si se escribiera a mano
 *       (campos obligatorios, longitudes, categoría existente, título único).</li>
 *   <li>Queda en PENDIENTE_REVISION con la vista previa por registro, o en ERROR si nada es válido.</li>
 *   <li>Al confirmar (IMPORTADO) se crean borradores <b>inactivos y sin verificar</b>: no son fuente del RAG
 *       ni se vectorizan hasta que alguien los revise, los active y los verifique en la base de conocimiento.</li>
 * </ol>
 * Cada paso confirma su propia transacción para que el estado intermedio sea visible y un fallo al procesar
 * deje la importación en ERROR en lugar de perderla.
 */
@Service
public class ImportacionService {

    private static final Logger log = LoggerFactory.getLogger(ImportacionService.class);

    public static final int MAX_BYTES = 1024 * 1024;
    public static final int MAX_REGISTROS = 200;
    static final int MAX_TITULO = 200;
    static final int MAX_CAMPO = 8000;
    /** Igual que NEXUS_MAX_CARACTERES_ARTICULO del flujo de indexación: un artículo más largo no se vectoriza. */
    static final int MAX_TEXTO_INDEXABLE = 20000;
    private static final int MAX_TITULO_GUARDADO = 300;
    private static final Set<Estado> NO_VIGENTES = EnumSet.of(Estado.ERROR, Estado.DESCARTADO);

    private final ImportacionRepository importaciones;
    private final ArticuloRepository articulos;
    private final CategoriaRepository categorias;
    private final UsuarioRepository usuarios;
    private final TransactionTemplate tx;

    public ImportacionService(ImportacionRepository importaciones, ArticuloRepository articulos,
                              CategoriaRepository categorias, UsuarioRepository usuarios, TransactionTemplate tx) {
        this.importaciones = importaciones;
        this.articulos = articulos;
        this.categorias = categorias;
        this.usuarios = usuarios;
        this.tx = tx;
    }

    // ---------------------------------------------------------------- Carga

    /**
     * Registra y procesa un archivo.
     *
     * @param categoriaId categoría para los registros que no indican una (opcional)
     * @return la importación con la vista previa de sus registros
     */
    public ImportacionDetalle cargar(String nombreOriginal, byte[] contenido, Long categoriaId) {
        if (contenido == null || contenido.length == 0) {
            throw new ReglaNegocioException("El archivo está vacío");
        }
        if (contenido.length > MAX_BYTES) {
            throw new ReglaNegocioException("El archivo supera el tamaño máximo permitido (1 MB)");
        }
        String nombre = nombreArchivo(nombreOriginal);
        Formato formato = formato(nombre);
        String hash = sha256(contenido);
        Long cuenta = SesionActual.cuenta().id();

        Long id = tx.execute(s -> {
            importaciones.findFirstByHashAndEstadoNotIn(hash, NO_VIGENTES).ifPresent(previa -> {
                throw new ReglaNegocioException("Este archivo ya se cargó en la importación #" + previa.getId()
                        + " (" + previa.getEstado() + "). Descarte esa importación para volver a cargarlo.");
            });
            Categoria categoria = categoriaId == null ? null : categorias.findById(categoriaId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Categoría", categoriaId));
            return importaciones.save(new ImportacionArticulo(nombre, formato, hash, contenido.length, categoria,
                    usuarios.getReferenceById(cuenta))).getId();
        });

        try {
            tx.executeWithoutResult(s -> entidad(id).marcarProcesando());
            tx.executeWithoutResult(s -> procesar(entidad(id), contenido));
        } catch (FormatoInvalidoException e) {
            tx.executeWithoutResult(s -> entidad(id).fallar(e.getMessage()));
        } catch (RuntimeException e) {
            // Sin el contenido del archivo en el mensaje ni en el registro.
            log.error("Fallo inesperado procesando la importación {}", id, e);
            tx.executeWithoutResult(s -> entidad(id).fallar("Error inesperado al procesar el archivo"));
        }
        return detalle(id);
    }

    private void procesar(ImportacionArticulo imp, byte[] contenido) {
        String texto = LectorArchivoArticulos.decodificar(contenido);
        List<Registro> registros = LectorArchivoArticulos.leer(imp.getFormato(), texto);
        if (registros.size() > MAX_REGISTROS) {
            throw new FormatoInvalidoException("El archivo tiene " + registros.size() + " artículos; el máximo por carga es "
                    + MAX_REGISTROS);
        }
        Map<String, Categoria> porNombre = categorias.findAll().stream()
                .collect(Collectors.toMap(c -> LectorArchivoArticulos.normalizarClave(c.getNombre()), Function.identity(),
                        (a, b) -> a));
        Set<String> titulos = registros.stream().map(r -> clave(r.valor(Campo.TITULO))).filter(t -> !t.isEmpty())
                .collect(Collectors.toSet());
        Set<String> existentes = titulos.isEmpty() ? Set.of() : new HashSet<>(articulos.titulosExistentes(titulos));
        Map<String, Integer> vistos = new HashMap<>();

        for (Registro r : registros) {
            List<String> errores = new ArrayList<>(r.errores());
            LectorArchivoArticulos.OBLIGATORIOS.stream()
                    .filter(c -> r.valor(c) == null || r.valor(c).isBlank())
                    .forEach(c -> errores.add("Falta el campo " + LectorArchivoArticulos.nombre(c)));
            String titulo = vacioANulo(r.valor(Campo.TITULO));
            if (titulo != null && titulo.length() > MAX_TITULO) {
                errores.add("El título tiene " + titulo.length() + " caracteres; el máximo es " + MAX_TITULO);
            }
            for (Campo c : List.of(Campo.PROBLEMA, Campo.SINTOMAS, Campo.CAUSA, Campo.SOLUCION)) {
                String v = r.valor(c);
                if (v != null && v.length() > MAX_CAMPO) {
                    errores.add("El campo " + LectorArchivoArticulos.nombre(c) + " tiene " + v.length()
                            + " caracteres; el máximo es " + MAX_CAMPO);
                }
            }
            Categoria categoria = imp.getCategoria();
            String nombreCategoria = vacioANulo(r.valor(Campo.CATEGORIA));
            if (nombreCategoria != null) {
                categoria = porNombre.get(LectorArchivoArticulos.normalizarClave(nombreCategoria));
                if (categoria == null) {
                    errores.add("La categoría «" + recortar(nombreCategoria) + "» no existe");
                }
            }
            int largo = LectorArchivoArticulos.OBLIGATORIOS.stream()
                    .mapToInt(c -> r.valor(c) == null ? 0 : r.valor(c).length()).sum();
            if (largo > MAX_TEXTO_INDEXABLE) {
                errores.add("El artículo tiene " + largo + " caracteres en total; la indexación admite " + MAX_TEXTO_INDEXABLE);
            }

            FilaImportacion.Estado estado = FilaImportacion.Estado.VALIDA;
            if (!errores.isEmpty()) {
                estado = FilaImportacion.Estado.INVALIDA;
            } else if (existentes.contains(clave(titulo))) {
                estado = FilaImportacion.Estado.DUPLICADA;
                errores.add("Ya existe un artículo con este título en la base de conocimiento");
            } else if (vistos.containsKey(clave(titulo))) {
                estado = FilaImportacion.Estado.DUPLICADA;
                errores.add("El título se repite: ya aparece en el registro de la línea " + vistos.get(clave(titulo)));
            }
            if (titulo != null) {
                vistos.putIfAbsent(clave(titulo), r.numero());
            }
            imp.agregarFila(new FilaImportacion(r.numero(), recortarTitulo(titulo), vacioANulo(r.valor(Campo.PROBLEMA)),
                    vacioANulo(r.valor(Campo.SINTOMAS)), vacioANulo(r.valor(Campo.CAUSA)),
                    vacioANulo(r.valor(Campo.SOLUCION)), categoria, estado, errores));
        }
        imp.terminarProcesamiento();
    }

    // ---------------------------------------------------------------- Revisión

    /**
     * Crea los borradores de los registros seleccionados (todos los válidos si {@code filaIds} es null).
     * Los válidos no seleccionados quedan DESCARTADA. Los borradores nacen inactivos y sin verificar.
     */
    public ImportacionDetalle confirmar(Long id, List<Long> filaIds) {
        Long cuenta = SesionActual.cuenta().id();
        tx.executeWithoutResult(s -> {
            ImportacionArticulo imp = paraRevisar(id);
            List<FilaImportacion> validas = imp.getFilas().stream()
                    .filter(f -> f.getEstado() == FilaImportacion.Estado.VALIDA).toList();
            Set<Long> seleccion = filaIds == null
                    ? validas.stream().map(FilaImportacion::getId).collect(Collectors.toSet())
                    : new HashSet<>(filaIds);
            Map<Long, FilaImportacion> porId = imp.getFilas().stream()
                    .collect(Collectors.toMap(FilaImportacion::getId, Function.identity()));
            for (Long filaId : seleccion) {
                FilaImportacion f = porId.get(filaId);
                if (f == null) {
                    throw new ReglaNegocioException("El registro " + filaId + " no pertenece a la importación #" + id);
                }
                if (f.getEstado() != FilaImportacion.Estado.VALIDA) {
                    throw new ReglaNegocioException("El registro de la línea " + f.getNumero() + " no se puede importar ("
                            + f.getEstado() + ")");
                }
            }
            if (seleccion.isEmpty()) {
                throw new ReglaNegocioException("Seleccione al menos un registro válido o descarte la importación");
            }

            int creados = 0;
            for (FilaImportacion f : validas) {
                if (!seleccion.contains(f.getId())) {
                    f.marcar(FilaImportacion.Estado.DESCARTADA, List.of());
                } else if (articulos.existsByTituloIgnoreCase(f.getTitulo())) {
                    f.marcar(FilaImportacion.Estado.DUPLICADA,
                            List.of("Se creó otro artículo con este título después de cargar el archivo"));
                } else {
                    f.marcarImportada(articulos.save(borrador(f, imp)));
                    creados++;
                }
            }
            if (creados == 0) {
                throw new ReglaNegocioException("No se creó ningún artículo: los títulos seleccionados ya existen");
            }
            imp.revisar(Estado.IMPORTADO, usuarios.getReferenceById(cuenta));
        });
        return detalle(id);
    }

    public ImportacionDetalle descartar(Long id) {
        Long cuenta = SesionActual.cuenta().id();
        tx.executeWithoutResult(s -> {
            ImportacionArticulo imp = paraRevisar(id);
            imp.getFilas().stream().filter(f -> f.getEstado() == FilaImportacion.Estado.VALIDA)
                    .forEach(f -> f.marcar(FilaImportacion.Estado.DESCARTADA, List.of()));
            imp.revisar(Estado.DESCARTADO, usuarios.getReferenceById(cuenta));
        });
        return detalle(id);
    }

    // ---------------------------------------------------------------- Consulta

    public List<ImportacionResumen> listar() {
        return tx.execute(s -> importaciones.findTop100ByOrderByFechaCargaDescIdDesc().stream()
                .map(ImportacionResumen::de).toList());
    }

    public ImportacionDetalle detalle(Long id) {
        return tx.execute(s -> ImportacionDetalle.de(entidad(id)));
    }

    // ---------------------------------------------------------------- Apoyo

    /** Borrador: nunca activo ni verificado; el administrador lo revisa en la base de conocimiento. */
    private static ArticuloConocimiento borrador(FilaImportacion f, ImportacionArticulo imp) {
        ArticuloConocimiento a = new ArticuloConocimiento();
        a.setTitulo(f.getTitulo());
        a.setProblema(f.getProblema());
        a.setSintomas(f.getSintomas());
        a.setCausa(f.getCausa());
        a.setSolucion(f.getSolucion());
        a.setCategoria(f.getCategoria());
        a.setActivo(false);
        a.setVerificado(false);
        a.setImportacion(imp);
        return a;
    }

    private ImportacionArticulo paraRevisar(Long id) {
        ImportacionArticulo imp = importaciones.findParaModificar(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Importación", id));
        if (imp.getEstado() != Estado.PENDIENTE_REVISION) {
            throw new ReglaNegocioException("La importación #" + id + " está en estado " + imp.getEstado()
                    + "; solo se revisan las que están en PENDIENTE_REVISION");
        }
        return imp;
    }

    private ImportacionArticulo entidad(Long id) {
        return importaciones.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Importación", id));
    }

    private static Formato formato(String nombre) {
        try {
            return LectorArchivoArticulos.formatoDe(nombre);
        } catch (FormatoInvalidoException e) {
            throw new ReglaNegocioException(e.getMessage());
        }
    }

    /** Solo el nombre base, sin rutas ni caracteres de control, y como máximo 255 caracteres. */
    static String nombreArchivo(String original) {
        String n = original == null ? "" : original.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\x00-\\x1F\\x7F]", "").strip();
        if (n.isEmpty()) {
            throw new ReglaNegocioException("El archivo no tiene nombre");
        }
        return n.length() <= 255 ? n : n.substring(n.length() - 255);
    }

    private static String clave(String titulo) {
        return titulo == null ? "" : titulo.strip().toLowerCase(Locale.ROOT);
    }

    private static String vacioANulo(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    private static String recortarTitulo(String t) {
        return t == null || t.length() <= MAX_TITULO_GUARDADO ? t : t.substring(0, MAX_TITULO_GUARDADO);
    }

    private static String recortar(String s) {
        return s.length() <= 60 ? s : s.substring(0, 60) + "…";
    }

    static String sha256(byte[] contenido) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
