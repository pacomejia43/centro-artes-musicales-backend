package com.centroartesmusicales.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * En este proyecto Usuario.email es en realidad un "ID de usuario" (ver el commit que renombró la
 * etiqueta del panel de "Email" a "ID usuario") — un alumno real en producción tenía ahí
 * "Valentina1", y Customer.create de Stripe rechazaba la solicitud completa por formato de email
 * inválido hasta que se agregó este filtro.
 */
class StripeServiceTest {

    @Test
    void pareceEmailValido_esVerdaderoParaUnCorreoReal() {
        assertThat(StripeService.pareceEmailValido("valentina@test.com")).isTrue();
    }

    @Test
    void pareceEmailValido_esFalsoParaUnIdDeUsuarioSinArroba() {
        assertThat(StripeService.pareceEmailValido("Valentina1")).isFalse();
    }

    @Test
    void pareceEmailValido_esFalsoParaNull() {
        assertThat(StripeService.pareceEmailValido(null)).isFalse();
    }

    @Test
    void pareceEmailValido_esFalsoParaTextoConArrobaPeroSinDominio() {
        assertThat(StripeService.pareceEmailValido("valentina@")).isFalse();
    }
}
