package com.nexus.support.service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.nexus.support.domain.ImportacionArticulo.Formato;

/**
 * Convierte el contenido de un archivo TXT, MD o CSV en registros de artículo. No valida reglas de negocio
 * (obligatoriedad, longitudes, duplicados): eso lo hace {@link ImportacionService}. Solo reconoce la
 * estructura y reporta problemas de formato por registro o, si el archivo entero es ilegible, con
 * {@link FormatoInvalidoException}. Los mensajes nunca incluyen el contenido del archivo.
 *
 * <p>Formatos admitidos (UTF-8, con o sin BOM):
 * <ul>
 *   <li><b>CSV</b> (RFC 4180): primera fila de cabecera con titulo, problema, sintomas, causa, solucion y,
 *       opcionalmente, categoria. Separador coma, punto y coma o tabulador (se detecta en la cabecera).
 *       Campos entre comillas dobles, con "" como comilla escapada y saltos de línea permitidos.</li>
 *   <li><b>MD</b>: cada artículo empieza con un título de nivel 1 ({@code # Título}) y sus campos son
 *       títulos de nivel 2 ({@code ## Problema}, {@code ## Síntomas}, {@code ## Causa}, {@code ## Solución},
 *       {@code ## Categoría}).</li>
 *   <li><b>TXT</b>: artículos separados por una línea {@code ---}. Cada campo empieza con
 *       {@code Clave: valor} (Título, Problema, Síntomas, Causa, Solución, Categoría) y puede continuar en
 *       las líneas siguientes.</li>
 * </ul>
 */
public final class LectorArchivoArticulos {

    public enum Campo { TITULO, PROBLEMA, SINTOMAS, CAUSA, SOLUCION, CATEGORIA }

    /** Campos que todo artículo debe traer. */
    public static final List<Campo> OBLIGATORIOS =
            List.of(Campo.TITULO, Campo.PROBLEMA, Campo.SINTOMAS, Campo.CAUSA, Campo.SOLUCION);

    /** Registro leído. {@code valores} solo contiene los campos presentes; {@code errores}, problemas de formato. */
    public record Registro(int numero, Map<Campo, String> valores, List<String> errores) {
        public String valor(Campo campo) {
            return valores.get(campo);
        }
    }

    /** El archivo no se puede interpretar en absoluto (codificación, cabecera, comillas sin cerrar...). */
    public static class FormatoInvalidoException extends RuntimeException {
        public FormatoInvalidoException(String mensaje) {
            super(mensaje);
        }
    }

    private static final Map<String, Campo> ALIAS = new LinkedHashMap<>();

    static {
        alias(Campo.TITULO, "titulo", "title", "nombre");
        alias(Campo.PROBLEMA, "problema", "descripcion", "descripcion del problema");
        alias(Campo.SINTOMAS, "sintomas", "sintoma");
        alias(Campo.CAUSA, "causa", "causa raiz", "causas");
        alias(Campo.SOLUCION, "solucion", "procedimiento", "procedimiento de solucion", "pasos", "resolucion");
        alias(Campo.CATEGORIA, "categoria");
    }

    private static void alias(Campo campo, String... nombres) {
        for (String n : nombres) {
            ALIAS.put(n, campo);
        }
    }

