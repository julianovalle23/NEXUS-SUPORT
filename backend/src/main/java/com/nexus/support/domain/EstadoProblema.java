package com.nexus.support.domain;

/** Ciclo de un problema. Se puede cerrar sin causa confirmada (por ejemplo, si no se reprodujo). */
public enum EstadoProblema {
    ABIERTO,
    EN_ANALISIS,
    /** Exige causa y evidencia registradas (restricción ck_problema_causa). */
    CAUSA_CONFIRMADA,
    CERRADO
}
