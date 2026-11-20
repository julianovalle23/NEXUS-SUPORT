package com.nexus.support.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.nexus.support.domain.ImportacionArticulo.Formato;
import com.nexus.support.service.LectorArchivoArticulos.Campo;
import com.nexus.support.service.LectorArchivoArticulos.FormatoInvalidoException;
import com.nexus.support.service.LectorArchivoArticulos.Registro;

/** Lectura de archivos TXT, MD y CSV: solo estructura; las reglas de negocio se prueban en ImportacionServiceTest. */
class LectorArchivoArticulosTest {

    private static List<Registro> leer(Formato f, String texto) {
        return LectorArchivoArticulos.leer(f, LectorArchivoArticulos.decodificar(texto.getBytes(StandardCharsets.UTF_8)));
    }

    // ---------------------------------------------------------------- Formato y codificación

    @Test
    void elFormatoSeDecidePorLaExtensionSinImportarMayusculas() {
        assertThat(LectorArchivoArticulos.formatoDe("Base.CSV")).isEqualTo(Formato.CSV);
        assertThat(LectorArchivoArticulos.formatoDe("guia.markdown")).isEqualTo(Formato.MD);
        assertThat(LectorArchivoArticulos.formatoDe("notas.md")).isEqualTo(Formato.MD);
        assertThat(LectorArchivoArticulos.formatoDe("notas.txt")).isEqualTo(Formato.TXT);
        assertThatThrownBy(() -> LectorArchivoArticulos.formatoDe("manual.pdf")).isInstanceOf(FormatoInvalidoException.class);
        assertThatThrownBy(() -> LectorArchivoArticulos.formatoDe("sin_extension")).isInstanceOf(FormatoInvalidoException.class);
    }

    @Test
    void unArchivoQueNoEsUtf8SeRechaza() {
        byte[] latin1 = "Título: Señal".getBytes(StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> LectorArchivoArticulos.decodificar(latin1))
                .isInstanceOf(FormatoInvalidoException.class).hasMessageContaining("UTF-8");
    }

    @Test
    void unArchivoBinarioConNulosSeRechaza() {
        assertThatThrownBy(() -> LectorArchivoArticulos.decodificar(new byte[] {'a', 0, 'b'}))
                .isInstanceOf(FormatoInvalidoException.class).hasMessageContaining("binario");
    }

    @Test
    void seQuitaElBomYSeUnificanLosSaltosDeLinea() {
        byte[] conBom = "﻿a\r\nb\rc".getBytes(StandardCharsets.UTF_8);

        assertThat(LectorArchivoArticulos.decodificar(conBom)).isEqualTo("a\nb\nc");
    }

    @Test
    void unArchivoSinArticulosSeRechaza() {
        assertThatThrownBy(() -> leer(Formato.TXT, "\n\n---\n\n")).isInstanceOf(FormatoInvalidoException.class);
        assertThatThrownBy(() -> leer(Formato.CSV, "titulo,problema,sintomas,causa,solucion\n"))
                .isInstanceOf(FormatoInvalidoException.class);
    }

    // ---------------------------------------------------------------- CSV

    @Test
    void csvConComillasSeparadoresYSaltosDeLineaDentroDeUnCampo() {
        String csv = """
                titulo,problema,sintomas,causa,solucion
                VPN no conecta,"Error 809, al conectar","Dice ""tiempo agotado""\",Puerto bloqueado,"1. Abrir puerto
                2. Reintentar"
                Impresora,No imprime,Cola detenida,Spooler,Reiniciar spooler
                """;

        List<Registro> r = leer(Formato.CSV, csv);

        assertThat(r).hasSize(2);
        assertThat(r.get(0).valor(Campo.PROBLEMA)).isEqualTo("Error 809, al conectar");
        assertThat(r.get(0).valor(Campo.SINTOMAS)).isEqualTo("Dice \"tiempo agotado\"");
        assertThat(r.get(0).valor(Campo.SOLUCION)).isEqualTo("1. Abrir puerto\n2. Reintentar");
        assertThat(r.get(0).numero()).isEqualTo(2);
        assertThat(r.get(1).numero()).as("línea física, contando el salto dentro de comillas").isEqualTo(4);
        assertThat(r).allSatisfy(x -> assertThat(x.errores()).isEmpty());
    }

