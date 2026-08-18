package com.centroartesmusicales.backend.dto.pago;

import java.math.BigDecimal;

public record ConfiguracionPagoResponse(
        BigDecimal montoMensualDefault
) {
}
