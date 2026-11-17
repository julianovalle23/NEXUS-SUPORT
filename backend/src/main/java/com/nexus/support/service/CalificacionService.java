package com.nexus.support.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.support.domain.Calificacion;
import com.nexus.support.domain.RespuestaIA;
import com.nexus.support.dto.AsistenciaDtos.CalificacionDto;
import com.nexus.support.dto.AsistenciaDtos.CalificacionRequest;
import com.nexus.support.exception.RecursoNoEncontradoException;
import com.nexus.support.repository.CalificacionRepository;
import com.nexus.support.repository.RespuestaIARepository;

@Service
@Transactional
public class CalificacionService {

    private final RespuestaIARepository respuestas;
    private final CalificacionRepository calificaciones;

    public CalificacionService(RespuestaIARepository respuestas, CalificacionRepository calificaciones) {
        this.respuestas = respuestas;
        this.calificaciones = calificaciones;
    }

    /** Crea la calificación o la reemplaza si la respuesta ya había sido calificada. */
    public CalificacionDto calificar(Long respuestaId, CalificacionRequest req) {
        RespuestaIA respuesta = respuestas.findById(respuestaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Respuesta", respuestaId));
        Calificacion c = calificaciones.findByRespuestaId(respuestaId).orElseGet(() -> new Calificacion(respuesta));
        c.setPuntuacion(req.puntuacion().shortValue());
        c.setComentario(req.comentario() == null || req.comentario().isBlank() ? null : req.comentario().trim());
        return CalificacionDto.de(calificaciones.saveAndFlush(c));
    }

    @Transactional(readOnly = true)
    public CalificacionDto obtener(Long respuestaId) {
        return calificaciones.findByRespuestaId(respuestaId).map(CalificacionDto::de)
                .orElseThrow(() -> new RecursoNoEncontradoException("Calificación de la respuesta", respuestaId));
    }
}
