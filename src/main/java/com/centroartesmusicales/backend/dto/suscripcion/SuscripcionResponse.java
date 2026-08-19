package com.centroartesmusicales.backend.dto.suscripcion;

import com.centroartesmusicales.backend.model.EstadoSuscripcion;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SuscripcionResponse(
        Long id,
        EstadoSuscripcion estado,
        BigDecimal monto,
        boolean cancelacionProgramada,
        LocalDate fechaCancelacion
) {
}
