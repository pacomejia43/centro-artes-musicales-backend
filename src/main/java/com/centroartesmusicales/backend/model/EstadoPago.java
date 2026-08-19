package com.centroartesmusicales.backend.model;

/**
 * VENCIDO is deliberately not a member here — whether a pago is overdue is a function of
 * fechaLimite vs. today, not an event, so it's computed at read time (see PagoService) instead
 * of persisted (which would need a scheduled job to keep it in sync).
 *
 * REEMBOLSADO is set explicitly by PagoService#marcarReembolso in response to a Stripe refund
 * webhook — it never falls out of the normal pagado-vs-monto recalculation, because a refunded
 * pago must stay visually distinct from one that was simply never paid.
 */
public enum EstadoPago {
    PENDIENTE,
    PARCIAL,
    PAGADO,
    REEMBOLSADO
}
