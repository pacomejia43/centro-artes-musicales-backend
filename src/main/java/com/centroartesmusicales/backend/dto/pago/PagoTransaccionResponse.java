package com.centroartesmusicales.backend.dto.pago;

import com.centroartesmusicales.backend.model.EstadoTransaccion;
import com.centroartesmusicales.backend.model.MetodoPago;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PagoTransaccionResponse(
        Long id,
        BigDecimal monto,
        LocalDateTime fecha,
        MetodoPago metodoPago,
        String referencia,
        EstadoTransaccion estado,
        String motivoRechazo,
        LocalDateTime revisadoAt,
        String stripeCheckoutSessionId,
        String stripeSubscriptionId
) {
}
