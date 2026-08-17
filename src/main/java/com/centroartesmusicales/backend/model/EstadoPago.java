package com.centroartesmusicales.backend.model;

/**
 * VENCIDO is deliberately not a member here — whether a pago is overdue is a function of
 * fechaLimite vs. today, not an event, so it's computed at read time (see PagoService) instead
 * of persisted (which would need a scheduled job to keep it in sync).
 */
public enum EstadoPago {
    PENDIENTE,
    PARCIAL,
    PAGADO
}
