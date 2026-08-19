package com.centroartesmusicales.backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre el caso real que rompió el checkout en producción: un STRIPE_SECRET_KEY pegado en
 * Railway con un salto de línea o espacio de más, que el SDK de Stripe rechaza por completo con
 * "API key is invalid, as it contains whitespace" (ver StripeConfig).
 */
class AppPropertiesTest {

    @Test
    void stripe_recortaEspaciosAlPrincipioYAlFinalDeCadaClave() {
        var stripe = new AppProperties.Stripe(" sk_test_abc \n", "\tpk_test_abc", "whsec_abc ");

        assertThat(stripe.secretKey()).isEqualTo("sk_test_abc");
        assertThat(stripe.publishableKey()).isEqualTo("pk_test_abc");
        assertThat(stripe.webhookSecret()).isEqualTo("whsec_abc");
    }

    @Test
    void stripe_configurado_esFalsoSiLaClaveSecretaEraSoloEspacios() {
        var stripe = new AppProperties.Stripe("   ", "pk_test_abc", "whsec_abc");

        assertThat(stripe.configurado()).isFalse();
    }

    @Test
    void stripe_configurado_esVerdaderoConUnaClaveSecretaValida() {
        var stripe = new AppProperties.Stripe(" sk_test_abc ", "pk_test_abc", "whsec_abc");

        assertThat(stripe.configurado()).isTrue();
    }

    @Test
    void stripe_toleraValoresNulos() {
        var stripe = new AppProperties.Stripe(null, null, null);

        assertThat(stripe.secretKey()).isNull();
        assertThat(stripe.configurado()).isFalse();
    }
}
