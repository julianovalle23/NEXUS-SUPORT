-- ===========================================================================
-- NEXUS Support - Consultas analíticas (RF06)
-- Son las mismas consultas que ejecuta AnaliticaRepository.java, con los
-- parámetros escritos como literales para verificarlas manualmente:
--
--   psql -U nexus -d nexus_support -f docs/analisis/consultas-analiticas.sql
--
-- Convenciones:
--   * Resuelto  = estado final (Resuelto o Cerrado).
--   * Tiempo de resolución (horas) = fecha_resolucion - fecha_creacion, solo resueltos.
--   * Cumplimiento SLA = resueltos dentro de prioridad.sla_horas / total resueltos.
--   * Para filtrar, cambie los NULL del CTE "parametros".
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 1. Resumen general: total, tasa de resolución, tiempos y SLA
-- ---------------------------------------------------------------------------
WITH parametros AS (
    SELECT CAST(NULL AS date) AS desde, CAST(NULL AS date) AS hasta,
           CAST(NULL AS bigint) AS categoria_id, CAST(NULL AS bigint) AS prioridad_id
), f AS (
    SELECT i.*, e.es_final AS resuelto,
           CASE WHEN e.es_final AND i.fecha_resolucion IS NOT NULL
                THEN EXTRACT(EPOCH FROM (i.fecha_resolucion - i.fecha_creacion)) / 3600.0 END AS horas
    FROM incidente i
    JOIN estado_incidente e ON e.id = i.estado_id, parametros p
    WHERE (p.desde IS NULL OR i.fecha_creacion >= p.desde)
      AND (p.hasta IS NULL OR i.fecha_creacion < p.hasta + 1)
      AND (p.categoria_id IS NULL OR i.categoria_id = p.categoria_id)
      AND (p.prioridad_id IS NULL OR i.prioridad_id = p.prioridad_id)
)
SELECT COUNT(*)                                                          AS total_incidentes,
       COUNT(*) FILTER (WHERE resuelto)                                  AS resueltos,
       COUNT(*) FILTER (WHERE NOT resuelto)                              AS pendientes,
       ROUND(100.0 * COUNT(*) FILTER (WHERE resuelto) / NULLIF(COUNT(*), 0), 2) AS tasa_resolucion_pct,
       ROUND(CAST(AVG(horas) AS numeric), 2)                             AS tiempo_promedio_h,
       ROUND(CAST(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY horas) AS numeric), 2) AS tiempo_mediana_h,
       ROUND(100.0 * COUNT(*) FILTER (WHERE horas <= pr.sla_horas)
             / NULLIF(COUNT(*) FILTER (WHERE resuelto), 0), 2)           AS cumplimiento_sla_pct
FROM f JOIN prioridad pr ON pr.id = f.prioridad_id;

-- ---------------------------------------------------------------------------
-- 2. Incidentes por categoría: frecuencia, porcentaje, tasa y tiempo promedio
--    (la primera fila es la categoría más recurrente)
-- ---------------------------------------------------------------------------
SELECT c.nombre                                                          AS categoria,
       COUNT(*)                                                          AS total,
       ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2)                AS porcentaje,
       COUNT(*) FILTER (WHERE e.es_final)                                AS resueltos,
       ROUND(100.0 * COUNT(*) FILTER (WHERE e.es_final) / COUNT(*), 2)   AS tasa_resolucion_pct,
       ROUND(CAST(AVG(EXTRACT(EPOCH FROM (i.fecha_resolucion - i.fecha_creacion)) / 3600.0)
             FILTER (WHERE e.es_final) AS numeric), 2)                   AS tiempo_promedio_h
FROM incidente i
JOIN categoria c        ON c.id = i.categoria_id
JOIN estado_incidente e ON e.id = i.estado_id
GROUP BY c.nombre
ORDER BY total DESC, c.nombre;

