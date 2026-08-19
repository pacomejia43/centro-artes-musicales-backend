package com.centroartesmusicales.backend.mapper;

import com.centroartesmusicales.backend.dto.pago.PagoResponse;
import com.centroartesmusicales.backend.dto.pago.PagoTransaccionResponse;
import com.centroartesmusicales.backend.model.Pago;
import com.centroartesmusicales.backend.model.PagoTransaccion;

import java.math.BigDecimal;

public final class PagoMapper {

    private PagoMapper() {
    }

    public static PagoResponse toResponse(Pago pago, BigDecimal montoPagado, boolean vencido) {
        BigDecimal saldoPendiente = pago.getMonto().subtract(montoPagado);
        return new PagoResponse(
                pago.getId(),
                pago.getAlumno().getId(),
                pago.getAlumno().getUsuario().getNombre(),
                pago.getMonto(),
                pago.getPeriodo().toString(),
                pago.getFechaLimite(),
                pago.getNotas(),
                pago.getEstado(),
                montoPagado,
                saldoPendiente,
                vencido,
                pago.getTransacciones().stream().map(PagoMapper::toResponse).toList()
        );
    }

    public static PagoTransaccionResponse toResponse(PagoTransaccion transaccion) {
        return new PagoTransaccionResponse(
                transaccion.getId(),
                transaccion.getMonto(),
                transaccion.getFecha(),
                transaccion.getMetodoPago(),
                transaccion.getReferencia(),
                transaccion.getEstado(),
                transaccion.getMotivoRechazo(),
                transaccion.getRevisadoAt(),
                transaccion.getStripeCheckoutSessionId(),
                transaccion.getStripeSubscriptionId()
        );
    }
}
