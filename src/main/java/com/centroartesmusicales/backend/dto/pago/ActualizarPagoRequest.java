package com.centroartesmusicales.backend.dto.pago;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/** Edita un cargo ya existente (ej. corregir el período si quedó registrado en el mes equivocado). */
public record ActualizarPagoRequest(
        @Positive BigDecimal monto,
        YearMonth periodo,
        LocalDate fechaLimite,
        String notas
) {
}
