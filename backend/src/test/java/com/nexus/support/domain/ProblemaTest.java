package com.nexus.support.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProblemaTest {

    @Test
    void cerrarRegistraFechaDeCierreYReabrirLaElimina() {
        Problema p = new Problema(new Usuario());

        p.cambiarEstado(EstadoProblema.CERRADO);
        assertThat(p.getFechaCierre()).isNotNull();

        p.cambiarEstado(EstadoProblema.EN_ANALISIS);
        assertThat(p.getFechaCierre()).isNull();
    }

    @Test
    void laCausaSoloEstaRespaldadaConCausaYEvidencia() {
        Problema p = new Problema(new Usuario());
        assertThat(p.causaRespaldada()).isFalse();

        p.setCausa("Certificado del servidor de correo vencido");
        assertThat(p.causaRespaldada()).isFalse();

        p.setEvidenciaCausa("   ");
        assertThat(p.causaRespaldada()).isFalse();

        p.setEvidenciaCausa("Registro del servidor: certificado expirado el 2026-11-01");
        assertThat(p.causaRespaldada()).isTrue();
    }
}
