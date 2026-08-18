package com.centroartesmusicales.backend.dto.pago;

import com.centroartesmusicales.backend.model.EstadoPago;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PagoResponse(
        Long id,
        Long alumnoId,
        String alumnoNombre,
        BigDecimal monto,
        String periodo,
        LocalDate fechaLimite,
        String notas,
        EstadoPago estado,
        BigDecimal montoPagado,
        BigDecimal saldoPendiente,
        boolean vencido,
        List<PagoTransaccionResponse> transacciones
) {
}
