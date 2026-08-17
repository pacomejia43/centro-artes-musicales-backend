package com.centroartesmusicales.backend.dto.pago;

import com.centroartesmusicales.backend.model.MetodoPago;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RegistrarTransaccionRequest(
        @NotNull @Positive BigDecimal monto,
        LocalDateTime fecha,
        @NotNull MetodoPago metodoPago,
        String referencia
) {
}
