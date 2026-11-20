package com.nexus.support.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
            // Sin campo 'pregunta' se usa la descripción del incidente con los datos del ingreso guiado;
            // un texto enviado en blanco se rechaza.
            boolean estructurado = props.rag().ingresoGuiadoEstructurado();
            String porDefecto = estructurado ? textoOpcional(inc.getDescripcion()) : preguntaGuiada(inc);
            String pregunta = (req == null || req.pregunta() == null) ? porDefecto : req.pregunta().trim();
            if (pregunta == null || pregunta.isBlank()) {
                // Prueba 7: sin descripción no se ejecuta el flujo inteligente
                throw new ReglaNegocioException("La descripción del incidente está vacía; no se ejecuta la asistencia inteligente");
            }
            ConsultaIA nueva = new ConsultaIA(inc, pregunta, umbral);
            nueva.setCorrelacionId(UUID.randomUUID().toString());
            ConsultaIA c = consultas.save(nueva);
            return new Inicio(c.getId(), new RagSolicitud(c.getId(), pregunta,
                    incidenteRag(inc, estructurado),
                    umbral.doubleValue(), props.rag().topK(), props.rag().minResultados(), simularFalloEmbeddings,
                    c.getCorrelacionId()));
        });

        // 2) Invocar n8n sin transacción abierta
        RagResultado resultado;
        try {
            resultado = n8n.consultarRag(inicio.solicitud());
        } catch (N8nClient.N8nException e) {
            resultado = new RagResultado("ERROR", null, null, null, null, null, "N8N", e.getMessage(), null,
                    inicio.solicitud().correlationId(), null);
        }

        // 3) Persistir el resultado
        RagResultado r = resultado;
        return tx.execute(s -> ConsultaDto.de(registrarResultado(inicio.consultaId(), r)));
    }

    /** Límite de la pregunta (igual que AsistenciaRequest y NEXUS_MAX_CARACTERES_PREGUNTA del flujo RAG). */
    static final int MAX_PREGUNTA = 5000;

    /** Con ingreso guiado estructurado, el elemento, el mensaje y los pasos van como campos propios. */
    static IncidenteRag incidenteRag(Incidente inc, boolean estructurado) {
        if (!estructurado) {
            return new IncidenteRag(inc.getTitulo(), inc.getCategoria().getNombre(), inc.getPrioridad().getNombre());
        }
        return new IncidenteRag(inc.getTitulo(), inc.getCategoria().getNombre(), inc.getPrioridad().getNombre(),
                inc.getElemento() == null ? null : inc.getElemento().getNombre(),
                textoOpcional(inc.getMensajeError()), textoOpcional(inc.getPasosIntentados()));
    }

    private static String textoOpcional(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * Descripción del incidente más el elemento afectado, el mensaje de error y los pasos ya intentados, para
     * que la búsqueda use esos términos y la respuesta no repita lo que ya se probó. Si supera
     * {@link #MAX_PREGUNTA}, se acorta la descripción y se conservan los datos guiados. Sin descripción
     * devuelve null para que la consulta se rechace.
     */
    static String preguntaGuiada(Incidente inc) {
        String descripcion = inc.getDescripcion() == null ? "" : inc.getDescripcion().trim();
        if (descripcion.isEmpty()) {
            return null;
        }
        StringBuilder extra = new StringBuilder();
        if (inc.getElemento() != null) {
            extra.append("\nEquipo, aplicación o servicio afectado: ").append(inc.getElemento().getNombre());
        }
        if (inc.getMensajeError() != null && !inc.getMensajeError().isBlank()) {
            extra.append("\nMensaje de error: ").append(inc.getMensajeError().trim());
        }
        if (inc.getPasosIntentados() != null && !inc.getPasosIntentados().isBlank()) {
            extra.append("\nPasos ya intentados: ").append(inc.getPasosIntentados().trim());
        }
        int disponible = MAX_PREGUNTA - extra.length();
        if (descripcion.length() > disponible) {
            descripcion = descripcion.substring(0, Math.max(0, disponible - 1)).trim() + "…";
        }
        return descripcion + extra;
    }

    private ConsultaIA registrarResultado(Long consultaId, RagResultado r) {
        ConsultaIA consulta = consultas.findById(consultaId).orElseThrow();
        consulta.setEjecucionN8n(N8nClient.idSeguro(r.ejecucionId()));
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
     * Los candidatos que el modelo recibió pero no citó (auditoria.candidatas con citada=false) se guardan
     * con usada=false, sujetos a la misma validación; nunca cuentan como evidencia ni hacen RESPONDIDA.
     */
    private void registrarRespondida(ConsultaIA consulta, RagResultado r) {
        List<FuenteRag> fuentesRag = r.fuentes() == null ? List.of() : r.fuentes();
        List<CandidataRag> candidatasRag = r.auditoria() == null || r.auditoria().candidatas() == null ? List.of()
                : r.auditoria().candidatas();
        List<Long> ids = Stream.concat(fuentesRag.stream().map(FuenteRag::articuloId),
                candidatasRag.stream().map(CandidataRag::articuloId)).filter(Objects::nonNull).distinct().toList();
        Map<Long, ArticuloConocimiento> validos = articulos.findFuentesValidas(ids).stream()
                .collect(Collectors.toMap(ArticuloConocimiento::getId, Function.identity()));
        List<FuenteRag> aceptadas = fuentesRag.stream()
                .filter(f -> validos.containsKey(f.articuloId()) && f.similitud() != null)
                .filter(distintoPor(FuenteRag::articuloId))
                .toList();
        Set<Long> citadas = aceptadas.stream().map(FuenteRag::articuloId).collect(Collectors.toSet());
        List<CandidataRag> noCitadas = candidatasRag.stream()
                .filter(c -> !Boolean.TRUE.equals(c.citada()) && validos.containsKey(c.articuloId())
                        && !citadas.contains(c.articuloId()) && c.similitud() != null
                        && c.similitud() >= 0 && c.similitud() <= 1)
                .filter(distintoPor(CandidataRag::articuloId))
                .toList();

        if (aceptadas.isEmpty() || r.respuesta() == null || r.respuesta().isBlank()) {
            registrarSinResultados(consulta, null);
            return;
        }
        String pasos = r.pasos() == null ? null : String.join("\n", r.pasos().stream()
                .map(p -> p.replace('\n', ' ').trim()).filter(p -> !p.isEmpty()).toList());
        RespuestaIA respuesta = new RespuestaIA(consulta, r.respuesta(), pasos, r.modelo());
        aceptadas.forEach(f -> respuesta.agregarFuente(validos.get(f.articuloId()), similitud(f.similitud()), true));
        noCitadas.forEach(c -> respuesta.agregarFuente(validos.get(c.articuloId()), similitud(c.similitud()), false));
        consulta.setRespuesta(respuestas.save(respuesta));
        consulta.setEstado(EstadoConsulta.RESPONDIDA);
    }

    private static BigDecimal similitud(double valor) {
        return BigDecimal.valueOf(valor).setScale(4, RoundingMode.HALF_UP);
    }

    /** Filtro con estado para descartar repetidos (uk_fuente impide dos filas del mismo artículo). */
    private static <T> Predicate<T> distintoPor(Function<T, Long> clave) {
        Set<Long> vistos = new HashSet<>();
        return t -> vistos.add(clave.apply(t));
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
