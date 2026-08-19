package com.centroartesmusicales.backend.dto.pago;

/** url es la Stripe Checkout hospedada — la app solo redirige ahí, nunca colecta datos de tarjeta. */
public record CheckoutSessionResponse(String url) {
}
