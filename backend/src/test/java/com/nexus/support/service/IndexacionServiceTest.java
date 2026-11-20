package com.nexus.support.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.nexus.support.service.IndexacionService.EstadoIndexacion;
import com.nexus.support.service.IndexacionService.Origen;
import com.nexus.support.service.IndexacionService.Situacion;

/**
 * Bloqueo de una sola indexación a la vez y coalescencia de solicitudes pendientes.
 * El ejecutor se sustituye por una cola manual: las tareas solo corren cuando la prueba lo decide,
 * lo que permite simular «llega otra solicitud mientras la primera sigue en curso» sin hilos reales.
 */
class IndexacionServiceTest {

    private static final Map<String, Object> OK = Map.of("estado", "OK", "sinCambios", 3);

    private N8nClient n8n;
    private final List<Runnable> cola = new ArrayList<>();
    private IndexacionService service;

    @BeforeEach
    void setUp() {
        n8n = mock(N8nClient.class);
        when(n8n.indexar(anyBoolean())).thenReturn(OK);
        service = new IndexacionService(n8n, cola::add);
    }

    @Test
    void sinEjecucionesElEstadoEsNunca() {
        assertThat(service.estado().estado()).isEqualTo(Situacion.NUNCA);
        verifyNoInteractions(n8n);
    }

    @Test
    void laSolicitudNoEsperaAN8nYQuedaEnCurso() {
        EstadoIndexacion e = service.solicitar(false, Origen.MANUAL);

        assertThat(e.estado()).isEqualTo(Situacion.EN_CURSO);
        assertThat(e.origen()).isEqualTo(Origen.MANUAL);
        assertThat(e.inicio()).isNotNull();
        assertThat(e.pendiente()).isFalse();
        assertThat(cola).hasSize(1);
        verifyNoInteractions(n8n);
    }

    @Test
    void unaSolicitudDuranteOtraEnCursoNoLanzaUnaSegundaEjecucion() {
        service.solicitar(false, Origen.MANUAL);

        EstadoIndexacion e = service.solicitar(false, Origen.ARTICULO);

        assertThat(e.estado()).isEqualTo(Situacion.EN_CURSO);
        assertThat(e.pendiente()).isTrue();
        assertThat(cola).as("no se encola una segunda tarea").hasSize(1);
    }

    @Test
    void variasSolicitudesPendientesSeAtiendenConUnaSolaEjecucionAdicional() {
        service.solicitar(false, Origen.MANUAL);
        service.solicitar(false, Origen.ARTICULO);
        service.solicitar(false, Origen.ARTICULO);
        service.solicitar(false, Origen.ARTICULO);

        ejecutarCola();

        verify(n8n, times(2)).indexar(false);
        EstadoIndexacion e = service.estado();
        assertThat(e.estado()).isEqualTo(Situacion.FINALIZADA);
        assertThat(e.pendiente()).isFalse();
        assertThat(e.origen()).isEqualTo(Origen.ARTICULO);
        assertThat(e.resultado()).isEqualTo(OK);
        assertThat(e.fin()).isNotNull();
    }

    @Test
    void siAlgunaPendientePidioForzarLaEjecucionAdicionalFuerza() {
        service.solicitar(false, Origen.MANUAL);
        service.solicitar(true, Origen.MANUAL);
        service.solicitar(false, Origen.ARTICULO);

        ejecutarCola();

        InOrder orden = inOrder(n8n);
        orden.verify(n8n).indexar(false);
        orden.verify(n8n).indexar(true);
        verifyNoMoreInteractions(n8n);
        assertThat(service.estado().forzar()).isTrue();
    }

    @Test
    void trasTerminarSeAceptaUnaNuevaEjecucion() {
        service.solicitar(false, Origen.MANUAL);
        ejecutarCola();

        EstadoIndexacion e = service.solicitar(false, Origen.MANUAL);

        assertThat(e.estado()).isEqualTo(Situacion.EN_CURSO);
        assertThat(e.pendiente()).isFalse();
        assertThat(cola).hasSize(1);
    }

