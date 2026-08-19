package com.centroartesmusicales.backend.dto.pago;

/** La publishable key no es secreta (está diseñada para vivir en el frontend); se sirve desde el
 *  backend en vez de hardcodearla en el sitio estático para poder rotarla vía variable de entorno
 *  sin tocar el código del frontend. */
public record StripeConfigResponse(String stripePublishableKey) {
}
