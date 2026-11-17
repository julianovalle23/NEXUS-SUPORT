package com.nexus.support.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexus.support.config.NexusProperties;
import com.nexus.support.domain.ArticuloConocimiento;
import com.nexus.support.domain.ConsultaIA;
import com.nexus.support.domain.EstadoConsulta;
import com.nexus.support.domain.Incidente;
import com.nexus.support.domain.RespuestaIA;
import com.nexus.support.dto.AsistenciaDtos.*;
import com.nexus.support.dto.PaginaDto;
import com.nexus.support.exception.RecursoNoEncontradoException;
import com.nexus.support.exception.ReglaNegocioException;
import com.nexus.support.repository.ArticuloRepository;
import com.nexus.support.repository.ConsultaIARepository;
import com.nexus.support.repository.RespuestaIARepository;

/**
 * Orquesta una consulta inteligente (RF04, RF05, RF08).
 *
 * <p>La llamada a n8n se hace FUERA de cualquier transacción: primero se guarda la
 * consulta como PENDIENTE (queda en el historial aunque n8n falle), luego se invoca
 * el flujo y finalmente se persiste el resultado en una segunda transacción.</p>
 */
@Service
public class AsistenciaService {

    private static final Logger log = LoggerFactory.getLogger(AsistenciaService.class);

    static final String MENSAJE_SIN_FUENTES =
            "No se encontró información suficiente en la base de conocimiento para este incidente.";

    private final IncidenteService incidentes;
    private final ConsultaIARepository consultas;
    private final RespuestaIARepository respuestas;
    private final ArticuloRepository articulos;
    private final N8nClient n8n;
    private final NexusProperties props;
    private final TransactionTemplate tx;

    public AsistenciaService(IncidenteService incidentes, ConsultaIARepository consultas,
                             RespuestaIARepository respuestas, ArticuloRepository articulos, N8nClient n8n,
                             NexusProperties props, TransactionTemplate tx) {
        this.incidentes = incidentes;
        this.consultas = consultas;
        this.respuestas = respuestas;
        this.articulos = articulos;
        this.n8n = n8n;
        this.props = props;
        this.tx = tx;
    }

    public ConsultaDto consultar(Long incidenteId, AsistenciaRequest req, boolean simularFalloEmbeddings) {
        if (simularFalloEmbeddings && !props.rag().permitirSimulacionFallos()) {
            throw new ReglaNegocioException("La simulación de fallos está deshabilitada (RAG_PERMITIR_SIMULACION_FALLOS)");
        }
        BigDecimal umbral = BigDecimal.valueOf(props.rag().umbralSimilitud()).setScale(3, RoundingMode.HALF_UP);

        // 1) Validar y registrar la consulta como PENDIENTE
        record Inicio(Long consultaId, RagSolicitud solicitud) {
        }
        Inicio inicio = tx.execute(s -> {
            Incidente inc = incidentes.entidad(incidenteId);
            // Sin campo 'pregunta' se usa la descripción del incidente; un texto enviado en blanco se rechaza.
            String pregunta = (req == null || req.pregunta() == null) ? inc.getDescripcion() : req.pregunta().trim();
            if (pregunta == null || pregunta.isBlank()) {
                // Prueba 7: sin descripción no se ejecuta el flujo inteligente
                throw new ReglaNegocioException("La descripción del incidente está vacía; no se ejecuta la asistencia inteligente");
            }
            ConsultaIA c = consultas.save(new ConsultaIA(inc, pregunta, umbral));
            return new Inicio(c.getId(), new RagSolicitud(c.getId(), pregunta,
                    new IncidenteRag(inc.getTitulo(), inc.getCategoria().getNombre(), inc.getPrioridad().getNombre()),
                    umbral.doubleValue(), props.rag().topK(), props.rag().minResultados(), simularFalloEmbeddings));
        });

        // 2) Invocar n8n sin transacción abierta
        RagResultado resultado;
        try {
            resultado = n8n.consultarRag(inicio.solicitud());
        } catch (N8nClient.N8nException e) {
            resultado = new RagResultado("ERROR", null, null, null, null, null, "N8N", e.getMessage());
        }

        // 3) Persistir el resultado
        RagResultado r = resultado;
        return tx.execute(s -> ConsultaDto.de(registrarResultado(inicio.consultaId(), r)));
    }

