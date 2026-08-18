package com.centroartesmusicales.backend.dto.alumno;

import com.centroartesmusicales.backend.model.Instrumento;

public record CupoInstrumentoResponse(
        Instrumento instrumento,
        int cupoMensual
) {
}
