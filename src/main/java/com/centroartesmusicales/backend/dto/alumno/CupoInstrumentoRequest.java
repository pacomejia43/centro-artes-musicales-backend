package com.centroartesmusicales.backend.dto.alumno;

import com.centroartesmusicales.backend.model.Instrumento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CupoInstrumentoRequest(
        @NotNull Instrumento instrumento,
        @NotNull @Positive Integer cupoMensual
) {
}
