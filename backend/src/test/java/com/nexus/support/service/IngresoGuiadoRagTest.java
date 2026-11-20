package com.nexus.support.service;

import static com.nexus.support.service.Datos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Ingreso guiado hacia n8n (RAG_INGRESO_GUIADO_ESTRUCTURADO). Con false el contrato debe ser el que acepta el
 * flujo publicado (solo titulo, categoria y prioridad); con true viajan los campos guiados y solo los no vacíos.
 */
class IngresoGuiadoRagTest {

    private static final String DESCRIPCION = "Los correos se quedan en la bandeja de salida.";
    private final ObjectMapper json = new ObjectMapper();
    private Incidente inc;

    @BeforeEach
    void setUp() {
        inc = incidente(7, categoria(5, "Correo"), prioridad(2, "Media", 2, 24), estado(1, "Abierto", false),
                usuario(1, "Laura Gomez", Rol.TECNICO));
        inc.setElemento(elemento(30, "Outlook 365", true));
        inc.setMensajeError("  0x800CCC0E  ");
        inc.setPasosIntentados("Reinicié el equipo");
    }

    // ---------- IncidenteRag ----------

    @Test
    void sinModoEstructuradoSoloSeEnvianTituloCategoriaYPrioridad() {
        IncidenteRag r = AsistenciaService.incidenteRag(inc, false);

        assertThat(r).isEqualTo(new IncidenteRag("Outlook no envía correos", "Correo", "Media"));
        assertThat(campos(r)).containsExactlyInAnyOrder("titulo", "categoria", "prioridad");
    }

    @Test
    void conModoEstructuradoViajanLosCamposGuiadosSinEspaciosSobrantes() {
        IncidenteRag r = AsistenciaService.incidenteRag(inc, true);

        assertThat(r.elemento()).isEqualTo("Outlook 365");
        assertThat(r.mensajeError()).isEqualTo("0x800CCC0E");
        assertThat(r.pasosIntentados()).isEqualTo("Reinicié el equipo");
        assertThat(campos(r)).containsExactlyInAnyOrder("titulo", "categoria", "prioridad", "elemento",
                "mensajeError", "pasosIntentados");
    }

    @Test
    void conModoEstructuradoLosCamposVaciosNoSeEnvian() {
        inc.setElemento(null);
        inc.setMensajeError("   ");
        inc.setPasosIntentados(null);

        IncidenteRag r = AsistenciaService.incidenteRag(inc, true);

        assertThat(r.elemento()).isNull();
        assertThat(r.mensajeError()).isNull();
        assertThat(campos(r)).containsExactlyInAnyOrder("titulo", "categoria", "prioridad");
    }

    // ---------- Solicitud completa enviada a n8n ----------

    @Test
    void sinModoEstructuradoLosDatosGuiadosVanEnElTextoDeLaPregunta() {
        RagSolicitud s = consultar(false);

        assertThat(s.pregunta()).startsWith(DESCRIPCION)
                .contains("\nEquipo, aplicación o servicio afectado: Outlook 365")
                .contains("\nMensaje de error: 0x800CCC0E")
                .endsWith("\nPasos ya intentados: Reinicié el equipo");
        assertThat(campos(s.incidente())).containsExactlyInAnyOrder("titulo", "categoria", "prioridad");
    }

    @Test
    void conModoEstructuradoLaPreguntaEsSoloLaDescripcionYLosDatosVanAparte() {
        RagSolicitud s = consultar(true);

        assertThat(s.pregunta()).isEqualTo(DESCRIPCION);
        assertThat(s.incidente().mensajeError()).isEqualTo("0x800CCC0E");
        assertThat(s.incidente().pasosIntentados()).isEqualTo("Reinicié el equipo");
        assertThat(s.incidente().elemento()).isEqualTo("Outlook 365");
    }

    @Test
    void unaPreguntaEscritaSeRespetaEnAmbosModos() {
        assertThat(consultar(false, "¿Cómo reconfiguro SMTP?").pregunta()).isEqualTo("¿Cómo reconfiguro SMTP?");
        assertThat(consultar(true, "¿Cómo reconfiguro SMTP?").pregunta()).isEqualTo("¿Cómo reconfiguro SMTP?");
    }

    /** Claves del JSON que recibe n8n. */
    private List<String> campos(IncidenteRag r) {
        JsonNode nodo = json.valueToTree(r);
        return iterable(nodo.fieldNames());
    }

    private static List<String> iterable(java.util.Iterator<String> it) {
        List<String> l = new java.util.ArrayList<>();
        it.forEachRemaining(l::add);
        return l;
    }

    private RagSolicitud consultar(boolean estructurado) {
        return consultar(estructurado, null);
    }

    private RagSolicitud consultar(boolean estructurado, String pregunta) {
        IncidenteService incidentes = mock(IncidenteService.class);
        ConsultaIARepository consultas = mock(ConsultaIARepository.class);
        N8nClient n8n = mock(N8nClient.class);
        PlatformTransactionManager ptm = mock(PlatformTransactionManager.class);
        when(ptm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var props = new NexusProperties(null, "t", new NexusProperties.Rag(0.45, 5, 1, 60, false, estructurado), null);
        var service = new AsistenciaService(incidentes, consultas, mock(RespuestaIARepository.class),
                mock(ArticuloRepository.class), n8n, props, new TransactionTemplate(ptm));

        when(incidentes.entidad(7L)).thenReturn(inc);
        ConsultaIA[] guardada = new ConsultaIA[1];
        when(consultas.save(any())).thenAnswer(i -> {
            guardada[0] = i.getArgument(0);
            ReflectionTestUtils.setField(guardada[0], "id", 10L);
            return guardada[0];
        });
        when(consultas.findById(10L)).thenAnswer(i -> Optional.of(guardada[0]));
        when(n8n.consultarRag(any())).thenReturn(
                new RagResultado("SIN_RESULTADOS", "Sin información", List.of(), List.of(), 0.2, null, null, null, null));

        service.consultar(7L, pregunta == null ? null
                : new com.nexus.support.dto.AsistenciaDtos.AsistenciaRequest(pregunta), false);

        ArgumentCaptor<RagSolicitud> captor = ArgumentCaptor.forClass(RagSolicitud.class);
        verify(n8n).consultarRag(captor.capture());
        return captor.getValue();
    }
}