    @Test
    void csvConPuntoYComaYCabeceraConTildesYMayusculas() {
        String csv = "Título;Problema;Síntomas;Causa raíz;Solución;Categoría\r\n"
                + "Wifi;Sin red;No aparece la red;Adaptador apagado;Encender adaptador;Redes\r\n";

        Registro r = leer(Formato.CSV, csv).get(0);

        assertThat(r.valor(Campo.TITULO)).isEqualTo("Wifi");
        assertThat(r.valor(Campo.CAUSA)).isEqualTo("Adaptador apagado");
        assertThat(r.valor(Campo.CATEGORIA)).isEqualTo("Redes");
    }

    @Test
    void lasColumnasDesconocidasSeIgnoran() {
        String csv = "id,titulo,problema,sintomas,causa,solucion,autor\n7,Wifi,a,b,c,d,Laura\n";

        Registro r = leer(Formato.CSV, csv).get(0);

        assertThat(r.valores()).containsOnlyKeys(Campo.TITULO, Campo.PROBLEMA, Campo.SINTOMAS, Campo.CAUSA,
                Campo.SOLUCION);
        assertThat(r.errores()).isEmpty();
    }

    @Test
    void unaCabeceraSinColumnasObligatoriasSeRechazaIndicandoCuales() {
        assertThatThrownBy(() -> leer(Formato.CSV, "titulo,problema\nWifi,Sin red\n"))
                .isInstanceOf(FormatoInvalidoException.class)
                .hasMessageContaining("síntomas").hasMessageContaining("causa").hasMessageContaining("solución");
    }

    @Test
    void unaCabeceraConColumnasRepetidasSeRechaza() {
        assertThatThrownBy(() -> leer(Formato.CSV, "titulo,problema,sintomas,causa,solucion,Solución\n"))
                .isInstanceOf(FormatoInvalidoException.class).hasMessageContaining("repite");
    }

    @Test
    void comillasSinCerrarIndicanLaLineaDondeEmpiezan() {
        String csv = "titulo,problema,sintomas,causa,solucion\nA,b,c,d,e\nB,\"sin cierre,c,d,e\n";

        assertThatThrownBy(() -> leer(Formato.CSV, csv))
                .isInstanceOf(FormatoInvalidoException.class).hasMessageContaining("Línea 3");
    }

    @Test
    void unaComillaEnMedioDeUnCampoSinComillasSeRechaza() {
        assertThatThrownBy(() -> leer(Formato.CSV, "titulo,problema,sintomas,causa,solucion\nA,mal\"o,c,d,e\n"))
                .isInstanceOf(FormatoInvalidoException.class);
    }

    @Test
    void unaFilaConColumnasDeMasODeMenosQuedaConError() {
        String csv = "titulo,problema,sintomas,causa,solucion\nA,b,c\nB,b,c,d,e\n\n";

        List<Registro> r = leer(Formato.CSV, csv);

        assertThat(r).as("la línea vacía final no cuenta").hasSize(2);
        assertThat(r.get(0).errores()).singleElement().asString().contains("3 columnas");
        assertThat(r.get(1).errores()).isEmpty();
    }

    @Test
    void losCaracteresDeControlSeEliminanYLosEspaciosSeRecortan() {
        String csv = "titulo,problema,sintomas,causa,solucion\n  Wifi\u0007  ,b,c,d,e\n";

        assertThat(leer(Formato.CSV, csv).get(0).valor(Campo.TITULO)).isEqualTo("Wifi");
    }

    // ---------------------------------------------------------------- Markdown

