package com.nexus.support.funcional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Las 10 pruebas funcionales obligatorias, ejecutadas contra la aplicación en ejecución (mvn spring-boot:run).
 * Llama a la API como lo haría el frontend y contrasta los resultados con SQL directo sobre PostgreSQL.
 * Deja el reporte en docs/evidencias/resultado-pruebas.md.
 *
 * <p>No forma parte de {@code mvn test} (el sufijo IT la excluye). Ejecutar con:
 * {@code mvn test -Dtest=PruebasFuncionalesIT} (requiere RAG_PERMITIR_SIMULACION_FALLOS=true para la prueba 8).
 *
 * <p>Crea incidentes de prueba y deja el artículo 2 reactivado al terminar.
 */
class PruebasFuncionalesIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Guarda la cookie de sesión y la cookie XSRF-TOKEN entre peticiones. */
    private final CookieManager cookies = new CookieManager();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .cookieHandler(cookies).build();
    private final String base = Entorno.get("API_BASE_URL", "http://localhost:"
            + Entorno.get("BACKEND_PORT", "8080") + "/api");
    private final List<String[]> resultados = new ArrayList<>();

    private record Respuesta(int estado, JsonNode cuerpo) {
    }

    @Test
    void ejecutarPruebasFuncionales() throws Exception {
        try (Connection bd = DriverManager.getConnection(
                Entorno.get("DB_URL", "jdbc:postgresql://localhost:5432/nexus_support"),
                Entorno.get("DB_USER", "nexus"), Entorno.requerido("DB_PASSWORD"))) {
            ejecutar(bd);
        } finally {
            escribirReporte();
        }
        long fallidas = resultados.stream().filter(r -> r[2].equals("false")).count();
        assertEquals(0, fallidas, fallidas + " pruebas funcionales fallaron; ver docs/evidencias/resultado-pruebas.md");
    }

    /** Inicia sesión con la cuenta de administración inicial definida en .env. */
    private void iniciarSesion() throws Exception {
        api("GET", "/auth/sesion", null); // entrega la cookie CSRF
        Respuesta r = api("POST", "/auth/login", Map.of(
                "correo", Entorno.requerido("NEXUS_ADMIN_CORREO"), "clave", Entorno.requerido("NEXUS_ADMIN_CLAVE")));
        assertEquals(200, r.estado(), "No se pudo iniciar sesión como administrador: " + r.cuerpo());
    }

    private String tokenCsrf() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("XSRF-TOKEN")).map(HttpCookie::getValue).findFirst().orElse("");
    }

    private void ejecutar(Connection bd) throws Exception {
        iniciarSesion();
        // 1
        long i1 = incidente("Sin acceso a internet en Contabilidad",
                "El equipo no navega, el icono de red indica sin internet y la IP asignada es 169.254.10.4", 1, 3, 6);
        Respuesta r = api("POST", "/incidentes/" + i1 + "/asistencia", null);
        JsonNode c = r.cuerpo();
        registrar("P1", "Incidente de conectividad con soluciones RAG",
                r.estado() == 201 && "RESPONDIDA".equals(c.path("estado").asText()) && !fuentes(c).isEmpty(),
                "incidente #" + i1 + " → consulta #" + c.path("id").asText() + " " + c.path("estado").asText()
                        + ", fuentes (id, similitud) = " + fuentes(c));
        long resp1 = c.path("respuesta").path("id").asLong();

        // 2
        long i2 = incidente("Aplicación de nómina no inicia",
                "Al abrir la aplicación aparece 'No se encontró VCRUNTIME140.dll' y se cierra con error de ejecución", 4, 2, 8);
        r = api("POST", "/incidentes/" + i2 + "/asistencia", null);
        c = r.cuerpo();
        registrar("P2", "Error de ejecución y artículos recuperados",
                r.estado() == 201 && "RESPONDIDA".equals(c.path("estado").asText()) && idsFuentes(c).contains(17L),
                "incidente #" + i2 + " → " + c.path("estado").asText() + ", fuentes = " + fuentes(c)
                        + " (se espera el artículo 17)");

        // 3
        long i3 = incidente("Lector biométrico de bodega",
                "El lector biométrico de huellas de la bodega no registra a los empleados nuevos", 2, 2, 4);
        r = api("POST", "/incidentes/" + i3 + "/asistencia", null);
        c = r.cuerpo();
        String texto = c.path("respuesta").path("respuesta").asText("");
        registrar("P3", "Incidente sin información en la base de conocimiento",
                r.estado() == 201 && "SIN_RESULTADOS".equals(c.path("estado").asText()) && fuentes(c).isEmpty(),
                "incidente #" + i3 + " → " + c.path("estado").asText() + ": "
                        + texto.substring(0, Math.min(150, texto.length())));

        // 4
        r = api("GET", "/incidentes?categoriaId=1&tamano=200", null);
        String totalSql = sql(bd, "SELECT COUNT(*) FROM incidente WHERE categoria_id = 1");
        boolean todos = true;
        for (JsonNode i : r.cuerpo().path("contenido")) {
            todos &= i.path("categoria").path("id").asInt() == 1;
        }
        String totalApi = r.cuerpo().path("totalElementos").asText();
        registrar("P4", "Incidentes de una categoría coinciden con la base relacional",
                r.estado() == 200 && todos && totalApi.equals(totalSql),
                "API totalElementos = " + totalApi + " · SQL COUNT(*) = " + totalSql + " · todos de Conectividad = " + todos);

        // 5
        r = api("GET", "/analitica/tendencia?granularidad=semana", null);
        Map<String, Long> serieApi = new TreeMap<>();
        for (JsonNode x : r.cuerpo()) {
            if (x.path("total").asLong() > 0) {
                serieApi.put(x.path("periodo").asText(), x.path("total").asLong());
            }
        }
        Map<String, Long> serieSql = new TreeMap<>();
        try (Statement st = bd.createStatement(); ResultSet rs = st.executeQuery(
                "SELECT to_char(date_trunc('week', fecha_creacion), 'YYYY-MM-DD') AS semana, COUNT(*) AS n "
                        + "FROM incidente GROUP BY 1 ORDER BY 1")) {
            while (rs.next()) {
                serieSql.put(rs.getString(1), rs.getLong(2));
            }
        }
        registrar("P5", "Evolución semanal verificada con SQL", r.estado() == 200 && serieApi.equals(serieSql),
                r.cuerpo().size() + " semanas en la API (" + serieApi.size() + " con incidentes) · coinciden con SQL = "
                        + serieApi.equals(serieSql));

        // 6
        api("PATCH", "/articulos/2/estado", Map.of("activo", false, "verificado", true));
        try {
            Thread.sleep(5000); // la reindexación en Qdrant es asíncrona; la validación relacional aplica de inmediato
            r = api("POST", "/incidentes/" + i1 + "/asistencia", null);
            c = r.cuerpo();
            List<Long> ids = idsFuentes(c);
            registrar("P6", "Artículo desactivado no se usa como fuente", r.estado() == 201 && !ids.contains(2L),
                    "artículo 2 desactivado → consulta #" + c.path("id").asText() + " " + c.path("estado").asText()
                            + ", fuentes = " + ids);
        } finally {
            api("PATCH", "/articulos/2/estado", Map.of("activo", true, "verificado", true));
        }

        // 7
        int antes = api("GET", "/incidentes/" + i1 + "/consultas", null).cuerpo().size();
        r = api("POST", "/incidentes/" + i1 + "/asistencia", Map.of("pregunta", "   "));
        int despues = api("GET", "/incidentes/" + i1 + "/consultas", null).cuerpo().size();
        Respuesta r2 = api("POST", "/incidentes", Map.of("titulo", "x", "descripcion", "", "categoriaId", 1,
                "prioridadId", 1, "usuarioId", 1));
        registrar("P7", "Descripción vacía no ejecuta el flujo", r.estado() == 422 && antes == despues && r2.estado() == 400,
                "pregunta vacía → HTTP " + r.estado() + " '" + r.cuerpo().path("mensaje").asText() + "', consultas "
                        + antes + "→" + despues + "; incidente sin descripción → HTTP " + r2.estado() + " "
                        + r2.cuerpo().path("campos"));

        // 8
        r = api("POST", "/incidentes/" + i1 + "/asistencia?simularFalloEmbeddings=true", null);
        c = r.cuerpo();
        registrar("P8", "Fallo del servicio de embeddings", r.estado() == 201 && "ERROR".equals(c.path("estado").asText()),
                "consulta #" + c.path("id").asText() + " → " + c.path("estado").asText() + ": "
                        + c.path("detalleError").asText());

        // 9
        r = api("GET", "/incidentes/" + i1 + "/consultas", null);
        List<String> historial = new ArrayList<>();
        for (JsonNode x : r.cuerpo()) {
            historial.add("(" + x.path("id").asText() + ", " + x.path("estado").asText() + ")");
        }
        registrar("P9", "Historial de asistencias del incidente", r.estado() == 200 && historial.size() >= 3,
                "consultas (id, estado) = " + historial);

        // 10
        r = api("PUT", "/respuestas/" + resp1 + "/calificacion",
                Map.of("puntuacion", 4, "comentario", "Resolvió el problema"));
        String fila = sql(bd, "SELECT puntuacion || ' | ' || coalesce(comentario, '') FROM calificacion "
                + "WHERE respuesta_id = " + resp1);
        Respuesta r3 = api("PUT", "/respuestas/" + resp1 + "/calificacion", Map.of("puntuacion", 6));
        registrar("P10", "Calificación persistida", r.estado() == 200 && fila.startsWith("4") && r3.estado() == 400,
                "PUT → HTTP " + r.estado() + "; en BD: '" + fila + "'; puntuación 6 → HTTP " + r3.estado());
    }

    private long incidente(String titulo, String descripcion, int categoria, int prioridad, int usuario) throws Exception {
        Respuesta r = api("POST", "/incidentes", Map.of("titulo", titulo, "descripcion", descripcion,
                "categoriaId", categoria, "prioridadId", prioridad, "usuarioId", usuario));
        if (r.estado() != 201) {
            throw new IllegalStateException("No se pudo crear el incidente: HTTP " + r.estado() + " " + r.cuerpo());
        }
        return r.cuerpo().path("id").asLong();
    }

    private Respuesta api(String metodo, String ruta, Object cuerpo) throws Exception {
        HttpRequest.BodyPublisher publisher = cuerpo == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(cuerpo), StandardCharsets.UTF_8);
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(base + ruta))
                .timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                // Las escrituras exigen el token CSRF de la sesión
                .header("X-XSRF-TOKEN", tokenCsrf())
                .method(metodo, publisher)
                .build();
        HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String texto = respuesta.body();
        JsonNode json = texto == null || texto.isBlank() ? JSON.nullNode() : JSON.readTree(texto);
        return new Respuesta(respuesta.statusCode(), json);
    }

    private static String sql(Connection bd, String consulta) throws SQLException {
        try (Statement st = bd.createStatement(); ResultSet rs = st.executeQuery(consulta)) {
            return rs.next() ? rs.getString(1) : "";
        }
    }

    private static List<String> fuentes(JsonNode consulta) {
        List<String> lista = new ArrayList<>();
        for (JsonNode f : consulta.path("respuesta").path("fuentes")) {
            lista.add("(" + f.path("articuloId").asText() + ", " + f.path("similitud").asText() + ")");
        }
        return lista;
    }

    private static List<Long> idsFuentes(JsonNode consulta) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode f : consulta.path("respuesta").path("fuentes")) {
            ids.add(f.path("articuloId").asLong());
        }
        return ids;
    }

    private void registrar(String numero, String nombre, boolean ok, String evidencia) {
        resultados.add(new String[]{numero, nombre, String.valueOf(ok), evidencia});
        System.out.printf("[%s] %s %s%n       %s%n", ok ? "OK " : "FALLA", numero, nombre, evidencia);
    }

    private void escribirReporte() throws Exception {
        Path destino = Entorno.RAIZ.resolve("docs/evidencias/resultado-pruebas.md");
        Files.createDirectories(destino.getParent());
        List<String> lineas = new ArrayList<>(List.of(
                "# Resultado de pruebas funcionales", "",
                "Ejecutado: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        + " con `mvn test -Dtest=PruebasFuncionalesIT`", "",
                "| # | Prueba | Resultado | Evidencia |", "|---|---|---|---|"));
        for (String[] r : resultados) {
            lineas.add("| " + r[0] + " | " + r[1] + " | " + (r[2].equals("true") ? "✅ Pasa" : "❌ Falla") + " | "
                    + r[3].replace('|', '/') + " |");
        }
        Files.writeString(destino, String.join("\n", lineas) + "\n", StandardCharsets.UTF_8);
        long ok = resultados.stream().filter(r -> r[2].equals("true")).count();
        System.out.printf("%n%d/%d pruebas OK · reporte en %s%n", ok, resultados.size(), destino);
    }
}
