package com.nexus.support.funcional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuración para las pruebas que se ejecutan contra el stack levantado.
 * Lee el archivo .env de la raíz del repositorio; las variables de entorno del sistema tienen prioridad.
 */
final class Entorno {

    /** Raíz del repositorio: Maven ejecuta las pruebas con el directorio de trabajo en ./backend. */
    static final Path RAIZ = Path.of("..").toAbsolutePath().normalize();

    private static final Map<String, String> VALORES = cargar();

    private Entorno() {
    }

    static String get(String clave, String porDefecto) {
        String valor = VALORES.get(clave);
        return valor == null || valor.isBlank() ? porDefecto : valor;
    }

    static String requerido(String clave) {
        String valor = get(clave, null);
        if (valor == null) {
            throw new IllegalStateException("Falta la variable " + clave + " (en .env o en el entorno)");
        }
        return valor;
    }

    private static Map<String, String> cargar() {
        Map<String, String> valores = new HashMap<>();
        Path archivo = RAIZ.resolve(".env");
        if (Files.exists(archivo)) {
            try {
                for (String linea : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
                    int igual = linea.indexOf('=');
                    if (igual > 0 && !linea.stripLeading().startsWith("#")) {
                        valores.put(linea.substring(0, igual).strip(), linea.substring(igual + 1).strip());
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo leer " + archivo, e);
            }
        }
        valores.putAll(System.getenv());
        return valores;
    }
}