    @Test
    void markdownConVariosArticulosYSecciones() {
        String md = """
                # VPN no conecta

                ## Problema
                Error 809.

                ## Síntomas
                - Tiempo agotado
                - Solo fuera de la oficina

                ## Causa
                Puerto bloqueado.

                ## Solución
                1. Abrir el puerto.
                2. Reintentar.

                ## Categoría
                Redes

                # Impresora sin cola
                ## Problema
                No imprime.
                ## Sintomas
                Cola detenida.
                ## Causa
                Spooler.
                ## Procedimiento
                Reiniciar el spooler.
                """;

        List<Registro> r = leer(Formato.MD, md);

        assertThat(r).hasSize(2);
        assertThat(r.get(0).valor(Campo.TITULO)).isEqualTo("VPN no conecta");
        assertThat(r.get(0).valor(Campo.SINTOMAS)).isEqualTo("- Tiempo agotado\n- Solo fuera de la oficina");
        assertThat(r.get(0).valor(Campo.CATEGORIA)).isEqualTo("Redes");
        assertThat(r.get(1).valor(Campo.SOLUCION)).isEqualTo("Reiniciar el spooler.");
        assertThat(r.get(1).numero()).isEqualTo(20);
        assertThat(r).allSatisfy(x -> assertThat(x.errores()).isEmpty());
    }

    @Test
    void unTituloDentroDeUnBloqueDeCodigoNoAbreOtroArticulo() {
        String md = "# Script\n## Problema\nFalla\n## Solución\n```bash\n# comentario del script\necho ok\n```\n";

        List<Registro> r = leer(Formato.MD, md);

        assertThat(r).hasSize(1);
        assertThat(r.get(0).valor(Campo.SOLUCION)).contains("# comentario del script");
    }

    @Test
    void seccionesDesconocidasRepetidasOTextoSueltoQuedanComoErroresDelArticulo() {
        String md = "# A\ntexto suelto\n## Problema\nx\n## Problema\ny\n## Anexos\nz\n";

        Registro r = leer(Formato.MD, md).get(0);

        // Línea 2 suelta, Problema repetido, su texto «y» sin sección, sección Anexos y su texto «z»
        assertThat(r.errores()).hasSize(5);
        assertThat(String.join(" ", r.errores())).contains("fuera de una sección").contains("dos veces")
                .contains("Anexos");
        assertThat(r.valor(Campo.PROBLEMA)).isEqualTo("x");
    }

    @Test
    void textoAntesDelPrimerArticuloOSinTitulosSeRechaza() {
        assertThatThrownBy(() -> leer(Formato.MD, "Introducción\n# A\n## Problema\nx\n"))
                .isInstanceOf(FormatoInvalidoException.class).hasMessageContaining("Línea 1");
        assertThatThrownBy(() -> leer(Formato.MD, "## Problema\nx\n")).isInstanceOf(FormatoInvalidoException.class);
    }

    // ---------------------------------------------------------------- Texto plano

    @Test
    void textoPlanoConBloquesYCamposDeVariasLineas() {
        String txt = """
                Título: VPN no conecta
                Problema: Error 809
                Síntomas: Tiempo agotado
                Causa: Puerto bloqueado
                Solución: 1. Abrir el puerto
                2. Reintentar
                Nota: si persiste, escalar
                ---
                Titulo: Impresora
                Problema: No imprime
                Sintomas: Cola detenida
                Causa: Spooler
                Solucion: Reiniciar spooler
                Categoria: Hardware
                ---
                """;

        List<Registro> r = leer(Formato.TXT, txt);

        assertThat(r).hasSize(2);
        assertThat(r.get(0).valor(Campo.SOLUCION))
                .as("las claves no reconocidas, como Nota, continúan el campo anterior")
                .isEqualTo("1. Abrir el puerto\n2. Reintentar\nNota: si persiste, escalar");
        assertThat(r.get(1).numero()).isEqualTo(9);
        assertThat(r.get(1).valor(Campo.CATEGORIA)).isEqualTo("Hardware");
        assertThat(r).allSatisfy(x -> assertThat(x.errores()).isEmpty());
    }

    @Test
    void textoPlanoConCampoRepetidoOTextoFueraDeCampoQuedaConErrores() {
        String txt = "texto inicial\nTítulo: A\nTítulo: B\nProblema: x\n";

        Registro r = leer(Formato.TXT, txt).get(0);

        assertThat(r.errores()).hasSize(2);
        assertThat(r.valor(Campo.TITULO)).isEqualTo("A");
        assertThat(r.numero()).isEqualTo(1);
    }
}
