package com.centroartesmusicales.backend.model;

/**
 * Espejo simplificado (en español) del status de una Stripe Subscription, actualizado siempre
 * desde StripeWebhookService en respuesta a customer.subscription.* — nunca se decide en el
 * frontend ni se infiere localmente sin un evento de Stripe de por medio.
 */
public enum EstadoSuscripcion {
    ACTIVA,
    PAST_DUE,
    CANCELADA,
    INCOMPLETA
}
