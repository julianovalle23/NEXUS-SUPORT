package com.nexus.support.exception;

/** Violación de una regla de negocio (HTTP 422). */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
