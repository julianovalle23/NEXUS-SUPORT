package com.nexus.support.funcional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Calibración del umbral de similitud del RAG.
 *
 * <p>Genera embeddings con el MISMO modelo configurado en .env para los artículos activos y verificados
 * (leídos de la API interna de Spring Boot) y para un conjunto de consultas etiquetadas con el artículo que
 * debería responderlas (o {@code null} si la base de conocimiento no cubre el problema). Para cada umbral
 * candidato reporta el recall (consultas relevantes que conservan su artículo) y los falsos positivos
 * (consultas sin cobertura que quedarían con alguna fuente).
 *
 * <p>No forma parte de {@code mvn test}. Ejecutar con el stack levantado:
 * {@code mvn test -Dtest=CalibracionUmbralIT}. No escribe en la base de datos ni en Qdrant.
 */
class CalibracionUmbralIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    private record Consulta(String texto, String esperado) {
    }

    private record Articulo(String titulo, String texto) {
    }

    private static final List<Consulta> CONSULTAS = List.of(
            new Consulta("El computador no tiene internet, el icono de red tiene una X roja y dice cable desconectado",
                    "Equipo sin acceso a internet por cable de red"),
            new Consulta("Mi equipo tiene IP 169.254 y no navega", "Sin internet: IP 169.254.x.x por falla de DHCP"),
            new Consulta("Las páginas no abren por nombre, sale DNS_PROBE_FINISHED_NXDOMAIN",
                    "Navegación lenta o páginas que no cargan por fallo de DNS"),
            new Consulta("El portátil dice conectado sin internet en el wifi de la oficina",
                    "Wi-Fi corporativo conectado pero sin internet"),
            new Consulta("Desde la casa la VPN no conecta, tiempo de espera agotado",
                    "VPN no conecta desde casa (error de autenticación o timeout)"),
            new Consulta("La impresora del piso no imprime y los documentos se quedan en cola",
                    "Impresora de red no imprime: trabajos en cola"),
            new Consulta("Al guardar la factura en el ERP sale NullPointerException",
                    "Error de ejecución \"NullPointerException\" en el ERP"),
            new Consulta("La aplicación se cierra al abrir con error VCRUNTIME140.dll",
                    "Aplicación de escritorio se cierra al iniciar (falta runtime)"),
            new Consulta("El reporte anual se congela y muestra OutOfMemoryError Java heap space",
                    "Aplicación muestra error de memoria \"Java heap space\""),
            new Consulta("Outlook deja los correos en la bandeja de salida",
                    "Outlook no envía correos (quedan en bandeja de salida)"),
            new Consulta("La cuenta se bloquea cada vez que intento iniciar sesión",
                    "Cuenta de dominio bloqueada por intentos fallidos"),
            new Consulta("No puedo abrir la carpeta compartida de contabilidad, acceso denegado",
                    "Acceso denegado a carpeta compartida"),
            // Sin cobertura en la base de conocimiento
            new Consulta("El lector biométrico de huellas de la bodega no registra empleados nuevos", null),
            new Consulta("La planta eléctrica no arrancó durante el corte de energía", null),
            new Consulta("El aire acondicionado del cuarto de servidores gotea agua", null),
            new Consulta("Necesito cambiar el tóner de la fotocopiadora a color", null));

    private static final double[] UMBRALES = {0.30, 0.35, 0.40, 0.45, 0.50, 0.55, 0.60};

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Test
    void calibrarUmbral() throws Exception {
        String backend = Entorno.get("BACKEND_URL_LOCAL", "http://localhost:" + Entorno.get("BACKEND_PORT", "8080"));
        JsonNode lista = enviar(HttpRequest.newBuilder(URI.create(backend + "/api/internal/articulos"))
                .header("X-Internal-Token", Entorno.requerido("INTERNAL_API_TOKEN"))
                .timeout(Duration.ofSeconds(30)).GET().build());
        List<Articulo> articulos = new ArrayList<>();
        for (JsonNode a : lista.path("articulos")) {
            if (a.path("fuenteValida").asBoolean()) {
                articulos.add(new Articulo(a.path("titulo").asText(), a.path("texto").asText()));
            }
        }
        String modelo = Entorno.get("EMBEDDING_MODEL", "openai/text-embedding-3-small");
        System.out.printf("Artículos válidos: %d · consultas: %d · modelo: %s%n", articulos.size(), CONSULTAS.size(), modelo);

        List<double[]> va = embeddings(modelo, articulos.stream().map(Articulo::texto).toList());
        List<double[]> vq = embeddings(modelo, CONSULTAS.stream().map(Consulta::texto).toList());

        // Por consulta: [similitud del mejor artículo, similitud del artículo esperado (NaN si no aplica)]
        List<double[]> filas = new ArrayList<>();
        System.out.println("\nConsulta | esperado | mejor artículo | sim. mejor | sim. esperado");
        for (int q = 0; q < CONSULTAS.size(); q++) {
            Consulta consulta = CONSULTAS.get(q);
            int mejor = 0;
            double simEsperado = Double.NaN;
            double[] sims = new double[articulos.size()];
            for (int a = 0; a < articulos.size(); a++) {
                sims[a] = coseno(vq.get(q), va.get(a));
                if (sims[a] > sims[mejor]) {
                    mejor = a;
                }
                if (articulos.get(a).titulo().equals(consulta.esperado())) {
                    simEsperado = sims[a];
                }
            }
            filas.add(new double[]{sims[mejor], simEsperado});
            System.out.printf("- %-55.55s | %s | %-40.40s | %.4f | %s%n", consulta.texto(),
                    consulta.esperado() != null ? "sí" : "no", articulos.get(mejor).titulo(), sims[mejor],
                    Double.isNaN(simEsperado) ? "" : String.format("%.4f", simEsperado));
        }

        System.out.println("\nUmbral | recall (relevantes con su artículo >= umbral) | falsos positivos (sin cobertura con fuente)");
        for (double u : UMBRALES) {
            int relevantes = 0;
            int aciertos = 0;
            int sinCobertura = 0;
            int falsosPositivos = 0;
            for (int q = 0; q < CONSULTAS.size(); q++) {
                double[] f = filas.get(q);
                if (CONSULTAS.get(q).esperado() != null) {
                    relevantes++;
                    if (!Double.isNaN(f[1]) && f[1] >= u) {
                        aciertos++;
                    }
                } else {
                    sinCobertura++;
                    if (f[0] >= u) {
                        falsosPositivos++;
                    }
                }
            }
            System.out.printf("  %.2f | %6.1f%% | %6.1f%%%n", u, 100.0 * aciertos / relevantes,
                    100.0 * falsosPositivos / sinCobertura);
        }
        System.out.println("\nElija el umbral más alto que mantenga un recall aceptable con 0% de falsos positivos "
                + "y regístrelo en RAG_UMBRAL_SIMILITUD.");
    }

    private List<double[]> embeddings(String modelo, List<String> textos) throws Exception {
        String url = Entorno.get("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1") + "/embeddings";
        JsonNode r = enviar(HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + Entorno.requerido("OPENROUTER_API_KEY"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(
                        JSON.writeValueAsString(Map.of("model", modelo, "input", textos)), StandardCharsets.UTF_8))
                .build());
        List<JsonNode> datos = new ArrayList<>();
        r.path("data").forEach(datos::add);
        datos.sort(Comparator.comparingInt(d -> d.path("index").asInt()));
        List<double[]> vectores = new ArrayList<>();
        for (JsonNode d : datos) {
            JsonNode e = d.path("embedding");
            double[] v = new double[e.size()];
            for (int i = 0; i < v.length; i++) {
                v[i] = e.get(i).asDouble();
            }
            vectores.add(v);
        }
        return vectores;
    }

    private JsonNode enviar(HttpRequest peticion) throws Exception {
        HttpResponse<String> r = http.send(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (r.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + r.statusCode() + " en " + peticion.uri() + ": " + r.body());
        }
        return JSON.readTree(r.body());
    }

    private static double coseno(double[] a, double[] b) {
        double punto = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            punto += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return punto / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
