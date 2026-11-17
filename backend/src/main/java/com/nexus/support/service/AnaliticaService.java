package com.nexus.support.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.support.domain.EstadoSla;
import com.nexus.support.dto.AnaliticaDtos.*;
import com.nexus.support.exception.ReglaNegocioException;
import com.nexus.support.repository.AnaliticaRepository;

@Service
@Transactional(readOnly = true)
public class AnaliticaService {

    private final AnaliticaRepository repo;

    public AnaliticaService(AnaliticaRepository repo) {
        this.repo = repo;
    }

    public Filtro filtro(LocalDate desde, LocalDate hasta, Long categoriaId, Long prioridadId) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new ReglaNegocioException("La fecha 'hasta' no puede ser anterior a 'desde'");
        }
        return new Filtro(desde, hasta, categoriaId, prioridadId);
    }

    public Resumen resumen(Filtro f) {
        return repo.resumen(f);
    }

    public List<PorCategoria> porCategoria(Filtro f) {
        return repo.porCategoria(f);
    }

    public List<PorPrioridad> porPrioridad(Filtro f) {
        return repo.porPrioridad(f);
    }

    public List<PorEstado> porEstado(Filtro f) {
        return repo.porEstado(f);
    }

    /** @param granularidad "dia" o "semana" */
    public List<PuntoTendencia> tendencia(Filtro f, String granularidad) {
        return repo.tendencia(f, granularidadSql(granularidad));
    }

    public List<ProblemaRecurrente> recurrentes(Filtro f, int limite) {
        return repo.recurrentes(f, Math.clamp(limite, 1, 50));
    }

    public AsistenciaStats asistencia(Filtro f) {
        return repo.asistencia(f);
    }

    /** Todos los indicadores del dashboard en una sola llamada, con los mismos filtros. */
    public Dashboard dashboard(Filtro f, String granularidad) {
        return new Dashboard(f, resumen(f), porCategoria(f), porPrioridad(f), porEstado(f),
                tendencia(f, granularidad), granularidad, recurrentes(f, 8), asistencia(f), slaOperativo(f));
    }

    /** Incidentes no finalizados frente al SLA de su prioridad, calculado en SQL con la hora actual del servidor. */
    public SlaOperativo slaOperativo(Filtro f) {
        return repo.slaOperativo(f, LocalDateTime.now(), EstadoSla.FRACCION_ALERTA, LIMITE_LISTAS);
    }

    /**
     * Calidad de la base de conocimiento a partir del historial RAG y las calificaciones.
     * Las listas son datos observados; las recomendaciones se derivan de ellos con reglas fijas.
     */
    public Conocimiento conocimiento(Filtro f) {
        List<ArticuloUso> uso = repo.usoArticulos(f);
        List<CoberturaCategoria> cobertura = repo.cobertura(f);
        long sinFuentes = cobertura.stream().mapToLong(CoberturaCategoria::sinResultados).sum();
        return new Conocimiento(uso.stream().limit(LIMITE_LISTAS).toList(), sinFuentes,
                repo.consultasSinFuentes(f, LIMITE_LISTAS), cobertura, recomendaciones(uso, cobertura));
    }

    static List<Recomendacion> recomendaciones(List<ArticuloUso> uso, List<CoberturaCategoria> cobertura) {
        List<Recomendacion> r = new ArrayList<>();
        for (CoberturaCategoria c : cobertura) {
            if (c.articulosValidos() == 0) {
                r.add(new Recomendacion("SIN_ARTICULOS_VALIDOS",
                        "Crear o verificar artículos de " + c.categoria(),
                        "0 artículos activos y verificados; " + c.consultas() + " consulta(s) en el periodo"));
            }
            if (c.sinResultados() > 0) {
                r.add(new Recomendacion("CONSULTAS_SIN_FUENTES",
                        "Revisar la cobertura de " + c.categoria() + " con las consultas que no encontraron fuentes",
                        c.sinResultados() + " de " + c.consultas() + " consulta(s) sin fuentes suficientes"));
            }
            if (c.malCalificadas() > 0) {
                r.add(new Recomendacion("RESPUESTAS_MAL_CALIFICADAS",
                        "Revisar las respuestas con calificación baja en " + c.categoria(),
                        c.malCalificadas() + " respuesta(s) calificadas con " + CALIFICACION_BAJA + " o menos"));
            }
        }
        for (ArticuloUso a : uso) {
            if (a.calificaciones() >= MIN_CALIFICACIONES && a.calificacionPromedio() != null
                    && a.calificacionPromedio().compareTo(PROMEDIO_BAJO) <= 0) {
                r.add(new Recomendacion("ARTICULO_MAL_CALIFICADO",
                        "Revisar el contenido del artículo #" + a.id() + " «" + a.titulo() + "»",
                        "calificación promedio " + a.calificacionPromedio() + " en " + a.calificaciones()
                                + " respuesta(s) que lo usaron"));
            }
        }
        return r;
    }

    private static final int LIMITE_LISTAS = 10;
    private static final int CALIFICACION_BAJA = 2;
    private static final int MIN_CALIFICACIONES = 2;
    private static final BigDecimal PROMEDIO_BAJO = new BigDecimal("2.5");

    private static String granularidadSql(String g) {
        return switch (g == null ? "semana" : g.toLowerCase()) {
            case "dia" -> "day";
            case "semana" -> "week";
            default -> throw new ReglaNegocioException("Granularidad inválida: use 'dia' o 'semana'");
        };
    }
}