-- ---------------------------------------------------------------------------
-- 3. Incidentes por prioridad y cumplimiento de SLA
-- ---------------------------------------------------------------------------
SELECT p.nombre                                                          AS prioridad,
       p.sla_horas,
       COUNT(*)                                                          AS total,
       ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2)                AS porcentaje,
       ROUND(CAST(AVG(EXTRACT(EPOCH FROM (i.fecha_resolucion - i.fecha_creacion)) / 3600.0)
             FILTER (WHERE e.es_final) AS numeric), 2)                   AS tiempo_promedio_h,
       ROUND(100.0 * COUNT(*) FILTER (WHERE e.es_final
             AND i.fecha_resolucion - i.fecha_creacion <= p.sla_horas * INTERVAL '1 hour')
             / NULLIF(COUNT(*) FILTER (WHERE e.es_final), 0), 2)         AS cumplimiento_sla_pct
FROM incidente i
JOIN prioridad p        ON p.id = i.prioridad_id
JOIN estado_incidente e ON e.id = i.estado_id
GROUP BY p.nombre, p.nivel, p.sla_horas
ORDER BY p.nivel DESC;

-- ---------------------------------------------------------------------------
-- 4. Incidentes por estado
-- ---------------------------------------------------------------------------
SELECT e.nombre AS estado, COUNT(*) AS total,
       ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2) AS porcentaje
FROM incidente i JOIN estado_incidente e ON e.id = i.estado_id
GROUP BY e.nombre, e.orden
ORDER BY e.orden;

-- ---------------------------------------------------------------------------
-- 5. Tendencia semanal (semanas ISO, inician en lunes). Para diaria use 'day'.
--    Incluye semanas sin incidentes para que la serie sea continua.
-- ---------------------------------------------------------------------------
WITH rango AS (
    SELECT date_trunc('week', MIN(fecha_creacion)) AS ini, date_trunc('week', MAX(fecha_creacion)) AS fin
    FROM incidente
), semanas AS (
    SELECT generate_series(ini, fin, INTERVAL '1 week') AS semana FROM rango
)
SELECT CAST(s.semana AS date)                       AS semana,
       COUNT(i.id)                                  AS total,
       COUNT(i.id) FILTER (WHERE e.es_final)        AS resueltos
FROM semanas s
LEFT JOIN incidente i        ON date_trunc('week', i.fecha_creacion) = s.semana
LEFT JOIN estado_incidente e ON e.id = i.estado_id
GROUP BY s.semana
ORDER BY s.semana;

-- ---------------------------------------------------------------------------
-- 6. Tendencia mensual por categoría (para la interpretación del informe)
-- ---------------------------------------------------------------------------
SELECT to_char(date_trunc('month', i.fecha_creacion), 'YYYY-MM') AS mes,
       c.nombre AS categoria,
       COUNT(*) AS total
FROM incidente i JOIN categoria c ON c.id = i.categoria_id
GROUP BY 1, c.nombre
ORDER BY 1, total DESC;

-- ---------------------------------------------------------------------------
-- 7. Problemas recurrentes (títulos repetidos)
-- ---------------------------------------------------------------------------
SELECT MIN(i.titulo) AS problema, c.nombre AS categoria, COUNT(*) AS ocurrencias,
       ROUND(CAST(AVG(EXTRACT(EPOCH FROM (i.fecha_resolucion - i.fecha_creacion)) / 3600.0) AS numeric), 2) AS tiempo_promedio_h
FROM incidente i JOIN categoria c ON c.id = i.categoria_id
GROUP BY LOWER(i.titulo), c.nombre
HAVING COUNT(*) > 1
ORDER BY ocurrencias DESC, problema
LIMIT 10;

-- ---------------------------------------------------------------------------
-- 8. Uso del asistente RAG
-- ---------------------------------------------------------------------------
SELECT c.estado, COUNT(*) AS consultas, ROUND(AVG(k.puntuacion), 2) AS calificacion_promedio
FROM consulta_ia c
LEFT JOIN respuesta_ia r ON r.consulta_id = c.id
LEFT JOIN calificacion k ON k.respuesta_id = r.id
GROUP BY c.estado
ORDER BY c.estado;

