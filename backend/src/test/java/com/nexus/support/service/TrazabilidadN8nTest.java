package com.nexus.support.service;

import static com.nexus.support.service.Datos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import com.nexus.support.config.NexusProperties;
import com.nexus.support.domain.ConsultaIA;
import com.nexus.support.domain.Incidente;
import com.nexus.support.domain.Rol;
import com.nexus.support.dto.AsistenciaDtos.IncidenteRag;
import com.nexus.support.dto.AsistenciaDtos.RagResultado;
import com.nexus.support.dto.AsistenciaDtos.RagSolicitud;
import com.nexus.support.repository.ArticuloRepository;
import com.nexus.support.repository.ConsultaIARepository;
import com.nexus.support.repository.RespuestaIARepository;

/**
 * Trazabilidad entre Spring Boot y n8n (etapa A de §13): correlationId en la cabecera X-Correlation-Id, eco de
 * correlationId y ejecucionId en la respuesta, y persistencia en consulta_ia solo de identificadores seguros.
 */
class TrazabilidadN8nTest {

    private static final String UUID_RE = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final NexusProperties.Rag RAG = new NexusProperties.Rag(0.45, 5, 1, 60, false, false);

    // ---------- N8nClient: cabecera y eco ----------

    @Nested
    class Cliente {

        private MockRestServiceServer servidor;
        private N8nClient cliente;

        @BeforeEach
        void setUp() {
            RestClient.Builder builder = RestClient.builder();
            servidor = MockRestServiceServer.bindTo(builder).build();
            RestClient http = builder.build();
            var props = new NexusProperties(new NexusProperties.N8n("http://n8n", "/rag", "/indexar", 60), "t", RAG, null);
            cliente = new N8nClient(http, http, props);
        }

