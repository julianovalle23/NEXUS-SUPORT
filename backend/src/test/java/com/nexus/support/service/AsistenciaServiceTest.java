package com.nexus.support.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexus.support.config.NexusProperties;
import com.nexus.support.domain.*;
import com.nexus.support.dto.AsistenciaDtos.*;
import com.nexus.support.exception.ReglaNegocioException;
import com.nexus.support.repository.ArticuloRepository;
import com.nexus.support.repository.ConsultaIARepository;
import com.nexus.support.repository.RespuestaIARepository;

class AsistenciaServiceTest {

    private IncidenteService incidentes;
    private ConsultaIARepository consultas;
    private RespuestaIARepository respuestas;
    private ArticuloRepository articulos;
    private N8nClient n8n;
    private AsistenciaService service;
    private ConsultaIA consultaGuardada;

    @BeforeEach
    void setUp() {
        incidentes = mock(IncidenteService.class);
        consultas = mock(ConsultaIARepository.class);
        respuestas = mock(RespuestaIARepository.class);
        articulos = mock(ArticuloRepository.class);
        n8n = mock(N8nClient.class);

        PlatformTransactionManager ptm = mock(PlatformTransactionManager.class);
        when(ptm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var props = new NexusProperties(null, "token", new NexusProperties.Rag(0.45, 5, 1, 60, true), null);
        service = new AsistenciaService(incidentes, consultas, respuestas, articulos, n8n, props,
                new TransactionTemplate(ptm));

        when(incidentes.entidad(1L)).thenReturn(incidente("El equipo no tiene internet"));
        when(consultas.save(any())).thenAnswer(inv -> {
            consultaGuardada = inv.getArgument(0);
            ReflectionTestUtils.setField(consultaGuardada, "id", 10L);
            return consultaGuardada;
        });
        when(consultas.findById(10L)).thenAnswer(inv -> Optional.of(consultaGuardada));
        when(respuestas.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void preguntaEnBlancoNoEjecutaElFlujo() {
        assertThatThrownBy(() -> service.consultar(1L, new AsistenciaRequest("   "), false))
                .isInstanceOf(ReglaNegocioException.class);
        verifyNoInteractions(n8n);
        verify(consultas, never()).save(any());
    }

    @Test
    void sinPreguntaUsaLaDescripcionDelIncidente() {
        when(n8n.consultarRag(any())).thenReturn(sinResultados());
        service.consultar(1L, null, false);
        verify(n8n).consultarRag(argThat(s -> s.pregunta().equals("El equipo no tiene internet") && s.umbral() == 0.45));
    }

    @Test
    void falloDeN8nDejaLaConsultaEnError() {
        when(n8n.consultarRag(any())).thenThrow(new N8nClient.N8nException("timeout"));
        ConsultaDto r = service.consultar(1L, null, false);
        assertThat(r.estado()).isEqualTo(EstadoConsulta.ERROR);
        assertThat(r.detalleError()).contains("[N8N]").contains("timeout");
        assertThat(r.respuesta()).isNull();
    }

    @Test
    void errorReportadoPorElFlujoConservaLaEtapa() {
        when(n8n.consultarRag(any())).thenReturn(
                new RagResultado("ERROR", null, null, null, null, null, "EMBEDDINGS", "401 Unauthorized"));
        ConsultaDto r = service.consultar(1L, null, false);
        assertThat(r.estado()).isEqualTo(EstadoConsulta.ERROR);
        assertThat(r.detalleError()).startsWith("[EMBEDDINGS]");
    }

    @Test
    void respuestaConFuentesValidasSeGuardaComoRespondida() {
        ArticuloConocimiento a2 = articulo(2L, true, true);
        when(articulos.findFuentesValidas(anyCollection())).thenReturn(List.of(a2));
        when(n8n.consultarRag(any())).thenReturn(new RagResultado("RESPONDIDA", "Renovar IP", List.of("paso 1", "paso 2"),
                List.of(new FuenteRag(2L, "DHCP", "Conectividad", 0.71234, true)), 0.71234, "modelo", null, null));

        ConsultaDto r = service.consultar(1L, null, false);

        assertThat(r.estado()).isEqualTo(EstadoConsulta.RESPONDIDA);
        assertThat(r.respuesta().pasos()).containsExactly("paso 1", "paso 2");
        assertThat(r.respuesta().fuentes()).singleElement()
                .satisfies(f -> assertThat(f.similitud()).isEqualByComparingTo("0.7123"));
    }

    @Test
    void fuentesInactivasSeDescartanYSinFuentesNoHayRespuesta() {
        // n8n devolvió el artículo 2, pero en la base relacional ya no es una fuente válida
        when(articulos.findFuentesValidas(anyCollection())).thenReturn(List.of());
        when(n8n.consultarRag(any())).thenReturn(new RagResultado("RESPONDIDA", "Texto", List.of(),
                List.of(new FuenteRag(2L, "DHCP", "Conectividad", 0.8, true)), 0.8, "modelo", null, null));

        ConsultaDto r = service.consultar(1L, null, false);

        assertThat(r.estado()).isEqualTo(EstadoConsulta.SIN_RESULTADOS);
        assertThat(r.respuesta().fuentes()).isEmpty();
    }

    @Test
    void simulacionDeFallosDeshabilitadaSeRechaza() {
        var props = new NexusProperties(null, "t", new NexusProperties.Rag(0.45, 5, 1, 60, false), null);
        var s = new AsistenciaService(incidentes, consultas, respuestas, articulos, n8n, props, mock(TransactionTemplate.class));
        assertThatThrownBy(() -> s.consultar(1L, null, true)).isInstanceOf(ReglaNegocioException.class);
    }

    private static RagResultado sinResultados() {
        return new RagResultado("SIN_RESULTADOS", "Sin información", List.of(), List.of(), 0.2, null, null, null);
    }

    private static Incidente incidente(String descripcion) {
        Incidente i = new Incidente();
        ReflectionTestUtils.setField(i, "id", 1L);
        i.setTitulo("Sin internet");
        i.setDescripcion(descripcion);
        Categoria c = new Categoria();
        c.setNombre("Conectividad");
        i.setCategoria(c);
        Prioridad p = new Prioridad();
        ReflectionTestUtils.setField(p, "nombre", "Alta");
        i.setPrioridad(p);
        return i;
    }

    private static ArticuloConocimiento articulo(Long id, boolean activo, boolean verificado) {
        ArticuloConocimiento a = new ArticuloConocimiento();
        ReflectionTestUtils.setField(a, "id", id);
        a.setTitulo("Artículo " + id);
        a.setActivo(activo);
        a.setVerificado(verificado);
        return a;
    }
}