    private static final Pattern SEPARADOR_TXT = Pattern.compile("^-{3,}\\s*$");
    private static final Pattern CLAVE_TXT = Pattern.compile("^([^:]{1,40}):(.*)$");
    private static final Pattern TITULO_MD = Pattern.compile("^#\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern SECCION_MD = Pattern.compile("^##\\s+(.+?)\\s*#*\\s*$");
    /** Caracteres de control salvo tabulador y salto de línea. */
    private static final Pattern CONTROL = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    private LectorArchivoArticulos() {
    }

    /** Formato según la extensión del nombre (el tipo MIME lo decide el navegador y no es fiable). */
    public static Formato formatoDe(String nombreArchivo) {
        String n = nombreArchivo == null ? "" : nombreArchivo.toLowerCase(Locale.ROOT).trim();
        if (n.endsWith(".csv")) return Formato.CSV;
        if (n.endsWith(".md") || n.endsWith(".markdown")) return Formato.MD;
        if (n.endsWith(".txt")) return Formato.TXT;
        throw new FormatoInvalidoException("Formato no admitido: use un archivo .txt, .md o .csv");
    }

    /** Decodifica UTF-8 estricto, quita el BOM y unifica los saltos de línea. */
    public static String decodificar(byte[] contenido) {
        String texto;
        try {
            texto = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(contenido)).toString();
        } catch (CharacterCodingException e) {
            throw new FormatoInvalidoException("El archivo no está codificado en UTF-8");
        }
        if (texto.indexOf('\0') >= 0) {
            throw new FormatoInvalidoException("El archivo parece binario: contiene caracteres nulos");
        }
        if (texto.startsWith("﻿")) {
            texto = texto.substring(1);
        }
        return texto.replace("\r\n", "\n").replace('\r', '\n');
    }

    public static List<Registro> leer(Formato formato, String texto) {
        List<Registro> registros = switch (formato) {
            case CSV -> leerCsv(texto);
            case MD -> leerMd(texto);
            case TXT -> leerTxt(texto);
        };
        if (registros.isEmpty()) {
            throw new FormatoInvalidoException("El archivo no contiene ningún artículo");
        }
        return registros;
    }

