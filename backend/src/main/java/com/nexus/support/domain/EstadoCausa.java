package com.nexus.support.domain;

/** Estado de la causa en una revisión posterior. */
public enum EstadoCausa {
    /** Demostrada: exige causa y evidencia (restricción ck_revision_causa). */
    CONFIRMADA,
    /** Aún no demostrada; el texto de la causa, si lo hay, es una hipótesis. */
    PENDIENTE
}
