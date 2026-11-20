package com.nexus.support.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.nexus.support.domain.AlertaSla;
import com.nexus.support.dto.AlertaSlaDtos.AlertaSlaDto;
import com.nexus.support.dto.CatalogoDtos.Ref;

/**
 * Alertas de SLA (tabla alerta_sla). Las reglas están en SQL para evaluar todos los incidentes abiertos
 * en una sola sentencia y con los mismos cálculos que AnaliticaRepository (SLA de resolución por
 * prioridad.sla_horas y primera respuesta por v_primera_respuesta).
 */
@Repository
public class AlertaSlaRepository {

    /**
     * Alertas que deberían existir en este momento: una fila por (incidente, tipo, nivel, vencimiento).
     * Parámetros :ahora y :fraccion (EstadoSla.FRACCION_ALERTA).
     */
    private static final String VIGENTES = """
            WITH abiertos AS (
                SELECT i.id, i.usuario_id, i.fecha_creacion, p.sla_horas * 60 AS objetivo_resolucion,
                       v.objetivo_minutos AS objetivo_respuesta, v.respondido_en, v.sin_datos
                FROM incidente i
                JOIN prioridad p ON p.id = i.prioridad_id
                JOIN estado_incidente e ON e.id = i.estado_id
                JOIN v_primera_respuesta v ON v.incidente_id = i.id
                WHERE NOT e.es_final
            ), plazos AS (
                SELECT id, usuario_id, CAST('RESOLUCION' AS varchar) AS tipo, objetivo_resolucion AS objetivo,
                       fecha_creacion + make_interval(mins => objetivo_resolucion) AS vence
                FROM abiertos
                UNION ALL
                SELECT id, usuario_id, CAST('PRIMERA_RESPUESTA' AS varchar), objetivo_respuesta,
                       fecha_creacion + make_interval(mins => objetivo_respuesta)
                FROM abiertos
                WHERE objetivo_respuesta IS NOT NULL AND NOT sin_datos AND respondido_en IS NULL
            ), vigentes AS (
                SELECT plazos.*,
                       CASE WHEN CAST(:ahora AS timestamp) > vence THEN 'ESCALAMIENTO'
                            WHEN vence - CAST(:ahora AS timestamp)
                                 <= make_interval(mins => CAST(ROUND(objetivo * :fraccion) AS int)) THEN 'AVISO'
                       END AS nivel
                FROM plazos
            )
            """;

    private static final String SELECT = """
            SELECT a.id, a.incidente_id, i.titulo, p.nombre AS prioridad, e.nombre AS estado_incidente,
                   a.tipo, a.nivel, a.vence, a.generada_en, a.estado, i.usuario_id AS responsable_id,
                   u.nombre AS responsable, ug.nombre AS responsable_al_generar, ua.nombre AS atendida_por,
                   a.fecha_cierre, a.nota
            FROM alerta_sla a
            JOIN incidente i ON i.id = a.incidente_id
            JOIN prioridad p ON p.id = i.prioridad_id
            JOIN estado_incidente e ON e.id = i.estado_id
            JOIN usuario u ON u.id = i.usuario_id
            JOIN usuario ug ON ug.id = a.responsable_id
            LEFT JOIN usuario ua ON ua.id = a.atendida_por_id
            """;

    private static final String FILTRO = """
            WHERE (CAST(:estado AS varchar) IS NULL OR a.estado = CAST(:estado AS varchar))
              AND (CAST(:responsable AS bigint) IS NULL OR i.usuario_id = CAST(:responsable AS bigint))
            """;

    private static final RowMapper<AlertaSlaDto> MAPPER = (rs, n) -> new AlertaSlaDto(rs.getLong("id"),
            new Ref(rs.getLong("incidente_id"), rs.getString("titulo")), rs.getString("prioridad"),
            rs.getString("estado_incidente"), AlertaSla.Tipo.valueOf(rs.getString("tipo")),
            AlertaSla.Nivel.valueOf(rs.getString("nivel")), fecha(rs.getTimestamp("vence")),
            fecha(rs.getTimestamp("generada_en")), AlertaSla.Estado.valueOf(rs.getString("estado")),
            new Ref(rs.getLong("responsable_id"), rs.getString("responsable")), rs.getString("responsable_al_generar"),
            rs.getString("atendida_por"), fecha(rs.getTimestamp("fecha_cierre")), rs.getString("nota"));