    private ConsultaIA registrarResultado(Long consultaId, RagResultado r) {
        ConsultaIA consulta = consultas.findById(consultaId).orElseThrow();
        switch (r.estado()) {
            case "RESPONDIDA" -> registrarRespondida(consulta, r);
            case "SIN_RESULTADOS" -> registrarSinResultados(consulta, r.respuesta());
            default -> {
                String detalle = (r.etapa() == null ? "" : "[" + r.etapa() + "] ")
                        + (r.mensaje() == null ? "Error desconocido en el flujo de asistencia" : r.mensaje());
                log.warn("Consulta {} terminó en ERROR: {}", consultaId, detalle);
                consulta.marcarError(detalle);
            }
        }
        return consulta;
    }

    /**
     * Regla de negocio: los artículos citados deben existir en la base relacional y
     * estar activos y verificados. Se revalida aquí aunque n8n ya lo haya hecho.
     */
    private void registrarRespondida(ConsultaIA consulta, RagResultado r) {
        List<FuenteRag> fuentesRag = r.fuentes() == null ? List.of() : r.fuentes();
        Map<Long, ArticuloConocimiento> validos = articulos
                .findFuentesValidas(fuentesRag.stream().map(FuenteRag::articuloId).toList()).stream()
                .collect(Collectors.toMap(ArticuloConocimiento::getId, Function.identity()));
        List<FuenteRag> aceptadas = fuentesRag.stream()
                .filter(f -> validos.containsKey(f.articuloId()) && f.similitud() != null)
                .toList();

        if (aceptadas.isEmpty() || r.respuesta() == null || r.respuesta().isBlank()) {
            registrarSinResultados(consulta, null);
            return;
        }
        String pasos = r.pasos() == null ? null : String.join("\n", r.pasos().stream()
                .map(p -> p.replace('\n', ' ').trim()).filter(p -> !p.isEmpty()).toList());
        RespuestaIA respuesta = new RespuestaIA(consulta, r.respuesta(), pasos, r.modelo());
        aceptadas.forEach(f -> respuesta.agregarFuente(validos.get(f.articuloId()),
                BigDecimal.valueOf(f.similitud()).setScale(4, RoundingMode.HALF_UP)));
        consulta.setRespuesta(respuestas.save(respuesta));
        consulta.setEstado(EstadoConsulta.RESPONDIDA);
    }

    private void registrarSinResultados(ConsultaIA consulta, String mensaje) {
        RespuestaIA respuesta = new RespuestaIA(consulta,
                mensaje == null || mensaje.isBlank() ? MENSAJE_SIN_FUENTES : mensaje, null, null);
        consulta.setRespuesta(respuestas.save(respuesta));
        consulta.setEstado(EstadoConsulta.SIN_RESULTADOS);
    }

    // ---------- Historial (RF08) ----------

    public List<ConsultaDto> historialIncidente(Long incidenteId) {
        return tx.execute(s -> {
            incidentes.entidad(incidenteId);
            return consultas.findByIncidenteIdOrderByFechaDesc(incidenteId).stream().map(ConsultaDto::de).toList();
        });
    }

    public PaginaDto<ConsultaDto> historial(EstadoConsulta estado, int pagina, int tamano) {
        var pageable = PageRequest.of(Math.max(pagina, 0), Math.clamp(tamano, 1, 100));
        return tx.execute(s -> PaginaDto.de(estado == null
                ? consultas.findAllByOrderByFechaDesc(pageable)
                : consultas.findByEstadoOrderByFechaDesc(estado, pageable), ConsultaDto::de));
    }

    public ConsultaDto obtener(Long consultaId) {
        return tx.execute(s -> ConsultaDto.de(consultas.findById(consultaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Consulta", consultaId))));
    }
}
