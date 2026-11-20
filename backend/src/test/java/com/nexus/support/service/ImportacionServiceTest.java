package com.nexus.support.service;

import static com.nexus.support.service.Datos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexus.support.domain.ArticuloConocimiento;
import com.nexus.support.domain.Categoria;
import com.nexus.support.domain.FilaImportacion;
import com.nexus.support.domain.ImportacionArticulo;
import com.nexus.support.domain.ImportacionArticulo.Estado;
import com.nexus.support.domain.Rol;
import com.nexus.support.domain.Usuario;
import com.nexus.support.dto.ImportacionDtos.FilaDto;
import com.nexus.support.dto.ImportacionDtos.ImportacionDetalle;
import com.nexus.support.exception.ReglaNegocioException;
import com.nexus.support.repository.ArticuloRepository;
import com.nexus.support.repository.CategoriaRepository;
import com.nexus.support.repository.ImportacionRepository;
import com.nexus.support.repository.UsuarioRepository;

/**
 * Carga, validación y revisión de importaciones. Los repositorios se simulan en memoria: la importación
 * guardada es la misma instancia que devuelven las búsquedas, como haría el contexto de persistencia.
 * El índice único parcial por hash y el bloqueo de la fila solo se comprueban con PostgreSQL.
 */
class ImportacionServiceTest {

    private static final String CABECERA = "titulo,problema,sintomas,causa,solucion,categoria\n";

    private final Usuario admin = usuario(20, "Admin Nexus", Rol.ADMIN);
    private final Categoria redes = categoria(1, "Redes");
    private final Categoria hardware = categoria(2, "Hardware");

    private ImportacionRepository importaciones;
    private ArticuloRepository articulos;
    private ImportacionService service;
    private ImportacionArticulo guardada;
    private final List<ArticuloConocimiento> creados = new ArrayList<>();