    /** Minúsculas, sin tildes, sin signos de cierre y con espacios simples: "Síntomas:" → "sintomas". */
    static String normalizarClave(String clave) {
        String sinTildes = Normalizer.normalize(clave, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("[_\\-]", " ").replaceAll("[:*`]", "")
                .replaceAll("\\s+", " ").trim();
    }

    static Campo campo(String clave) {
        return ALIAS.get(normalizarClave(clave));
    }

    private static String limpiar(String valor) {
        return CONTROL.matcher(valor).replaceAll("").strip();
    }

    // ---------------------------------------------------------------- CSV

    private static List<Registro> leerCsv(String texto) {
        char separador = detectarSeparador(texto);
        List<FilaCsv> filas = filasCsv(texto, separador);
        if (filas.isEmpty()) {
            throw new FormatoInvalidoException("El CSV está vacío");
        }
        List<String> cabecera = filas.get(0).celdas();
        // Las columnas no reconocidas se ignoran (p. ej., un id o una fecha exportados de otra herramienta).
        Map<Integer, Campo> columnas = new LinkedHashMap<>();
        for (int i = 0; i < cabecera.size(); i++) {
            Campo c = campo(cabecera.get(i));
            if (c == null) {
                continue;
            }
            if (columnas.containsValue(c)) {
                throw new FormatoInvalidoException("La cabecera repite la columna " + nombre(c));
            } else {
                columnas.put(i, c);
            }
        }
        List<String> faltan = OBLIGATORIOS.stream().filter(c -> !columnas.containsValue(c))
                .map(LectorArchivoArticulos::nombre).toList();
        if (!faltan.isEmpty()) {
            throw new FormatoInvalidoException("La cabecera del CSV no tiene las columnas obligatorias: "
                    + String.join(", ", faltan) + " (debe ser la primera fila)");
        }

        List<Registro> registros = new ArrayList<>();
        for (FilaCsv fila : filas.subList(1, filas.size())) {
            if (fila.celdas().stream().allMatch(String::isBlank)) {
                continue;
            }
            List<String> errores = new ArrayList<>();
            if (fila.celdas().size() != cabecera.size()) {
                errores.add("Tiene " + fila.celdas().size() + " columnas y la cabecera " + cabecera.size());
            }
            Map<Campo, String> valores = new EnumMap<>(Campo.class);
            columnas.forEach((i, c) -> {
                if (i < fila.celdas().size()) {
                    valores.put(c, limpiar(fila.celdas().get(i)));
                }
            });
            registros.add(new Registro(fila.linea(), valores, errores));
        }
        return registros;
    }

    private record FilaCsv(int linea, List<String> celdas) {
    }

    /** El separador más frecuente fuera de comillas en la primera fila (la cabecera). */
    private static char detectarSeparador(String texto) {
        int coma = 0;
        int puntoComa = 0;
        int tab = 0;
        boolean comillas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '"') comillas = !comillas;
            else if (!comillas && c == '\n') break;
            else if (!comillas && c == ',') coma++;
            else if (!comillas && c == ';') puntoComa++;
            else if (!comillas && c == '\t') tab++;
        }
        if (puntoComa > coma && puntoComa >= tab) return ';';
        if (tab > coma && tab > puntoComa) return '\t';
        return ',';
    }

    /** Analizador RFC 4180 con la línea física en la que empieza cada fila. */
    private static List<FilaCsv> filasCsv(String texto, char sep) {
        List<FilaCsv> filas = new ArrayList<>();
        List<String> celdas = new ArrayList<>();
        StringBuilder celda = new StringBuilder();
        boolean comillas = false;
        boolean filaConDatos = false;
        int linea = 1;
        int inicioFila = 1;
        int inicioComillas = 0;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (comillas) {
                if (c == '"' && i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                    celda.append('"');
                    i++;
                } else if (c == '"') {
                    comillas = false;
                } else {
                    if (c == '\n') linea++;
                    celda.append(c);
                }
                continue;
            }
            if (c == '"' && celda.toString().isBlank()) {
                comillas = true;
                inicioComillas = linea;
                celda.setLength(0);
                filaConDatos = true;
            } else if (c == '"') {
                throw new FormatoInvalidoException("Línea " + linea + ": comilla en medio de un campo sin comillas");
            } else if (c == sep) {
                celdas.add(celda.toString());
                celda.setLength(0);
                filaConDatos = true;
            } else if (c == '\n') {
                if (filaConDatos || !celda.isEmpty()) {
                    celdas.add(celda.toString());
                    filas.add(new FilaCsv(inicioFila, List.copyOf(celdas)));
                }
                celdas.clear();
                celda.setLength(0);
                filaConDatos = false;
                linea++;
                inicioFila = linea;
            } else {
                celda.append(c);
            }
        }
        if (comillas) {
            throw new FormatoInvalidoException("Línea " + inicioComillas + ": comillas sin cerrar");
        }
        if (filaConDatos || !celda.isEmpty()) {
            celdas.add(celda.toString());
            filas.add(new FilaCsv(inicioFila, List.copyOf(celdas)));
        }
        return filas;
    }

    // ---------------------------------------------------------------- Markdown

    private static List<Registro> leerMd(String texto) {
        List<Registro> registros = new ArrayList<>();
        String[] lineas = texto.split("\n", -1);
        Map<Campo, StringBuilder> actual = null;
        List<String> errores = null;
        Campo seccion = null;
        int numero = 0;
        int lineaInicio = 0;
        boolean enCodigo = false;
        for (int n = 0; n < lineas.length; n++) {
            String l = lineas[n];
            if (l.strip().startsWith("```")) {
                enCodigo = !enCodigo;
            }
            Matcher titulo = enCodigo ? null : TITULO_MD.matcher(l);
            Matcher sub = enCodigo ? null : SECCION_MD.matcher(l);
            if (titulo != null && titulo.matches()) {
                cerrarMd(registros, actual, errores, lineaInicio);
                numero++;
                lineaInicio = n + 1;
                actual = new EnumMap<>(Campo.class);
                errores = new ArrayList<>();
                actual.put(Campo.TITULO, new StringBuilder(titulo.group(1)));
                seccion = null;
            } else if (sub != null && sub.matches() && actual != null) {
                seccion = campo(sub.group(1));
                if (seccion == null || seccion == Campo.TITULO) {
                    errores.add("Sección desconocida «" + recortar(sub.group(1)) + "» (línea " + (n + 1) + ")");
                    seccion = null;
                } else if (actual.containsKey(seccion)) {
                    errores.add("La sección " + nombre(seccion) + " aparece dos veces (línea " + (n + 1) + ")");
                    seccion = null;
                } else {
                    actual.put(seccion, new StringBuilder());
                }
            } else if (actual == null) {
                if (!l.isBlank()) {
                    throw new FormatoInvalidoException("Línea " + (n + 1)
                            + ": hay texto antes del primer artículo; cada artículo debe empezar con «# Título»");
                }
            } else if (seccion != null) {
                actual.get(seccion).append(l).append('\n');
            } else if (!l.isBlank()) {
                errores.add("Línea " + (n + 1) + ": texto fuera de una sección «## Campo»");
            }
        }
        cerrarMd(registros, actual, errores, lineaInicio);
        if (numero == 0) {
            throw new FormatoInvalidoException("No se encontró ningún artículo: cada uno debe empezar con «# Título»");
        }
        return registros;
    }

    private static void cerrarMd(List<Registro> registros, Map<Campo, StringBuilder> actual, List<String> errores,
                                 int linea) {
        if (actual == null) {
            return;
        }
        Map<Campo, String> valores = new EnumMap<>(Campo.class);
        actual.forEach((c, v) -> valores.put(c, limpiar(v.toString())));
        registros.add(new Registro(linea, valores, errores));
    }

    // ---------------------------------------------------------------- Texto plano

    private static List<Registro> leerTxt(String texto) {
        List<Registro> registros = new ArrayList<>();
        String[] lineas = texto.split("\n", -1);
        Map<Campo, StringBuilder> actual = new EnumMap<>(Campo.class);
        List<String> errores = new ArrayList<>();
        Campo campo = null;
        int inicio = 0;
        for (int n = 0; n < lineas.length; n++) {
            String l = lineas[n];
            if (SEPARADOR_TXT.matcher(l).matches()) {
                cerrarTxt(registros, actual, errores, inicio);
                actual = new EnumMap<>(Campo.class);
                errores = new ArrayList<>();
                campo = null;
                inicio = 0;
                continue;
            }
            Matcher m = CLAVE_TXT.matcher(l);
            Campo clave = m.matches() ? campo(m.group(1)) : null;
            if (clave != null) {
                if (inicio == 0) inicio = n + 1;
                if (actual.containsKey(clave)) {
                    errores.add("El campo " + nombre(clave) + " aparece dos veces (línea " + (n + 1) + ")");
                    campo = null;
                } else {
                    campo = clave;
                    actual.put(clave, new StringBuilder(m.group(2)).append('\n'));
                }
            } else if (campo != null) {
                actual.get(campo).append(l).append('\n');
            } else if (!l.isBlank()) {
                if (inicio == 0) inicio = n + 1;
                errores.add("Línea " + (n + 1) + ": texto fuera de un campo «Clave: valor»");
            }
        }
        cerrarTxt(registros, actual, errores, inicio);
        return registros;
    }

    private static void cerrarTxt(List<Registro> registros, Map<Campo, StringBuilder> actual, List<String> errores,
                                  int inicio) {
        if (actual.isEmpty() && errores.isEmpty()) {
            return; // bloque vacío (p. ej., separador al final)
        }
        Map<Campo, String> valores = new EnumMap<>(Campo.class);
        actual.forEach((c, v) -> valores.put(c, limpiar(v.toString())));
        registros.add(new Registro(inicio, valores, errores));
    }

    // ---------------------------------------------------------------- Utilidades

    public static String nombre(Campo c) {
        return switch (c) {
            case TITULO -> "título";
            case PROBLEMA -> "problema";
            case SINTOMAS -> "síntomas";
            case CAUSA -> "causa";
            case SOLUCION -> "solución";
            case CATEGORIA -> "categoría";
        };
    }

    private static String recortar(String s) {
        String t = s.strip();
        return t.length() <= 40 ? t : t.substring(0, 40) + "…";
    }
}