        @Test
        void elRagEnviaElCorrelationIdEnLaCabeceraYNoEnElCuerpo() {
            AtomicReference<String> cabecera = new AtomicReference<>();
            servidor.expect(requestTo("http://n8n/rag"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(capturar(cabecera))
                    .andExpect(jsonPath("$.correlationId").doesNotExist())
                    .andExpect(jsonPath("$.consultaId").value(10))
                    .andRespond(withSuccess("""
                            {"estado":"SIN_RESULTADOS","correlationId":"abc-123","ejecucionId":"4521"}
                            """, MediaType.APPLICATION_JSON));

            RagResultado r = cliente.consultarRag(solicitud("abc-123"));

            servidor.verify();
            assertThat(cabecera.get()).isEqualTo("abc-123");
            assertThat(r.correlationId()).isEqualTo("abc-123");
            assertThat(r.ejecucionId()).isEqualTo("4521");
        }

        @Test
        void unCorrelationIdInseguroSeReemplazaPorUnUuid() {
            AtomicReference<String> cabecera = new AtomicReference<>();
            servidor.expect(requestTo("http://n8n/rag"))
                    .andExpect(capturar(cabecera))
                    .andRespond(withSuccess("{\"estado\":\"SIN_RESULTADOS\"}", MediaType.APPLICATION_JSON));

            cliente.consultarRag(solicitud("id con espacios\r\nX-Inyectada: 1"));

            servidor.verify();
            assertThat(cabecera.get()).matches(UUID_RE);
        }

        @Test
        void conElFlujoPublicadoSinEcoElRagFuncionaYLosIdsLleganNulos() {
            servidor.expect(requestTo("http://n8n/rag"))
                    .andRespond(withSuccess("{\"estado\":\"SIN_RESULTADOS\"}", MediaType.APPLICATION_JSON));

            RagResultado r = cliente.consultarRag(solicitud("abc-123"));

            assertThat(r.estado()).isEqualTo("SIN_RESULTADOS");
            assertThat(r.correlationId()).isNull();
            assertThat(r.ejecucionId()).isNull();
        }

        @Test
        void laIndexacionEnviaUnUuidYLoAgregaSiN8nNoLoDevuelve() {
            AtomicReference<String> cabecera = new AtomicReference<>();
            servidor.expect(requestTo("http://n8n/indexar"))
                    .andExpect(capturar(cabecera))
                    .andExpect(jsonPath("$.forzar").value(false))
                    .andExpect(jsonPath("$.correlationId").doesNotExist())
                    .andRespond(withSuccess("{\"estado\":\"OK\",\"etapa\":\"COMPLETADA\"}", MediaType.APPLICATION_JSON));

            Map<String, Object> r = cliente.indexar(false);

            servidor.verify();
            assertThat(cabecera.get()).matches(UUID_RE);
            assertThat(r).containsEntry("correlationId", cabecera.get()).containsEntry("estado", "OK");
        }

        @Test
        void laIndexacionConservaElEcoYElIdDeEjecucionDeN8n() {
            AtomicReference<String> cabecera = new AtomicReference<>();
            servidor.expect(requestTo("http://n8n/indexar"))
                    .andExpect(capturar(cabecera))
                    .andRespond(req -> withSuccess("{\"estado\":\"OK\",\"correlationId\":\""
                            + req.getHeaders().getFirst(N8nClient.HEADER_CORRELACION) + "\",\"ejecucionId\":\"77\"}",
                            MediaType.APPLICATION_JSON).createResponse(req));

            Map<String, Object> r = cliente.indexar(true);

            assertThat(r).containsEntry("correlationId", cabecera.get()).containsEntry("ejecucionId", "77");
        }

        private RequestMatcher capturar(AtomicReference<String> destino) {
            return req -> destino.set(req.getHeaders().getFirst(N8nClient.HEADER_CORRELACION));
        }

        private RagSolicitud solicitud(String correlacion) {
            return new RagSolicitud(10L, "No envía correos", new IncidenteRag("Outlook", "Correo", "Media"),
                    0.45, 5, 1, false, correlacion);
        }
    }

    // ---------- N8nClient.idSeguro ----------

    @Test
    void idSeguroAceptaSoloIdentificadoresCortosSinCaracteresEspeciales() {
        assertThat(N8nClient.idSeguro("4521")).isEqualTo("4521");
        assertThat(N8nClient.idSeguro(4521)).isEqualTo("4521");
        assertThat(N8nClient.idSeguro("a.b_c-9")).isEqualTo("a.b_c-9");
        assertThat(N8nClient.idSeguro("x".repeat(64))).hasSize(64);

        assertThat(N8nClient.idSeguro(null)).isNull();
        assertThat(N8nClient.idSeguro("")).isNull();
        assertThat(N8nClient.idSeguro("x".repeat(65))).isNull();
        assertThat(N8nClient.idSeguro("12\n34")).isNull();
        assertThat(N8nClient.idSeguro("<script>")).isNull();
    }

    // ---------- AsistenciaService: persistencia ----------

    @Nested
    class Persistencia {

        private N8nClient n8n;
        private AsistenciaService service;
        private final ConsultaIA[] guardada = new ConsultaIA[1];

        @BeforeEach
        void setUp() {
            IncidenteService incidentes = mock(IncidenteService.class);
            ConsultaIARepository consultas = mock(ConsultaIARepository.class);
            n8n = mock(N8nClient.class);
            PlatformTransactionManager ptm = mock(PlatformTransactionManager.class);
            when(ptm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            service = new AsistenciaService(incidentes, consultas, mock(RespuestaIARepository.class),
                    mock(ArticuloRepository.class), n8n, new NexusProperties(null, "t", RAG, null),
                    new TransactionTemplate(ptm));

            Incidente inc = incidente(7, categoria(5, "Correo"), prioridad(2, "Media", 2, 24),
                    estado(1, "Abierto", false), usuario(1, "Laura Gomez", Rol.TECNICO));
            when(incidentes.entidad(7L)).thenReturn(inc);
            when(consultas.save(any())).thenAnswer(i -> {
                guardada[0] = i.getArgument(0);
                ReflectionTestUtils.setField(guardada[0], "id", 10L);
                return guardada[0];
            });
            when(consultas.findById(10L)).thenAnswer(i -> Optional.of(guardada[0]));
        }

        @Test
        void laConsultaGuardaUnUuidYEsElMismoQueSeEnviaAN8n() {
            responder("4521");

            service.consultar(7L, null, false);

            ArgumentCaptor<RagSolicitud> captor = ArgumentCaptor.forClass(RagSolicitud.class);
            verify(n8n).consultarRag(captor.capture());
            assertThat(guardada[0].getCorrelacionId()).matches(UUID_RE);
            assertThat(captor.getValue().correlationId()).isEqualTo(guardada[0].getCorrelacionId());
        }

        @Test
        void seGuardaElIdDeEjecucionQueDevuelveN8n() {
            responder("4521");

            service.consultar(7L, null, false);

            assertThat(guardada[0].getEjecucionN8n()).isEqualTo("4521");
        }

        @Test
        void unIdDeEjecucionInseguroNoSeGuarda() {
            responder("45 21; DROP TABLE consulta_ia");

            service.consultar(7L, null, false);

            assertThat(guardada[0].getEjecucionN8n()).isNull();
        }

        @Test
        void sinRespuestaDeN8nLaConsultaConservaSuCorrelacionYQuedaSinEjecucion() {
            when(n8n.consultarRag(any())).thenThrow(new N8nClient.N8nException("timeout"));

            service.consultar(7L, null, false);

            assertThat(guardada[0].getCorrelacionId()).matches(UUID_RE);
            assertThat(guardada[0].getEjecucionN8n()).isNull();
            assertThat(guardada[0].getEstado().name()).isEqualTo("ERROR");
        }

        private void responder(String ejecucionId) {
            when(n8n.consultarRag(any())).thenAnswer(i -> new RagResultado("SIN_RESULTADOS", "Sin información",
                    List.of(), List.of(), 0.2, null, null, null, null,
                    i.<RagSolicitud>getArgument(0).correlationId(), ejecucionId));
        }
    }
}