-- ---------------------------------------------------------------------------
-- 9. Efectividad de las respuestas con fuentes (sin filtros)
--    resolvio: lo declarado al calificar (NULL = no indicado).
--    Reapertura: fila ACTUALIZACION o REAPERTURA con estado no final cuya fila anterior del historial era final,
--    posterior a la primera consulta RESPONDIDA del incidente. Es coincidencia temporal, no causa.
-- ---------------------------------------------------------------------------
WITH resp AS (
    SELECT c.incidente_id, c.fecha, k.resolvio
    FROM consulta_ia c
    JOIN respuesta_ia r      ON r.consulta_id = c.id
    LEFT JOIN calificacion k ON k.respuesta_id = r.id
    WHERE c.estado = 'RESPONDIDA'
), asistidos AS (
    SELECT incidente_id, MIN(fecha) AS primera FROM resp GROUP BY incidente_id
), transiciones AS (
    SELECT h.incidente_id, h.fecha, h.tipo, e.es_final,
           LAG(e.es_final) OVER (PARTITION BY h.incidente_id ORDER BY h.fecha, h.id) AS final_antes
    FROM incidente_historial h JOIN estado_incidente e ON e.id = h.estado_id
)
SELECT (SELECT COUNT(*) FROM resp)                        AS respuestas_con_fuentes,
       (SELECT COUNT(*) FROM resp WHERE resolvio)         AS resolvio,
       (SELECT COUNT(*) FROM resp WHERE NOT resolvio)     AS no_resolvio,
       (SELECT COUNT(*) FROM resp WHERE resolvio IS NULL) AS sin_indicar,
       (SELECT COUNT(*) FROM asistidos)                   AS incidentes_asistidos,
       (SELECT COUNT(DISTINCT a.incidente_id) FROM asistidos a
        JOIN transiciones t ON t.incidente_id = a.incidente_id AND t.fecha > a.primera
        WHERE t.tipo IN ('ACTUALIZACION', 'REAPERTURA') AND t.final_antes AND NOT t.es_final) AS reabiertos_tras_asistencia;

-- ---------------------------------------------------------------------------
-- 10. Incidentes por elemento de TI afectado (sin filtros; NULL = sin elemento)
-- ---------------------------------------------------------------------------
SELECT el.nombre AS elemento, el.tipo,
       COUNT(*)                                AS total,
       COUNT(*) FILTER (WHERE NOT e.es_final)  AS pendientes,
       ROUND(CAST(AVG(EXTRACT(EPOCH FROM (i.fecha_resolucion - i.fecha_creacion)) / 3600.0)
             FILTER (WHERE e.es_final) AS numeric), 2) AS tiempo_promedio_h
FROM incidente i
JOIN estado_incidente e  ON e.id = i.estado_id
LEFT JOIN elemento_ti el ON el.id = i.elemento_id
GROUP BY el.id, el.nombre, el.tipo
ORDER BY (el.id IS NULL) DESC, total DESC, el.nombre;

-- ---------------------------------------------------------------------------
-- 11. Problemas con más incidentes vinculados (causa_confirmada solo si hay evidencia)
-- ---------------------------------------------------------------------------
SELECT p.id, p.titulo, p.estado,
       (p.estado = 'CAUSA_CONFIRMADA')         AS causa_confirmada,
       COUNT(i.id)                             AS incidentes,
       COUNT(i.id) FILTER (WHERE NOT e.es_final) AS abiertos
FROM problema p
LEFT JOIN incidente i        ON i.problema_id = p.id
LEFT JOIN estado_incidente e ON e.id = i.estado_id
GROUP BY p.id, p.titulo, p.estado
ORDER BY incidentes DESC, p.id;

-- ---------------------------------------------------------------------------
-- 12. Revisiones posteriores pendientes: incidentes finalizados sin revisión con prioridad
--     de nivel >= 3 (Alta) o resueltos después del SLA de resolución
-- ---------------------------------------------------------------------------
SELECT i.id, i.titulo, p.nombre AS prioridad, i.fecha_resolucion,
       p.nivel >= 3 AS prioridad_alta,
       i.fecha_resolucion > i.fecha_creacion + make_interval(hours => p.sla_horas) AS sla_incumplido
FROM incidente i
JOIN estado_incidente e ON e.id = i.estado_id
JOIN prioridad p        ON p.id = i.prioridad_id
WHERE e.es_final AND i.fecha_resolucion IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM revision_incidente r WHERE r.incidente_id = i.id)
  AND (p.nivel >= 3 OR i.fecha_resolucion > i.fecha_creacion + make_interval(hours => p.sla_horas))
ORDER BY i.fecha_resolucion DESC, i.id DESC;
