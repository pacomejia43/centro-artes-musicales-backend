package com.centroartesmusicales.backend.dto.clase;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record SolicitarReagendacionRequest(
        @NotNull @Future LocalDateTime fechaHoraPropuesta,
        Long profesorId,
        String motivo
) {
}