    @Test
    void sinConexionConN8nSeInformaYNoDejaElBloqueoTomado() {
        when(n8n.indexar(anyBoolean())).thenThrow(new N8nClient.N8nException("Connection refused"));
        service.solicitar(false, Origen.MANUAL);

        ejecutarCola();

        EstadoIndexacion e = service.estado();
        assertThat(e.estado()).isEqualTo(Situacion.SIN_CONEXION);
        assertThat(e.mensaje()).contains("Connection refused");
        assertThat(service.solicitar(false, Origen.MANUAL).pendiente()).as("el turno quedó libre").isFalse();
    }

    @Test
    void unFalloInesperadoTampocoDejaElBloqueoTomado() {
        when(n8n.indexar(anyBoolean())).thenThrow(new IllegalStateException("respuesta rara"));
        service.solicitar(false, Origen.MANUAL);

        ejecutarCola();

        EstadoIndexacion e = service.estado();
        assertThat(e.estado()).isEqualTo(Situacion.ERROR);
        assertThat(e.mensaje()).doesNotContain("respuesta rara");
        assertThat(service.solicitar(false, Origen.MANUAL).estado()).isEqualTo(Situacion.EN_CURSO);
        assertThat(cola).hasSize(1);
    }

    @Test
    void siElEjecutorRechazaLaTareaSeLiberaElTurno() {
        Executor lleno = r -> { throw new RejectedExecutionException("cola llena"); };
        IndexacionService s = new IndexacionService(n8n, lleno);

        EstadoIndexacion e = s.solicitar(false, Origen.MANUAL);

        assertThat(e.estado()).isEqualTo(Situacion.ERROR);
        assertThat(e.pendiente()).isFalse();
        // Un segundo intento vuelve a reservar el turno (y vuelve a ser rechazado): no queda «en curso» colgado.
        assertThat(s.solicitar(false, Origen.MANUAL).estado()).isEqualTo(Situacion.ERROR);
        verifyNoInteractions(n8n);
    }

    @Test
    void laEdicionDeUnArticuloSoloSolicitaSinForzar() {
        service.alModificarArticulo(new IndexacionService.ArticuloModificado(5L));

        assertThat(service.estado().origen()).isEqualTo(Origen.ARTICULO);
        ejecutarCola();
        verify(n8n).indexar(false);
    }

    @Test
    void laIndexacionInicialSeOmiteSiYaHayUnaEnCurso() {
        service.solicitar(false, Origen.MANUAL);

        service.indexacionInicial();

        verifyNoInteractions(n8n);
        assertThat(service.estado().pendiente()).as("el arranque queda cubierto por la pendiente").isTrue();
    }

    @Test
    void laIndexacionInicialSeEjecutaEnElPropioHiloYTermina() {
        service.indexacionInicial();

        verify(n8n, times(1)).indexar(false);
        assertThat(cola).isEmpty();
        assertThat(service.estado().estado()).isEqualTo(Situacion.FINALIZADA);
        assertThat(service.estado().origen()).isEqualTo(Origen.ARRANQUE);
    }

    @Test
    void unResultadoDeErrorDeN8nSigueSiendoFinalizadaConElDetalle() {
        Map<String, Object> parcial = Map.of("estado", "ERROR", "etapa", "INDEXACION_PARCIAL");
        when(n8n.indexar(anyBoolean())).thenReturn(parcial);
        service.solicitar(false, Origen.MANUAL);

        ejecutarCola();

        assertThat(service.estado().estado()).isEqualTo(Situacion.FINALIZADA);
        assertThat(service.estado().resultado()).isEqualTo(parcial);
    }

    private void ejecutarCola() {
        while (!cola.isEmpty()) {
            cola.remove(0).run();
        }
    }
}