    private final NamedParameterJdbcTemplate jdbc;

    public AlertaSlaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Pasa a SUPERADA cada alerta pendiente que ya no corresponde a una alerta vigente. */
    public int superar(LocalDateTime ahora, double fraccion) {
        return jdbc.update(VIGENTES + """
                UPDATE alerta_sla a SET estado = 'SUPERADA', fecha_cierre = CAST(:ahora AS timestamp)
                WHERE a.estado = 'PENDIENTE'
                  AND NOT EXISTS (SELECT 1 FROM vigentes v
                                  WHERE v.id = a.incidente_id AND v.tipo = a.tipo AND v.nivel = a.nivel
                                    AND v.vence = a.vence)
                """, reglas(ahora, fraccion));
    }

    /**
     * Registra las alertas vigentes que aún no existen y reactiva las SUPERADAS que vuelven a aplicar
     * (incidente reabierto). Las ATENDIDAS no se repiten.
     */
    public int generar(LocalDateTime ahora, double fraccion) {
        return jdbc.update(VIGENTES + """
                INSERT INTO alerta_sla (incidente_id, tipo, nivel, vence, responsable_id, generada_en)
                SELECT id, tipo, nivel, vence, usuario_id, CAST(:ahora AS timestamp)
                FROM vigentes WHERE nivel IS NOT NULL
                ON CONFLICT ON CONSTRAINT uk_alerta_sla DO UPDATE
                    SET estado = 'PENDIENTE', fecha_cierre = NULL, generada_en = EXCLUDED.generada_en,
                        responsable_id = EXCLUDED.responsable_id
                    WHERE alerta_sla.estado = 'SUPERADA'
                """, reglas(ahora, fraccion));
    }

    /** Pendientes primero, escalamientos antes que avisos, y luego por vencimiento. */
    public List<AlertaSlaDto> listar(AlertaSla.Estado estado, Long responsableId, int limite) {
        return jdbc.query(SELECT + FILTRO + """
                ORDER BY CASE a.estado WHEN 'PENDIENTE' THEN 0 ELSE 1 END,
                         CASE a.nivel WHEN 'ESCALAMIENTO' THEN 0 ELSE 1 END,
                         a.vence, a.id
                LIMIT :limite
                """, filtro(estado, responsableId).addValue("limite", limite), MAPPER);
    }

    /** Pendientes: {total, escalamientos}. */
    public long[] contarPendientes(Long responsableId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE a.nivel = 'ESCALAMIENTO') AS escalamientos
                FROM alerta_sla a JOIN incidente i ON i.id = a.incidente_id
                """ + FILTRO, filtro(AlertaSla.Estado.PENDIENTE, responsableId),
                (rs, n) -> new long[] {rs.getLong("total"), rs.getLong("escalamientos")});
    }

    public Optional<AlertaSlaDto> buscar(Long id) {
        return jdbc.query(SELECT + "WHERE a.id = :id", new MapSqlParameterSource("id", id), MAPPER)
                .stream().findFirst();
    }

    /** Marca una alerta PENDIENTE como ATENDIDA; devuelve false si ya no estaba pendiente. */
    public boolean atender(Long id, Long actorId, String nota, LocalDateTime ahora) {
        return jdbc.update("""
                UPDATE alerta_sla SET estado = 'ATENDIDA', atendida_por_id = :actor, nota = :nota,
                                      fecha_cierre = CAST(:ahora AS timestamp)
                WHERE id = :id AND estado = 'PENDIENTE'
                """, new MapSqlParameterSource("id", id).addValue("actor", actorId).addValue("nota", nota)
                .addValue("ahora", Timestamp.valueOf(ahora))) == 1;
    }

    private static MapSqlParameterSource reglas(LocalDateTime ahora, double fraccion) {
        return new MapSqlParameterSource("ahora", Timestamp.valueOf(ahora)).addValue("fraccion", fraccion);
    }

    private static MapSqlParameterSource filtro(AlertaSla.Estado estado, Long responsableId) {
        return new MapSqlParameterSource("estado", estado == null ? null : estado.name())
                .addValue("responsable", responsableId);
    }

    private static LocalDateTime fecha(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
