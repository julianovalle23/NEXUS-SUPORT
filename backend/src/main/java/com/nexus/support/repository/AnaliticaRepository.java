package com.nexus.support.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.nexus.support.domain.EstadoPrimeraRespuesta;
import com.nexus.support.dto.AnaliticaDtos.*;
import com.nexus.support.dto.RevisionDtos.RevisionPendiente;
import com.nexus.support.dto.RevisionDtos.RevisionesPendientes;

/**
 * Consultas analíticas (RF06) en SQL nativo de PostgreSQL. Todos los indicadores
 * se calculan sobre los datos almacenados; las mismas consultas están documentadas
 * en docs/analisis/consultas-analiticas.sql para verificarlas manualmente.
 *
 * <p>Convenciones: un incidente está "resuelto" si su estado es final (Resuelto o
 * Cerrado). El tiempo de resolución se mide en horas entre fecha_creacion y
 * fecha_resolucion, solo para incidentes resueltos.</p>
 */
@Repository
public class AnaliticaRepository {

    /** CTE con los incidentes que cumplen los filtros; la reutilizan todas las consultas. */
    private static final String BASE = """
            WITH f AS (
                SELECT i.*,
                       e.es_final AS resuelto,
                       CASE WHEN e.es_final AND i.fecha_resolucion IS NOT NULL
                            THEN EXTRACT(EPOCH FROM (i.fecha_resolucion - i.fecha_creacion)) / 3600.0 END AS horas
                FROM incidente i
                JOIN estado_incidente e ON e.id = i.estado_id
                WHERE (CAST(:desde AS date) IS NULL OR i.fecha_creacion >= CAST(:desde AS date))
                  AND (CAST(:hasta AS date) IS NULL OR i.fecha_creacion < CAST(:hasta AS date) + 1)
                  AND (CAST(:categoriaId AS bigint) IS NULL OR i.categoria_id = :categoriaId)
                  AND (CAST(:prioridadId AS bigint) IS NULL OR i.prioridad_id = :prioridadId)
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AnaliticaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Resumen resumen(Filtro filtro) {
        String sql = BASE + """
                , top AS (
                    SELECT c.nombre, COUNT(*) AS n
                    FROM f JOIN categoria c ON c.id = f.categoria_id
                    GROUP BY c.nombre ORDER BY n DESC, c.nombre LIMIT 1
                )
                SELECT COUNT(*)                                               AS total,
                       COUNT(*) FILTER (WHERE resuelto)                       AS resueltos,
                       COUNT(*) FILTER (WHERE NOT resuelto)                   AS pendientes,
                       ROUND(100.0 * COUNT(*) FILTER (WHERE resuelto) / NULLIF(COUNT(*), 0), 2) AS tasa,
                       ROUND(CAST(AVG(horas) AS numeric), 2)                  AS promedio,
                       ROUND(CAST(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY horas) AS numeric), 2) AS mediana,
                       ROUND(100.0 * COUNT(*) FILTER (WHERE horas <= p.sla_horas)
                             / NULLIF(COUNT(*) FILTER (WHERE resuelto), 0), 2) AS sla,
                       (SELECT nombre FROM top)                               AS top_categoria,
                       COALESCE((SELECT n FROM top), 0)                       AS top_n
                FROM f JOIN prioridad p ON p.id = f.prioridad_id
                """;
        return jdbc.queryForObject(sql, params(filtro), (rs, i) -> new Resumen(
                rs.getLong("total"), rs.getLong("resueltos"), rs.getLong("pendientes"), rs.getBigDecimal("tasa"),
                rs.getBigDecimal("promedio"), rs.getBigDecimal("mediana"), rs.getBigDecimal("sla"),
                rs.getString("top_categoria"), rs.getLong("top_n")));
    }

    public List<PorCategoria> porCategoria(Filtro filtro) {
        String sql = BASE + """
                SELECT c.id, c.nombre,
                       COUNT(*) AS total,
                       ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2)                   AS porcentaje,
                       COUNT(*) FILTER (WHERE f.resuelto)                                  AS resueltos,
                       ROUND(100.0 * COUNT(*) FILTER (WHERE f.resuelto) / COUNT(*), 2)     AS tasa,
                       ROUND(CAST(AVG(f.horas) AS numeric), 2)                             AS promedio
                FROM f JOIN categoria c ON c.id = f.categoria_id
                GROUP BY c.id, c.nombre
                ORDER BY total DESC, c.nombre
                """;
        return jdbc.query(sql, params(filtro), (rs, i) -> new PorCategoria(rs.getLong("id"), rs.getString("nombre"),
                rs.getLong("total"), rs.getBigDecimal("porcentaje"), rs.getLong("resueltos"),
                rs.getBigDecimal("tasa"), rs.getBigDecimal("promedio")));
    }

    public List<PorPrioridad> porPrioridad(Filtro filtro) {
        String sql = BASE + """
                SELECT p.id, p.nombre, p.nivel, p.sla_horas,
                       COUNT(*) AS total,
                       ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2) AS porcentaje,
                       ROUND(CAST(AVG(f.horas) AS numeric), 2)           AS promedio,
                       ROUND(100.0 * COUNT(*) FILTER (WHERE f.horas <= p.sla_horas)
                             / NULLIF(COUNT(*) FILTER (WHERE f.resuelto), 0), 2) AS sla
                FROM f JOIN prioridad p ON p.id = f.prioridad_id
                GROUP BY p.id, p.nombre, p.nivel, p.sla_horas
                ORDER BY p.nivel DESC
                """;
        return jdbc.query(sql, params(filtro), (rs, i) -> new PorPrioridad(rs.getLong("id"), rs.getString("nombre"),
                rs.getInt("nivel"), rs.getInt("sla_horas"), rs.getLong("total"), rs.getBigDecimal("porcentaje"),
                rs.getBigDecimal("promedio"), rs.getBigDecimal("sla")));
    }

    public List<PorEstado> porEstado(Filtro filtro) {
        String sql = BASE + """
                SELECT e.id, e.nombre, e.es_final,
                       COUNT(*) AS total,
                       ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2) AS porcentaje
                FROM f JOIN estado_incidente e ON e.id = f.estado_id
                GROUP BY e.id, e.nombre, e.es_final, e.orden
                ORDER BY e.orden
                """;
        return jdbc.query(sql, params(filtro), (rs, i) -> new PorEstado(rs.getLong("id"), rs.getString("nombre"),
                rs.getBoolean("es_final"), rs.getLong("total"), rs.getBigDecimal("porcentaje")));
    }

    /**
     * Evolución diaria o semanal. Se generan todos los periodos del rango (incluso
     * los que no tienen incidentes) para que la serie sea continua.
     *
     * @param granularidad "day" o "week" (validado en el servicio; se inserta como literal)
     */
    public List<PuntoTendencia> tendencia(Filtro filtro, String granularidad) {
        if (!granularidad.equals("day") && !granularidad.equals("week")) {
            throw new IllegalArgumentException("Granularidad no soportada: " + granularidad);
        }
        String sql = BASE + """
                , rango AS (
                    SELECT date_trunc('%1$s', MIN(fecha_creacion)) AS ini,
                           date_trunc('%1$s', MAX(fecha_creacion)) AS fin
                    FROM f
                ), periodos AS (
                    SELECT generate_series(ini, fin, INTERVAL '1 %1$s') AS periodo FROM rango WHERE ini IS NOT NULL
                )
                SELECT CAST(p.periodo AS date) AS periodo,
                       COUNT(f.id)                         AS total,
                       COUNT(f.id) FILTER (WHERE f.resuelto) AS resueltos
                FROM periodos p
                LEFT JOIN f ON date_trunc('%1$s', f.fecha_creacion) = p.periodo
                GROUP BY p.periodo
                ORDER BY p.periodo
                """.formatted(granularidad);
        return jdbc.query(sql, params(filtro), (rs, i) -> new PuntoTendencia(rs.getDate("periodo").toLocalDate(),
                rs.getLong("total"), rs.getLong("resueltos")));
    }

    /** Problemas que más se repiten (agrupados por título normalizado). */
    public List<ProblemaRecurrente> recurrentes(Filtro filtro, int limite) {
        String sql = BASE + """
                SELECT MIN(f.titulo) AS titulo, c.nombre AS categoria, COUNT(*) AS ocurrencias,
                       ROUND(CAST(AVG(f.horas) AS numeric), 2) AS promedio
                FROM f JOIN categoria c ON c.id = f.categoria_id
                GROUP BY LOWER(f.titulo), c.nombre
                HAVING COUNT(*) > 1
                ORDER BY ocurrencias DESC, titulo
                LIMIT :limite
                """;
        return jdbc.query(sql, params(filtro).addValue("limite", limite), (rs, i) -> new ProblemaRecurrente(
                rs.getString("titulo"), rs.getString("categoria"), rs.getLong("ocurrencias"),
                rs.getBigDecimal("promedio")));
    }

    /**
     * Incidentes por elemento de TI afectado. Los incidentes sin elemento salen en una fila con
     * elementoId nulo (primera, para que el límite nunca la oculte).
     */
    public List<PorElemento> porElemento(Filtro filtro, int limite) {
        String sql = BASE + """
                SELECT el.id, el.nombre, el.tipo,
                       COUNT(*)                                   AS total,
                       COUNT(*) FILTER (WHERE NOT f.resuelto)     AS pendientes,
                       ROUND(CAST(AVG(f.horas) AS numeric), 2)    AS promedio,
                       ROUND(100.0 * COUNT(*) FILTER (WHERE f.horas <= p.sla_horas)
                             / NULLIF(COUNT(*) FILTER (WHERE f.resuelto), 0), 2) AS sla
                FROM f
                JOIN prioridad p        ON p.id = f.prioridad_id
                LEFT JOIN elemento_ti el ON el.id = f.elemento_id
                GROUP BY el.id, el.nombre, el.tipo
                ORDER BY (el.id IS NULL) DESC, total DESC, el.nombre
                LIMIT :limite
                """;
        return jdbc.query(sql, params(filtro).addValue("limite", limite), (rs, i) -> new PorElemento(
                (Long) rs.getObject("id"), rs.getString("nombre"), rs.getString("tipo"), rs.getLong("total"),
                rs.getLong("pendientes"), rs.getBigDecimal("promedio"), rs.getBigDecimal("sla")));
    }

    /** Uso del asistente RAG en el periodo (filtrado por la fecha de la consulta). */
    public AsistenciaStats asistencia(Filtro filtro) {
        String sql = """
                SELECT COUNT(*)                                          AS total,
                       COUNT(*) FILTER (WHERE c.estado = 'RESPONDIDA')     AS respondidas,
                       COUNT(*) FILTER (WHERE c.estado = 'SIN_RESULTADOS') AS sin_resultados,
                       COUNT(*) FILTER (WHERE c.estado = 'ERROR')          AS errores,
                       COUNT(*) FILTER (WHERE c.estado = 'PENDIENTE')      AS pendientes,
                       ROUND(AVG(k.puntuacion), 2)                         AS calificacion,
                       COUNT(k.id)                                         AS calificadas
                FROM consulta_ia c
                JOIN incidente i ON i.id = c.incidente_id
                LEFT JOIN respuesta_ia r ON r.consulta_id = c.id
                LEFT JOIN calificacion k ON k.respuesta_id = r.id
                WHERE (CAST(:desde AS date) IS NULL OR c.fecha >= CAST(:desde AS date))
                  AND (CAST(:hasta AS date) IS NULL OR c.fecha < CAST(:hasta AS date) + 1)
                  AND (CAST(:categoriaId AS bigint) IS NULL OR i.categoria_id = :categoriaId)
                  AND (CAST(:prioridadId AS bigint) IS NULL OR i.prioridad_id = :prioridadId)
                """;
        return jdbc.queryForObject(sql, params(filtro), (rs, i) -> new AsistenciaStats(rs.getLong("total"),
                rs.getLong("respondidas"), rs.getLong("sin_resultados"), rs.getLong("errores"),
                rs.getLong("pendientes"), rs.getBigDecimal("calificacion"), rs.getLong("calificadas")));
    }

    // ---------- SLA operativo ----------

    /**
     * Incidentes no finalizados (con los filtros del dashboard) clasificados frente al SLA de su prioridad.
     * Misma regla que {@link com.nexus.support.domain.EstadoSla}: vence = creación + sla_horas;
     * "por vencer" cuando quedan como máximo fraccion × sla_horas.
     */
    private static final String SLA_ABIERTOS = BASE + """
            , a AS (
                SELECT f.id, f.titulo, p.nombre AS prioridad, p.sla_horas, u.nombre AS responsable, e.nombre AS estado,
                       f.fecha_creacion + make_interval(hours => p.sla_horas) AS vence
                FROM f
                JOIN prioridad p ON p.id = f.prioridad_id
                JOIN usuario u ON u.id = f.usuario_id
                JOIN estado_incidente e ON e.id = f.estado_id
                WHERE NOT f.resuelto
            ), s AS (
                SELECT a.*,
                       ROUND(CAST(EXTRACT(EPOCH FROM (a.vence - CAST(:ahora AS timestamp))) / 3600.0 AS numeric), 2) AS restantes,
                       CASE WHEN CAST(:ahora AS timestamp) > a.vence THEN 'VENCIDO'
                            WHEN a.vence - CAST(:ahora AS timestamp)
                                 <= make_interval(mins => CAST(ROUND(a.sla_horas * 60 * :fraccion) AS int)) THEN 'POR_VENCER'
                            ELSE 'EN_PLAZO' END AS estado_sla
                FROM a
            )
            """;

    public SlaOperativo slaOperativo(Filtro filtro, LocalDateTime ahora, double fraccion, int limite) {
        MapSqlParameterSource p = params(filtro).addValue("ahora", ahora).addValue("fraccion", fraccion)
                .addValue("limite", limite);
        List<IncidenteSla> criticos = jdbc.query(SLA_ABIERTOS + """
                SELECT * FROM s WHERE estado_sla <> 'EN_PLAZO' ORDER BY vence, id LIMIT :limite
                """, p, (rs, i) -> new IncidenteSla(rs.getLong("id"), rs.getString("titulo"), rs.getString("prioridad"),
                rs.getInt("sla_horas"), rs.getString("responsable"), rs.getString("estado"),
                rs.getTimestamp("vence").toLocalDateTime(), rs.getBigDecimal("restantes"), rs.getString("estado_sla")));
        return jdbc.queryForObject(SLA_ABIERTOS + """
                SELECT COUNT(*)                                          AS abiertos,
                       COUNT(*) FILTER (WHERE estado_sla = 'EN_PLAZO')   AS en_plazo,
                       COUNT(*) FILTER (WHERE estado_sla = 'POR_VENCER') AS por_vencer,
                       COUNT(*) FILTER (WHERE estado_sla = 'VENCIDO')    AS vencidos
                FROM s
                """, p, (rs, i) -> new SlaOperativo(rs.getLong("abiertos"), rs.getLong("en_plazo"),
                rs.getLong("por_vencer"), rs.getLong("vencidos"), fraccion, criticos,
                primeraRespuestaResumen(p)));
    }

    // ---------- SLA de primera respuesta ----------

    /**
     * Incidentes (con los filtros) clasificados frente a su objetivo de primera respuesta.
     * Datos de la vista v_primera_respuesta (database/init/05-mejoras.sql); "por vencer" igual que el SLA de
     * resolución: quedan como máximo fraccion × objetivo.
     */
    private static final String PRIMERA_RESPUESTA = BASE + """
            , pr AS (
                SELECT f.id, v.objetivo_minutos, v.respondido_en, v.sin_datos,
                       f.fecha_creacion + make_interval(mins => v.objetivo_minutos) AS vence
                FROM f JOIN v_primera_respuesta v ON v.incidente_id = f.id
            ), prs AS (
                SELECT pr.*,
                       CASE WHEN objetivo_minutos IS NULL THEN 'SIN_OBJETIVO'
                            WHEN sin_datos THEN 'SIN_DATOS'
                            WHEN respondido_en IS NOT NULL THEN
                                 CASE WHEN respondido_en > vence THEN 'INCUMPLIDO' ELSE 'CUMPLIDO' END
                            WHEN CAST(:ahora AS timestamp) > vence THEN 'VENCIDO'
                            WHEN vence - CAST(:ahora AS timestamp)
                                 <= make_interval(mins => CAST(ROUND(objetivo_minutos * :fraccion) AS int)) THEN 'POR_VENCER'
                            ELSE 'EN_PLAZO' END AS estado_pr
                FROM pr
            )
            """;

    private PrimeraRespuestaResumen primeraRespuestaResumen(MapSqlParameterSource p) {
        return jdbc.queryForObject(PRIMERA_RESPUESTA + """
                SELECT COUNT(*) FILTER (WHERE estado_pr = 'EN_PLAZO')     AS en_plazo,
                       COUNT(*) FILTER (WHERE estado_pr = 'POR_VENCER')   AS por_vencer,
                       COUNT(*) FILTER (WHERE estado_pr = 'VENCIDO')      AS vencidos,
                       COUNT(*) FILTER (WHERE estado_pr = 'CUMPLIDO')     AS cumplidos,
                       COUNT(*) FILTER (WHERE estado_pr = 'INCUMPLIDO')   AS incumplidos,
                       COUNT(*) FILTER (WHERE estado_pr = 'SIN_OBJETIVO') AS sin_objetivo,
                       COUNT(*) FILTER (WHERE estado_pr = 'SIN_DATOS')    AS sin_datos,
                       ROUND(100.0 * COUNT(*) FILTER (WHERE estado_pr = 'CUMPLIDO')
                             / NULLIF(COUNT(*) FILTER (WHERE estado_pr IN ('CUMPLIDO', 'INCUMPLIDO')), 0), 2) AS cumplimiento
                FROM prs
                """, p, (rs, i) -> new PrimeraRespuestaResumen(rs.getLong("en_plazo"), rs.getLong("por_vencer"),
                rs.getLong("vencidos"), rs.getLong("cumplidos"), rs.getLong("incumplidos"),
                rs.getLong("sin_objetivo"), rs.getLong("sin_datos"), rs.getBigDecimal("cumplimiento")));
    }

    /** Primera respuesta de un incidente; vacío si el incidente no existe. */
    public Optional<PrimeraRespuesta> primeraRespuesta(Long incidenteId, LocalDateTime ahora, double fraccion) {
        MapSqlParameterSource p = params(new Filtro(null, null, null, null))
                .addValue("ahora", ahora).addValue("fraccion", fraccion).addValue("id", incidenteId);
        return jdbc.query(PRIMERA_RESPUESTA + """
                SELECT objetivo_minutos, vence, respondido_en, estado_pr,
                       CASE WHEN estado_pr IN ('EN_PLAZO', 'POR_VENCER', 'VENCIDO')
                            THEN ROUND(CAST(EXTRACT(EPOCH FROM (vence - CAST(:ahora AS timestamp))) / 60.0 AS numeric), 1)
                       END AS restantes
                FROM prs WHERE id = :id
                """, p, (rs, i) -> new PrimeraRespuesta((Integer) rs.getObject("objetivo_minutos"),
                fecha(rs.getTimestamp("vence")), fecha(rs.getTimestamp("respondido_en")),
                EstadoPrimeraRespuesta.valueOf(rs.getString("estado_pr")), rs.getBigDecimal("restantes")))
                .stream().findFirst();
    }

    // ---------- Revisiones posteriores ----------

    /**
     * Incidentes finalizados sin revisión que se consideran importantes: prioridad con nivel >= nivelMinimo
     * o resueltos después del SLA de resolución de su prioridad.
     */
    private static final String PENDIENTES_REVISION = """
            WITH c AS (
                SELECT i.id, i.titulo, p.nombre AS prioridad, i.fecha_resolucion,
                       p.nivel >= :nivelMinimo AS alta,
                       i.fecha_resolucion > i.fecha_creacion + make_interval(hours => p.sla_horas) AS incumplido
                FROM incidente i
                JOIN estado_incidente e ON e.id = i.estado_id
                JOIN prioridad p        ON p.id = i.prioridad_id
                WHERE e.es_final AND i.fecha_resolucion IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM revision_incidente r WHERE r.incidente_id = i.id)
            )
            """;

    public RevisionesPendientes revisionesPendientes(int nivelMinimo, int limite) {
        MapSqlParameterSource p = new MapSqlParameterSource("nivelMinimo", nivelMinimo).addValue("limite", limite);
        Long total = jdbc.queryForObject(PENDIENTES_REVISION + "SELECT COUNT(*) FROM c WHERE alta OR incumplido",
                p, Long.class);
        List<RevisionPendiente> ultimas = jdbc.query(PENDIENTES_REVISION + """
                SELECT * FROM c WHERE alta OR incumplido
                ORDER BY fecha_resolucion DESC, id DESC
                LIMIT :limite
                """, p, (rs, i) -> new RevisionPendiente(rs.getLong("id"), rs.getString("titulo"),
                rs.getString("prioridad"), rs.getTimestamp("fecha_resolucion").toLocalDateTime(),
                rs.getBoolean("alta") && rs.getBoolean("incumplido") ? "Prioridad alta y SLA incumplido"
                        : rs.getBoolean("alta") ? "Prioridad alta" : "SLA incumplido"));
        return new RevisionesPendientes(total == null ? 0 : total, nivelMinimo, ultimas);
    }

    private static LocalDateTime fecha(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }

    // ---------- Calidad de la base de conocimiento ----------

    /** Filtro común para consultas RAG: fecha de la consulta y categoría/prioridad del incidente. */
    private static final String FILTRO_CONSULTA = """
            (CAST(:desde AS date) IS NULL OR c.fecha >= CAST(:desde AS date))
              AND (CAST(:hasta AS date) IS NULL OR c.fecha < CAST(:hasta AS date) + 1)
              AND (CAST(:categoriaId AS bigint) IS NULL OR i.categoria_id = :categoriaId)
              AND (CAST(:prioridadId AS bigint) IS NULL OR i.prioridad_id = :prioridadId)
            """;

    /** Artículos citados como fuente (usada = true, sin candidatos no citados) en respuestas guardadas, con similitud y calificación de esas respuestas. */
    public List<ArticuloUso> usoArticulos(Filtro filtro) {
        String sql = """
                SELECT a.id, a.titulo, cat.nombre AS categoria, (a.activo AND a.verificado) AS fuente_valida,
                       COUNT(*)                                   AS usos,
                       ROUND(AVG(fr.similitud), 4)                AS similitud,
                       ROUND(AVG(k.puntuacion), 2)                AS calificacion,
                       COUNT(k.id)                                AS calificaciones
                FROM fuente_respuesta fr
                JOIN respuesta_ia r            ON r.id = fr.respuesta_id
                JOIN consulta_ia c             ON c.id = r.consulta_id
                JOIN incidente i               ON i.id = c.incidente_id
                JOIN articulo_conocimiento a   ON a.id = fr.articulo_id
                LEFT JOIN categoria cat        ON cat.id = a.categoria_id
                LEFT JOIN calificacion k       ON k.respuesta_id = r.id
                WHERE fr.usada AND """ + FILTRO_CONSULTA + """
                GROUP BY a.id, a.titulo, cat.nombre, a.activo, a.verificado
                ORDER BY calificacion DESC NULLS LAST, usos DESC, a.id
                """;
        return jdbc.query(sql, params(filtro), (rs, i) -> new ArticuloUso(rs.getLong("id"), rs.getString("titulo"),
                rs.getString("categoria"), rs.getBoolean("fuente_valida"), rs.getLong("usos"),
                rs.getBigDecimal("similitud"), rs.getBigDecimal("calificacion"), rs.getLong("calificaciones")));
    }

    /** Consultas que terminaron sin fuentes suficientes, de la más reciente a la más antigua. */
    public List<ConsultaSinFuentes> consultasSinFuentes(Filtro filtro, int limite) {
        String sql = """
                SELECT c.id, c.fecha, i.id AS incidente_id, i.titulo, cat.nombre AS categoria, c.pregunta
                FROM consulta_ia c
                JOIN incidente i   ON i.id = c.incidente_id
                JOIN categoria cat ON cat.id = i.categoria_id
                WHERE c.estado = 'SIN_RESULTADOS' AND """ + FILTRO_CONSULTA + """
                ORDER BY c.fecha DESC, c.id DESC
                LIMIT :limite
                """;
        return jdbc.query(sql, params(filtro).addValue("limite", limite), (rs, i) -> new ConsultaSinFuentes(
                rs.getLong("id"), rs.getTimestamp("fecha").toLocalDateTime(), rs.getLong("incidente_id"),
                rs.getString("titulo"), rs.getString("categoria"), rs.getString("pregunta")));
    }

    /** Por categoría: artículos válidos disponibles frente a consultas, consultas sin fuentes y respuestas mal calificadas. */
    public List<CoberturaCategoria> cobertura(Filtro filtro) {
        String sql = """
                SELECT cat.id, cat.nombre,
                       (SELECT COUNT(*) FROM articulo_conocimiento a
                        WHERE a.categoria_id = cat.id AND a.activo AND a.verificado)     AS validos,
                       COUNT(c.id)                                                      AS consultas,
                       COUNT(c.id) FILTER (WHERE c.estado = 'RESPONDIDA')               AS respondidas,
                       COUNT(c.id) FILTER (WHERE c.estado = 'SIN_RESULTADOS')           AS sin_resultados,
                       COUNT(k.id) FILTER (WHERE k.puntuacion <= 2)                     AS mal_calificadas,
                       ROUND(AVG(k.puntuacion), 2)                                      AS calificacion
                FROM categoria cat
                LEFT JOIN incidente i     ON i.categoria_id = cat.id
                                         AND (CAST(:prioridadId AS bigint) IS NULL OR i.prioridad_id = :prioridadId)
                LEFT JOIN consulta_ia c   ON c.incidente_id = i.id
                                         AND (CAST(:desde AS date) IS NULL OR c.fecha >= CAST(:desde AS date))
                                         AND (CAST(:hasta AS date) IS NULL OR c.fecha < CAST(:hasta AS date) + 1)
                LEFT JOIN respuesta_ia r  ON r.consulta_id = c.id
                LEFT JOIN calificacion k  ON k.respuesta_id = r.id
                WHERE CAST(:categoriaId AS bigint) IS NULL OR cat.id = :categoriaId
                GROUP BY cat.id, cat.nombre
                ORDER BY sin_resultados DESC, cat.nombre
                """;
        return jdbc.query(sql, params(filtro), (rs, i) -> new CoberturaCategoria(rs.getLong("id"),
                rs.getString("nombre"), rs.getLong("validos"), rs.getLong("consultas"), rs.getLong("respondidas"),
                rs.getLong("sin_resultados"), rs.getLong("mal_calificadas"), rs.getBigDecimal("calificacion")));
    }

    /**
     * Respuestas con fuentes según lo declarado al calificar, e incidentes reabiertos después de su
     * primera consulta RESPONDIDA. Reapertura = fila ACTUALIZACION o REAPERTURA del historial con estado no final cuya
     * fila anterior tenía estado final (una fila MIGRACION sirve como "anterior": era la situación real
     * en ese momento, pero nunca cuenta como cambio).
     */
    public Efectividad efectividad(Filtro filtro) {
        String sql = """
                WITH resp AS (
                    SELECT c.incidente_id, c.fecha, k.resolvio, k.id AS calificacion_id
                    FROM consulta_ia c
                    JOIN incidente i          ON i.id = c.incidente_id
                    JOIN respuesta_ia r       ON r.consulta_id = c.id
                    LEFT JOIN calificacion k  ON k.respuesta_id = r.id
                    WHERE c.estado = 'RESPONDIDA' AND """ + FILTRO_CONSULTA + """
                ), asistidos AS (
                    SELECT incidente_id, MIN(fecha) AS primera FROM resp GROUP BY incidente_id
                ), transiciones AS (
                    SELECT h.incidente_id, h.fecha, h.tipo, e.es_final,
                           LAG(e.es_final) OVER (PARTITION BY h.incidente_id ORDER BY h.fecha, h.id) AS final_antes
                    FROM incidente_historial h
                    JOIN estado_incidente e ON e.id = h.estado_id
                    WHERE h.incidente_id IN (SELECT incidente_id FROM asistidos)
                ), reaperturas AS (
                    SELECT incidente_id, fecha FROM transiciones
                    WHERE tipo IN ('ACTUALIZACION', 'REAPERTURA') AND final_antes AND NOT es_final
                )
                SELECT (SELECT COUNT(*) FROM resp)                                  AS respuestas,
                       (SELECT COUNT(*) FROM resp WHERE resolvio)                   AS resolvio,
                       (SELECT COUNT(*) FROM resp WHERE NOT resolvio)               AS no_resolvio,
                       (SELECT COUNT(*) FROM resp WHERE resolvio IS NULL)           AS sin_indicar,
                       (SELECT COUNT(*) FROM asistidos)                             AS asistidos,
                       (SELECT COUNT(DISTINCT a.incidente_id) FROM asistidos a
                        JOIN reaperturas x ON x.incidente_id = a.incidente_id AND x.fecha > a.primera) AS reabiertos
                """;
        return jdbc.queryForObject(sql, params(filtro), (rs, i) -> new Efectividad(rs.getLong("respuestas"),
                rs.getLong("resolvio"), rs.getLong("no_resolvio"), rs.getLong("sin_indicar"),
                rs.getLong("asistidos"), rs.getLong("reabiertos")));
    }

    private static MapSqlParameterSource params(Filtro f) {
        return new MapSqlParameterSource()
                .addValue("desde", f.desde())
                .addValue("hasta", f.hasta())
                .addValue("categoriaId", f.categoriaId())
                .addValue("prioridadId", f.prioridadId());
    }
}
