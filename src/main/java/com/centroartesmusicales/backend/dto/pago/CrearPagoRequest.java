package com.centroartesmusicales.backend.dto.pago;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public record CrearPagoRequest(
        @Positive BigDecimal monto,
        YearMonth periodo,
        @NotNull LocalDate fechaLimite,
        String notas
) {
}