    @BeforeEach
    void setUp() {
        importaciones = mock(ImportacionRepository.class);
        articulos = mock(ArticuloRepository.class);
        CategoriaRepository categorias = mock(CategoriaRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        PlatformTransactionManager ptm = mock(PlatformTransactionManager.class);
        when(ptm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new ImportacionService(importaciones, articulos, categorias, usuarios, new TransactionTemplate(ptm));

        when(categorias.findAll()).thenReturn(List.of(redes, hardware));
        when(categorias.findById(2L)).thenReturn(Optional.of(hardware));
        when(usuarios.getReferenceById(20L)).thenReturn(admin);
        when(importaciones.findFirstByHashAndEstadoNotIn(anyString(), anyCollection())).thenReturn(Optional.empty());
        when(importaciones.save(any())).thenAnswer(inv -> {
            guardada = inv.getArgument(0);
            ReflectionTestUtils.setField(guardada, "id", 5L);
            ReflectionTestUtils.invokeMethod(guardada, "alCrear");
            return guardada;
        });
        when(importaciones.findById(5L)).thenAnswer(inv -> Optional.ofNullable(guardada));
        when(importaciones.findParaModificar(5L)).thenAnswer(inv -> Optional.ofNullable(guardada));
        when(articulos.titulosExistentes(anyCollection())).thenReturn(List.of());
        AtomicLong ids = new AtomicLong(100);
        when(articulos.save(any())).thenAnswer(inv -> {
            ArticuloConocimiento a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", ids.getAndIncrement());
            creados.add(a);
            return a;
        });
        sesion(admin);
    }

    @AfterEach
    void tearDown() {
        cerrarSesion();
    }

    // ---------------------------------------------------------------- Carga y validación

    @Test
    void unArchivoValidoQuedaPendienteDeRevisionSinCrearArticulos() {
        ImportacionDetalle d = cargar("base.csv", CABECERA
                + "VPN no conecta,Error 809,Tiempo agotado,Puerto bloqueado,Abrir puerto,Redes\n"
                + "Impresora,No imprime,Cola detenida,Spooler,Reiniciar spooler,hardware\n");

        assertThat(d.resumen().estado()).isEqualTo(Estado.PENDIENTE_REVISION);
        assertThat(d.resumen().formato()).isEqualTo(ImportacionArticulo.Formato.CSV);
        assertThat(d.resumen().totalRegistros()).isEqualTo(2);
        assertThat(d.resumen().registros()).containsEntry(FilaImportacion.Estado.VALIDA, 2L);
        assertThat(d.resumen().cargadoPor()).isEqualTo("Admin Nexus");
        assertThat(d.filas()).extracting(f -> f.categoria().nombre())
                .as("la categoría se reconoce sin importar mayúsculas").containsExactly("Redes", "Hardware");
        assertThat(guardada.getHash()).matches("[0-9a-f]{64}");
        verify(articulos, never()).save(any());
    }

    @Test
    void cadaRegistroQuedaValidoInvalidoODuplicadoConSuMotivo() {
        when(articulos.titulosExistentes(anyCollection())).thenReturn(List.of("impresora"));

        ImportacionDetalle d = cargar("base.csv", CABECERA
                + "VPN no conecta,Error 809,Tiempo agotado,Puerto bloqueado,Abrir puerto,\n"   // válido
                + "Sin causa,Problema,Síntomas,,Solución,\n"                                   // falta causa
                + "Impresora,No imprime,Cola,Spooler,Reiniciar,\n"                             // ya existe
                + "vpn NO conecta,Otro,Otro,Otro,Otro,\n"                                      // repetido en el archivo
                + "Teclado,No escribe,Nada,USB,Cambiar puerto,Periféricos\n");                 // categoría inexistente

        assertThat(d.resumen().estado()).isEqualTo(Estado.PENDIENTE_REVISION);
        assertThat(d.filas()).extracting(FilaDto::numero, FilaDto::estado).containsExactly(
                tuple(2, FilaImportacion.Estado.VALIDA),
                tuple(3, FilaImportacion.Estado.INVALIDA),
                tuple(4, FilaImportacion.Estado.DUPLICADA),
                tuple(5, FilaImportacion.Estado.DUPLICADA),
                tuple(6, FilaImportacion.Estado.INVALIDA));
        assertThat(d.filas().get(1).errores()).containsExactly("Falta el campo causa");
        assertThat(d.filas().get(2).errores()).singleElement().asString().contains("Ya existe");
        assertThat(d.filas().get(3).errores()).singleElement().asString().contains("línea 2");
        assertThat(d.filas().get(4).errores()).singleElement().asString().contains("Periféricos").contains("no existe");
    }

    @Test
    void losLimitesDeLongitudSeValidanPorRegistro() {
        String largo = "x".repeat(ImportacionService.MAX_CAMPO + 1);
        String titulo = "T".repeat(ImportacionService.MAX_TITULO + 1);

        ImportacionDetalle d = cargar("base.txt", "Título: " + titulo
                + "\nProblema: p\nSíntomas: s\nCausa: c\nSolución: " + largo + "\n");

        assertThat(d.resumen().estado()).as("sin registros válidos").isEqualTo(Estado.ERROR);
        assertThat(d.filas().get(0).errores()).anySatisfy(e -> assertThat(e).contains("título"))
                .anySatisfy(e -> assertThat(e).contains("solución"));
        assertThat(d.filas().get(0).titulo()).hasSizeLessThanOrEqualTo(300);
    }

    @Test
    void laCategoriaPorDefectoSeAplicaALosRegistrosQueNoIndicanUna() {
        ImportacionDetalle d = service.cargar("base.md",
                bytes("# Monitor\n## Problema\nNegro\n## Síntomas\nSin imagen\n## Causa\nCable\n## Solución\nCambiar cable\n"),
                2L);

        assertThat(d.resumen().categoria().nombre()).isEqualTo("Hardware");
        assertThat(d.filas()).singleElement().satisfies(f -> assertThat(f.categoria().nombre()).isEqualTo("Hardware"));
    }

    @Test
    void sinRegistrosValidosLaImportacionQuedaEnError() {
        ImportacionDetalle d = cargar("base.csv", CABECERA + "Solo título,,,,,\n");

        assertThat(d.resumen().estado()).isEqualTo(Estado.ERROR);
        assertThat(d.resumen().detalleError()).contains("Ningún registro");
        assertThat(d.filas()).singleElement().satisfies(f -> assertThat(f.errores()).hasSize(4));
    }

    @Test
    void unArchivoIlegibleQuedaEnErrorSinCopiarSuContenidoAlMensaje() {
        ImportacionDetalle d = cargar("base.csv", "titulo,problema\nSECRETO-INTERNO,dato\n");

        assertThat(d.resumen().estado()).isEqualTo(Estado.ERROR);
        assertThat(d.resumen().detalleError()).contains("columnas obligatorias").doesNotContain("SECRETO-INTERNO");
        assertThat(d.filas()).isEmpty();
    }

    @Test
    void unaCodificacionNoUtf8QuedaEnError() {
        ImportacionDetalle d = service.cargar("base.txt", "Título: Señal".getBytes(StandardCharsets.ISO_8859_1), null);

        assertThat(d.resumen().estado()).isEqualTo(Estado.ERROR);
        assertThat(d.resumen().detalleError()).contains("UTF-8");
    }

    @Test
    void elMismoArchivoConUnaImportacionVigenteSeRechaza() {
        ImportacionArticulo previa = mock(ImportacionArticulo.class);
        when(previa.getId()).thenReturn(3L);
        when(previa.getEstado()).thenReturn(Estado.PENDIENTE_REVISION);
        when(importaciones.findFirstByHashAndEstadoNotIn(anyString(),
                argThat(c -> c.contains(Estado.ERROR) && c.contains(Estado.DESCARTADO) && c.size() == 2)))
                .thenReturn(Optional.of(previa));

        assertThatThrownBy(() -> cargar("base.csv", CABECERA + "A,b,c,d,e,\n"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("#3");
        verify(importaciones, never()).save(any());
    }

    @Test
    void archivosVaciosDemasiadoGrandesOConOtraExtensionSeRechazanSinRegistrarse() {
        assertThatThrownBy(() -> service.cargar("base.csv", new byte[0], null)).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> service.cargar("base.csv", new byte[ImportacionService.MAX_BYTES + 1], null))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("1 MB");
        assertThatThrownBy(() -> cargar("manual.pdf", "x")).isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining(".csv");
        verify(importaciones, never()).save(any());
    }

    @Test
    void masDeDoscientosArticulosQuedaEnError() {
        StringBuilder csv = new StringBuilder(CABECERA);
        for (int i = 0; i <= ImportacionService.MAX_REGISTROS; i++) {
            csv.append("Art ").append(i).append(",p,s,c,s,\n");
        }

        ImportacionDetalle d = cargar("base.csv", csv.toString());

        assertThat(d.resumen().estado()).isEqualTo(Estado.ERROR);
        assertThat(d.resumen().detalleError()).contains("201").contains("200");
    }

    @Test
    void delNombreDelArchivoSoloSeConservaElNombreBase() {
        assertThat(ImportacionService.nombreArchivo("C:\\Users\\ana\\Desktop\\base.csv")).isEqualTo("base.csv");
        assertThat(ImportacionService.nombreArchivo("../../etc/base.csv")).isEqualTo("base.csv");
        assertThatThrownBy(() -> ImportacionService.nombreArchivo("carpeta/")).isInstanceOf(ReglaNegocioException.class);
    }

    // ---------------------------------------------------------------- Revisión

    @Test
    void confirmarCreaBorradoresInactivosYSinVerificarSoloDeLosSeleccionados() {
        cargarDosValidos();
        Long primera = guardada.getFilas().get(0).getId();

        ImportacionDetalle d = service.confirmar(5L, List.of(primera));

        assertThat(creados).singleElement().satisfies(a -> {
            assertThat(a.getTitulo()).isEqualTo("VPN no conecta");
            assertThat(a.isActivo()).isFalse();
            assertThat(a.isVerificado()).isFalse();
            assertThat(a.esFuenteValida()).isFalse();
            assertThat(a.getImportacion()).isSameAs(guardada);
            assertThat(a.getCategoria()).isSameAs(redes);
        });
        assertThat(d.resumen().estado()).isEqualTo(Estado.IMPORTADO);
        assertThat(d.resumen().revisadoPor()).isEqualTo("Admin Nexus");
        assertThat(d.resumen().fechaRevision()).isNotNull();
        assertThat(d.filas()).extracting(FilaDto::estado, FilaDto::articuloId).containsExactly(
                tuple(FilaImportacion.Estado.IMPORTADA, 100L),
                tuple(FilaImportacion.Estado.DESCARTADA, null));
    }

    @Test
    void confirmarSinSeleccionImportaTodosLosValidos() {
        cargarDosValidos();

        ImportacionDetalle d = service.confirmar(5L, null);

        assertThat(creados).hasSize(2);
        assertThat(d.resumen().registros()).containsEntry(FilaImportacion.Estado.IMPORTADA, 2L);
    }

    @Test
    void noSePuedeConfirmarUnRegistroQueNoEsValidoNiUnoDeOtraImportacion() {
        cargar("base.csv", CABECERA + "VPN no conecta,a,b,c,d,\nIncompleto,a,,,,\n");
        numerarFilas();
        Long invalida = guardada.getFilas().get(1).getId();

        assertThatThrownBy(() -> service.confirmar(5L, List.of(invalida)))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("línea 3");
        assertThatThrownBy(() -> service.confirmar(5L, List.of(999L)))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no pertenece");
        assertThatThrownBy(() -> service.confirmar(5L, List.of()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("al menos un registro");
        assertThat(creados).isEmpty();
        assertThat(guardada.getEstado()).isEqualTo(Estado.PENDIENTE_REVISION);
    }

    @Test
    void unaImportacionYaRevisadaNoSeVuelveAConfirmarNiDescartar() {
        cargarDosValidos();
        service.confirmar(5L, null);

        assertThatThrownBy(() -> service.confirmar(5L, null)).isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("IMPORTADO");
        assertThatThrownBy(() -> service.descartar(5L)).isInstanceOf(ReglaNegocioException.class);
        assertThat(creados).hasSize(2);
        verify(importaciones, atLeastOnce()).findParaModificar(5L);
    }

    @Test
    void unTituloCreadoDespuesDeLaCargaNoSeDuplicaAlConfirmar() {
        cargarDosValidos();
        when(articulos.existsByTituloIgnoreCase("Impresora")).thenReturn(true);

        ImportacionDetalle d = service.confirmar(5L, null);

        assertThat(creados).extracting(ArticuloConocimiento::getTitulo).containsExactly("VPN no conecta");
        assertThat(d.filas().get(1).estado()).isEqualTo(FilaImportacion.Estado.DUPLICADA);
        assertThat(d.filas().get(1).errores()).singleElement().asString().contains("después de cargar");
    }

    @Test
    void siTodosLosSeleccionadosYaExistenNoSeMarcaComoImportada() {
        cargarDosValidos();
        when(articulos.existsByTituloIgnoreCase(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.confirmar(5L, null)).isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("No se creó ningún artículo");
        assertThat(creados).isEmpty();
        assertThat(guardada.getEstado()).isEqualTo(Estado.PENDIENTE_REVISION);
    }

    @Test
    void descartarCierraLaImportacionSinCrearArticulos() {
        cargarDosValidos();

        ImportacionDetalle d = service.descartar(5L);

        assertThat(d.resumen().estado()).isEqualTo(Estado.DESCARTADO);
        assertThat(d.filas()).allSatisfy(f -> assertThat(f.estado()).isEqualTo(FilaImportacion.Estado.DESCARTADA));
        verify(articulos, never()).save(any());
    }

    // ---------------------------------------------------------------- Apoyo

    private ImportacionDetalle cargar(String nombre, String contenido) {
        return service.cargar(nombre, bytes(contenido), null);
    }

    private void cargarDosValidos() {
        cargar("base.csv", CABECERA
                + "VPN no conecta,Error 809,Tiempo agotado,Puerto bloqueado,Abrir puerto,Redes\n"
                + "Impresora,No imprime,Cola detenida,Spooler,Reiniciar spooler,Hardware\n");
        numerarFilas();
    }

    /** Simula los ids que asignaría la base al guardar las filas. */
    private void numerarFilas() {
        long id = 1;
        for (FilaImportacion f : guardada.getFilas()) {
            ReflectionTestUtils.setField(f, "id", id++);
        }
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
