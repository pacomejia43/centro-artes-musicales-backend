package com.centroartesmusicales.backend.model;

/**
 * FALLIDA y REEMBOLSADA son estados terminales que llegan solo desde webhooks de Stripe
 * (payment_intent.payment_failed / invoice.payment_failed y charge.refunded respectivamente) —
 * ninguna de las dos mueve el saldo del Pago: sumConfirmadoByPagoId solo suma CONFIRMADA, así
 * que ambas se comportan como PENDIENTE/RECHAZADA a efectos del balance.
 */
public enum EstadoTransaccion {
    PENDIENTE,
    CONFIRMADA,
    RECHAZADA,
    FALLIDA,
    REEMBOLSADA
}
