package com.centroartesmusicales.backend.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Inicializa la API key global del SDK de Stripe al arrancar. Deliberadamente no falla el
 * arranque si STRIPE_SECRET_KEY está vacío (a diferencia de JWT_SECRET/DB_PASSWORD) — así se
 * puede desplegar este código en Railway antes de configurar las variables de Stripe sin tumbar
 * el resto del backend; StripeService es quien rechaza con un mensaje claro si se intenta usar
 * Stripe sin configurar (ver AppProperties.Stripe#configurado).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StripeConfig {

    private final AppProperties appProperties;

    @PostConstruct
    public void inicializar() {
        if (!appProperties.stripe().configurado()) {
            log.warn("STRIPE_SECRET_KEY no está configurado. Los endpoints de pago con Stripe "
                    + "responderán con error hasta que se configure.");
            return;
        }
        Stripe.apiKey = appProperties.stripe().secretKey();
    }
}
